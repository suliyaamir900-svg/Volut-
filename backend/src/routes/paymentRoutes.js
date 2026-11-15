const express = require("express");
const crypto = require("crypto");
const {
  resolveYoupiConfig,
  toPublicMerchantInfo,
  buildUpiPayUri,
  verifyYoupiSignature,
  createYoupiPaymentSession,
  evaluateSettlementFulfillment,
  YoupiNotConfiguredError,
} = require("../services/youpiService");
const { DEFAULT_PLANS, computePlanDurationMs } = require("../services/entitlementHelpers");

function createPaymentRouter(getAdminFn) {
  const router = express.Router();

  // Authenticated: fetch admin-configured merchant UPI / Bank display details (no secrets)
  router.get("/merchant-info", async (req, res) => {
    try {
      const config = await resolveYoupiConfig(req.db);
      return res.json(toPublicMerchantInfo(config));
    } catch (err) {
      return res.status(500).json({ error: "MERCHANT_INFO_ERROR", message: err.message });
    }
  });

  // Authenticated: create YouPI PG or Direct Merchant UPI payment order
  router.post("/create-order", async (req, res) => {
    const { planId, idempotencyKey, paymentMethod = "AUTO" } = req.body || {};
    if (!planId || typeof planId !== "string") {
      return res.status(400).json({ error: "INVALID_PLAN", message: "planId is required" });
    }

    try {
      const planSnap = await req.db.collection("plans").doc(planId).get();
      const plan = planSnap.exists ? planSnap.data() : DEFAULT_PLANS[planId];
      if (!plan || !plan.active || Number(plan.priceInr) <= 0) {
        return res.status(404).json({
          error: "PLAN_UNAVAILABLE",
          message: "Selected paid plan is unavailable or inactive",
        });
      }

      const amountInr = Number(plan.priceInr);
      const amountPaise = Math.round(amountInr * 100);
      const orderId = `yr_ord_${crypto.randomUUID()}`;
      const idemKey = idempotencyKey || orderId;
      const config = await resolveYoupiConfig(req.db);

      const useDirectUpi =
        (paymentMethod === "DIRECT_UPI" && config.hasDirectUpiOrBank) ||
        (!config.isConfigured && config.hasDirectUpiOrBank);

      let youpiPaymentId = "";
      let checkoutUrl = "";
      let status = "PENDING";
      let upiPayUri = buildUpiPayUri({
        paymentUpiId: config.paymentUpiId,
        payeeName: config.payeeName,
        amountInr,
        orderId,
        planName: plan.name,
      });

      if (!useDirectUpi) {
        if (!config.isConfigured) {
          throw new YoupiNotConfiguredError(config.missing);
        }
        const sessionResponse = await createYoupiPaymentSession({
          config,
          amountPaise,
          orderId,
          idempotencyKey: idemKey,
          customer: {
            name: req.user.name || "YaadRakshak User",
            email: req.user.email || "",
          },
          metadata: {
            userId: req.user.uid,
            planId,
          },
        });
        const payload = sessionResponse.payload || sessionResponse;
        youpiPaymentId = payload.paymentId || "";
        checkoutUrl = payload.checkoutUrl || "";
        status = payload.status || "PENDING";
      } else {
        status = "PENDING_PAYMENT";
      }

      const now = new Date();
      const orderRecord = {
        orderId,
        userId: req.user.uid,
        userEmail: req.user.email || "",
        planId,
        planName: plan.name || planId,
        amountPaise,
        currency: "INR",
        status,
        paymentMethod: useDirectUpi ? "DIRECT_UPI" : "YOUPI_PG",
        youpiPaymentId,
        checkoutUrl,
        upiPayUri,
        utrReference: "",
        idempotencyKey: idemKey,
        createdAt: now,
        createdAtMillis: now.getTime(),
        updatedAt: now,
      };

      await req.db
        .collection("users")
        .doc(req.user.uid)
        .collection("payment_orders")
        .doc(orderId)
        .set(orderRecord);

      await req.db.collection("order_lookup").doc(orderId).set({
        orderId,
        userId: req.user.uid,
        userEmail: req.user.email || "",
        planId,
        planName: plan.name || planId,
        amountPaise,
        currency: "INR",
        status,
        paymentMethod: orderRecord.paymentMethod,
        utrReference: "",
        createdAt: now,
        createdAtMillis: now.getTime(),
        updatedAt: now,
      });

      return res.status(201).json({
        ...orderRecord,
        merchantInfo: toPublicMerchantInfo(config),
      });
    } catch (err) {
      if (err instanceof YoupiNotConfiguredError) {
        return res.status(503).json({
          error: err.code,
          message: err.message,
          missingFields: err.missingFields,
        });
      }
      return res.status(err.statusCode || 502).json({
        error: "PAYMENT_ORDER_FAILED",
        message: err.message,
      });
    }
  });

  // Authenticated: user submits 12-digit UTR/reference after UPI/Bank payment for Admin/Gateway verification
  router.post("/orders/:orderId/submit-utr", async (req, res) => {
    const { orderId } = req.params;
    const { utrReference } = req.body || {};
    const cleanUtr = String(utrReference || "").trim();
    if (cleanUtr.length < 6) {
      return res.status(400).json({ error: "INVALID_UTR", message: "Enter a valid UTR / transaction reference." });
    }

    try {
      const orderRef = req.db
        .collection("users")
        .doc(req.user.uid)
        .collection("payment_orders")
        .doc(orderId);
      const orderSnap = await orderRef.get();
      if (!orderSnap.exists) {
        return res.status(404).json({ error: "ORDER_NOT_FOUND", message: "Order not found." });
      }
      const existing = orderSnap.data();
      if (existing.status === "COMPLETED") {
        return res.json({ orderId, status: "COMPLETED", utrReference: existing.utrReference || cleanUtr });
      }

      const now = new Date();
      await orderRef.set(
        {
          utrReference: cleanUtr,
          status: "PENDING_RECONCILIATION",
          updatedAt: now,
        },
        { merge: true }
      );
      await req.db.collection("order_lookup").doc(orderId).set(
        {
          utrReference: cleanUtr,
          status: "PENDING_RECONCILIATION",
          updatedAt: now,
        },
        { merge: true }
      );
      await req.db.collection("audit_logs").add({
        actorUid: req.user.uid,
        action: "PAYMENT_UTR_SUBMITTED",
        targetType: "PAYMENT_ORDER",
        targetId: orderId,
        status: "PENDING_RECONCILIATION",
        detail: `utr=${cleanUtr}; plan=${existing.planId}`,
        createdAt: now,
      });

      return res.json({
        orderId,
        status: "PENDING_RECONCILIATION",
        utrReference: cleanUtr,
      });
    } catch (err) {
      return res.status(500).json({ error: "UTR_SUBMIT_ERROR", message: err.message });
    }
  });

  // Authenticated: poll a single order's server status
  router.get("/orders/:orderId", async (req, res) => {
    try {
      const snap = await req.db
        .collection("users")
        .doc(req.user.uid)
        .collection("payment_orders")
        .doc(req.params.orderId)
        .get();
      if (!snap.exists) {
        return res.status(404).json({ error: "ORDER_NOT_FOUND", message: "Order not found." });
      }
      return res.json(snap.data());
    } catch (err) {
      return res.status(500).json({ error: "ORDER_LOOKUP_ERROR", message: err.message });
    }
  });

  // Authenticated: refresh / restore subscription entitlement from server (with adFree + plan quotas)
  router.get("/entitlement", async (req, res) => {
    try {
      const userSnap = await req.db.collection("users").doc(req.user.uid).get();
      const userDoc = userSnap.exists ? userSnap.data() : {};

      const subSnap = await req.db
        .collection("users")
        .doc(req.user.uid)
        .collection("subscriptions")
        .doc("current")
        .get();

      let sub = subSnap.exists
        ? subSnap.data()
        : {
            planId: userDoc.subscriptionTier || "free",
            status: "ACTIVE",
            expiresAtMillis: 0,
            verifiedByServer: true,
          };

      if (sub.expiresAtMillis && Date.now() > sub.expiresAtMillis && sub.status === "ACTIVE") {
        await subSnap.ref.update({ status: "EXPIRED", updatedAt: new Date() });
        await req.db.collection("users").doc(req.user.uid).set(
          { subscriptionTier: "free" },
          { merge: true }
        );
        sub = { ...sub, status: "EXPIRED", planId: "free" };
      }

      const activePlanId = sub.status === "ACTIVE" ? (sub.planId || "free") : "free";
      const planSnap = await req.db.collection("plans").doc(activePlanId).get();
      const plan = planSnap.exists ? planSnap.data() : (DEFAULT_PLANS[activePlanId] || DEFAULT_PLANS.free);

      const adFree =
        typeof userDoc.adFree === "boolean"
          ? userDoc.adFree
          : Boolean(plan.adFree);

      return res.json({
        planId: activePlanId,
        planName: plan.name || activePlanId,
        status: sub.status || "ACTIVE",
        expiresAtMillis: sub.expiresAtMillis || 0,
        verifiedByServer: Boolean(sub.verifiedByServer ?? true),
        adFree,
        aiMonthlyCredits: Number(plan.aiMonthlyCredits || 20),
        storageLimitMb: Number(plan.storageLimitMb || 100),
        aiCreditsRemaining:
          typeof userDoc.aiCreditsRemaining === "number"
            ? userDoc.aiCreditsRemaining
            : Number(plan.aiMonthlyCredits || 20),
      });
    } catch (err) {
      return res.status(500).json({ error: "ENTITLEMENT_ERROR", message: err.message });
    }
  });

  // Public YouPI PG Webhook (MUST use express.raw({ type: "application/json" }) BEFORE JSON parsing)
  const webhookHandler = async (req, res) => {
    try {
      const { db } = getAdminFn();
      const config = await resolveYoupiConfig(db);
      if (!config.webhookSecret) {
        return res.status(503).json({ error: "YOUPI_NOT_CONFIGURED", message: "Webhook secret missing" });
      }

      const rawBody = req.body;
      const signatureHeader = req.headers["x-gateway-signature"];
      if (!verifyYoupiSignature(rawBody, signatureHeader, config.webhookSecret)) {
        return res.status(401).json({ error: "INVALID_SIGNATURE", message: "Webhook signature mismatch" });
      }

      const gatewayEvent = req.headers["x-gateway-event"] || "";
      const deliveryId = req.headers["x-gateway-delivery-id"] || "";
      const payload = JSON.parse(rawBody.toString("utf8"));
      const orderId = payload.orderId;

      if (!orderId) {
        return res.status(400).json({ error: "MISSING_ORDER_ID" });
      }

      if (deliveryId) {
        const deliveryRef = db.collection("webhook_deliveries").doc(String(deliveryId));
        const deliverySnap = await deliveryRef.get();
        if (deliverySnap.exists) {
          return res.status(200).json({ received: true, deduplicated: true });
        }
        await deliveryRef.set({
          deliveryId,
          orderId,
          gatewayEvent,
          receivedAt: new Date(),
        });
      }

      const lookupSnap = await db.collection("order_lookup").doc(orderId).get();
      if (!lookupSnap.exists) {
        return res.status(404).json({ error: "ORDER_NOT_FOUND" });
      }
      const lookup = lookupSnap.data();
      const orderRef = db
        .collection("users")
        .doc(lookup.userId)
        .collection("payment_orders")
        .doc(orderId);

      if (
        Number(payload.amount) !== Number(lookup.amountPaise) ||
        String(payload.currency || "INR").toUpperCase() !== lookup.currency
      ) {
        await db.collection("audit_logs").add({
          actorUid: lookup.userId,
          action: "WEBHOOK_AMOUNT_MISMATCH",
          targetType: "PAYMENT_ORDER",
          targetId: orderId,
          status: "REJECTED",
          detail: `expected=${lookup.amountPaise} got=${payload.amount}`,
          createdAt: new Date(),
        });
        return res.status(400).json({ error: "AMOUNT_CURRENCY_MISMATCH" });
      }

      const { shouldActivate, orderStatus } = evaluateSettlementFulfillment(gatewayEvent, payload);
      const utrRef = String(payload.utr || payload.utrReference || "");

      await db.runTransaction(async (tx) => {
        const orderSnap = await tx.get(orderRef);
        const existingOrder = orderSnap.exists ? orderSnap.data() : {};
        const alreadyCompleted = existingOrder.status === "COMPLETED";
        const finalOrderStatus = alreadyCompleted && shouldActivate ? "COMPLETED" : orderStatus;

        tx.set(
          orderRef,
          {
            status: finalOrderStatus,
            utrReference: utrRef || existingOrder.utrReference || "",
            updatedAt: new Date(),
          },
          { merge: true }
        );

        tx.set(
          db.collection("order_lookup").doc(orderId),
          {
            status: finalOrderStatus,
            utrReference: utrRef || existingOrder.utrReference || "",
            updatedAt: new Date(),
          },
          { merge: true }
        );

        const txId = payload.transactionId || deliveryId || `tx_${Date.now()}`;
        const txRef = db
          .collection("users")
          .doc(lookup.userId)
          .collection("payment_transactions")
          .doc(txId);

        tx.set(txRef, {
          userId: lookup.userId,
          orderId,
          gatewayEvent: String(gatewayEvent || payload.status || ""),
          settlementState: String(payload.settlementState || ""),
          amountPaise: Number(payload.amount),
          utrReference: utrRef,
          verifiedSignature: true,
          processedAt: new Date(),
        });

        if (shouldActivate && !alreadyCompleted) {
          const planSnap = await tx.get(db.collection("plans").doc(lookup.planId));
          const plan = planSnap.exists ? planSnap.data() : (DEFAULT_PLANS[lookup.planId] || DEFAULT_PLANS.monthly);
          const expiresAtMillis = Date.now() + computePlanDurationMs(plan);

          const subRef = db
            .collection("users")
            .doc(lookup.userId)
            .collection("subscriptions")
            .doc("current");

          tx.set(subRef, {
            userId: lookup.userId,
            planId: lookup.planId,
            status: "ACTIVE",
            expiresAtMillis,
            verifiedByServer: true,
            adFree: Boolean(plan.adFree),
            updatedAt: new Date(),
          });

          const userRef = db.collection("users").doc(lookup.userId);
          tx.set(
            userRef,
            {
              userId: lookup.userId,
              subscriptionTier: lookup.planId,
              adFree: Boolean(plan.adFree),
              aiCreditsRemaining: Number(plan.aiMonthlyCredits || 500),
              storageLimitBytes: Number(plan.storageLimitMb || 5120) * 1024 * 1024,
              updatedAt: new Date(),
            },
            { merge: true }
          );
        }
      });

      return res.status(200).json({ received: true, orderStatus, activated: shouldActivate });
    } catch (err) {
      return res.status(400).json({ error: "WEBHOOK_ERROR", message: err.message });
    }
  };

  return { router, webhookHandler, DEFAULT_PLANS, computePlanDurationMs };
}

module.exports = { createPaymentRouter, DEFAULT_PLANS, computePlanDurationMs };

const express = require("express");
const { listGroqModels, testGroqConnection, resolveGroqConfig } = require("../services/groqService");
const { resolveYoupiConfig, toPublicMerchantInfo } = require("../services/youpiService");
const { DEFAULT_PLANS, computePlanDurationMs, summarizeAiUsage } = require("../services/entitlementHelpers");

function toMillis(val) {
  if (!val) return 0;
  if (typeof val === "number") return val;
  if (val instanceof Date) return val.getTime();
  if (typeof val.toMillis === "function") return val.toMillis();
  if (typeof val._seconds === "number") return val._seconds * 1000;
  return 0;
}

function createAdminRouter() {
  const router = express.Router();

  router.get("/overview", async (req, res) => {
    try {
      const groqCfg = await resolveGroqConfig(req.db);
      const youpiCfg = await resolveYoupiConfig(req.db);

      let plansSnap = await req.db.collection("plans").get();
      if (plansSnap.empty) {
        const batch = req.db.batch();
        for (const [pid, plan] of Object.entries(DEFAULT_PLANS)) {
          batch.set(req.db.collection("plans").doc(pid), { ...plan, updatedAt: new Date() });
        }
        await batch.commit();
        plansSnap = await req.db.collection("plans").get();
      }

      const logsSnap = await req.db
        .collection("audit_logs")
        .orderBy("createdAt", "desc")
        .limit(60)
        .get();
      const usersSnap = await req.db.collection("users").limit(200).get();
      const ordersSnap = await req.db
        .collection("order_lookup")
        .orderBy("createdAtMillis", "desc")
        .limit(60)
        .get()
        .catch(async () => req.db.collection("order_lookup").limit(60).get());

      const auditLogs = logsSnap.docs.map((d) => {
        const data = d.data();
        return {
          id: d.id,
          actorUid: data.actorUid || "",
          action: data.action || "",
          targetType: data.targetType || "",
          targetId: data.targetId || "",
          status: data.status || "",
          detail: data.detail || "",
          tokensUsed: Number(data.tokensUsed || 0),
          createdAtMillis: toMillis(data.createdAt),
        };
      });

      const plans = plansSnap.docs
        .map((d) => {
          const p = d.data();
          return {
            planId: p.planId || d.id,
            name: p.name || d.id,
            priceInr: Number(p.priceInr || 0),
            interval: p.interval || "monthly",
            aiMonthlyCredits: Number(p.aiMonthlyCredits || 0),
            storageLimitMb: Number(p.storageLimitMb || 100),
            adFree: Boolean(p.adFree),
            active: Boolean(p.active !== false),
          };
        })
        .sort((a, b) => a.priceInr - b.priceInr);

      const planMap = Object.fromEntries(plans.map((p) => [p.planId, p]));

      const users = usersSnap.docs.map((d) => {
        const u = d.data();
        const tier = u.subscriptionTier || "free";
        const tierPlan = planMap[tier] || DEFAULT_PLANS.free;
        return {
          userId: u.userId || d.id,
          email: u.email || "",
          displayName: u.displayName || "User",
          role: u.role || "user",
          subscriptionTier: tier,
          adFree: typeof u.adFree === "boolean" ? u.adFree : Boolean(tierPlan.adFree),
          aiCreditsRemaining: Number(u.aiCreditsRemaining ?? tierPlan.aiMonthlyCredits ?? 20),
          storageLimitMb: Math.round(Number(u.storageLimitBytes || (tierPlan.storageLimitMb * 1024 * 1024)) / (1024 * 1024)),
        };
      });

      const payments = ordersSnap.docs
        .map((d) => {
          const o = d.data();
          return {
            orderId: o.orderId || d.id,
            userId: o.userId || "",
            userEmail: o.userEmail || "",
            planId: o.planId || "",
            planName: o.planName || o.planId || "",
            amountPaise: Number(o.amountPaise || 0),
            currency: o.currency || "INR",
            status: o.status || "PENDING",
            paymentMethod: o.paymentMethod || "YOUPI_PG",
            utrReference: o.utrReference || "",
            createdAtMillis: Number(o.createdAtMillis || toMillis(o.createdAt)),
          };
        })
        .sort((a, b) => b.createdAtMillis - a.createdAtMillis);

      return res.json({
        groq: {
          isConfigured: groqCfg.isConfigured,
          maskedKey: groqCfg.maskedKey,
          defaultModel: groqCfg.defaultModel,
          maxTokens: groqCfg.maxTokens,
          dailyFreeLimit: groqCfg.dailyFreeLimit,
          dailyPremiumLimit: groqCfg.dailyPremiumLimit,
        },
        youpi: {
          isConfigured: youpiCfg.isConfigured,
          hasDirectUpiOrBank: youpiCfg.hasDirectUpiOrBank,
          missing: youpiCfg.missing,
          apiUrl: youpiCfg.apiUrl,
          merchantId: youpiCfg.merchantId,
          returnUrl: youpiCfg.returnUrl,
          paymentUpiId: youpiCfg.paymentUpiId,
          payeeName: youpiCfg.payeeName,
          bankName: youpiCfg.bankName,
          bankAccountNumber: youpiCfg.bankAccountNumber,
          maskedBankAccount: youpiCfg.maskedBankAccount,
          ifscCode: youpiCfg.ifscCode,
        },
        aiUsageStats: summarizeAiUsage(auditLogs),
        plans,
        usersCount: users.length,
        users,
        payments,
        auditLogs,
      });
    } catch (err) {
      return res.status(500).json({ error: "ADMIN_OVERVIEW_ERROR", message: err.message });
    }
  });

  router.put("/groq-config", async (req, res) => {
    const { apiKey, defaultModel, maxTokens, dailyFreeLimit, dailyPremiumLimit } = req.body || {};
    const update = {
      defaultModel: defaultModel || "llama-3.3-70b-versatile",
      maxTokens: Number(maxTokens || 1024),
      dailyFreeLimit: Number(dailyFreeLimit || 20),
      dailyPremiumLimit: Number(dailyPremiumLimit || 500),
      updatedAt: new Date(),
      updatedBy: req.user.uid,
    };
    if (typeof apiKey === "string" && apiKey.trim().length > 0) {
      update.apiKey = apiKey.trim();
    }
    await req.db.collection("app_config").doc("groq").set(update, { merge: true });
    await req.db.collection("audit_logs").add({
      actorUid: req.user.uid,
      action: "ADMIN_UPDATE_GROQ_CONFIG",
      targetType: "APP_CONFIG",
      status: "SUCCESS",
      detail: `defaultModel=${update.defaultModel}; keyUpdated=${Boolean(update.apiKey)}`,
      createdAt: new Date(),
    });
    return res.json({ saved: true });
  });

  router.get("/groq-models", async (req, res) => {
    try {
      const data = await listGroqModels(req.db);
      return res.json(data);
    } catch (err) {
      return res.status(err.statusCode || 503).json({ error: err.code || "GROQ_ERROR", message: err.message });
    }
  });

  router.post("/groq-test", async (req, res) => {
    try {
      const result = await testGroqConnection(req.db, req.body?.model);
      return res.json(result);
    } catch (err) {
      return res.status(err.statusCode || 503).json({ error: err.code || "GROQ_ERROR", message: err.message });
    }
  });

  router.put("/youpi-config", async (req, res) => {
    const {
      apiUrl,
      merchantId,
      merchantApiKey,
      webhookSecret,
      returnUrl,
      paymentUpiId,
      payeeName,
      bankName,
      bankAccountNumber,
      ifscCode,
    } = req.body || {};

    const update = {
      apiUrl: String(apiUrl || "").trim(),
      merchantId: String(merchantId || "").trim(),
      returnUrl: String(returnUrl || "https://yaadrakshak.app/payments/return").trim(),
      paymentUpiId: String(paymentUpiId || "").trim(),
      payeeName: String(payeeName || "YaadRakshak").trim(),
      bankName: String(bankName || "").trim(),
      bankAccountNumber: String(bankAccountNumber || "").trim(),
      ifscCode: String(ifscCode || "").trim().toUpperCase(),
      updatedAt: new Date(),
      updatedBy: req.user.uid,
    };
    if (typeof merchantApiKey === "string" && merchantApiKey.trim()) {
      update.merchantApiKey = merchantApiKey.trim();
    }
    if (typeof webhookSecret === "string" && webhookSecret.trim()) {
      update.webhookSecret = webhookSecret.trim();
    }

    await req.db.collection("app_config").doc("youpi_pg").set(update, { merge: true });

    const resolved = await resolveYoupiConfig(req.db);
    await req.db
      .collection("app_config")
      .doc("payments_public")
      .set(
        {
          ...toPublicMerchantInfo(resolved),
          updatedAt: new Date(),
        },
        { merge: true }
      );

    await req.db.collection("audit_logs").add({
      actorUid: req.user.uid,
      action: "ADMIN_UPDATE_PAYMENT_CONFIG",
      targetType: "APP_CONFIG",
      status: "SUCCESS",
      detail: `merchantId=${update.merchantId}; upi=${update.paymentUpiId}; bank=${update.bankName}`,
      createdAt: new Date(),
    });

    return res.json({ saved: true });
  });

  router.put("/plans/:planId", async (req, res) => {
    const planId = String(req.params.planId || "").trim().toLowerCase().replace(/[^a-z0-9_-]/g, "");
    if (!planId) {
      return res.status(400).json({ error: "INVALID_PLAN_ID", message: "Invalid planId" });
    }
    const { name, priceInr, interval, aiMonthlyCredits, storageLimitMb, adFree, active } = req.body || {};
    await req.db.collection("plans").doc(planId).set(
      {
        planId,
        name: String(name || planId).trim(),
        priceInr: Math.max(0, Number(priceInr || 0)),
        interval: String(interval || "monthly").trim(),
        aiMonthlyCredits: Math.max(0, Number(aiMonthlyCredits || 0)),
        storageLimitMb: Math.max(0, Number(storageLimitMb || 100)),
        adFree: Boolean(adFree),
        active: Boolean(active !== false),
        updatedAt: new Date(),
      },
      { merge: true }
    );
    await req.db.collection("audit_logs").add({
      actorUid: req.user.uid,
      action: "ADMIN_UPSERT_PLAN",
      targetType: "PLAN",
      targetId: planId,
      status: "SUCCESS",
      detail: `name=${name}; price=INR ${priceInr}; adFree=${Boolean(adFree)}`,
      createdAt: new Date(),
    });
    return res.json({ saved: true, planId });
  });

  router.delete("/plans/:planId", async (req, res) => {
    const { planId } = req.params;
    if (planId === "free") {
      return res.status(400).json({ error: "PROTECTED_PLAN", message: "The base Free plan cannot be deleted." });
    }
    await req.db.collection("plans").doc(planId).delete();
    await req.db.collection("audit_logs").add({
      actorUid: req.user.uid,
      action: "ADMIN_DELETE_PLAN",
      targetType: "PLAN",
      targetId: planId,
      status: "SUCCESS",
      detail: `Deleted plan ${planId}`,
      createdAt: new Date(),
    });
    return res.json({ deleted: true, planId });
  });

  // Admin: directly manage a user's plan, adFree ("Ads Block") toggle, and AI credits
  router.put("/users/:uid/subscription", async (req, res) => {
    const targetUid = req.params.uid;
    const { subscriptionTier, adFree, aiCreditsRemaining, storageLimitMb, durationDays = 30 } = req.body || {};
    try {
      const planId = String(subscriptionTier || "free").trim();
      const planSnap = await req.db.collection("plans").doc(planId).get();
      const plan = planSnap.exists ? planSnap.data() : (DEFAULT_PLANS[planId] || DEFAULT_PLANS.free);

      const finalAdFree = typeof adFree === "boolean" ? adFree : Boolean(plan.adFree);
      const finalCredits =
        typeof aiCreditsRemaining === "number"
          ? Math.max(0, aiCreditsRemaining)
          : Number(plan.aiMonthlyCredits || 20);
      const finalStorageMb =
        typeof storageLimitMb === "number"
          ? Math.max(50, storageLimitMb)
          : Number(plan.storageLimitMb || 100);

      const expiresAtMillis =
        planId === "free" ? 0 : Date.now() + Number(durationDays || 30) * 24 * 60 * 60 * 1000;

      const now = new Date();
      await req.db.collection("users").doc(targetUid).set(
        {
          userId: targetUid,
          subscriptionTier: planId,
          adFree: finalAdFree,
          aiCreditsRemaining: finalCredits,
          storageLimitBytes: finalStorageMb * 1024 * 1024,
          updatedAt: now,
        },
        { merge: true }
      );

      await req.db
        .collection("users")
        .doc(targetUid)
        .collection("subscriptions")
        .doc("current")
        .set({
          userId: targetUid,
          planId,
          status: "ACTIVE",
          expiresAtMillis,
          verifiedByServer: true,
          adFree: finalAdFree,
          updatedByAdmin: req.user.uid,
          updatedAt: now,
        });

      await req.db.collection("audit_logs").add({
        actorUid: req.user.uid,
        action: "ADMIN_UPDATE_USER_ENTITLEMENT",
        targetType: "USER",
        targetId: targetUid,
        status: "SUCCESS",
        detail: `tier=${planId}; adFree=${finalAdFree}; credits=${finalCredits}`,
        createdAt: now,
      });

      return res.json({ saved: true, userId: targetUid, planId, adFree: finalAdFree, aiCreditsRemaining: finalCredits });
    } catch (err) {
      return res.status(500).json({ error: "ADMIN_USER_UPDATE_ERROR", message: err.message });
    }
  });

  // Admin: manually reconcile / verify / fail / refund an incoming YouPI PG or Direct UPI order
  router.post("/payments/:orderId/reconcile", async (req, res) => {
    const { orderId } = req.params;
    const { status, utrReference, note } = req.body || {};
    const nextStatus = String(status || "").toUpperCase();
    if (!["COMPLETED", "FAILED", "REFUNDED", "CANCELLED"].includes(nextStatus)) {
      return res.status(400).json({ error: "INVALID_STATUS", message: "Status must be COMPLETED, FAILED, REFUNDED, or CANCELLED" });
    }

    try {
      const lookupRef = req.db.collection("order_lookup").doc(orderId);
      const lookupSnap = await lookupRef.get();
      if (!lookupSnap.exists) {
        return res.status(404).json({ error: "ORDER_NOT_FOUND", message: "Order not found." });
      }
      const lookup = lookupSnap.data();
      const orderRef = req.db
        .collection("users")
        .doc(lookup.userId)
        .collection("payment_orders")
        .doc(orderId);

      await req.db.runTransaction(async (tx) => {
        const orderSnap = await tx.get(orderRef);
        const existingOrder = orderSnap.exists ? orderSnap.data() : {};
        const now = new Date();
        const finalUtr = String(utrReference || existingOrder.utrReference || "");

        tx.set(
          orderRef,
          {
            status: nextStatus,
            utrReference: finalUtr,
            adminNote: String(note || ""),
            updatedAt: now,
          },
          { merge: true }
        );
        tx.set(
          lookupRef,
          {
            status: nextStatus,
            utrReference: finalUtr,
            updatedAt: now,
          },
          { merge: true }
        );

        const txId = `tx_admin_${Date.now()}`;
        tx.set(
          req.db.collection("users").doc(lookup.userId).collection("payment_transactions").doc(txId),
          {
            userId: lookup.userId,
            orderId,
            gatewayEvent: `ADMIN_${nextStatus}`,
            settlementState: nextStatus === "COMPLETED" ? "RECONCILED" : nextStatus,
            amountPaise: Number(lookup.amountPaise || 0),
            utrReference: finalUtr,
            verifiedSignature: true,
            processedAt: now,
          }
        );

        const planSnap = await tx.get(req.db.collection("plans").doc(lookup.planId));
        const plan = planSnap.exists ? planSnap.data() : (DEFAULT_PLANS[lookup.planId] || DEFAULT_PLANS.monthly);

        if (nextStatus === "COMPLETED") {
          const expiresAtMillis = Date.now() + computePlanDurationMs(plan);
          tx.set(
            req.db.collection("users").doc(lookup.userId).collection("subscriptions").doc("current"),
            {
              userId: lookup.userId,
              planId: lookup.planId,
              status: "ACTIVE",
              expiresAtMillis,
              verifiedByServer: true,
              adFree: Boolean(plan.adFree),
              updatedAt: now,
            }
          );
          tx.set(
            req.db.collection("users").doc(lookup.userId),
            {
              userId: lookup.userId,
              subscriptionTier: lookup.planId,
              adFree: Boolean(plan.adFree),
              aiCreditsRemaining: Number(plan.aiMonthlyCredits || 500),
              storageLimitBytes: Number(plan.storageLimitMb || 5120) * 1024 * 1024,
              updatedAt: now,
            },
            { merge: true }
          );
        } else if (nextStatus === "REFUNDED") {
          tx.set(
            req.db.collection("users").doc(lookup.userId).collection("subscriptions").doc("current"),
            {
              userId: lookup.userId,
              planId: "free",
              status: "REFUNDED",
              expiresAtMillis: 0,
              verifiedByServer: true,
              adFree: false,
              updatedAt: now,
            }
          );
          tx.set(
            req.db.collection("users").doc(lookup.userId),
            {
              userId: lookup.userId,
              subscriptionTier: "free",
              adFree: false,
              updatedAt: now,
            },
            { merge: true }
          );
        }
      });

      await req.db.collection("audit_logs").add({
        actorUid: req.user.uid,
        action: "ADMIN_RECONCILE_PAYMENT",
        targetType: "PAYMENT_ORDER",
        targetId: orderId,
        status: nextStatus,
        detail: `userId=${lookup.userId}; plan=${lookup.planId}; utr=${utrReference || ""}`,
        createdAt: new Date(),
      });

      return res.json({ saved: true, orderId, status: nextStatus });
    } catch (err) {
      return res.status(500).json({ error: "RECONCILE_ERROR", message: err.message });
    }
  });

  return router;
}

module.exports = { createAdminRouter, summarizeAiUsage };

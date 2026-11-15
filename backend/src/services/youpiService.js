const crypto = require("crypto");

class YoupiNotConfiguredError extends Error {
  constructor(missingFields) {
    super(`YouPI PG configuration incomplete: ${missingFields.join(", ")}`);
    this.name = "YoupiNotConfiguredError";
    this.code = "YOUPI_NOT_CONFIGURED";
    this.statusCode = 503;
    this.missingFields = missingFields;
  }
}

function maskAccount(val) {
  const s = String(val || "").trim();
  if (!s) return "";
  if (s.length <= 4) return s;
  return "•".repeat(Math.max(4, s.length - 4)) + s.slice(-4);
}

async function resolveYoupiConfig(db) {
  let runtimeConfig = {};
  if (db) {
    const doc = await db.collection("app_config").doc("youpi_pg").get();
    if (doc.exists) {
      runtimeConfig = doc.data() || {};
    }
  }
  const apiUrl = (process.env.YOUPI_API_URL || runtimeConfig.apiUrl || "").replace(/\/$/, "");
  const merchantId = process.env.YOUPI_MERCHANT_ID || runtimeConfig.merchantId || "";
  const merchantApiKey = process.env.YOUPI_MERCHANT_API_KEY || runtimeConfig.merchantApiKey || "";
  const webhookSecret = process.env.YOUPI_WEBHOOK_SECRET || runtimeConfig.webhookSecret || "";
  const returnUrl = process.env.YOUPI_RETURN_URL || runtimeConfig.returnUrl || "https://yaadrakshak.app/payments/return";

  const paymentUpiId = String(runtimeConfig.paymentUpiId || process.env.MERCHANT_UPI_ID || "").trim();
  const payeeName = String(runtimeConfig.payeeName || process.env.MERCHANT_PAYEE_NAME || "YaadRakshak").trim();
  const bankName = String(runtimeConfig.bankName || "").trim();
  const bankAccountNumber = String(runtimeConfig.bankAccountNumber || "").trim();
  const ifscCode = String(runtimeConfig.ifscCode || "").trim().toUpperCase();

  const missing = [];
  if (!apiUrl) missing.push("YOUPI_API_URL");
  if (!merchantId) missing.push("YOUPI_MERCHANT_ID");
  if (!merchantApiKey) missing.push("YOUPI_MERCHANT_API_KEY");
  if (!webhookSecret) missing.push("YOUPI_WEBHOOK_SECRET");

  return {
    apiUrl,
    merchantId,
    merchantApiKey,
    webhookSecret,
    returnUrl,
    paymentUpiId,
    payeeName,
    bankName,
    bankAccountNumber,
    maskedBankAccount: maskAccount(bankAccountNumber),
    ifscCode,
    isConfigured: missing.length === 0,
    hasDirectUpiOrBank: Boolean(paymentUpiId || (bankAccountNumber && ifscCode)),
    missing,
  };
}

function toPublicMerchantInfo(config) {
  return {
    paymentUpiId: config.paymentUpiId || "",
    payeeName: config.payeeName || "YaadRakshak",
    bankName: config.bankName || "",
    bankAccountDisplay: config.bankAccountNumber || "",
    maskedBankAccount: config.maskedBankAccount || "",
    ifscCode: config.ifscCode || "",
    merchantId: config.merchantId || "",
    youpiReady: Boolean(config.isConfigured),
    upiReady: Boolean(config.paymentUpiId),
    bankReady: Boolean(config.bankAccountNumber && config.ifscCode),
  };
}

/**
 * Official YouPI PG webhook HMAC-SHA256 verification against raw unparsed request bytes.
 * Header format: X-Gateway-Signature: sha256=<64-char hex>
 */
function verifyYoupiSignature(rawBodyBuffer, signatureHeader, webhookSecret) {
  if (!webhookSecret || !signatureHeader || !Buffer.isBuffer(rawBodyBuffer)) {
    return false;
  }
  const computedHash = crypto
    .createHmac("sha256", webhookSecret)
    .update(rawBodyBuffer)
    .digest("hex");
  const expectedSignature = `sha256=${computedHash}`;

  const actualBuf = Buffer.from(String(signatureHeader), "utf8");
  const expectedBuf = Buffer.from(expectedSignature, "utf8");
  if (actualBuf.length !== expectedBuf.length) {
    return false;
  }
  return crypto.timingSafeEqual(actualBuf, expectedBuf);
}

/**
 * Creates a YouPI PG V2 payment session via POST /api/v1/payments with MANUAL_UPI.
 */
async function createYoupiPaymentSession({
  config,
  amountPaise,
  orderId,
  idempotencyKey,
  customer,
  metadata,
  fetchImpl = fetch,
}) {
  if (!config.isConfigured) {
    throw new YoupiNotConfiguredError(config.missing);
  }
  const response = await fetchImpl(`${config.apiUrl}/api/v1/payments`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Merchant-Id": config.merchantId,
      "Idempotency-Key": idempotencyKey,
      Authorization: `Bearer ${config.merchantApiKey}`,
    },
    body: JSON.stringify({
      amount: amountPaise,
      currency: "INR",
      method: "MANUAL_UPI",
      orderId,
      customer,
      returnUrl: config.returnUrl,
      metadata,
    }),
    signal: AbortSignal.timeout(20000),
  });

  if (!response.ok) {
    const errText = await response.text();
    const error = new Error(`YouPI PG rejected session creation (${response.status}): ${errText.slice(0, 300)}`);
    error.statusCode = response.status >= 500 ? 502 : response.status;
    throw error;
  }
  return response.json();
}

/**
 * Evaluates whether a verified YouPI PG webhook event authorizes Premium fulfillment.
 * Per YouPI PG MERCHANT_INTEGRATION.md:
 * - OBSERVED_SUCCESS has settlementState: NOT_RECONCILED and MUST NOT activate fulfillment alone.
 * - Only RECONCILED (or explicit bank-verified SUCCESS) activates Premium entitlement.
 */
function evaluateSettlementFulfillment(gatewayEvent, payload) {
  const normalizedEvent = String(gatewayEvent || payload.status || "").toUpperCase();
  const settlementState = String(payload.settlementState || "").toUpperCase();

  if (normalizedEvent === "RECONCILED" || (normalizedEvent === "SUCCESS" && settlementState !== "NOT_RECONCILED")) {
    return { shouldActivate: true, orderStatus: "COMPLETED" };
  }
  if (normalizedEvent === "OBSERVED_SUCCESS") {
    return { shouldActivate: false, orderStatus: "PENDING_RECONCILIATION" };
  }
  if (normalizedEvent === "MANUAL_REVIEW") {
    return { shouldActivate: false, orderStatus: "MANUAL_REVIEW" };
  }
  if (normalizedEvent === "REFUNDED") {
    return { shouldActivate: false, orderStatus: "REFUNDED" };
  }
  if (normalizedEvent === "CANCELLED") {
    return { shouldActivate: false, orderStatus: "CANCELLED" };
  }
  if (normalizedEvent === "FAILED" || normalizedEvent === "EXPIRED") {
    return { shouldActivate: false, orderStatus: "FAILED" };
  }
  return { shouldActivate: false, orderStatus: "PENDING" };
}

function buildUpiPayUri({ paymentUpiId, payeeName, amountInr, orderId, planName }) {
  if (!paymentUpiId) return "";
  const params = new URLSearchParams({
    pa: paymentUpiId,
    pn: payeeName || "YaadRakshak",
    am: Number(amountInr || 0).toFixed(2),
    cu: "INR",
    tr: orderId,
    tn: `YaadRakshak ${planName || "Premium"}`,
  });
  return `upi://pay?${params.toString()}`;
}

module.exports = {
  YoupiNotConfiguredError,
  resolveYoupiConfig,
  toPublicMerchantInfo,
  buildUpiPayUri,
  verifyYoupiSignature,
  createYoupiPaymentSession,
  evaluateSettlementFulfillment,
};

const { test } = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {
  verifyYoupiSignature,
  evaluateSettlementFulfillment,
  toPublicMerchantInfo,
  buildUpiPayUri,
} = require("../src/services/youpiService");
const {
  resolveGroqConfig,
  chatCompletion,
  GroqNotConfiguredError,
} = require("../src/services/groqService");
const { summarizeAiUsage, computePlanDurationMs } = require("../src/services/entitlementHelpers");

test("YouPI PG HMAC-SHA256 verification accepts exact raw body and rejects tampered body", () => {
  const secret = "test_webhook_secret_123";
  const rawString = '{"orderId":"ord_1","amount":9900,"currency":"INR","status":"RECONCILED"}';
  const rawBuf = Buffer.from(rawString, "utf8");
  const validSig = "sha256=" + crypto.createHmac("sha256", secret).update(rawBuf).digest("hex");

  assert.equal(verifyYoupiSignature(rawBuf, validSig, secret), true);
  assert.equal(verifyYoupiSignature(Buffer.from(rawString + " ", "utf8"), validSig, secret), false);
  assert.equal(verifyYoupiSignature(rawBuf, "sha256=deadbeef", secret), false);
  assert.equal(verifyYoupiSignature(rawBuf, validSig, ""), false);
});

test("YouPI PG settlement evaluation does NOT activate Premium on OBSERVED_SUCCESS alone", () => {
  const observed = evaluateSettlementFulfillment("OBSERVED_SUCCESS", {
    status: "OBSERVED_SUCCESS",
    settlementState: "NOT_RECONCILED",
  });
  assert.equal(observed.shouldActivate, false);
  assert.equal(observed.orderStatus, "PENDING_RECONCILIATION");

  const reconciled = evaluateSettlementFulfillment("RECONCILED", {
    status: "RECONCILED",
    settlementState: "RECONCILED",
  });
  assert.equal(reconciled.shouldActivate, true);
  assert.equal(reconciled.orderStatus, "COMPLETED");

  const refunded = evaluateSettlementFulfillment("REFUNDED", { status: "REFUNDED" });
  assert.equal(refunded.shouldActivate, false);
  assert.equal(refunded.orderStatus, "REFUNDED");
});

test("toPublicMerchantInfo exposes merchant UPI/bank display fields and NEVER exposes secrets", () => {
  const publicInfo = toPublicMerchantInfo({
    apiUrl: "https://youpi.example.com",
    merchantId: "m_live_101",
    merchantApiKey: "SECRET_KEY_MUST_NOT_LEAK",
    webhookSecret: "SECRET_WEBHOOK_MUST_NOT_LEAK",
    paymentUpiId: "yaadrakshak@upi",
    payeeName: "YaadRakshak Services",
    bankName: "HDFC Bank",
    bankAccountNumber: "50100012345678",
    maskedBankAccount: "••••••••••5678",
    ifscCode: "HDFC0001234",
    isConfigured: true,
  });

  assert.equal(publicInfo.paymentUpiId, "yaadrakshak@upi");
  assert.equal(publicInfo.payeeName, "YaadRakshak Services");
  assert.equal(publicInfo.upiReady, true);
  assert.equal(publicInfo.bankReady, true);
  assert.equal(publicInfo.merchantApiKey, undefined);
  assert.equal(publicInfo.webhookSecret, undefined);

  const upiUri = buildUpiPayUri({
    paymentUpiId: "yaadrakshak@upi",
    payeeName: "YaadRakshak Services",
    amountInr: 99,
    orderId: "yr_ord_abc",
    planName: "Premium Monthly",
  });
  assert.ok(upiUri.startsWith("upi://pay?"));
  assert.ok(upiUri.includes("pa=yaadrakshak%40upi"));
  assert.ok(upiUri.includes("am=99.00"));
});

test("Admin AI usage stats and plan duration helpers compute exact metrics", () => {
  const stats = summarizeAiUsage([
    { targetType: "AI_REQUEST", action: "AI_MEMORY_CHAT", status: "SUCCESS", tokensUsed: 150 },
    { targetType: "AI_REQUEST", action: "AI_EXTRACT", status: "SUCCESS", tokensUsed: 80 },
    { targetType: "AI_REQUEST", action: "AI_MEMORY_CHAT", status: "ERROR", tokensUsed: 0 },
    { targetType: "APP_CONFIG", action: "ADMIN_UPDATE_GROQ_CONFIG", status: "SUCCESS", tokensUsed: 0 },
  ]);
  assert.deepEqual(stats, {
    totalRequests: 3,
    successCount: 2,
    errorCount: 1,
    totalTokens: 230,
  });

  assert.equal(computePlanDurationMs({ interval: "yearly" }), 365 * 24 * 60 * 60 * 1000);
  assert.equal(computePlanDurationMs({ interval: "quarterly" }), 90 * 24 * 60 * 60 * 1000);
  assert.equal(computePlanDurationMs({ interval: "monthly" }), 30 * 24 * 60 * 60 * 1000);
});

test("Groq service rejects unconfigured key explicitly without mocking AI output", async () => {
  const prevKey = process.env.GROQ_API_KEY;
  delete process.env.GROQ_API_KEY;
  try {
    const cfg = await resolveGroqConfig(null);
    assert.equal(cfg.isConfigured, false);
    assert.equal(cfg.maskedKey, "");
    await assert.rejects(
      () =>
        chatCompletion({
          db: null,
          messages: [{ role: "user", content: "hi" }],
        }),
      GroqNotConfiguredError
    );
  } finally {
    if (prevKey !== undefined) process.env.GROQ_API_KEY = prevKey;
  }
});

test("Groq service retries 429 rate-limit and succeeds on subsequent attempt", async () => {
  const prevKey = process.env.GROQ_API_KEY;
  process.env.GROQ_API_KEY = "gsk_test_key_9999";
  let calls = 0;
  const mockFetch = async () => {
    calls++;
    if (calls === 1) {
      return { status: 429, ok: false, text: async () => "rate limit" };
    }
    return {
      status: 200,
      ok: true,
      json: async () => ({
        model: "llama-3.3-70b-versatile",
        choices: [{ message: { content: "OK" } }],
        usage: { total_tokens: 12 },
      }),
    };
  };
  try {
    const cfg = await resolveGroqConfig(null);
    assert.equal(cfg.isConfigured, true);
    assert.equal(cfg.maskedKey, "gsk_••••9999");

    const res = await chatCompletion({
      db: null,
      messages: [{ role: "user", content: "ping" }],
      fetchImpl: mockFetch,
    });
    assert.equal(calls, 2);
    assert.equal(res.choices[0].message.content, "OK");
  } finally {
    if (prevKey === undefined) delete process.env.GROQ_API_KEY;
    else process.env.GROQ_API_KEY = prevKey;
  }
});

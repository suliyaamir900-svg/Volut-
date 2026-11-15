const DEFAULT_PLANS = {
  free: {
    planId: "free",
    name: "Free Plan",
    priceInr: 0,
    interval: "free",
    aiMonthlyCredits: 20,
    storageLimitMb: 100,
    adFree: false,
    active: true,
  },
  monthly: {
    planId: "monthly",
    name: "Premium Monthly",
    priceInr: 99,
    interval: "monthly",
    aiMonthlyCredits: 500,
    storageLimitMb: 5120,
    adFree: true,
    active: true,
  },
  yearly: {
    planId: "yearly",
    name: "Premium Yearly",
    priceInr: 799,
    interval: "yearly",
    aiMonthlyCredits: 6000,
    storageLimitMb: 20480,
    adFree: true,
    active: true,
  },
};

function computePlanDurationMs(plan) {
  const interval = String((plan && plan.interval) || "monthly").toLowerCase();
  if (interval === "yearly") return 365 * 24 * 60 * 60 * 1000;
  if (interval === "quarterly") return 90 * 24 * 60 * 60 * 1000;
  if (interval === "weekly") return 7 * 24 * 60 * 60 * 1000;
  return 30 * 24 * 60 * 60 * 1000;
}

function summarizeAiUsage(auditLogDocs) {
  let totalRequests = 0;
  let successCount = 0;
  let errorCount = 0;
  let totalTokens = 0;
  for (const log of auditLogDocs) {
    if (log.targetType === "AI_REQUEST" || String(log.action || "").startsWith("AI_")) {
      totalRequests += 1;
      if (log.status === "SUCCESS") successCount += 1;
      if (log.status === "ERROR") errorCount += 1;
      totalTokens += Number(log.tokensUsed || 0);
    }
  }
  return { totalRequests, successCount, errorCount, totalTokens };
}

module.exports = {
  DEFAULT_PLANS,
  computePlanDurationMs,
  summarizeAiUsage,
};

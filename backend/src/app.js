const express = require("express");
const helmet = require("helmet");
const cors = require("cors");
const rateLimit = require("express-rate-limit");
const { getFirebaseAdmin, createAuthMiddleware, requireAdmin } = require("./middleware/auth");
const { createAiRouter } = require("./routes/aiRoutes");
const { createPaymentRouter } = require("./routes/paymentRoutes");
const { createAdminRouter } = require("./routes/adminRoutes");

function createApp(deps = {}) {
  const app = express();
  app.use(helmet());
  app.use(
    cors({
      origin: (process.env.ALLOWED_ORIGINS || "*").split(","),
    })
  );

  const getAdminFn = deps.getAdmin || getFirebaseAdmin;
  const requireAuth = createAuthMiddleware({ getAdmin: getAdminFn });
  const { router: paymentRouter, webhookHandler } = createPaymentRouter(getAdminFn);

  // CRITICAL: Raw unparsed body MUST be registered BEFORE express.json() for HMAC-SHA256 verification
  app.post(
    "/api/payments/youpi-webhook",
    express.raw({ type: "application/json", limit: "1mb" }),
    webhookHandler
  );

  app.use(express.json({ limit: "2mb" }));

  const aiLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: 30,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req) => req.user?.uid || req.ip,
  });

  app.get("/health", (_req, res) => {
    res.json({ status: "ok", service: "yaadrakshak-backend" });
  });

  app.use("/api/ai", requireAuth, aiLimiter, createAiRouter());
  app.use("/api/payments", requireAuth, paymentRouter);
  app.use("/api/admin", requireAuth, requireAdmin, createAdminRouter());

  return app;
}

module.exports = { createApp };

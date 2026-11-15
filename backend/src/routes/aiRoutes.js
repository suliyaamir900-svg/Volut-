const express = require("express");
const {
  chatCompletion,
  resolveGroqConfig,
  GroqNotConfiguredError,
} = require("../services/groqService");

async function enforceAndConsumeAiQuota(db, uid, isAdmin) {
  if (isAdmin) return { allowed: true, remaining: 9999 };
  const userRef = db.collection("users").doc(uid);
  const cfg = await resolveGroqConfig(db);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(userRef);
    const data = snap.exists ? snap.data() : {};
    const tier = data.subscriptionTier || "free";
    const defaultCredits = tier === "free" ? cfg.dailyFreeLimit : cfg.dailyPremiumLimit;
    const currentCredits =
      typeof data.aiCreditsRemaining === "number" ? data.aiCreditsRemaining : defaultCredits;

    if (currentCredits <= 0) {
      return { allowed: false, remaining: 0, tier };
    }
    const nextCredits = currentCredits - 1;
    tx.set(
      userRef,
      {
        userId: uid,
        aiCreditsRemaining: nextCredits,
      },
      { merge: true }
    );
    return { allowed: true, remaining: nextCredits, tier };
  });
}

async function logAiUsage(db, entry) {
  await db.collection("audit_logs").add({
    ...entry,
    targetType: "AI_REQUEST",
    createdAt: new Date(),
  });
}

function createAiRouter() {
  const router = express.Router();

  // 1. Grounded Personal Memory Q&A & Assistant
  router.post("/chat", async (req, res) => {
    const { question, memories = [], history = [], model } = req.body || {};
    if (!question || typeof question !== "string") {
      return res.status(400).json({ error: "INVALID_REQUEST", message: "question is required" });
    }

    try {
      const quota = await enforceAndConsumeAiQuota(req.db, req.user.uid, req.isAdmin);
      if (!quota.allowed) {
        return res.status(429).json({
          error: "AI_CREDITS_EXHAUSTED",
          message: "Daily AI credits exhausted. Upgrade to Premium for higher limits.",
          remainingCredits: 0,
        });
      }

      const contextBlock =
        Array.isArray(memories) && memories.length > 0
          ? memories
              .slice(0, 12)
              .map(
                (m, idx) =>
                  `[Source ${idx + 1} | ID=${m.id} | Type=${m.memoryType} | Title=${m.title}]\n${String(
                    m.content || ""
                  ).slice(0, 1800)}`
              )
              .join("\n\n---\n\n")
          : "NO_SAVED_MEMORIES_PROVIDED";

      const systemPrompt = [
        "You are YaadRakshak AI Memory Assistant.",
        "CRITICAL GROUNDING RULE: Answer personal questions ONLY using the authorized saved memories provided below.",
        "If the answer is not present in the provided memories, clearly respond that the information was not found in the user's saved records. NEVER invent personal numbers, dates, prices, or facts.",
        "Return strict JSON matching: {\"answer\": string, \"citedMemoryIds\": string[], \"found\": boolean, \"relatedSuggestions\": string[]}.",
        "",
        "AUTHORIZED SAVED MEMORIES:",
        contextBlock,
      ].join("\n");

      const messages = [{ role: "system", content: systemPrompt }];
      if (Array.isArray(history)) {
        for (const msg of history.slice(-6)) {
          if (msg && (msg.role === "user" || msg.role === "assistant") && typeof msg.content === "string") {
            messages.push({ role: msg.role, content: msg.content });
          }
        }
      }
      messages.push({ role: "user", content: question });

      const completion = await chatCompletion({
        db: req.db,
        model,
        messages,
        responseFormat: { type: "json_object" },
      });

      const rawContent = completion.choices?.[0]?.message?.content || "{}";
      let parsed;
      try {
        parsed = JSON.parse(rawContent);
      } catch {
        parsed = { answer: rawContent, citedMemoryIds: [], found: true, relatedSuggestions: [] };
      }

      await logAiUsage(req.db, {
        actorUid: req.user.uid,
        action: "AI_MEMORY_CHAT",
        status: "SUCCESS",
        tokensUsed: Number(completion.usage?.total_tokens || 0),
        model: completion.model || "",
        detail: `model=${completion.model}; tokens=${completion.usage?.total_tokens || 0}`,
      });

      return res.json({
        answer: parsed.answer || "Information not found in your saved records.",
        citedMemoryIds: Array.isArray(parsed.citedMemoryIds) ? parsed.citedMemoryIds : [],
        found: Boolean(parsed.found),
        relatedSuggestions: Array.isArray(parsed.relatedSuggestions) ? parsed.relatedSuggestions : [],
        model: completion.model,
        tokensUsed: completion.usage?.total_tokens || 0,
        remainingCredits: quota.remaining,
      });
    } catch (err) {
      await logAiUsage(req.db, {
        actorUid: req.user.uid,
        action: "AI_MEMORY_CHAT",
        status: "ERROR",
        tokensUsed: 0,
        detail: err.message,
      }).catch(() => {});

      if (err instanceof GroqNotConfiguredError) {
        return res.status(503).json({
          error: err.code,
          message: err.message,
        });
      }
      return res.status(err.statusCode || 502).json({
        error: err.code || "AI_ERROR",
        message: err.message,
      });
    }
  });

  // 2. Extraction, Summarization, Auto-Categorization, Smart Tags, Receipt/Warranty/Voice Parsing
  router.post("/extract", async (req, res) => {
    const { text, memoryType = "DOCUMENT", model } = req.body || {};
    if (!text || typeof text !== "string") {
      return res.status(400).json({ error: "INVALID_REQUEST", message: "text is required" });
    }

    try {
      const quota = await enforceAndConsumeAiQuota(req.db, req.user.uid, req.isAdmin);
      if (!quota.allowed) {
        return res.status(429).json({
          error: "AI_CREDITS_EXHAUSTED",
          message: "Daily AI credits exhausted.",
          remainingCredits: 0,
        });
      }

      const systemPrompt = [
        "Extract structured metadata from the provided OCR text, voice transcript, or document text.",
        "NEVER fabricate fields that do not appear in the text; use null when absent.",
        "Return strict JSON: {",
        '  "summary": string,',
        '  "category": string,',
        '  "tags": string[],',
        '  "receipt": { "merchantName": string|null, "totalAmountInr": number|null, "taxAmountInr": number|null, "invoiceNumber": string|null, "dateIso": string|null } | null,',
        '  "warranty": { "productName": string|null, "brand": string|null, "serialNumber": string|null, "expiryDateIso": string|null, "coverageNotes": string|null } | null',
        "}",
      ].join("\n");

      const completion = await chatCompletion({
        db: req.db,
        model,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: `Memory Type: ${memoryType}\n\nContent:\n${text.slice(0, 12000)}` },
        ],
        responseFormat: { type: "json_object" },
      });

      const rawContent = completion.choices?.[0]?.message?.content || "{}";
      const parsed = JSON.parse(rawContent);

      await logAiUsage(req.db, {
        actorUid: req.user.uid,
        action: "AI_EXTRACT",
        status: "SUCCESS",
        tokensUsed: Number(completion.usage?.total_tokens || 0),
        model: completion.model || "",
        detail: `type=${memoryType}; model=${completion.model}`,
      });

      return res.json({
        ...parsed,
        model: completion.model,
        tokensUsed: completion.usage?.total_tokens || 0,
        remainingCredits: quota.remaining,
      });
    } catch (err) {
      if (err instanceof GroqNotConfiguredError) {
        return res.status(503).json({ error: err.code, message: err.message });
      }
      return res.status(err.statusCode || 502).json({ error: err.code || "AI_ERROR", message: err.message });
    }
  });

  return router;
}

module.exports = { createAiRouter };

const GROQ_BASE_URL = "https://api.groq.com/openai/v1";

class GroqNotConfiguredError extends Error {
  constructor() {
    super("GROQ_API_KEY is not configured on the backend server.");
    this.name = "GroqNotConfiguredError";
    this.code = "GROQ_NOT_CONFIGURED";
    this.statusCode = 503;
  }
}

class GroqUpstreamError extends Error {
  constructor(message, statusCode = 502, code = "GROQ_UPSTREAM_ERROR") {
    super(message);
    this.name = "GroqUpstreamError";
    this.statusCode = statusCode;
    this.code = code;
  }
}

function maskSecret(val) {
  const s = String(val || "").trim();
  if (!s) return "";
  if (s.length <= 8) return "••••" + s.slice(-2);
  return s.slice(0, 4) + "••••" + s.slice(-4);
}

async function resolveGroqConfig(db) {
  let runtimeConfig = {};
  if (db) {
    const doc = await db.collection("app_config").doc("groq").get();
    if (doc.exists) {
      runtimeConfig = doc.data() || {};
    }
  }
  const apiKey = process.env.GROQ_API_KEY || runtimeConfig.apiKey || "";
  const defaultModel =
    runtimeConfig.defaultModel ||
    process.env.GROQ_DEFAULT_MODEL ||
    "llama-3.3-70b-versatile";
  const maxTokens = Number(runtimeConfig.maxTokens || 1024);
  const dailyFreeLimit = Number(runtimeConfig.dailyFreeLimit || 20);
  const dailyPremiumLimit = Number(runtimeConfig.dailyPremiumLimit || 500);

  return {
    apiKey,
    maskedKey: maskSecret(apiKey),
    isConfigured: Boolean(apiKey && apiKey.trim().length > 0),
    defaultModel,
    maxTokens,
    dailyFreeLimit,
    dailyPremiumLimit,
  };
}

async function fetchWithRetry(url, options, fetchImpl = fetch, maxAttempts = 3) {
  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const response = await fetchImpl(url, {
        ...options,
        signal: AbortSignal.timeout(25000),
      });
      if (response.status === 429 || response.status >= 500) {
        const text = await response.text();
        lastError = new GroqUpstreamError(
          `Groq API error (${response.status}): ${text.slice(0, 300)}`,
          response.status === 429 ? 429 : 502,
          response.status === 429 ? "GROQ_RATE_LIMITED" : "GROQ_SERVER_ERROR"
        );
        if (attempt < maxAttempts) {
          await new Promise((r) => setTimeout(r, 400 * attempt));
          continue;
        }
        throw lastError;
      }
      if (!response.ok) {
        const text = await response.text();
        throw new GroqUpstreamError(
          `Groq request rejected (${response.status}): ${text.slice(0, 300)}`,
          response.status,
          "GROQ_BAD_REQUEST"
        );
      }
      return await response.json();
    } catch (err) {
      if (err instanceof GroqUpstreamError) {
        lastError = err;
        if (attempt >= maxAttempts) throw err;
      } else {
        lastError = new GroqUpstreamError(`Groq network failure: ${err.message}`, 504, "GROQ_TIMEOUT");
        if (attempt >= maxAttempts) throw lastError;
      }
    }
  }
  throw lastError;
}

async function listGroqModels(db, fetchImpl = fetch) {
  const cfg = await resolveGroqConfig(db);
  if (!cfg.isConfigured) {
    throw new GroqNotConfiguredError();
  }
  return fetchWithRetry(
    `${GROQ_BASE_URL}/models`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${cfg.apiKey}`,
      },
    },
    fetchImpl,
    2
  );
}

async function chatCompletion({ db, model, messages, maxTokens, responseFormat, fetchImpl = fetch }) {
  const cfg = await resolveGroqConfig(db);
  if (!cfg.isConfigured) {
    throw new GroqNotConfiguredError();
  }
  const chosenModel = model || cfg.defaultModel;
  const body = {
    model: chosenModel,
    messages,
    max_tokens: Math.min(Number(maxTokens || cfg.maxTokens), cfg.maxTokens),
    temperature: 0.2,
  };
  if (responseFormat) {
    body.response_format = responseFormat;
  }

  return fetchWithRetry(
    `${GROQ_BASE_URL}/chat/completions`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${cfg.apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    },
    fetchImpl
  );
}

async function testGroqConnection(db, model, fetchImpl = fetch) {
  const result = await chatCompletion({
    db,
    model,
    messages: [{ role: "user", content: "Reply with OK." }],
    maxTokens: 16,
    fetchImpl,
  });
  return {
    connected: true,
    model: result.model || model,
    usage: result.usage || null,
    reply: result.choices?.[0]?.message?.content || "",
  };
}

module.exports = {
  GROQ_BASE_URL,
  GroqNotConfiguredError,
  GroqUpstreamError,
  resolveGroqConfig,
  listGroqModels,
  chatCompletion,
  testGroqConnection,
};

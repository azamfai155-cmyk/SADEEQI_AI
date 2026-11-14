import { cors, json, safeEqual, validateInput, DEFAULT_SYSTEM_PROMPT } from "./_lib.js";

export const config = {
  api: { bodyParser: { sizeLimit: "4mb" } }
};

export default async function handler(req, res) {
  if (cors(req, res)) return;
  if (req.method !== "POST") return json(res, 405, { error: "Method not allowed." }, { Allow: "POST, OPTIONS" });

  const expectedToken = process.env.SADEEQI_APP_TOKEN;
  if (!expectedToken) return json(res, 503, { error: "Backend auth is not configured." });
  const authorization = req.headers.authorization || "";
  const token = authorization.replace(/^Bearer\s+/i, "");
  if (!safeEqual(token, expectedToken)) return json(res, 401, { error: "Unauthorized." });

  if (!process.env.OPENAI_API_KEY || !process.env.OPENAI_MODEL) {
    return json(res, 503, { error: "OpenAI server secrets are not configured." });
  }

  const body = req.body;
  if (!body || typeof body !== "object" || Array.isArray(body)) {
    return json(res, 400, { error: "Invalid JSON." });
  }
  const validationError = validateInput(body.input);
  if (validationError) return json(res, 400, { error: validationError });

  const payload = {
    model: process.env.OPENAI_MODEL,
    instructions: process.env.SADEEQI_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT,
    input: body.input,
    store: false
  };
  if (process.env.ALLOW_WEB_SEARCH !== "false" && body.enable_web_search === true) {
    payload.tools = [{ type: "web_search" }];
  }

  try {
    const upstream = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        Authorization: \`Bearer \${process.env.OPENAI_API_KEY}\`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(55000)
    });

    let result;
    try { result = await upstream.json(); }
    catch { return json(res, 502, { error: "Invalid response from AI provider." }); }

    if (!upstream.ok) {
      const status = upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502;
      return json(res, status, { error: result?.error?.message || "OpenAI request failed." });
    }

    const output = (result.output || [])
      .flatMap(item => item.content || [])
      .filter(part => part.type === "output_text")
      .map(part => part.text)
      .join("\n")
      .trim();

    if (!output) return json(res, 502, { error: "AI provider returned an empty response." });
    return json(res, 200, {
      id: result.id,
      model: result.model || process.env.OPENAI_MODEL,
      output_text: output,
      usage: result.usage || null
    });
  } catch {
    return json(res, 502, { error: "AI provider unavailable. Check provider status and server logs." });
  }
}

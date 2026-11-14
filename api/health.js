import { cors, json } from "./_lib.js";

export default function handler(req, res) {
  if (cors(req, res)) return;
  if (req.method !== "GET") return json(res, 405, { error: "Method not allowed." }, { Allow: "GET, OPTIONS" });
  return json(res, 200, {
    service: "sadeeqi-ai",
    version: "1.3.0",
    runtime: "vercel-nodejs",
    configured: Boolean(process.env.OPENAI_API_KEY && process.env.SADEEQI_APP_TOKEN && process.env.OPENAI_MODEL)
  });
}

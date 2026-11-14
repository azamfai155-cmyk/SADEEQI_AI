import express from 'express';
import crypto from 'node:crypto';

export const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '12mb' }));

const API_KEY = process.env.OPENAI_API_KEY;
const APP_TOKEN = process.env.SADEEQI_APP_TOKEN;
const MODEL = process.env.OPENAI_MODEL;
const SYSTEM_PROMPT = process.env.SADEEQI_SYSTEM_PROMPT ||
  'أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية وبلهجة يمنية طبيعية عند ملاءمتها. كن عملياً ودقيقاً وصريحاً ولا تدّع تنفيذ إجراء لم تنفذه. اطلب الموافقة قبل الإجراءات الحساسة.';

function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function validateInput(input) {
  if (!Array.isArray(input) || input.length < 1 || input.length > 24) return 'input must contain 1 to 24 turns.';
  for (const turn of input) {
    if (!turn || !['user', 'assistant'].includes(turn.role)) return 'Invalid role.';
    if (typeof turn.content === 'string') {
      if (turn.content.length > 16000) return 'Message too large.';
    } else if (!Array.isArray(turn.content) || turn.content.length < 1 || turn.content.length > 4) {
      return 'Invalid content.';
    }
  }
  return null;
}

app.get('/health', (_req, res) => {
  res.set('Cache-Control', 'no-store').json({
    service: 'sadeeqi-ai',
    version: '1.2.1',
    configured: Boolean(API_KEY && APP_TOKEN && MODEL)
  });
});

app.post('/v1/chat', async (req, res) => {
  if (!APP_TOKEN) return res.status(503).json({ error: 'Backend auth is not configured.' });
  const token = (req.headers.authorization || '').replace(/^Bearer\\s+/i, '');
  if (!safeEqual(token, APP_TOKEN)) return res.status(401).json({ error: 'Unauthorized.' });
  if (!API_KEY || !MODEL) return res.status(503).json({ error: 'OpenAI server secrets are not configured.' });

  const validationError = validateInput(req.body?.input);
  if (validationError) return res.status(400).json({ error: validationError });

  const payload = {
    model: MODEL,
    instructions: SYSTEM_PROMPT,
    input: req.body.input,
    store: false
  };
  if (process.env.ALLOW_WEB_SEARCH !== 'false' && req.body.enable_web_search === true) {
    payload.tools = [{ type: 'web_search' }];
  }

  try {
    const upstream = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(110000)
    });
    const result = await upstream.json();
    if (!upstream.ok) {
      const status = upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502;
      return res.status(status).json({ error: result?.error?.message || 'OpenAI request failed.' });
    }
    const output = (result.output || []).flatMap(item => item.content || [])
      .filter(part => part.type === 'output_text').map(part => part.text).join('\\n').trim();
    if (!output) return res.status(502).json({ error: 'AI provider returned an empty response.' });
    return res.json({ id: result.id, model: result.model || MODEL, output_text: output, usage: result.usage || null });
  } catch {
    return res.status(502).json({ error: 'AI provider unavailable. Check provider status and server logs.' });
  }
});

export default function handler(req, res) {
  return app(req, res);
}

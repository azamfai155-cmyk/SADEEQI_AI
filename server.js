
import crypto from 'node:crypto';

const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '12mb' }));
const PORT = Number(process.env.PORT || 8080);
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const APP_TOKEN = process.env.SADEEQI_APP_TOKEN;
const MODEL = process.env.OPENAI_MODEL;
const ALLOW_WEB_SEARCH = process.env.ALLOW_WEB_SEARCH !== 'false';
const SYSTEM_PROMPT = process.env.SADEEQI_SYSTEM_PROMPT || `أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية، واستخدم اللهجة اليمنية بصورة طبيعية عندما تلائم السياق. كن عملياً ودقيقاً وصريحاً، ولا تدّع تنفيذ إجراء لم تنفذه. اطلب موافقة عزام قبل أي إجراء حساس أو شراء أو إرسال أو حذف. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API. وضّح عدم اليقين واستشهد بالمصادر عند استخدام البحث.`;

function constantTimeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const aa = Buffer.from(a); const bb = Buffer.from(b);
  return aa.length === bb.length && crypto.timingSafeEqual(aa, bb);
}
function auth(req, res, next) {
  if (!APP_TOKEN) return res.status(503).json({ error: 'Backend auth is not configured.' });
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!constantTimeEqual(token, APP_TOKEN)) return res.status(401).json({ error: 'Unauthorized.' });
  next();
}
app.get('/health', (_req, res) => res.json({ service: 'sadeeqi-ai', version: '1.2.0', configured: Boolean(OPENAI_API_KEY && APP_TOKEN && MODEL) }));
app.post('/v1/chat', auth, async (req, res) => {
  if (!OPENAI_API_KEY || !MODEL) return res.status(503).json({ error: 'OPENAI_API_KEY and OPENAI_MODEL must be configured on the server.' });
  const input = req.body?.input;
  if (!Array.isArray(input) || input.length < 1 || input.length > 24) return res.status(400).json({ error: 'input must contain 1 to 24 turns.' });
  for (const turn of input) {
    if (!['user', 'assistant'].includes(turn?.role)) return res.status(400).json({ error: 'Invalid role.' });
    if (!(typeof turn.content === 'string' || Array.isArray(turn.content))) return res.status(400).json({ error: 'Invalid content.' });
    if (typeof turn.content === 'string' && turn.content.length > 16000) return res.status(413).json({ error: 'Message too large.' });
  }
  const payload = { model: MODEL, instructions: SYSTEM_PROMPT, input, store: false };
  if (ALLOW_WEB_SEARCH && req.body?.enable_web_search === true) payload.tools = [{ type: 'web_search' }];
  try {
    const upstream = await fetch('https://api.openai.com/v1/responses', {
      method: 'POST',
      headers: { Authorization: `Bearer ${OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload), signal: AbortSignal.timeout(120000)
    });
    const raw = await upstream.json();
    if (!upstream.ok) return res.status(upstream.status).json({ error: raw?.error?.message || 'OpenAI request failed.' });
    const output = (raw.output || []).flatMap(item => item.content || [])
      .filter(part => part.type === 'output_text').map(part => part.text).join('\n').trim();
    return res.json({ id: raw.id, model: raw.model || MODEL, output_text: output, usage: raw.usage || null });
  } catch (error) {
    return res.status(502).json({ error: `AI provider unavailable: ${error.message}` });
  }
});
export default app;
export { app };

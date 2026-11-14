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


app.get('/v1/social/providers', (req, res) => {
  if (!APP_TOKEN) return res.status(503).json({ error: 'Backend auth is not configured.' });
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  if (!safeEqual(token, APP_TOKEN)) return res.status(401).json({ error: 'Unauthorized.' });

  // This endpoint reports developer-side API readiness only. It never claims
  // that a user's social account is connected; OAuth flows and token storage
  // are intentionally unavailable until implemented and reviewed.
  const providers = [
    { id: 'facebook', name: 'Facebook', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_APP_ID', 'META_APP_SECRET'], note: 'تتطلب صلاحيات Meta ومراجعة التطبيق حسب نوع البيانات.' },
    { id: 'instagram', name: 'Instagram', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_APP_ID', 'META_APP_SECRET'], note: 'تقتصر الإدارة على حسابات وأنواع المحتوى التي تسمح بها واجهات Instagram الرسمية.' },
    { id: 'messenger', name: 'Messenger', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_APP_ID', 'META_APP_SECRET'], note: 'تتطلب صفحة Facebook وصلاحيات Messenger المعتمدة.' },
    { id: 'whatsapp', name: 'WhatsApp', family: 'Meta', auth: 'WhatsApp Business Cloud API', env: ['WHATSAPP_TOKEN', 'WHATSAPP_PHONE_NUMBER_ID'], note: 'واجهة Cloud API مخصصة للأعمال؛ لا تمنح وصولاً عاماً إلى محادثات WhatsApp الشخصية.' },
    { id: 'telegram', name: 'Telegram', family: 'Telegram', auth: 'Bot API', env: ['TELEGRAM_BOT_TOKEN'], note: 'Bot API للبوتات والمحادثات التي يُسمح للبوت بالوصول إليها، وليس حساب المستخدم الشخصي.' },
    { id: 'tiktok', name: 'TikTok', family: 'TikTok', auth: 'TikTok OAuth 2.0', env: ['TIKTOK_CLIENT_KEY', 'TIKTOK_CLIENT_SECRET'], note: 'النشر والقراءة يعتمدان على موافقة TikTok ونطاقات المنتج المتاحة.' },
    { id: 'youtube', name: 'YouTube', family: 'Google', auth: 'Google OAuth 2.0', env: ['GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET'], note: 'إدارة القنوات عبر YouTube Data API وفق الحصص والصلاحيات.' },
    { id: 'x', name: 'X', family: 'X', auth: 'X OAuth 2.0', env: ['X_CLIENT_ID', 'X_CLIENT_SECRET'], note: 'القراءة والنشر مرتبطان بخطة API ونطاقات الوصول.' },
    { id: 'snapchat', name: 'Snapchat', family: 'Snap', auth: 'Snap OAuth 2.0', env: ['SNAP_CLIENT_ID', 'SNAP_CLIENT_SECRET'], note: 'الوصول التجاري يتطلب منتج Snap معتمداً وصلاحيات مناسبة.' },
    { id: 'linkedin', name: 'LinkedIn', family: 'LinkedIn', auth: 'LinkedIn OAuth 2.0', env: ['LINKEDIN_CLIENT_ID', 'LINKEDIN_CLIENT_SECRET'], note: 'واجهات النشر والإدارة تتطلب منتجات وصلاحيات LinkedIn المناسبة.' },
    { id: 'discord', name: 'Discord', family: 'Discord', auth: 'Discord OAuth 2.0 / Bot', env: ['DISCORD_CLIENT_ID', 'DISCORD_CLIENT_SECRET'], note: 'البوت لا يرى إلا الخوادم والقنوات التي أُضيف إليها ومنحته الصلاحيات.' },
    { id: 'reddit', name: 'Reddit', family: 'Reddit', auth: 'Reddit OAuth 2.0', env: ['REDDIT_CLIENT_ID', 'REDDIT_CLIENT_SECRET'], note: 'تخضع القراءة والنشر لسياسات Reddit وحدود الاستخدام.' },
    { id: 'pinterest', name: 'Pinterest', family: 'Pinterest', auth: 'Pinterest OAuth 2.0', env: ['PINTEREST_APP_ID', 'PINTEREST_APP_SECRET'], note: 'تتطلب صلاحيات التطبيق المعتمدة لإدارة اللوحات والدبابيس.' },
    { id: 'threads', name: 'Threads', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_APP_ID', 'META_APP_SECRET'], note: 'تتطلب صلاحيات Threads API ومراجعة التطبيق عند اللزوم.' }
  ].map(({ env, ...provider }) => ({
    ...provider,
    configured: env.every((name) => Boolean(process.env[name])),
    connected: false
  }));

  res.set('Cache-Control', 'no-store').json({
    providers,
    notice: 'هذه حالة إعداد واجهات المطور فقط. لا توجد حسابات اجتماعية متصلة حالياً، ولا يمكن الربط أو النشر قبل تنفيذ OAuth وموافقة المستخدم.'
  });
});

app.post('/v1/chat', async (req, res) => {
  if (!APP_TOKEN) return res.status(503).json({ error: 'Backend auth is not configured.' });
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
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

import express from 'express';
import crypto from 'node:crypto';
import { createRemoteJWKSet, jwtVerify } from 'jose';

export const app = express();
app.disable('x-powered-by');
app.use(express.json({ limit: '12mb' }));

const APP_TOKEN = process.env.SADEEQI_APP_TOKEN;
export function normalizeAuthMode(value) {
  const mode = (value || 'legacy').toLowerCase();
  return ['legacy', 'dual', 'supabase'].includes(mode) ? mode : 'invalid';
}
const AUTH_MODE = normalizeAuthMode(process.env.AUTH_MODE);
const SUPABASE_URL = (process.env.SUPABASE_URL || '').replace(/\/$/, '');
const SUPABASE_JWKS = SUPABASE_URL ? createRemoteJWKSet(new URL(`${SUPABASE_URL}/auth/v1/.well-known/jwks.json`)) : null;
const PROVIDER = (process.env.AI_PROVIDER || (process.env.GROQ_API_KEY ? 'groq' : 'openai')).toLowerCase();
const GROQ_API_KEY = process.env.GROQ_API_KEY;
const OPENAI_API_KEY = process.env.OPENAI_API_KEY;
const MODEL = PROVIDER === 'groq'
  ? (process.env.GROQ_MODEL || 'openai/gpt-oss-20b')
  : (process.env.OPENAI_MODEL || 'gpt-4.1-mini');
const API_KEY = PROVIDER === 'groq' ? GROQ_API_KEY : OPENAI_API_KEY;
const API_URL = PROVIDER === 'groq'
  ? 'https://api.groq.com/openai/v1/responses'
  : 'https://api.openai.com/v1/responses';
const SYSTEM_PROMPT = process.env.SADEEQI_SYSTEM_PROMPT ||
  'أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية وبلهجة يمنية طبيعية عند ملاءمتها. كن عملياً ودقيقاً وصريحاً ولا تدّع تنفيذ إجراء لم تنفذه. اطلب الموافقة قبل الإجراءات الحساسة.';

export function buildProviderRequest({
  provider = PROVIDER,
  model = MODEL,
  apiKey = API_KEY,
  input,
  instructions = SYSTEM_PROMPT,
  enableWebSearch = false
}) {
  if (!['groq', 'openai'].includes(provider)) {
    throw new Error('Unsupported AI_PROVIDER. Use groq or openai.');
  }
  if (typeof apiKey !== 'string' || !apiKey.trim()) {
    throw new Error(`${provider} server credentials are not configured.`);
  }
  if (typeof model !== 'string' || !model.trim()) {
    throw new Error('AI model is not configured.');
  }
  if (!Array.isArray(input) || input.length === 0) {
    throw new Error('AI input must contain at least one turn.');
  }

  const payload = { model, instructions, input };
  // OpenAI stores Responses by default. Groq's compatible endpoint does not
  // implement the store parameter and defaults to non-persistent responses.
  if (provider === 'openai') payload.store = false;
  if (enableWebSearch) {
    payload.tools = [{ type: provider === 'groq' ? 'browser_search' : 'web_search' }];
  }
  return {
    url: provider === 'groq'
      ? 'https://api.groq.com/openai/v1/responses'
      : 'https://api.openai.com/v1/responses',
    init: {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(110000)
    }
  };
}

export function extractProviderText(result) {
  if (typeof result?.output_text === 'string' && result.output_text.trim()) {
    return result.output_text.trim();
  }
  return (Array.isArray(result?.output) ? result.output : [])
    .flatMap(item => Array.isArray(item?.content) ? item.content : [])
    .filter(part => part?.type === 'output_text' && typeof part.text === 'string')
    .map(part => part.text)
    .join('\\n')
    .trim();
}


export function extractBearerToken(value) {
  return typeof value === 'string' ? value.replace(/^Bearer\s+/i, '').trim() : '';
}

function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

export async function verifySupabaseAccessToken(token) {
  if (!SUPABASE_JWKS || !SUPABASE_URL) throw new Error('Supabase auth is not configured.');
  const { payload } = await jwtVerify(token, SUPABASE_JWKS, {
    issuer: `${SUPABASE_URL}/auth/v1`,
    audience: 'authenticated',
    algorithms: ['ES256', 'RS256']
  });
  if (typeof payload.sub !== 'string' || !payload.sub || payload.role !== 'authenticated') {
    throw new Error('Invalid authenticated user claims.');
  }
  return { userId: payload.sub, email: typeof payload.email === 'string' ? payload.email : null };
}

export function createAuthMiddleware() {
  return async (req, res, next) => {
    if (AUTH_MODE === 'invalid') return res.status(503).json({ error: 'Backend authentication mode is invalid.' });
    const token = extractBearerToken(req.headers.authorization);
    if (AUTH_MODE === 'supabase') {
      if (!SUPABASE_JWKS) return res.status(503).json({ error: 'Supabase authentication is not configured.' });
      if (!token) return res.status(401).json({ error: 'Authentication required.' });
      try {
        req.auth = await verifySupabaseAccessToken(token);
        return next();
      } catch {
        return res.status(401).json({ error: 'Invalid or expired session. Please sign in again.' });
      }
    }
    if (AUTH_MODE === 'dual' && SUPABASE_JWKS && token) {
      try {
        req.auth = await verifySupabaseAccessToken(token);
        return next();
      } catch {
        // During the controlled migration only, allow the existing app token below.
      }
    }
    if (AUTH_MODE === 'dual' && !SUPABASE_JWKS && !APP_TOKEN) {
      return res.status(503).json({ error: 'Neither Supabase nor legacy authentication is configured.' });
    }
    if (!APP_TOKEN) return res.status(503).json({ error: 'Backend auth is not configured.' });
    if (!safeEqual(token, APP_TOKEN)) return res.status(401).json({ error: 'Unauthorized.' });
    req.auth = { userId: null, email: null };
    return next();
  };
}

const requireAuth = createAuthMiddleware();

export function createRateLimiter({ windowMs = 60_000, max = 20, now = Date.now } = {}) {
  const buckets = new Map();

  return (req, res, next) => {
    const identity = req.auth?.userId || req.ip || req.socket?.remoteAddress || 'unknown';
    const key = String(identity);
    const current = now();
    let bucket = buckets.get(key);

    if (!bucket || current - bucket.start >= windowMs) {
      bucket = { start: current, count: 0 };
      buckets.set(key, bucket);
    }
    bucket.count += 1;

    // Bound memory use on long-lived instances and discard expired entries.
    if (buckets.size > 10_000) {
      for (const [entryKey, entry] of buckets) {
        if (current - entry.start >= windowMs) buckets.delete(entryKey);
        if (buckets.size <= 9_000) break;
      }
      while (buckets.size > 10_000) buckets.delete(buckets.keys().next().value);
    }

    const remaining = Math.max(0, max - bucket.count);
    const resetAt = Math.ceil((bucket.start + windowMs) / 1000);
    res.set('RateLimit-Limit', String(max));
    res.set('RateLimit-Remaining', String(remaining));
    res.set('RateLimit-Reset', String(resetAt));

    if (bucket.count > max) {
      res.set('Retry-After', String(Math.max(1, Math.ceil((bucket.start + windowMs - current) / 1000))));
      return res.status(429).json({ error: 'طلبات كثيرة خلال وقت قصير. حاول مرة أخرى بعد قليل.' });
    }
    return next();
  };
}


// Per-instance best-effort protection; use a shared store for strict cross-instance limits.
const chatRateLimit = createRateLimiter({ windowMs: 60_000, max: 20 });

export function validateInput(input) {
  if (!Array.isArray(input) || input.length < 1 || input.length > 24) return 'input must contain 1 to 24 turns.';
  for (const turn of input) {
    if (!turn || !['user', 'assistant'].includes(turn.role)) return 'Invalid role.';
    if (typeof turn.content === 'string') {
      if (turn.content.length > 16000) return 'Message too large.';
      continue;
    }
    if (!Array.isArray(turn.content) || turn.content.length < 1 || turn.content.length > 4) {
      return 'Invalid content.';
    }
    for (const part of turn.content) {
      if (!part || !['input_text', 'input_image'].includes(part.type)) return 'Invalid content part.';
      if (part.type === 'input_text' &&
          (typeof part.text !== 'string' || part.text.length > 16000)) {
        return 'Invalid input text.';
      }
      if (part.type === 'input_image' &&
          (typeof part.image_url !== 'string' ||
           !(part.image_url.startsWith('https://') || part.image_url.startsWith('data:image/')))) {
        return 'Invalid image input.';
      }
    }
  }
  return null;
}

app.get('/health', (_req, res) => {
  res.set('Cache-Control', 'no-store').json({
    service: 'sadeeqi-ai',
    version: process.env.SADEEQI_VERSION || '1.5.1',
    provider: PROVIDER,
    model: MODEL,
    auth_mode: AUTH_MODE,
    configured: Boolean(AUTH_MODE !== 'invalid' && API_KEY && MODEL && (AUTH_MODE === 'supabase' ? SUPABASE_JWKS : AUTH_MODE === 'dual' ? (SUPABASE_JWKS || APP_TOKEN) : APP_TOKEN)),
    provider_status: API_KEY && MODEL ? 'configured_not_live_tested' : 'missing_credentials_or_model'
  });
});

export function buildSocialProviders(env = process.env) {
  const definitions = [
    { id: 'facebook', name: 'Facebook', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_CLIENT_ID', 'META_CLIENT_SECRET'], note: 'تتطلب صلاحيات Meta ومراجعة التطبيق حسب نوع البيانات.' },
    { id: 'instagram', name: 'Instagram', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_CLIENT_ID', 'META_CLIENT_SECRET'], note: 'تقتصر الإدارة على الحسابات وأنواع المحتوى التي تسمح بها واجهات Instagram الرسمية.' },
    { id: 'messenger', name: 'Messenger', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_CLIENT_ID', 'META_CLIENT_SECRET'], note: 'تتطلب صفحة Facebook وصلاحيات Messenger المعتمدة.' },
    { id: 'whatsapp', name: 'WhatsApp', family: 'Meta', auth: 'WhatsApp Business Cloud API', env: ['WHATSAPP_TOKEN', 'WHATSAPP_PHONE_NUMBER_ID'], note: 'واجهة Cloud API مخصصة للأعمال؛ لا تمنح وصولاً عاماً إلى محادثات WhatsApp الشخصية.' },
    { id: 'telegram', name: 'Telegram', family: 'Telegram', auth: 'Bot API', env: ['TELEGRAM_BOT_TOKEN'], note: 'Bot API للبوتات والمحادثات التي يُسمح للبوت بالوصول إليها، وليس حساب المستخدم الشخصي.' },
    { id: 'tiktok', name: 'TikTok', family: 'TikTok', auth: 'TikTok OAuth 2.0', env: ['TIKTOK_CLIENT_KEY', 'TIKTOK_CLIENT_SECRET'], note: 'النشر والقراءة يعتمدان على موافقة TikTok ونطاقات المنتج المتاحة.' },
    { id: 'youtube', name: 'YouTube', family: 'Google', auth: 'Google OAuth 2.0', env: ['GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET'], note: 'إدارة القنوات عبر YouTube Data API وفق الحصص والصلاحيات.' },
    { id: 'x', name: 'X', family: 'X', auth: 'X OAuth 2.0', env: ['X_CLIENT_ID', 'X_CLIENT_SECRET'], note: 'القراءة والنشر مرتبطان بخطة API ونطاقات الوصول.' },
    { id: 'snapchat', name: 'Snapchat', family: 'Snap', auth: 'Snap OAuth 2.0', env: ['SNAPCHAT_CLIENT_ID', 'SNAPCHAT_CLIENT_SECRET'], note: 'الوصول التجاري يتطلب منتج Snap معتمداً وصلاحيات مناسبة.' },
    { id: 'linkedin', name: 'LinkedIn', family: 'LinkedIn', auth: 'LinkedIn OAuth 2.0', env: ['LINKEDIN_CLIENT_ID', 'LINKEDIN_CLIENT_SECRET'], note: 'واجهات النشر والإدارة تتطلب منتجات وصلاحيات LinkedIn المناسبة.' },
    { id: 'discord', name: 'Discord', family: 'Discord', auth: 'Discord OAuth 2.0 / Bot', env: ['DISCORD_CLIENT_ID', 'DISCORD_CLIENT_SECRET'], note: 'البوت لا يرى إلا الخوادم والقنوات التي أُضيف إليها ومنحته الصلاحيات.' },
    { id: 'reddit', name: 'Reddit', family: 'Reddit', auth: 'Reddit OAuth 2.0', env: ['REDDIT_CLIENT_ID', 'REDDIT_CLIENT_SECRET'], note: 'تخضع القراءة والنشر لسياسات Reddit وحدود الاستخدام.' },
    { id: 'pinterest', name: 'Pinterest', family: 'Pinterest', auth: 'Pinterest OAuth 2.0', env: ['PINTEREST_APP_ID', 'PINTEREST_APP_SECRET'], note: 'تتطلب صلاحيات التطبيق المعتمدة لإدارة اللوحات والدبابيس.' },
    { id: 'threads', name: 'Threads', family: 'Meta', auth: 'Meta OAuth 2.0', env: ['META_CLIENT_ID', 'META_CLIENT_SECRET'], note: 'تتطلب صلاحيات Threads API ومراجعة التطبيق عند اللزوم.' },
    { id: 'twitch', name: 'Twitch', family: 'Twitch', auth: 'Twitch OAuth 2.0', env: ['TWITCH_CLIENT_ID', 'TWITCH_CLIENT_SECRET'], note: 'الوظائف تعتمد على نطاقات OAuth وتسجيل تطبيق Twitch.' }
  ];

  return definitions.map(({ env: required, ...provider }) => {
    const credentialsPresent = required.every((name) => Boolean(env[name]));
    const encryptionReady = Boolean(env.SOCIAL_TOKEN_ENCRYPTION_KEY);
    const configured = provider.id === 'telegram'
      ? credentialsPresent
      : credentialsPresent && encryptionReady;
    return {
      ...provider,
      configured,
      status: configured ? 'credentials_and_encryption_ready' : 'setup_required',
      connected: false
    };
  });
}

app.get('/v1/social/providers', requireAuth, (_req, res) => {
  // Readiness only: OAuth, account linking and social actions are not implemented.
  const providers = buildSocialProviders();
  res.set('Cache-Control', 'no-store').json({
    phase: 'provider_readiness',
    connectedCount: providers.filter(provider => provider.connected).length,
    configuredCount: providers.filter(provider => provider.configured).length,
    providers,
    notice: 'هذه حالة إعداد واجهات المطور والتشفير فقط. لا توجد حسابات اجتماعية متصلة حالياً، ولا يمكن الربط أو النشر قبل تنفيذ OAuth وموافقة المستخدم.'
  });
});

app.post('/v1/chat', requireAuth, chatRateLimit, async (req, res) => {
  if (!API_KEY || !MODEL) return res.status(503).json({ error: `${PROVIDER} server credentials are not configured.` });

  const validationError = validateInput(req.body?.input);
  if (validationError) return res.status(400).json({ error: validationError });

  try {
    const providerRequest = buildProviderRequest({
      input: req.body.input,
      enableWebSearch: process.env.ALLOW_WEB_SEARCH !== 'false' && req.body.enable_web_search === true
    });
    const upstream = await fetch(providerRequest.url, providerRequest.init);
    let result;
    try {
      result = await upstream.json();
    } catch {
      return res.status(502).json({ error: `${PROVIDER} returned an invalid response.`, provider: PROVIDER });
    }
    if (!upstream.ok) {
      // Provider failures must not be mistaken for expired user sessions.
      const status = upstream.status === 429 ? 429 : 502;
      if (status === 429 && upstream.headers.get('retry-after')) {
        res.set('Retry-After', upstream.headers.get('retry-after'));
      }
      return res.status(status).json({
        error: result?.error?.message || `${PROVIDER} request failed.`,
        code: result?.error?.code || null,
        provider: PROVIDER
      });
    }
    const output = extractProviderText(result);
    if (!output) return res.status(502).json({ error: 'AI provider returned an empty response.', provider: PROVIDER });
    return res.json({ id: result.id, model: result.model || MODEL, provider: PROVIDER, output_text: output, usage: result.usage || null });
  } catch (error) {
    // Do not log prompt content, credentials, or upstream response bodies.
    console.error('[ai-provider] request failed', { provider: PROVIDER, name: error?.name || 'Error' });
    return res.status(502).json({ error: `${PROVIDER} unavailable. Check provider status and server logs.`, provider: PROVIDER });
  }
});

export default function handler(req, res) {
  return app(req, res);
}

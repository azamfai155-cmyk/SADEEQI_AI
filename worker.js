const DEFAULT_SYSTEM_PROMPT =
  'أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية، واستخدم اللهجة اليمنية بصورة طبيعية عندما تلائم السياق. كن عملياً ودقيقاً وصريحاً، ولا تدّع تنفيذ إجراء لم تنفذه. اطلب موافقة عزام قبل أي إجراء حساس أو شراء أو إرسال أو حذف. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API. وضّح عدم اليقين واستشهد بالمصادر عند استخدام البحث.';

const MAX_BODY_BYTES = 12 * 1024 * 1024;
const MAX_TURNS = 24;
const MAX_TEXT_LENGTH = 16000;

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      'cache-control': 'no-store',
      'x-content-type-options': 'nosniff',
      ...headers
    }
  });
}

function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  const left = new TextEncoder().encode(a);
  const right = new TextEncoder().encode(b);
  if (left.length !== right.length) return false;
  let mismatch = 0;
  for (let i = 0; i < left.length; i++) mismatch |= left[i] ^ right[i];
  return mismatch === 0;
}

function corsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const headers = {
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'Authorization, Content-Type',
    'vary': 'Origin'
  };
  if (env.ALLOWED_ORIGIN && origin === env.ALLOWED_ORIGIN) {
    headers['access-control-allow-origin'] = origin;
  }
  return headers;
}

const SOCIAL_PROVIDERS = [
  { id: 'facebook', name: 'Facebook', family: 'Meta', auth: 'OAuth 2.0', note: 'Pages and professional assets; App Review and approved permissions required.' },
  { id: 'instagram', name: 'Instagram', family: 'Meta', auth: 'OAuth 2.0', note: 'Professional accounts; messaging, publishing and insights depend on approved scopes.' },
  { id: 'whatsapp', name: 'WhatsApp Business', family: 'Meta', auth: 'Meta Business OAuth', note: 'Cloud API is for business numbers; templates and business verification may apply.' },
  { id: 'messenger', name: 'Messenger', family: 'Meta', auth: 'Meta OAuth', note: 'Page messaging requires approved pages_messaging access and webhooks.' },
  { id: 'telegram', name: 'Telegram', family: 'Telegram', auth: 'Bot token', note: 'Bot API only; it does not grant access to a personal Telegram inbox.' },
  { id: 'tiktok', name: 'TikTok', family: 'TikTok', auth: 'OAuth 2.0', note: 'Display and Content Posting products require app approval; unaudited posts may be private.' },
  { id: 'youtube', name: 'YouTube', family: 'Google', auth: 'Google OAuth 2.0', note: 'Channel data and uploads require YouTube scopes and quota.' },
  { id: 'x', name: 'X', family: 'X', auth: 'OAuth 2.0', note: 'Read/write access depends on the X API plan and app permissions.' },
  { id: 'snapchat', name: 'Snapchat', family: 'Snap', auth: 'Snap OAuth', note: 'Public Profile and Marketing APIs have separate eligibility and access.' },
  { id: 'linkedin', name: 'LinkedIn', family: 'LinkedIn', auth: 'OAuth 2.0', note: 'Posting and organization access require approved products and scopes.' },
  { id: 'discord', name: 'Discord', family: 'Discord', auth: 'OAuth 2.0 / Bot', note: 'Bot access is limited to servers and channels where it is installed.' },
  { id: 'reddit', name: 'Reddit', family: 'Reddit', auth: 'OAuth 2.0', note: 'API access is subject to Reddit Data API terms and rate limits.' },
  { id: 'pinterest', name: 'Pinterest', family: 'Pinterest', auth: 'OAuth 2.0', note: 'Pins and analytics require approved scopes and account eligibility.' },
  { id: 'threads', name: 'Threads', family: 'Meta', auth: 'Meta OAuth', note: 'Threads API supports eligible account publishing and insights with approved permissions.' },
  { id: 'twitch', name: 'Twitch', family: 'Twitch', auth: 'OAuth 2.0', note: 'Capabilities depend on user scopes and registered application.' }
];

const SOCIAL_ENV = {
  facebook: ['META_CLIENT_ID', 'META_CLIENT_SECRET'],
  instagram: ['META_CLIENT_ID', 'META_CLIENT_SECRET'],
  whatsapp: ['WHATSAPP_TOKEN', 'WHATSAPP_PHONE_NUMBER_ID'],
  messenger: ['META_CLIENT_ID', 'META_CLIENT_SECRET'],
  tiktok: ['TIKTOK_CLIENT_KEY', 'TIKTOK_CLIENT_SECRET'],
  youtube: ['GOOGLE_CLIENT_ID', 'GOOGLE_CLIENT_SECRET'],
  x: ['X_CLIENT_ID', 'X_CLIENT_SECRET'],
  snapchat: ['SNAPCHAT_CLIENT_ID', 'SNAPCHAT_CLIENT_SECRET'],
  linkedin: ['LINKEDIN_CLIENT_ID', 'LINKEDIN_CLIENT_SECRET'],
  discord: ['DISCORD_CLIENT_ID', 'DISCORD_CLIENT_SECRET'],
  reddit: ['REDDIT_CLIENT_ID', 'REDDIT_CLIENT_SECRET'],
  pinterest: ['PINTEREST_APP_ID', 'PINTEREST_APP_SECRET'],
  threads: ['META_CLIENT_ID', 'META_CLIENT_SECRET'],
  twitch: ['TWITCH_CLIENT_ID', 'TWITCH_CLIENT_SECRET']
};

function socialProviders(env) {
  return SOCIAL_PROVIDERS.map(provider => {
    const keys = SOCIAL_ENV[provider.id];
    const configured = provider.id === 'telegram'
      ? Boolean(env.TELEGRAM_BOT_TOKEN)
      : Boolean(keys && env[keys[0]] && env[keys[1]] && env.SOCIAL_TOKEN_ENCRYPTION_KEY);
    return {
      ...provider,
      configured,
      connected: false,
      status: configured ? 'oauth_ready_not_connected' : 'setup_required',
      availableActions: []
    };
  });
}

function validateInput(input) {
  if (!Array.isArray(input) || input.length < 1 || input.length > MAX_TURNS) {
    return 'input must contain 1 to 24 turns.';
  }
  for (const turn of input) {
    if (!turn || !['user', 'assistant'].includes(turn.role)) return 'Invalid role.';
    if (typeof turn.content === 'string') {
      if (turn.content.length > MAX_TEXT_LENGTH) return 'Message too large.';
      continue;
    }
    if (!Array.isArray(turn.content) || turn.content.length < 1 || turn.content.length > 4) {
      return 'Invalid content.';
    }
    for (const part of turn.content) {
      if (!part || !['input_text', 'input_image'].includes(part.type)) return 'Invalid content part.';
      if (part.type === 'input_text' && (typeof part.text !== 'string' || part.text.length > MAX_TEXT_LENGTH)) {
        return 'Invalid input text.';
      }
      if (part.type === 'input_image' && (typeof part.image_url !== 'string' ||
          !(part.image_url.startsWith('https://') || part.image_url.startsWith('data:image/')))) {
        return 'Invalid image input.';
      }
    }
  }
  return null;
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    const url = new URL(request.url);

    if (request.method === 'OPTIONS') {
      if (env.ALLOWED_ORIGIN && request.headers.get('Origin') !== env.ALLOWED_ORIGIN) {
        return json({ error: 'Origin not allowed.' }, 403, cors);
      }
      return new Response(null, { status: 204, headers: cors });
    }

    if (url.pathname === '/v1/social/providers' && request.method === 'GET') {
      if (!env.SADEEQI_APP_TOKEN) return json({ error: 'Backend auth is not configured.' }, 503, cors);
      const authorization = request.headers.get('Authorization') || '';
      const token = authorization.replace(/^Bearer\s+/i, '');
      if (!safeEqual(token, env.SADEEQI_APP_TOKEN)) return json({ error: 'Unauthorized.' }, 401, cors);
      const providers = socialProviders(env);
      return json({
        service: 'sadeeqi-social-hub',
        phase: 'provider_readiness',
        connectedCount: providers.filter(provider => provider.connected).length,
        configuredCount: providers.filter(provider => provider.configured).length,
        providers,
        notice: 'No social account is connected. OAuth and API actions remain disabled until provider credentials, encrypted token storage and user authentication are configured.'
      }, 200, cors);
    }

    if (url.pathname === '/health' && request.method === 'GET') {
      return json({
        service: 'sadeeqi-ai',
        version: '1.2.1',
        runtime: 'cloudflare-workers',
        configured: Boolean(env.OPENAI_API_KEY && env.SADEEQI_APP_TOKEN && env.OPENAI_MODEL)
      }, 200, cors);
    }

    if (url.pathname === '/' && request.method === 'GET') {
      return json({ name: 'SADEEQI AI', status: 'running', version: '1.2.1' }, 200, cors);
    }

    if (url.pathname !== '/v1/chat') return json({ error: 'Not found.' }, 404, cors);
    if (request.method !== 'POST') return json({ error: 'Method not allowed.' }, 405, { ...cors, allow: 'POST, OPTIONS' });

    if (!env.SADEEQI_APP_TOKEN) return json({ error: 'Backend auth is not configured.' }, 503, cors);
    const authorization = request.headers.get('Authorization') || '';
    const token = authorization.replace(/^Bearer\s+/i, '');
    if (!safeEqual(token, env.SADEEQI_APP_TOKEN)) return json({ error: 'Unauthorized.' }, 401, cors);

    if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL) {
      return json({ error: 'OpenAI server secrets are not configured.' }, 503, cors);
    }

    const declaredLength = Number(request.headers.get('Content-Length') || 0);
    if (declaredLength > MAX_BODY_BYTES) return json({ error: 'Request body too large.' }, 413, cors);

    let rawBody;
    try { rawBody = await request.text(); }
    catch { return json({ error: 'Unable to read request body.' }, 400, cors); }
    if (new TextEncoder().encode(rawBody).length > MAX_BODY_BYTES) {
      return json({ error: 'Request body too large.' }, 413, cors);
    }

    let body;
    try { body = JSON.parse(rawBody); }
    catch { return json({ error: 'Invalid JSON.' }, 400, cors); }

    const validationError = validateInput(body?.input);
    if (validationError) return json({ error: validationError }, 400, cors);

    const payload = {
      model: env.OPENAI_MODEL,
      instructions: env.SADEEQI_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT,
      input: body.input,
      store: false
    };
    if (env.ALLOW_WEB_SEARCH !== 'false' && body.enable_web_search === true) {
      payload.tools = [{ type: 'web_search' }];
    }

    try {
      const upstream = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${env.OPENAI_API_KEY}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(110000)
      });

      let result;
      try { result = await upstream.json(); }
      catch { return json({ error: 'Invalid response from AI provider.' }, 502, cors); }

      if (!upstream.ok) {
        const status = upstream.status >= 400 && upstream.status < 500 ? upstream.status : 502;
        return json({ error: result?.error?.message || 'OpenAI request failed.' }, status, cors);
      }

      const output = (result.output || [])
        .flatMap(item => item.content || [])
        .filter(part => part.type === 'output_text')
        .map(part => part.text)
        .join('\\n')
        .trim();

      if (!output) return json({ error: 'AI provider returned an empty response.' }, 502, cors);
      return json({
        id: result.id,
        model: result.model || env.OPENAI_MODEL,
        output_text: output,
        usage: result.usage || null
      }, 200, cors);
    } catch {
      return json({ error: 'AI provider unavailable. Check provider status and server logs.' }, 502, cors);
    }
  }
};

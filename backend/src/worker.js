const DEFAULT_SYSTEM_PROMPT = 'أنت صديقي، مساعد عزام الرقمي. اجعل العربية لغتك الأساسية في جميع الردود، بما فيها الشرح والعناوين والخطوات. اكتب عربية سليمة، طبيعية، واضحة وسهلة الفهم، بجمل قصيرة ومنظمة، وتجنب الترجمة الحرفية والركاكة والتكرار. استخدم العربية الفصحى المبسطة افتراضياً، وانتقل إلى اللهجة اليمنية القريبة من لهجة إب عندما يتحدث المستخدم بها أو يطلبها. لا تستخدم الإنجليزية إلا للمصطلحات التقنية أو الأسماء التي يلزم إبقاؤها، واشرح المصطلح الأجنبي بالعربية عند أول ظهور. كن عملياً ودقيقاً وصريحاً، ولا تخترع معلومات أو تدّع تنفيذ إجراء لم تنفذه. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API.';

const MAX_BODY_BYTES = 6 * 1024 * 1024;
const MAX_INPUT_TURNS = 24;
const MAX_TEXT_CHARS = 20000;
const MAX_IMAGE_DATA_CHARS = 4 * 1024 * 1024;
const MAX_OUTPUT_TOKENS = 1200;

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store, max-age=0',
    'x-content-type-options': 'nosniff',
    'referrer-policy': 'no-referrer',
    'x-frame-options': 'DENY',
    ...headers
  }});
}

function corsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const headers = {
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'Authorization, Content-Type',
    'access-control-max-age': '600',
    'vary': 'Origin'
  };
  if (env.ALLOWED_ORIGIN && origin === env.ALLOWED_ORIGIN) {
    headers['access-control-allow-origin'] = origin;
  }
  return headers;
}

function b64url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function unb64url(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error('Invalid base64url');
  const v = value.replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(v + '='.repeat((4 - v.length % 4) % 4)), c => c.charCodeAt(0));
}
async function signingKey(secret) {
  return crypto.subtle.importKey('raw', new TextEncoder().encode(secret),
    {name:'HMAC', hash:'SHA-256'}, false, ['sign','verify']);
}
async function issueSession(secret, now = Math.floor(Date.now()/1000)) {
  const header = b64url(new TextEncoder().encode(JSON.stringify({alg:'HS256',typ:'JWT'})));
  const payload = b64url(new TextEncoder().encode(JSON.stringify({
    iss:'sadeeqi-ai', aud:'sadeeqi-mobile', iat:now, exp:now+600
  })));
  const input = header + '.' + payload;
  const sig = await crypto.subtle.sign('HMAC', await signingKey(secret), new TextEncoder().encode(input));
  return input + '.' + b64url(new Uint8Array(sig));
}
async function verifySession(token, secret, now = Math.floor(Date.now()/1000)) {
  if (typeof token !== 'string' || token.length > 2048) return false;
  const parts = token.split('.');
  if (parts.length !== 3) return false;
  try {
    const header = JSON.parse(new TextDecoder().decode(unb64url(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(unb64url(parts[1])));
    if (header.alg !== 'HS256' || header.typ !== 'JWT' ||
        payload.iss !== 'sadeeqi-ai' || payload.aud !== 'sadeeqi-mobile' ||
        !Number.isInteger(payload.exp) || payload.exp <= now ||
        !Number.isInteger(payload.iat) || payload.iat > now + 30 ||
        payload.exp - payload.iat !== 600) return false;
    return await crypto.subtle.verify('HMAC', await signingKey(secret), unb64url(parts[2]),
      new TextEncoder().encode(parts[0] + '.' + parts[1]));
  } catch { return false; }
}

async function applyRateLimit(binding, request, scope) {
  if (!binding || typeof binding.limit !== 'function') return 'unconfigured';
  const ip = request.headers.get('CF-Connecting-IP');
  if (!ip) return 'unavailable';
  try {
    const result = await binding.limit({key: scope + ':' + ip});
    return result?.success === false ? 'limited' : result?.success === true ? 'ok' : 'unavailable';
  } catch {
    return 'unavailable';
  }
}

function validateInput(input) {
  if (!Array.isArray(input) || input.length < 1 || input.length > MAX_INPUT_TURNS) {
    return 'يجب أن يحتوي الطلب على رسالة واحدة إلى 24 رسالة.';
  }
  let textChars = 0;
  for (const turn of input) {
    if (!turn || !['user','assistant'].includes(turn.role)) return 'نوع الرسالة غير صالح.';
    if (typeof turn.content === 'string') {
      textChars += turn.content.length;
      continue;
    }
    if (!Array.isArray(turn.content) || turn.content.length < 1 || turn.content.length > 4) {
      return 'محتوى الرسالة غير صالح.';
    }
    for (const part of turn.content) {
      if (part?.type === 'input_text' && typeof part.text === 'string') {
        textChars += part.text.length;
      } else if (part?.type === 'input_image' && typeof part.image_url === 'string') {
        if (part.image_url.length > MAX_IMAGE_DATA_CHARS ||
            !/^data:image\/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(part.image_url)) {
          return 'صيغة الصورة غير مدعومة أو حجمها كبير.';
        }
      } else {
        return 'نوع أحد عناصر المحتوى غير مسموح.';
      }
    }
  }
  if (textChars > MAX_TEXT_CHARS) return 'حجم النص أكبر من الحد المسموح.';
  return null;
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    if (request.method === 'OPTIONS') return new Response(null, {status:204, headers:cors});
    const url = new URL(request.url);

    if (url.pathname === '/health' && request.method === 'GET') {
      const configured = Boolean(
        env.OPENAI_API_KEY && env.OPENAI_MODEL &&
        typeof env.SADEEQI_SESSION_SECRET === 'string' && env.SADEEQI_SESSION_SECRET.length >= 32 &&
        env.SESSION_RATE_LIMITER && typeof env.SESSION_RATE_LIMITER.limit === 'function' &&
        env.CHAT_RATE_LIMITER && typeof env.CHAT_RATE_LIMITER.limit === 'function'
      );
      return json({service:'sadeeqi-ai',version:'1.4.1',runtime:'cloudflare-workers',configured},200,cors);
    }

    if (url.pathname === '/v1/session' && request.method === 'POST') {
      const rate = await applyRateLimit(env.SESSION_RATE_LIMITER, request, 'session');
      if (rate === 'unconfigured' || rate === 'unavailable')
        return json({error:'Session protection is unavailable.'},503,cors);
      if (rate === 'limited') return json({error:'طلبات كثيرة. حاول بعد دقيقة.'},429,cors);
      if (typeof env.SADEEQI_SESSION_SECRET !== 'string' || env.SADEEQI_SESSION_SECRET.length < 32)
        return json({error:'Session signing is not configured.'},503,cors);
      return json({token:await issueSession(env.SADEEQI_SESSION_SECRET),token_type:'Bearer',expires_in:600},200,cors);
    }

    if (url.pathname !== '/v1/chat' || request.method !== 'POST') {
      return json({error:'Not found'},404,cors);
    }

    const rate = await applyRateLimit(env.CHAT_RATE_LIMITER, request, 'chat');
    if (rate === 'unconfigured' || rate === 'unavailable')
      return json({error:'Chat protection is unavailable.'},503,cors);
    if (rate === 'limited') return json({error:'طلبات كثيرة. حاول بعد دقيقة.'},429,cors);

    if (typeof env.SADEEQI_SESSION_SECRET !== 'string' || env.SADEEQI_SESSION_SECRET.length < 32)
      return json({error:'Session signing is not configured.'},503,cors);
    const authorization = request.headers.get('Authorization') || '';
    const match = /^Bearer ([A-Za-z0-9_.-]{1,2048})$/i.exec(authorization);
    if (!match || !await verifySession(match[1],env.SADEEQI_SESSION_SECRET))
      return json({error:'Unauthorized or expired session.'},401,cors);

    if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL)
      return json({error:'AI provider is not configured.'},503,cors);

    const declaredLength = Number(request.headers.get('Content-Length') || 0);
    if (declaredLength > MAX_BODY_BYTES) return json({error:'حجم الطلب أكبر من الحد المسموح.'},413,cors);
    let rawBody;
    try { rawBody = await request.text(); } catch { return json({error:'تعذر قراءة الطلب.'},400,cors); }
    if (new TextEncoder().encode(rawBody).byteLength > MAX_BODY_BYTES)
      return json({error:'حجم الطلب أكبر من الحد المسموح.'},413,cors);
    let body;
    try { body = JSON.parse(rawBody); } catch { return json({error:'Invalid JSON.'},400,cors); }
    const validationError = validateInput(body?.input);
    if (validationError) return json({error:validationError},400,cors);

    const payload = {
      model:env.OPENAI_MODEL,
      instructions:env.SADEEQI_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT,
      input:body.input,
      store:false,
      max_output_tokens:MAX_OUTPUT_TOKENS
    };
    if (env.ALLOW_WEB_SEARCH !== 'false' && body.enable_web_search === true) {
      payload.tools = [{type:'web_search'}];
    }

    try {
      const upstream = await fetch('https://api.openai.com/v1/responses',{
        method:'POST',
        headers:{Authorization:'Bearer '+env.OPENAI_API_KEY,'Content-Type':'application/json'},
        body:JSON.stringify(payload),
        signal:AbortSignal.timeout(90000)
      });
      const raw = await upstream.json().catch(()=>null);
      if (!upstream.ok) {
        // Do not expose provider response bodies, request IDs, or internal diagnostics to clients.
        return json({error:upstream.status === 429
          ? 'محرك الذكاء الاصطناعي مشغول حالياً. حاول لاحقاً.'
          : 'تعذر إكمال طلب الذكاء الاصطناعي.'},upstream.status === 429 ? 429 : 502,cors);
      }
      const output = (raw?.output || []).flatMap(item=>item.content || [])
        .filter(part=>part.type==='output_text').map(part=>part.text).join('\n').trim();
      if (!output) return json({error:'وصل رد فارغ من محرك الذكاء الاصطناعي.'},502,cors);
      return json({id:raw?.id || null,model:raw?.model || env.OPENAI_MODEL,output_text:output,usage:raw?.usage || null},200,cors);
    } catch {
      return json({error:'تعذر الاتصال بمحرك الذكاء الاصطناعي. حاول لاحقاً.'},502,cors);
    }
  }
};

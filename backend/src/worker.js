const DEFAULT_SYSTEM_PROMPT = 'أنت صديقي، مساعد عزام الرقمي. اجعل العربية لغتك الأساسية في جميع الردود، بما فيها الشرح والعناوين والخطوات. اكتب عربية سليمة، طبيعية، واضحة وسهلة الفهم، بجمل قصيرة ومنظمة، وتجنب الترجمة الحرفية والركاكة والتكرار. استخدم العربية الفصحى المبسطة افتراضياً، وانتقل إلى اللهجة اليمنية القريبة من لهجة إب عندما يتحدث المستخدم بها أو يطلبها. لا تستخدم الإنجليزية إلا للمصطلحات التقنية أو الأسماء التي يلزم إبقاؤها، واشرح المصطلح الأجنبي بالعربية عند أول ذكر. حافظ على الأرقام والتواريخ والخطوات بوضوح، ولا تخلط العربية والإنجليزية بلا حاجة. خاطب المستخدم باحترام وود دون مبالغة. كن عملياً ودقيقاً وصريحاً، ولا تخترع معلومات أو تدّع تنفيذ إجراء لم تنفذه. إذا لم تفهم الطلب فاسأل سؤالاً واحداً واضحاً. اطلب موافقة المستخدم قبل أي إجراء حساس أو شراء أو إرسال أو حذف. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API. وضّح عدم اليقين واستشهد بالمصادر عند استخدام البحث.';

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), { status, headers: {
    'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers
  }});
}
function corsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const headers = {'access-control-allow-methods':'GET, POST, OPTIONS',
    'access-control-allow-headers':'Authorization, Content-Type'};
  if (env.ALLOWED_ORIGIN && origin === env.ALLOWED_ORIGIN) {
    headers['access-control-allow-origin'] = origin; headers.vary = 'Origin';
  }
  return headers;
}
function b64url(bytes) {
  let binary = ''; for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function unb64url(value) {
  const v = value.replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(v + '='.repeat((4 - v.length % 4) % 4)), c => c.charCodeAt(0));
}
async function signingKey(secret) {
  return crypto.subtle.importKey('raw', new TextEncoder().encode(secret),
    {name:'HMAC', hash:'SHA-256'}, false, ['sign','verify']);
}
async function issueSession(secret, now = Math.floor(Date.now()/1000)) {
  const h = b64url(new TextEncoder().encode(JSON.stringify({alg:'HS256',typ:'JWT'})));
  const p = b64url(new TextEncoder().encode(JSON.stringify({iss:'sadeeqi-ai',aud:'sadeeqi-mobile',iat:now,exp:now+600})));
  const input = h + '.' + p;
  const sig = await crypto.subtle.sign('HMAC', await signingKey(secret), new TextEncoder().encode(input));
  return input + '.' + b64url(new Uint8Array(sig));
}
async function isRateLimited(binding, request, scope) {
  if (!binding || typeof binding.limit !== 'function') return null;
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const result = await binding.limit({key: scope + ':' + ip});
  return result?.success === false;
}
async function verifySession(token, secret, now = Math.floor(Date.now()/1000)) {
  if (typeof token !== 'string') return false;
  const parts = token.split('.');
  if (parts.length !== 3) return false;
  try {
    const header = JSON.parse(new TextDecoder().decode(unb64url(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(unb64url(parts[1])));
    if (header.alg !== 'HS256' || header.typ !== 'JWT' ||
        payload.iss !== 'sadeeqi-ai' || payload.aud !== 'sadeeqi-mobile' ||
        !Number.isInteger(payload.exp) || payload.exp <= now ||
        !Number.isInteger(payload.iat) || payload.iat > now + 30 ||
        payload.exp - payload.iat > 600) return false;
    return await crypto.subtle.verify('HMAC', await signingKey(secret), unb64url(parts[2]),
      new TextEncoder().encode(parts[0] + '.' + parts[1]));
  } catch { return false; }
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    if (request.method === 'OPTIONS') return new Response(null, {status:204, headers:cors});
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') {
      return json({service:'sadeeqi-ai',version:'1.3.0',runtime:'cloudflare-workers',
        configured:Boolean(env.OPENAI_API_KEY && env.SADEEQI_SESSION_SECRET && env.SADEEQI_SESSION_SECRET.length >= 32 && env.OPENAI_MODEL && env.SESSION_RATE_LIMITER && env.CHAT_RATE_LIMITER)},200,cors);
    }
    if (url.pathname === '/v1/session' && request.method === 'POST') {
      if (!env.SADEEQI_SESSION_SECRET || env.SADEEQI_SESSION_SECRET.length < 32)
        return json({error:'Session signing is not configured.'},503,cors);
      return json({token:await issueSession(env.SADEEQI_SESSION_SECRET),token_type:'Bearer',expires_in:600},200,cors);
    }
    if (url.pathname !== '/v1/chat' || request.method !== 'POST') return json({error:'Not found'},404,cors);
    if (!env.SADEEQI_SESSION_SECRET || env.SADEEQI_SESSION_SECRET.length < 32)
      return json({error:'Session signing is not configured.'},503,cors);
    const token = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i,'');
    if (!await verifySession(token,env.SADEEQI_SESSION_SECRET))
      return json({error:'Unauthorized or expired session.'},401,cors);
    if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL)
      return json({error:'OpenAI server secrets are not configured.'},503,cors);
    let body;
    try { body = await request.json(); } catch { return json({error:'Invalid JSON.'},400,cors); }
    const input = body?.input;
    if (!Array.isArray(input) || input.length < 1 || input.length > 24)
      return json({error:'input must contain 1 to 24 turns.'},400,cors);
    for (const turn of input) {
      if (!['user','assistant'].includes(turn?.role)) return json({error:'Invalid role.'},400,cors);
      if (!(typeof turn.content === 'string' || Array.isArray(turn.content))) return json({error:'Invalid content.'},400,cors);
      if (typeof turn.content === 'string' && turn.content.length > 16000) return json({error:'Message too large.'},413,cors);
    }
    const payload = {model:env.OPENAI_MODEL,instructions:env.SADEEQI_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT,input,store:false};
    if (env.ALLOW_WEB_SEARCH !== 'false' && body.enable_web_search === true) payload.tools = [{type:'web_search'}];
    try {
      const upstream = await fetch('https://api.openai.com/v1/responses',{
        method:'POST',headers:{Authorization:'Bearer '+env.OPENAI_API_KEY,'Content-Type':'application/json'},
        body:JSON.stringify(payload),signal:AbortSignal.timeout(110000)
      });
      const raw = await upstream.json();
      if (!upstream.ok) return json({error:raw?.error?.message || 'OpenAI request failed.'},upstream.status,cors);
      const output = (raw.output || []).flatMap(item=>item.content || [])
        .filter(part=>part.type==='output_text').map(part=>part.text).join('\n').trim();
      return json({id:raw.id,model:raw.model || env.OPENAI_MODEL,output_text:output,usage:raw.usage || null},200,cors);
    } catch { return json({error:'AI provider unavailable. Check provider status and server logs.'},502,cors); }
  }
};

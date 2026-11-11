const DEFAULT_SYSTEM_PROMPT = 'أنت صديقي، المساعد الرقمي الشخصي لعزام. أجب بالعربية، واستخدم اللهجة اليمنية بصورة طبيعية عندما تلائم السياق. كن عملياً ودقيقاً وصريحاً، ولا تدّع تنفيذ إجراء لم تنفذه. اطلب موافقة عزام قبل أي إجراء حساس أو شراء أو إرسال أو حذف. احترم الخصوصية ولا تطلب كلمات المرور أو مفاتيح API. وضّح عدم اليقين واستشهد بالمصادر عند استخدام البحث.';

function json(data, status = 200, extraHeaders = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...extraHeaders }
  });
}
function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
function corsHeaders(request, env) {
  const origin = request.headers.get('Origin');
  const allowed = env.ALLOWED_ORIGIN;
  const headers = { 'access-control-allow-methods': 'GET, POST, OPTIONS', 'access-control-allow-headers': 'Authorization, Content-Type' };
  if (allowed && origin === allowed) headers['access-control-allow-origin'] = origin;
  return headers;
}

export default {
  async fetch(request, env) {
    const cors = corsHeaders(request, env);
    if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
    const url = new URL(request.url);
    if (url.pathname === '/health' && request.method === 'GET') {
      return json({ service: 'sadeeqi-ai', version: '1.2.0', runtime: 'cloudflare-workers', configured: Boolean(env.OPENAI_API_KEY && env.SADEEQI_APP_TOKEN && env.OPENAI_MODEL) }, 200, cors);
    }
    if (url.pathname !== '/v1/chat' || request.method !== 'POST') return json({ error: 'Not found' }, 404, cors);
    if (!env.SADEEQI_APP_TOKEN) return json({ error: 'Backend auth is not configured.' }, 503, cors);
    const token = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
    if (!safeEqual(token, env.SADEEQI_APP_TOKEN)) return json({ error: 'Unauthorized.' }, 401, cors);
    if (!env.OPENAI_API_KEY || !env.OPENAI_MODEL) return json({ error: 'OpenAI server secrets are not configured.' }, 503, cors);

    let body;
    try { body = await request.json(); } catch { return json({ error: 'Invalid JSON.' }, 400, cors); }
    const input = body?.input;
    if (!Array.isArray(input) || input.length < 1 || input.length > 24) return json({ error: 'input must contain 1 to 24 turns.' }, 400, cors);
    for (const turn of input) {
      if (!['user', 'assistant'].includes(turn?.role)) return json({ error: 'Invalid role.' }, 400, cors);
      if (!(typeof turn.content === 'string' || Array.isArray(turn.content))) return json({ error: 'Invalid content.' }, 400, cors);
      if (typeof turn.content === 'string' && turn.content.length > 16000) return json({ error: 'Message too large.' }, 413, cors);
    }
    const payload = { model: env.OPENAI_MODEL, instructions: env.SADEEQI_SYSTEM_PROMPT || DEFAULT_SYSTEM_PROMPT, input, store: false };
    if (env.ALLOW_WEB_SEARCH !== 'false' && body.enable_web_search === true) payload.tools = [{ type: 'web_search' }];
    try {
      const upstream = await fetch('https://api.openai.com/v1/responses', {
        method: 'POST',
        headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
        signal: AbortSignal.timeout(110000)
      });
      const raw = await upstream.json();
      if (!upstream.ok) return json({ error: raw?.error?.message || 'OpenAI request failed.' }, upstream.status, cors);
      const output = (raw.output || []).flatMap(item => item.content || [])
        .filter(part => part.type === 'output_text').map(part => part.text).join('\n').trim();
      return json({ id: raw.id, model: raw.model || env.OPENAI_MODEL, output_text: output, usage: raw.usage || null }, 200, cors);
    } catch {
      return json({ error: 'AI provider unavailable. Check provider status and server logs.' }, 502, cors);
    }
  }
};

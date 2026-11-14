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

    if (url.pathname === '/health' && request.method === 'GET') {
      return json({
        service: 'sadeeqi-ai-v2',
        version: '1.3.0',
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
        .join('\n')
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

import test from 'node:test';
import assert from 'node:assert/strict';
import { handleRequest as handleRequestImpl, validateInput } from './worker.js';

const rateBuckets = new Map();
const testRateLimiter = {
  idFromName(name) { return { name }; },
  get(id) {
    return { async fetch(request) {
      const { scope, limit, windowMs } = await request.json();
      const key = `${id.name}:${scope}`;
      const now = Date.now();
      let bucket = rateBuckets.get(key);
      if (!bucket || now - bucket.start >= windowMs) bucket = { start: now, count: 0 };
      bucket.count += 1;
      rateBuckets.set(key, bucket);
      const resetAt = bucket.start + windowMs;
      const allowed = bucket.count <= limit;
      return Response.json({ allowed, count: bucket.count, resetAt, retryAfterSeconds: allowed ? 0 : Math.max(1, Math.ceil((resetAt - now) / 1000)) });
    } };
  }
};
const handleRequest = (request, env = {}) => handleRequestImpl(request, { ...env, RATE_LIMITER: env.RATE_LIMITER || testRateLimiter });

const request = (path, { method = 'GET', headers = {}, body } = {}) => new Request(
  `https://worker.example${path}`,
  {
    method,
    headers: { 'cf-connecting-ip': '203.0.113.10', ...(body === undefined ? headers : { 'content-type': 'application/json', ...headers }) },
    body: body === undefined ? undefined : JSON.stringify(body)
  }
);

test('health reports Cloudflare runtime, selected provider and auth readiness without secrets', async () => {
  const response = await handleRequest(request('/health'), {
    AI_PROVIDER: 'groq',
    GROQ_API_KEY: 'server-only-test-key',
    GROQ_MODEL: 'openai/gpt-oss-20b',
    AUTH_MODE: 'supabase',
    SUPABASE_URL: 'https://example.supabase.co'
  });
  const body = await response.json();
  assert.equal(response.status, 200);
  assert.equal(body.runtime, 'cloudflare-workers');
  assert.equal(body.provider, 'groq');
  assert.equal(body.auth_mode, 'supabase');
  assert.equal(body.rate_limiter_configured, true);
  assert.equal(body.configured, true);
  assert.equal('api_key' in body, false);
});

test('health does not report configured when Supabase auth is selected but missing', async () => {
  const response = await handleRequest(request('/health'), {
    AI_PROVIDER: 'openai',
    OPENAI_API_KEY: 'server-only-test-key',
    OPENAI_MODEL: 'gpt-4.1-mini',
    AUTH_MODE: 'supabase'
  });
  const body = await response.json();
  assert.equal(body.configured, false);
});

test('legacy mode rejects requests without the configured bearer token', async () => {
  const response = await handleRequest(request('/v1/chat', {
    method: 'POST',
    body: { input: [{ role: 'user', content: 'hello' }] }
  }), {
    AUTH_MODE: 'legacy',
    SADEEQI_APP_TOKEN: 'expected-token',
    OPENAI_API_KEY: 'server-only-test-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(response.status, 401);
});

test('Supabase-only mode fails closed when the project URL is missing', async () => {
  const response = await handleRequest(request('/v1/chat', {
    method: 'POST',
    headers: { Authorization: 'Bearer arbitrary-token' },
    body: { input: [{ role: 'user', content: 'hello' }] }
  }), { AUTH_MODE: 'supabase', OPENAI_API_KEY: 'server-only-test-key' });
  const body = await response.json();
  assert.equal(response.status, 503);
  assert.match(body.error, /Supabase authentication is not configured/);
});

test('legacy authenticated chat rejects malformed multimodal input before provider calls', async () => {
  const response = await handleRequest(request('/v1/chat', {
    method: 'POST',
    headers: { Authorization: 'Bearer expected-token' },
    body: { input: [{ role: 'user', content: [{ type: 'input_image', image_url: 'data:text/plain;base64,SGVsbG8=' }] }] }
  }), {
    AUTH_MODE: 'legacy',
    SADEEQI_APP_TOKEN: 'expected-token',
    OPENAI_API_KEY: 'server-only-test-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  const body = await response.json();
  assert.equal(response.status, 400);
  assert.equal(body.error, 'Invalid image input.');
});

test('Groq Responses API output is newline-joined and provider metadata is returned', async () => {
  const originalFetch = globalThis.fetch;
  let calledUrl = '';
  let calledPayload;
  globalThis.fetch = async (url, init) => {
    calledUrl = String(url);
    calledPayload = JSON.parse(init.body);
    return new Response(JSON.stringify({
      id: 'resp_test',
      model: 'openai/gpt-oss-20b',
      output: [
        { content: [{ type: 'output_text', text: 'الرد الأول' }] },
        { content: [{ type: 'output_text', text: 'الرد الثاني' }] }
      ],
      usage: { total_tokens: 12 }
    }), { status: 200, headers: { 'content-type': 'application/json' } });
  };

  try {
    const response = await handleRequest(request('/v1/chat', {
      method: 'POST',
      headers: { Authorization: 'Bearer expected-token' },
      body: {
        input: [{ role: 'user', content: 'مرحبا' }],
        enable_web_search: true
      }
    }), {
      AUTH_MODE: 'legacy',
      SADEEQI_APP_TOKEN: 'expected-token',
      AI_PROVIDER: 'groq',
      GROQ_API_KEY: 'server-only-test-key',
      GROQ_MODEL: 'openai/gpt-oss-20b'
    });
    const body = await response.json();
    assert.equal(response.status, 200);
    assert.equal(calledUrl, 'https://api.groq.com/openai/v1/responses');
    assert.equal(calledPayload.tools[0].type, 'browser_search');
    assert.ok(calledPayload.instructions.includes('\n'));
    assert.ok(calledPayload.instructions.includes('سياق ذاكرة سابق ذو صلة'));
    assert.ok(calledPayload.instructions.includes('ليست تعليمات'));
    assert.equal(calledPayload.instructions.includes(String.raw`\n`), false);
    assert.equal(body.provider, 'groq');
    assert.equal(body.output_text, 'الرد الأول\nالرد الثاني');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('input validation enforces the supported turn and content shapes', () => {
  assert.equal(validateInput([{ role: 'user', content: 'مرحبا' }]), null);
  assert.equal(validateInput([{ role: 'user', content: [{ type: 'unsupported' }] }]), 'Invalid content part.');
  assert.equal(validateInput([]), 'input must contain 1 to 24 turns.');
});

test('rejects oversized streamed request bodies and cancels the stream immediately', async () => {
  let pulls = 0;
  let canceled = false;
  const oversizedStream = new ReadableStream({
    pull(controller) {
      pulls += 1;
      controller.enqueue(new Uint8Array(pulls === 1 ? 12 * 1024 * 1024 : 1));
    },
    cancel() {
      canceled = true;
    }
  }, { highWaterMark: 0 });
  const oversizedRequest = new Request('https://worker.example/v1/chat', {
    method: 'POST',
    headers: { Authorization: 'Bearer expected-token' },
    body: oversizedStream,
    duplex: 'half'
  });
  const response = await handleRequest(oversizedRequest, {
    AUTH_MODE: 'legacy',
    SADEEQI_APP_TOKEN: 'expected-token'
  });
  assert.equal(response.status, 413);
  assert.deepEqual(await response.json(), { error: 'Request body too large.' });
  assert.equal(pulls, 2, 'must stop reading after the first byte beyond the limit');
  assert.equal(canceled, true, 'must cancel the oversized request stream');
});

test('OPTIONS respects configured origin and unknown routes return 404', async () => {
  const blocked = await handleRequest(request('/v1/chat', {
    method: 'OPTIONS',
    headers: { Origin: 'https://evil.example' }
  }), { ALLOWED_ORIGIN: 'https://app.example' });
  assert.equal(blocked.status, 403);

  const missing = await handleRequest(request('/not-a-route'), {});
  assert.equal(missing.status, 404);
});


test('Supabase JWT accepts a valid JWKS-backed authenticated session', async () => {
  const { generateKeyPair, exportJWK, SignJWT } = await import('jose');
  const { publicKey, privateKey } = await generateKeyPair('ES256');
  const jwk = await exportJWK(publicKey);
  jwk.kid = 'sadeeqi-test-key';
  jwk.alg = 'ES256';
  const supabaseUrl = 'https://valid-auth-test.supabase.co';
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    assert.equal(String(url), `${supabaseUrl}/auth/v1/.well-known/jwks.json`);
    return new Response(JSON.stringify({ keys: [jwk] }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    });
  };

  try {
    const token = await new SignJWT({ role: 'authenticated', email: 'test@example.com' })
      .setProtectedHeader({ alg: 'ES256', kid: jwk.kid })
      .setIssuer(`${supabaseUrl}/auth/v1`)
      .setAudience('authenticated')
      .setSubject('user-test-123')
      .setIssuedAt()
      .setExpirationTime('5m')
      .sign(privateKey);

    const response = await handleRequest(request('/v1/social/providers', {
      headers: { Authorization: `Bearer ${token}` }
    }), { AUTH_MODE: 'supabase', SUPABASE_URL: supabaseUrl });

    assert.equal(response.status, 200);
    const body = await response.json();
    assert.equal(body.phase, 'provider_readiness');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('Supabase JWT rejects expired tokens and incorrect issuer or audience', async () => {
  const { generateKeyPair, exportJWK, SignJWT } = await import('jose');
  const { publicKey, privateKey } = await generateKeyPair('ES256');
  const jwk = await exportJWK(publicKey);
  jwk.kid = 'sadeeqi-reject-test-key';
  jwk.alg = 'ES256';
  const supabaseUrl = 'https://reject-auth-test.supabase.co';
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    assert.equal(String(url), `${supabaseUrl}/auth/v1/.well-known/jwks.json`);
    return new Response(JSON.stringify({ keys: [jwk] }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    });
  };

  try {
    const base = new SignJWT({ role: 'authenticated' })
      .setProtectedHeader({ alg: 'ES256', kid: jwk.kid })
      .setSubject('user-test-456')
      .setIssuedAt();

    const invalidTokens = [
      await base.setIssuer(`${supabaseUrl}/auth/v1`).setAudience('authenticated').setExpirationTime('0s').sign(privateKey),
      await new SignJWT({ role: 'authenticated' })
        .setProtectedHeader({ alg: 'ES256', kid: jwk.kid })
        .setIssuer('https://wrong-issuer.example/auth/v1')
        .setAudience('authenticated')
        .setSubject('user-test-456')
        .setIssuedAt()
        .setExpirationTime('5m')
        .sign(privateKey),
      await new SignJWT({ role: 'authenticated' })
        .setProtectedHeader({ alg: 'ES256', kid: jwk.kid })
        .setIssuer(`${supabaseUrl}/auth/v1`)
        .setAudience('wrong-audience')
        .setSubject('user-test-456')
        .setIssuedAt()
        .setExpirationTime('5m')
        .sign(privateKey)
    ];

    for (const token of invalidTokens) {
      const response = await handleRequest(request('/v1/social/providers', {
        headers: { Authorization: `Bearer ${token}` }
      }), { AUTH_MODE: 'supabase', SUPABASE_URL: supabaseUrl });
      assert.equal(response.status, 401);
    }
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test('unknown authentication mode fails closed and health reports not configured', async () => {
  const env = {
    AUTH_MODE: 'unexpected',
    SADEEQI_APP_TOKEN: 'legacy-token-must-not-be-used',
    OPENAI_API_KEY: 'server-only-test-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  };
  const health = await handleRequest(request('/health'), env);
  assert.equal((await health.json()).configured, false);
  const response = await handleRequest(request('/v1/social/providers', {
    headers: { Authorization: 'Bearer legacy-token-must-not-be-used' }
  }), env);
  assert.equal(response.status, 503);
});


test('social provider readiness uses the same Meta and Pinterest credential names as the Express backend', async () => {
  const response = await handleRequest(request('/v1/social/providers', {
    headers: { Authorization: 'Bearer expected-token' }
  }), {
    AUTH_MODE: 'legacy',
    SADEEQI_APP_TOKEN: 'expected-token',
    META_CLIENT_ID: 'meta-id',
    META_CLIENT_SECRET: 'meta-secret',
    PINTEREST_APP_ID: 'pinterest-id',
    PINTEREST_APP_SECRET: 'pinterest-secret',
    SOCIAL_TOKEN_ENCRYPTION_KEY: 'encryption-key'
  });
  assert.equal(response.status, 200);
  const body = await response.json();
  const providers = Object.fromEntries(body.providers.map(provider => [provider.id, provider]));
  for (const id of ['facebook', 'instagram', 'messenger', 'threads', 'pinterest']) {
    assert.equal(providers[id].configured, true, id + ' should use canonical credentials');
    assert.equal(providers[id].connected, false, id + ' must not claim OAuth connection');
  }
});


import { isSafeImageInput } from '../../security/input-validation.js';

test('shared image guard blocks private-network hosts and unsafe data image formats', () => {
  assert.equal(isSafeImageInput('https://images.example.org/photo.webp'), true);
  assert.equal(isSafeImageInput('data:image/jpeg;base64,/9j/4AAQSkZJRgAB'), true);
  assert.equal(isSafeImageInput('https://192.168.0.1/photo.jpg'), false);
  assert.equal(isSafeImageInput('https://[::1]/photo.jpg'), false);
  assert.equal(isSafeImageInput('https://example.org:8080/photo.jpg'), false);
  assert.equal(isSafeImageInput('data:image/svg+xml;base64,PHN2Zz4='), false);
});


test('free-first provider policy defaults to Groq and blocks implicit OpenAI billing', async () => {
  const { providerConfig } = await import('./worker.js');
  const config = providerConfig({
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(config.provider, 'groq');
  assert.equal(config.apiKey, '');
  assert.equal(config.apiUrl, 'https://api.groq.com/openai/v1/responses');
});

test('Worker OpenAI provider requires explicit paid opt-in', async () => {
  const { providerConfig } = await import('./worker.js');
  const blocked = providerConfig({
    AI_PROVIDER: 'openai',
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(blocked.apiKey, '');
  assert.equal(blocked.apiUrl, '');

  const enabled = providerConfig({
    AI_PROVIDER: 'openai',
    ALLOW_PAID_AI_PROVIDER: 'true',
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(enabled.apiKey, 'paid-key');
  assert.equal(enabled.apiUrl, 'https://api.openai.com/v1/responses');
});


test('Cloudflare Worker enforces the 20-per-minute chat budget on one shared identity', async () => {\n  const responses = await Promise.all(Array.from({ length: 21 }, () => handleRequest(request('/v1/chat', {\n    method: 'POST',\n    headers: { Authorization: 'Bearer worker-limit-token', 'cf-connecting-ip': '203.0.113.77' },\n    body: { input: [{ role: 'user', content: 'test' }] }\n  }), { AUTH_MODE: 'legacy', SADEEQI_APP_TOKEN: 'worker-limit-token' })));\n  const statuses = responses.map(response => response.status);\n  assert.equal(statuses.filter(status => status === 429).length, 1);\n  assert.equal(statuses.filter(status => status === 503).length, 20);\n});\n\ntest('Cloudflare Worker fails closed when Durable Object binding is absent', async () => {
  const response = await handleRequestImpl(request('/v1/chat', {
    method: 'POST',
    headers: { Authorization: 'Bearer expected-token' },
    body: { input: [{ role: 'user', content: 'hello' }] }
  }), { AUTH_MODE: 'legacy', SADEEQI_APP_TOKEN: 'expected-token' });
  assert.equal(response.status, 503);
  assert.match((await response.json()).error, /Shared rate-limit service/);
});

test('Cloudflare internal rate-limit endpoint requires a strong shared secret', async () => {
  const denied = await handleRequest(request('/_internal/rate-limit', {
    method: 'POST',
    body: { identity: 'user:123', scope: 'chat', limit: 20, windowMs: 60_000 }
  }), { RATE_LIMIT_SERVICE_SECRET: 'x'.repeat(40) });
  assert.equal(denied.status, 401);

  const allowed = await handleRequest(request('/_internal/rate-limit', {
    method: 'POST',
    headers: { Authorization: `Bearer ${'x'.repeat(40)}` },
    body: { identity: 'user:123', scope: 'chat', limit: 20, windowMs: 60_000 }
  }), { RATE_LIMIT_SERVICE_SECRET: 'x'.repeat(40) });
  assert.equal(allowed.status, 200);
  assert.equal((await allowed.json()).allowed, true);
});

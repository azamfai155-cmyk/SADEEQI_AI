import test from 'node:test';
import assert from 'node:assert/strict';
import { app, buildSocialProviders, extractBearerToken, validateInput } from './server.js';
import http from 'node:http';

async function withServer(run) {
  const server = http.createServer(app);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  try { await run(server.address().port); }
  finally { await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())); }
}

test('health endpoint exposes service metadata without secrets', async () => {
  await withServer(async port => {
    const response = await fetch(`http://127.0.0.1:${port}/health`);
    const data = await response.json();
    assert.equal(response.status, 200);
    assert.equal(data.service, 'sadeeqi-ai');
    assert.equal(data.version, '1.5.1');
    assert.equal('api_key' in data, false);
  });
});

test('chat and social endpoints require authentication', async () => {
  await withServer(async port => {
    const chat = await fetch(`http://127.0.0.1:${port}/v1/chat`, {
      method: 'POST', headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ input: [{ role: 'user', content: 'hello' }] })
    });
    const social = await fetch(`http://127.0.0.1:${port}/v1/social/providers`);
    assert.ok([401, 503].includes(chat.status));
    assert.ok([401, 503].includes(social.status));
  });
});

test('bearer token parser strips the authorization scheme', () => {
  assert.equal(extractBearerToken('Bearer test-token'), 'test-token');
  assert.equal(extractBearerToken('bearer test-token'), 'test-token');
  assert.equal(extractBearerToken('test-token'), 'test-token');
  assert.equal(extractBearerToken(undefined), '');
});

test('chat input validator rejects malformed multimodal parts', () => {
  assert.equal(validateInput([{ role: 'user', content: [{ type: 'unexpected', value: 'bad' }] }]), 'Invalid content part.');
  assert.equal(validateInput([{ role: 'user', content: [{ type: 'input_text', text: 42 }] }]), 'Invalid input text.');
  assert.equal(validateInput([{ role: 'user', content: [{ type: 'input_image', image_url: 'data:text/plain;base64,SGVsbG8=' }] }]), 'Invalid image input.');
});

test('chat input validator accepts text and supported image inputs', () => {
  assert.equal(validateInput([{ role: 'user', content: 'مرحبا' }]), null);
  assert.equal(validateInput([{ role: 'user', content: [{ type: 'input_text', text: 'حلل الصورة' }, { type: 'input_image', image_url: 'https://example.com/image.jpg' }] }]), null);
});

test('social provider readiness exposes all 15 providers without leaking credentials', () => {
  const providers = buildSocialProviders({
    META_CLIENT_ID: 'test-id',
    META_CLIENT_SECRET: 'test-secret',
    SOCIAL_TOKEN_ENCRYPTION_KEY: 'test-encryption-key',
    WHATSAPP_TOKEN: 'test-whatsapp-token',
    WHATSAPP_PHONE_NUMBER_ID: '12345',
    TWITCH_CLIENT_ID: 'test-twitch-id',
    TWITCH_CLIENT_SECRET: 'test-twitch-secret'
  });
  assert.equal(providers.length, 15);
  assert.ok(providers.some(provider => provider.id === 'twitch' && provider.configured));
  assert.ok(providers.some(provider => provider.id === 'whatsapp' && provider.configured));
  assert.ok(providers.filter(provider => ['facebook', 'instagram', 'messenger', 'threads'].includes(provider.id))
    .every(provider => provider.configured));
  assert.ok(providers.every(provider => provider.connected === false));
  assert.ok(providers.every(provider => !('env' in provider)));
  assert.ok(providers.every(provider => !JSON.stringify(provider).includes('test-secret')));
});



test('OAuth providers are not marked ready without server-side token encryption', () => {
  const providers = buildSocialProviders({
    META_CLIENT_ID: 'test-id',
    META_CLIENT_SECRET: 'test-secret',
    TIKTOK_CLIENT_KEY: 'test-tiktok-id',
    TIKTOK_CLIENT_SECRET: 'test-tiktok-secret'
  });
  assert.ok(providers.filter(provider => ['facebook', 'instagram', 'messenger', 'threads', 'tiktok'].includes(provider.id))
    .every(provider => provider.configured === false && provider.status === 'setup_required'));
});

test('chat rate limiter returns 429 after the configured request budget', async () => {
  const { createRateLimiter } = await import('./server.js');
  let current = 1_000;
  const limit = createRateLimiter({ windowMs: 60_000, max: 2, now: () => current });
  const makeResponse = () => {
    const headers = {};
    return {
      headers,
      statusCode: 200,
      body: null,
      set(name, value) { headers[name] = value; return this; },
      status(code) { this.statusCode = code; return this; },
      json(value) { this.body = value; return this; }
    };
  };
  const req = { auth: { userId: 'user-a' } };
  const first = makeResponse();
  const second = makeResponse();
  const third = makeResponse();
  let nextCalls = 0;
  limit(req, first, () => { nextCalls += 1; });
  limit(req, second, () => { nextCalls += 1; });
  limit(req, third, () => { nextCalls += 1; });
  assert.equal(nextCalls, 2);
  assert.equal(third.statusCode, 429);
  assert.equal(third.headers['Retry-After'], '60');

  current += 60_001;
  const afterWindow = makeResponse();
  limit(req, afterWindow, () => { nextCalls += 1; });
  assert.equal(nextCalls, 3);
  assert.equal(afterWindow.statusCode, 200);
});


test('authentication mode parser rejects unknown values instead of silently falling back to legacy auth', async () => {
  const { normalizeAuthMode } = await import('./server.js');
  assert.equal(normalizeAuthMode('supabase'), 'supabase');
  assert.equal(normalizeAuthMode('DUAL'), 'dual');
  assert.equal(normalizeAuthMode('legacy'), 'legacy');
  assert.equal(normalizeAuthMode(undefined), 'invalid');
  assert.equal(normalizeAuthMode(''), 'invalid');
  assert.equal(normalizeAuthMode('unexpected'), 'invalid');
});


test('Groq Responses request uses supported endpoint and omits unsupported store option', async () => {
  const { buildProviderRequest } = await import('./server.js');
  const request = buildProviderRequest({
    provider: 'groq', model: 'openai/gpt-oss-20b', apiKey: 'test-groq-key',
    input: [{ role: 'user', content: 'hello' }]
  });
  assert.equal(request.url, 'https://api.groq.com/openai/v1/responses');
  assert.equal(request.init.headers.Authorization, 'Bearer test-groq-key');
  const payload = JSON.parse(request.init.body);
  assert.equal(payload.model, 'openai/gpt-oss-20b');
  assert.equal('store' in payload, false);
  assert.equal('tools' in payload, false);
});

test('OpenAI Responses request disables response storage by default', async () => {
  const { buildProviderRequest } = await import('./server.js');
  const request = buildProviderRequest({
    provider: 'openai', model: 'gpt-4.1-mini', apiKey: 'test-openai-key',
    input: [{ role: 'user', content: 'hello' }]
  });
  assert.equal(request.url, 'https://api.openai.com/v1/responses');
  assert.equal(JSON.parse(request.init.body).store, false);
});

test('web search is opt-in and uses the provider-specific tool name', async () => {
  const { buildProviderRequest } = await import('./server.js');
  const groq = buildProviderRequest({
    provider: 'groq', model: 'openai/gpt-oss-20b', apiKey: 'x',
    input: [{ role: 'user', content: 'hello' }], enableWebSearch: true
  });
  const openai = buildProviderRequest({
    provider: 'openai', model: 'gpt-4.1-mini', apiKey: 'x',
    input: [{ role: 'user', content: 'hello' }], enableWebSearch: true
  });
  assert.deepEqual(JSON.parse(groq.init.body).tools, [{ type: 'browser_search' }]);
  assert.deepEqual(JSON.parse(openai.init.body).tools, [{ type: 'web_search' }]);
});

test('provider response text extractor handles both Responses API output shapes', async () => {
  const { extractProviderText } = await import('./server.js');
  assert.equal(extractProviderText({ output_text: '  ready  ' }), 'ready');
  assert.equal(extractProviderText({ output: [
    { type: 'message', content: [{ type: 'output_text', text: 'مرحبا' }] }
  ] }), 'مرحبا');
  assert.equal(extractProviderText({ output: [] }), '');
});

test('provider request builder rejects unsupported providers and missing keys', async () => {
  const { buildProviderRequest } = await import('./server.js');
  assert.throws(() => buildProviderRequest({
    provider: 'unknown', apiKey: 'x', input: [{ role: 'user', content: 'x' }]
  }), /Unsupported AI_PROVIDER/);
  assert.throws(() => buildProviderRequest({
    provider: 'groq', apiKey: '', input: [{ role: 'user', content: 'x' }]
  }), /credentials are not configured/);
});


import { isSafeImageInput } from './security/input-validation.js';

test('image input guard allows bounded supported image data and rejects unsafe references', () => {
  assert.equal(isSafeImageInput('data:image/png;base64,iVBORw0KGgoAAAANSUhE'), true);
  assert.equal(isSafeImageInput('https://cdn.example.com/photo.jpg'), true);
  for (const value of [
    'data:text/html;base64,SGVsbG8=',
    'data:image/svg+xml;base64,PHN2Zz4=',
    'data:image/png;base64,/9j/4AAQSkZJRgAB',
    'data:image/png;base64,',
    'http://cdn.example.com/photo.jpg',
    'https://127.0.0.1/private.png',
    'https://10.0.0.5/image.png',
    'https://192.168.1.10/image.png',
    'https://localhost/image.png',
    'https://service.local/image.png',
    'https://user:pass@cdn.example.com/image.png',
    'https://cdn.example.com:8443/image.png'
  ]) assert.equal(isSafeImageInput(value), false, value);
});


test('free-first provider policy defaults to Groq and never falls back to paid OpenAI', async () => {
  const { resolveAiProviderConfig } = await import('./server.js');
  const config = resolveAiProviderConfig({
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(config.provider, 'groq');
  assert.equal(config.apiKey, '');
  assert.equal(config.apiUrl, 'https://api.groq.com/openai/v1/responses');
});

test('OpenAI provider is blocked unless paid usage is explicitly enabled', async () => {
  const { resolveAiProviderConfig } = await import('./server.js');
  const blocked = resolveAiProviderConfig({
    AI_PROVIDER: 'openai',
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(blocked.apiKey, '');
  assert.equal(blocked.apiUrl, '');

  const enabled = resolveAiProviderConfig({
    AI_PROVIDER: 'openai',
    ALLOW_PAID_AI_PROVIDER: 'true',
    OPENAI_API_KEY: 'paid-key',
    OPENAI_MODEL: 'gpt-4.1-mini'
  });
  assert.equal(enabled.apiKey, 'paid-key');
  assert.equal(enabled.apiUrl, 'https://api.openai.com/v1/responses');
});


test('default system and identity instructions use real newline separators', async () => {
  const { buildProviderRequest } = await import('./server.js');
  const request = buildProviderRequest({
    provider: 'groq',
    model: 'openai/gpt-oss-20b',
    apiKey: 'test-groq-key',
    input: [{ role: 'user', content: 'hello' }]
  });
  const instructions = JSON.parse(request.init.body).instructions;
  assert.match(instructions, /\n/);
  assert.equal(instructions.includes('\\n'), false);
  assert.match(instructions, /صديقي/);
});


test('distributed rate limiter uses shared Supabase counters and blocks over-limit requests', async () => {
  const { createDistributedRateLimiter } = await import('./server.js');
  const calls = [];
  const env = {
    ENFORCE_DISTRIBUTED_RATE_LIMIT: 'true',
    SUPABASE_URL: 'https://project.example.supabase.co',
    SUPABASE_SERVICE_ROLE_KEY: 'server-only-key',
    RATE_LIMIT_HASH_KEY: 'h'.repeat(48)
  };
  const middleware = createDistributedRateLimiter({
    scope: 'chat', limit: 1, env,
    fetchImpl: async (url, init) => {
      calls.push({ url: String(url), body: JSON.parse(init.body) });
      return new Response(JSON.stringify([{ allowed: false, retry_after_seconds: 19, current_hits: 2 }]), {
        status: 200, headers: { 'content-type': 'application/json' }
      });
    }
  });
  const res = {
    headers: {}, statusCode: 200, body: null,
    set(name, value) { this.headers[name] = value; return this; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
  let nextCalls = 0;
  await middleware({ ip: '203.0.113.20', auth: { userId: 'user-1' } }, res, () => { nextCalls += 1; });
  assert.equal(res.statusCode, 429);
  assert.equal(res.headers['Retry-After'], '19');
  assert.equal(nextCalls, 0);
  assert.equal(calls.length, 2);
  assert.ok(calls.every(call => call.url.endsWith('/rpc/consume_sadeeqi_rate_limit')));
  assert.ok(calls.every(call => /^[a-f0-9]{64}$/.test(call.body.p_key_hash)));
  assert.equal(JSON.stringify(calls).includes('203.0.113.20'), false);
});

test('distributed rate limiter fails closed when server credentials are missing', async () => {
  const { createDistributedRateLimiter } = await import('./server.js');
  const middleware = createDistributedRateLimiter({
    scope: 'chat', limit: 20,
    env: { ENFORCE_DISTRIBUTED_RATE_LIMIT: 'true', RATE_LIMIT_HASH_KEY: 'h'.repeat(48) }
  });
  const res = {
    statusCode: 200,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
  let nextCalls = 0;
  await middleware({ ip: '203.0.113.20', auth: { userId: 'user-1' } }, res, () => { nextCalls += 1; });
  assert.equal(res.statusCode, 503);
  assert.equal(nextCalls, 0);
});

test('chat audit writes metadata only through the server-side Supabase RPC', async () => {
  const { writeAuditEvent } = await import('./server.js');
  let sent;
  const env = {
    ENFORCE_AUDIT_LOGGING: 'true',
    SUPABASE_URL: 'https://project.example.supabase.co',
    SUPABASE_SERVICE_ROLE_KEY: 'server-only-key'
  };
  const written = await writeAuditEvent({
    actorId: '11111111-1111-4111-8111-111111111111',
    eventType: 'chat.requested',
    resourceType: 'chat',
    metadata: { method: 'POST', provider: 'groq', model: 'test-model' }
  }, {
    env,
    fetchImpl: async (url, init) => {
      sent = { url: String(url), headers: init.headers, body: JSON.parse(init.body) };
      return new Response(JSON.stringify('audit-event-id'), { status: 200 });
    }
  });
  assert.equal(written, true);
  assert.ok(sent.url.endsWith('/rpc/write_sadeeqi_audit_event'));
  assert.equal(sent.body.p_actor_id, '11111111-1111-4111-8111-111111111111');
  assert.deepEqual(sent.body.p_metadata, { method: 'POST', provider: 'groq', model: 'test-model' });
  assert.equal(JSON.stringify(sent.body).includes('prompt'), false);
  assert.equal(sent.headers.Authorization, 'Bearer server-only-key');
});

test('audit logging is a no-op when enforcement is disabled', async () => {
  const { writeAuditEvent } = await import('./server.js');
  const written = await writeAuditEvent({
    eventType: 'chat.requested', resourceType: 'chat'
  }, { env: { ENFORCE_AUDIT_LOGGING: 'false' }, fetchImpl: async () => {
    throw new Error('must not call Supabase');
  } });
  assert.equal(written, false);
});


test('owner middleware allows only the verified configured owner email', async () => {
  const { createOwnerMiddleware } = await import('./server.js');
  const middleware = createOwnerMiddleware({ ownerEmail: 'Owner@Example.com' });
  const invoke = (auth, spoofedRole) => {
    const res = {
      statusCode: 200, body: null,
      status(code) { this.statusCode = code; return this; },
      json(value) { this.body = value; return this; }
    };
    let nextCalls = 0;
    middleware({ auth, body: { role: spoofedRole }, headers: { 'x-user-role': spoofedRole } }, res, () => { nextCalls += 1; });
    return { res, nextCalls };
  };

  const owner = invoke({ userId: 'owner-id', email: 'owner@example.com' }, 'user');
  assert.equal(owner.nextCalls, 1);
  assert.equal(owner.res.statusCode, 200);

  const regular = invoke({ userId: 'user-id', email: 'user@example.com' }, 'owner');
  assert.equal(regular.nextCalls, 0);
  assert.equal(regular.res.statusCode, 403);

  const missingIdentity = invoke({ userId: 'legacy-id', email: null }, 'owner');
  assert.equal(missingIdentity.nextCalls, 0);
  assert.equal(missingIdentity.res.statusCode, 403);
});

test('owner middleware fails closed when OWNER_EMAIL is missing or blank', async () => {
  const { createOwnerMiddleware } = await import('./server.js');
  for (const ownerEmail of [undefined, '', '   ']) {
    const middleware = createOwnerMiddleware({ ownerEmail });
    const res = {
      statusCode: 200,
      status(code) { this.statusCode = code; return this; },
      json(value) { this.body = value; return this; }
    };
    let nextCalls = 0;
    middleware({ auth: { userId: 'owner-id', email: 'owner@example.com' } }, res, () => { nextCalls += 1; });
    assert.equal(nextCalls, 0);
    assert.equal(res.statusCode, 403);
  }
});

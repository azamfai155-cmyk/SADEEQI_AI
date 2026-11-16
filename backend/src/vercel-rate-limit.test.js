import test from 'node:test';
import assert from 'node:assert/strict';
import { createDistributedRateLimiter } from './vercel-rate-limit.js';

function responseRecorder() {
  return {
    statusCode: 200,
    headers: {},
    body: null,
    set(name, value) { this.headers[name] = value; return this; },
    status(code) { this.statusCode = code; return this; },
    json(body) { this.body = body; return this; }
  };
}

const configuredEnv = {
  RATE_LIMIT_SERVICE_URL: 'https://sadeeqi-ai.example.workers.dev/_internal/rate-limit',
  RATE_LIMIT_SERVICE_SECRET: 'test-secret-value-with-at-least-32-characters'
};

test('Vercel middleware sends authenticated user identity to the shared service', async () => {
  let payload;
  const limiter = createDistributedRateLimiter({
    scope: 'chat', limit: 20, windowMs: 60_000, env: configuredEnv,
    fetchImpl: async (url, init) => {
      assert.equal(url, configuredEnv.RATE_LIMIT_SERVICE_URL);
      assert.equal(init.headers.authorization, `Bearer ${configuredEnv.RATE_LIMIT_SERVICE_SECRET}`);
      payload = JSON.parse(init.body);
      return Response.json({ allowed: true, count: 1, resetAt: Date.now() + 60_000 });
    }
  });
  const res = responseRecorder();
  let nextCalls = 0;
  await limiter({ auth: { userId: 'supabase-user-123' }, headers: {} }, res, () => { nextCalls += 1; });
  assert.equal(nextCalls, 1);
  assert.equal(payload.identity, 'user:supabase-user-123');
  assert.equal(payload.scope, 'chat');
  assert.equal(res.headers['RateLimit-Remaining'], '19');
});

test('legacy mode uses only a syntactically valid platform-forwarded client IP', async () => {
  let payload;
  const limiter = createDistributedRateLimiter({
    env: configuredEnv,
    fetchImpl: async (_url, init) => {
      payload = JSON.parse(init.body);
      return Response.json({ allowed: true, count: 1, resetAt: Date.now() + 60_000 });
    }
  });
  const res = responseRecorder();
  await limiter({ auth: { userId: null }, headers: { 'x-forwarded-for': '203.0.113.9, 10.0.0.1' } }, res, () => {});
  assert.equal(payload.identity, 'ip:203.0.113.9');
});

test('Vercel middleware returns 429 and Retry-After when shared budget is exhausted', async () => {
  const limiter = createDistributedRateLimiter({
    env: configuredEnv,
    fetchImpl: async () => Response.json({ allowed: false, count: 21, resetAt: Date.now() + 60_000, retryAfterSeconds: 60 })
  });
  const res = responseRecorder();
  let nextCalls = 0;
  await limiter({ auth: { userId: 'user-429' }, headers: {} }, res, () => { nextCalls += 1; });
  assert.equal(res.statusCode, 429);
  assert.equal(res.headers['Retry-After'], '60');
  assert.equal(nextCalls, 0);
});

test('Vercel middleware fails closed for missing configuration and remote service failure', async () => {
  const missing = createDistributedRateLimiter({ env: {}, fetchImpl: async () => { throw new Error('must not run'); } });
  const first = responseRecorder();
  await missing({ auth: { userId: 'user-a' }, headers: {} }, first, () => { throw new Error('must fail closed'); });
  assert.equal(first.statusCode, 503);

  const unavailable = createDistributedRateLimiter({
    env: configuredEnv, fetchImpl: async () => { throw new Error('timeout'); }
  });
  const second = responseRecorder();
  await unavailable({ auth: { userId: 'user-a' }, headers: {} }, second, () => { throw new Error('must fail closed'); });
  assert.equal(second.statusCode, 503);
});

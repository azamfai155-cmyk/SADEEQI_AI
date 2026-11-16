import test from 'node:test';
import assert from 'node:assert/strict';
import { RateLimitDurableObject, consumeRateLimit, validRateLimitPolicy } from './rate-limit.js';

class AtomicSqlMock {
  constructor() { this.buckets = new Map(); }
  exec(query, ...args) {
    if (query.includes('CREATE TABLE')) return { one: () => undefined };
    const [scope, now, windowMs, compareNow] = args;
    const current = this.buckets.get(scope);
    let next;
    if (!current || current.window_start + windowMs <= compareNow) {
      next = { window_start: now, request_count: 1 };
    } else {
      next = { window_start: current.window_start, request_count: current.request_count + 1 };
    }
    this.buckets.set(scope, next);
    return { one: () => ({ ...next }) };
  }
}

const check = (object, scope = 'chat', limit = 20, windowMs = 60_000) =>
  object.fetch(new Request('https://rate-limit.internal/check', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ scope, limit, windowMs })
  }));

test('policy rejects unsafe scopes and out-of-range budgets', () => {
  assert.equal(validRateLimitPolicy('chat', 20, 60_000), true);
  assert.equal(validRateLimitPolicy('../chat', 20, 60_000), false);
  assert.equal(validRateLimitPolicy('chat', 0, 60_000), false);
  assert.equal(validRateLimitPolicy('chat', 20, 0), false);
});

test('Durable Object atomic counter rejects concurrent requests beyond the exact limit', async () => {
  const sql = new AtomicSqlMock();
  const objectA = new RateLimitDurableObject({ storage: { sql } });
  const objectB = new RateLimitDurableObject({ storage: { sql } });
  const results = await Promise.all(Array.from({ length: 100 }, (_, index) => check(index % 2 ? objectA : objectB)));
  const allowed = results.filter(response => response.status === 200);
  const bodies = await Promise.all(results.map(response => response.json()));
  assert.equal(bodies.filter(result => result.allowed).length, 20);
  assert.equal(bodies.filter(result => !result.allowed).length, 80);
  assert.equal(sql.buckets.get('chat').request_count, 100);
  assert.equal(allowed.length, 100);
});

test('counter survives creation of a new Worker-side Durable Object instance', async () => {
  const sql = new AtomicSqlMock();
  const firstInstance = new RateLimitDurableObject({ storage: { sql } });
  for (let i = 0; i < 3; i++) await check(firstInstance, 'chat', 5);
  const restartedInstance = new RateLimitDurableObject({ storage: { sql } });
  const result = await (await check(restartedInstance, 'chat', 5)).json();
  assert.equal(result.count, 4);
  assert.equal(result.allowed, true);
});

test('fixed window resets after its configured duration', async () => {
  const sql = new AtomicSqlMock();
  const originalNow = Date.now;
  let now = 10_000;
  Date.now = () => now;
  try {
    const object = new RateLimitDurableObject({ storage: { sql } });
    const first = await (await check(object, 'chat', 1, 60_000)).json();
    const second = await (await check(object, 'chat', 1, 60_000)).json();
    assert.equal(first.allowed, true);
    assert.equal(second.allowed, false);
    now += 60_001;
    const afterReset = await (await check(object, 'chat', 1, 60_000)).json();
    assert.equal(afterReset.allowed, true);
    assert.equal(afterReset.count, 1);
  } finally { Date.now = originalNow; }
});

test('same identity is deterministically routed to one shared Durable Object id', async () => {
  const ids = [];
  const namespace = {
    idFromName(name) { ids.push(name); return { name }; },
    get(id) { return { fetch: async () => Response.json({ allowed: true, count: 1, resetAt: Date.now() + 60_000 }) }; }
  };
  await consumeRateLimit({ RATE_LIMITER: namespace }, 'user:abc', 'chat', 20, 60_000);
  await consumeRateLimit({ RATE_LIMITER: namespace }, 'user:abc', 'chat', 20, 60_000);
  await consumeRateLimit({ RATE_LIMITER: namespace }, 'user:xyz', 'chat', 20, 60_000);
  assert.equal(ids[0], ids[1]);
  assert.notEqual(ids[0], ids[2]);
});

const encoder = new TextEncoder();

async function identityDigest(identity) {
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(identity));
  return Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
}

export function validRateLimitPolicy(scope, limit, windowMs) {
  return typeof scope === 'string' &&
    /^[a-z][a-z0-9_-]{1,63}$/.test(scope) &&
    Number.isInteger(limit) && limit >= 1 && limit <= 1000 &&
    Number.isInteger(windowMs) && windowMs >= 1000 && windowMs <= 86_400_000;
}

export async function consumeRateLimit(env, identity, scope, limit, windowMs) {
  if (typeof identity !== 'string' || identity.length < 3 || identity.length > 512 ||
      !validRateLimitPolicy(scope, limit, windowMs) || !env?.RATE_LIMITER) {
    throw new Error('Shared rate limiter is not configured or request is invalid.');
  }

  const digest = await identityDigest(identity);
  const id = env.RATE_LIMITER.idFromName(digest);
  const stub = env.RATE_LIMITER.get(id);
  const response = await stub.fetch('https://rate-limit.internal/check', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ scope, limit, windowMs })
  });
  if (!response.ok) throw new Error('Shared rate limiter request failed.');
  const result = await response.json();
  if (typeof result.allowed !== 'boolean' || !Number.isInteger(result.count) ||
      !Number.isFinite(result.resetAt)) {
    throw new Error('Shared rate limiter returned an invalid response.');
  }
  return result;
}

export class RateLimitDurableObject {
  constructor(ctx) {
    this.sql = ctx.storage.sql;
    this.sql.exec(`
      CREATE TABLE IF NOT EXISTS rate_limit_buckets (
        scope TEXT PRIMARY KEY,
        window_start INTEGER NOT NULL,
        request_count INTEGER NOT NULL
      )
    `);
  }

  async fetch(request) {
    if (request.method !== 'POST' || new URL(request.url).pathname !== '/check') {
      return Response.json({ error: 'Not found.' }, { status: 404 });
    }

    let input;
    try {
      const raw = await request.text();
      if (raw.length > 2048) return Response.json({ error: 'Invalid request.' }, { status: 400 });
      input = JSON.parse(raw);
    } catch {
      return Response.json({ error: 'Invalid request.' }, { status: 400 });
    }

    const { scope, limit, windowMs } = input || {};
    if (!validRateLimitPolicy(scope, limit, windowMs)) {
      return Response.json({ error: 'Invalid rate-limit policy.' }, { status: 400 });
    }

    const now = Date.now();
    // One SQLite UPSERT is the atomic read/modify/write operation. The DO ID is
    // derived from the identity hash, so all regions and Worker instances for
    // this identity are routed to the same strongly consistent object.
    const row = this.sql.exec(`
      INSERT INTO rate_limit_buckets (scope, window_start, request_count)
      VALUES (?, ?, 1)
      ON CONFLICT(scope) DO UPDATE SET
        request_count = CASE
          WHEN rate_limit_buckets.window_start + ? <= ? THEN 1
          ELSE rate_limit_buckets.request_count + 1
        END,
        window_start = CASE
          WHEN rate_limit_buckets.window_start + ? <= ? THEN excluded.window_start
          ELSE rate_limit_buckets.window_start
        END
      RETURNING request_count, window_start
    `, scope, now, windowMs, now, windowMs, now).one();

    const resetAt = row.window_start + windowMs;
    const allowed = row.request_count <= limit;
    return Response.json({
      allowed,
      count: row.request_count,
      limit,
      resetAt,
      retryAfterSeconds: allowed ? 0 : Math.max(1, Math.ceil((resetAt - now) / 1000))
    }, { headers: { 'cache-control': 'no-store' } });
  }
}

function constantTimeEqual(left, right) {
  if (typeof left !== 'string' || typeof right !== 'string' || left.length !== right.length) return false;
  let mismatch = 0;
  for (let index = 0; index < left.length; index++) {
    mismatch |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return mismatch === 0;
}

export function isInternalRateLimitAuthorized(request, secret) {
  if (typeof secret !== 'string' || secret.length < 32) return false;
  return constantTimeEqual(request.headers.get('authorization') || '', `Bearer ${secret}`);
}

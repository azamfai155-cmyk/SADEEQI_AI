import { isIP } from 'node:net';

export function createDistributedRateLimiter({
  scope = 'chat',
  limit = 20,
  windowMs = 60_000,
  env = process.env,
  fetchImpl = globalThis.fetch
} = {}) {
  return async (req, res, next) => {
    const endpoint = env.RATE_LIMIT_SERVICE_URL;
    const secret = env.RATE_LIMIT_SERVICE_SECRET;
    if (typeof endpoint !== 'string' || !endpoint.startsWith('https://') ||
        typeof secret !== 'string' || secret.length < 32) {
      return res.status(503).json({ error: 'Shared rate-limit service is not configured.' });
    }

    let identity;
    if (typeof req.auth?.userId === 'string' && req.auth.userId) {
      identity = `user:${req.auth.userId}`;
    } else {
      // Vercel documents that it overwrites x-forwarded-for with the public
      // client IP. Never trust arbitrary first-hop values from an untrusted proxy.
      const forwarded = req.headers?.['x-forwarded-for'];
      const candidate = String(Array.isArray(forwarded) ? forwarded[0] || '' : forwarded || '')
        .split(',')[0].trim();
      if (isIP(candidate) === 0) {
        return res.status(503).json({ error: 'A trusted client identity is unavailable.' });
      }
      identity = `ip:${candidate}`;
    }

    try {
      const response = await fetchImpl(endpoint, {
        method: 'POST',
        headers: {
          authorization: `Bearer ${secret}`,
          'content-type': 'application/json'
        },
        body: JSON.stringify({ identity, scope, limit, windowMs }),
        signal: AbortSignal.timeout(1200)
      });
      if (!response.ok) throw new Error('Rate-limit service rejected the request.');
      const result = await response.json();
      if (typeof result.allowed !== 'boolean' || !Number.isFinite(result.resetAt)) {
        throw new Error('Invalid rate-limit service response.');
      }
      const resetAt = Math.ceil(result.resetAt / 1000);
      res.set('RateLimit-Limit', String(limit));
      res.set('RateLimit-Remaining', String(Math.max(0, limit - result.count)));
      res.set('RateLimit-Reset', String(resetAt));
      if (!result.allowed) {
        const retryAfter = Math.max(1, Number(result.retryAfterSeconds) || Math.ceil((result.resetAt - Date.now()) / 1000));
        res.set('Retry-After', String(retryAfter));
        return res.status(429).json({ error: 'طلبات كثيرة خلال وقت قصير. حاول مرة أخرى بعد قليل.' });
      }
      return next();
    } catch {
      // Fail closed: provider-cost and authenticated write paths must never
      // silently continue when the shared counter cannot make a decision.
      res.set('Retry-After', '2');
      return res.status(503).json({ error: 'Shared rate-limit service is temporarily unavailable.' });
    }
  };
}

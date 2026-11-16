import test from 'node:test';
import assert from 'node:assert/strict';
import { handleRequest } from './worker.js';

test('Realtime voice session requires authentication before issuing a client secret', async () => {
  const originalFetch = globalThis.fetch;
  let upstreamCalled = false;
  globalThis.fetch = async () => {
    upstreamCalled = true;
    throw new Error('OpenAI must not be contacted for an unauthenticated request.');
  };

  try {
    const response = await handleRequest(
      new Request('https://worker.example/v1/voice/session', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ voice: 'marin' })
      }),
      {
        AUTH_MODE: 'legacy',
        SADEEQI_APP_TOKEN: 'expected-token',
        ALLOW_OPENAI_VOICE: 'true',
        OPENAI_REALTIME_API_KEY: 'server-only-test-key'
      }
    );

    assert.equal(response.status, 401);
    assert.equal(upstreamCalled, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('Realtime voice session rejects oversized bodies before contacting OpenAI', async () => {
  const originalFetch = globalThis.fetch;
  let upstreamCalled = false;
  globalThis.fetch = async () => {
    upstreamCalled = true;
    throw new Error('OpenAI must not be contacted for an oversized request.');
  };

  try {
    const response = await handleRequest(
      new Request('https://worker.example/v1/voice/session', {
        method: 'POST',
        headers: {
          authorization: 'Bearer expected-token',
          'content-type': 'application/json'
        },
        body: JSON.stringify({ voice: 'marin', padding: 'x'.repeat(5000) })
      }),
      {
        AUTH_MODE: 'legacy',
        SADEEQI_APP_TOKEN: 'expected-token',
        ALLOW_OPENAI_VOICE: 'true',
        OPENAI_REALTIME_API_KEY: 'server-only-test-key'
      }
    );

    assert.equal(response.status, 413);
    assert.equal(upstreamCalled, false);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

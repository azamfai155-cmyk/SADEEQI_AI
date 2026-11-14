import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.js';

const env = { OPENAI_API_KEY: 'test-key', SADEEQI_APP_TOKEN: 'test-token', OPENAI_MODEL: 'test-model' };

test('health reports configuration without exposing secrets', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/health'), {});
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.configured, false);
  assert.equal(JSON.stringify(data).includes('test-key'), false);
});

test('chat rejects missing bearer token', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ input: [{ role: 'user', content: 'مرحبا' }] })
  });
  assert.equal((await worker.fetch(request, env)).status, 401);
});

test('chat validates payload before contacting provider', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', {
    method: 'POST', headers: { 'content-type': 'application/json', authorization: 'Bearer test-token' },
    body: JSON.stringify({ input: [] })
  });
  assert.equal((await worker.fetch(request, env)).status, 400);
});

test('chat refuses unsupported HTTP methods', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', { method: 'GET' });
  assert.equal((await worker.fetch(request, env)).status, 405);
});


test('chat rejects non-image data URLs before contacting provider', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: 'Bearer test-token' },
    body: JSON.stringify({
      input: [{
        role: 'user',
        content: [{ type: 'input_image', image_url: 'data:text/plain;base64,SGVsbG8=' }]
      }]
    })
  });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 400);
});


test('social provider readiness requires app authentication', async () => {
  const request = new Request('https://example.workers.dev/v1/social/providers');
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 401);
});

test('social provider readiness lists platforms without claiming fake connections', async () => {
  const request = new Request('https://example.workers.dev/v1/social/providers', {
    headers: { authorization: 'Bearer test-token' }
  });
  const response = await worker.fetch(request, env);
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.providers.length, 15);
  assert.equal(data.connectedCount, 0);
  assert.ok(data.providers.every(provider => provider.connected === false));
});

import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './backend/src/worker.js';
const env = { OPENAI_API_KEY: 'test-key', SADEEQI_APP_TOKEN: 'test-token', OPENAI_MODEL: 'test-model' };
test('health reports missing config without exposing secrets', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/health'), {});
  const data = await response.json();
  assert.equal(response.status, 200);
  assert.equal(data.configured, false);
  assert.equal(JSON.stringify(data).includes('test-key'), false);
});
test('chat rejects missing bearer token', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', { method: 'POST', headers: {'content-type':'application/json'}, body: JSON.stringify({input:[{role:'user',content:'مرحبا'}]}) });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 401);
});
test('chat validates request payload before provider call', async () => {
  const request = new Request('https://example.workers.dev/v1/chat', { method: 'POST', headers: {'content-type':'application/json', authorization:'Bearer test-token'}, body: JSON.stringify({input:[]}) });
  const response = await worker.fetch(request, env);
  assert.equal(response.status, 400);
});

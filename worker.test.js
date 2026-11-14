import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './backend/src/worker.js';
const secret = 'test-session-signing-secret-at-least-32-characters';
const allow = {limit: async () => ({success:true})};
const env = {OPENAI_API_KEY:'test-key',SADEEQI_SESSION_SECRET:secret,OPENAI_MODEL:'test-model',
  SESSION_RATE_LIMITER:allow,CHAT_RATE_LIMITER:allow};

test('health reports missing config without exposing secrets', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/health'), {});
  const data = await response.json();
  assert.equal(response.status,200); assert.equal(data.configured,false);
  assert.equal(JSON.stringify(data).includes('test-key'),false);
});
test('session endpoint fails closed when rate limiting is not configured', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}), {
    OPENAI_API_KEY:'test-key',SADEEQI_SESSION_SECRET:secret,OPENAI_MODEL:'test-model'
  });
  assert.equal(response.status,503);
});
test('session endpoint rejects requests over the configured rate limit', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}), {
    ...env,SESSION_RATE_LIMITER:{limit:async()=>({success:false})}
  });
  assert.equal(response.status,429);
});
test('session endpoint issues a signed ten-minute token', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const data = await response.json();
  assert.equal(response.status,200); assert.equal(data.token_type,'Bearer');
  assert.equal(data.expires_in,600); assert.equal(data.token.split('.').length,3);
});
test('chat rejects requests over the configured rate limit', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'مرحبا'}]})}),
    {...env,CHAT_RATE_LIMITER:{limit:async()=>({success:false})}});
  assert.equal(response.status,429);
});
test('chat rejects missing bearer token', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'مرحبا'}]})}),env);
  assert.equal(response.status,401);
});
test('chat validates payload after session authentication', async () => {
  const issued = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+token},
    body:JSON.stringify({input:[]})}),env);
  assert.equal(response.status,400);
});
test('chat rejects a session signed with a different secret', async () => {
  const issued = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'test'}]})}),
    {...env,SADEEQI_SESSION_SECRET:'different-secret-that-is-also-long-enough'});
  assert.equal(response.status,401);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './backend/src/worker.js';

const secret = 'test-session-signing-secret-at-least-32-characters';
const allow = {limit: async () => ({success:true})};
const env = {
  OPENAI_API_KEY:'test-key',
  SADEEQI_SESSION_SECRET:secret,
  OPENAI_MODEL:'test-model',
  SESSION_RATE_LIMITER:allow,
  CHAT_RATE_LIMITER:allow
};
function request(url, init = {}) {
  const headers = new Headers(init.headers || {});
  if (!headers.has('CF-Connecting-IP')) headers.set('CF-Connecting-IP','192.0.2.10');
  return new Request(url,{...init,headers});
}

test('health reports missing configuration without exposing secrets', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/health'), {});
  const data = await response.json();
  assert.equal(response.status,200);
  assert.equal(data.configured,false);
  assert.equal(JSON.stringify(data).includes('test-key'),false);
});

test('health requires actual rate limiter bindings', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/health'), {
    ...env, SESSION_RATE_LIMITER:{}, CHAT_RATE_LIMITER:{}
  });
  assert.equal((await response.json()).configured,false);
});

test('session endpoint fails closed when rate limiting is missing', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}), {
    OPENAI_API_KEY:'test-key',SADEEQI_SESSION_SECRET:secret,OPENAI_MODEL:'test-model'
  });
  assert.equal(response.status,503);
});

test('session endpoint fails closed when limiter throws', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}), {
    ...env,SESSION_RATE_LIMITER:{limit:async()=>{throw new Error('unavailable');}}
  });
  assert.equal(response.status,503);
});

test('session endpoint rejects requests over configured rate limit', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}), {
    ...env,SESSION_RATE_LIMITER:{limit:async()=>({success:false})}
  });
  assert.equal(response.status,429);
});

test('session endpoint fails closed when client IP is unavailable', async () => {
  const response = await worker.fetch(new Request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  assert.equal(response.status,503);
});

test('session endpoint issues a signed ten-minute token', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const data = await response.json();
  assert.equal(response.status,200);
  assert.equal(data.token_type,'Bearer');
  assert.equal(data.expires_in,600);
  assert.equal(data.token.split('.').length,3);
});

test('chat endpoint fails closed when chat limiter is missing', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'مرحبا'}]})
  }),{...env,CHAT_RATE_LIMITER:undefined});
  assert.equal(response.status,503);
});

test('chat rejects requests over configured rate limit', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'مرحبا'}]})
  }),{...env,CHAT_RATE_LIMITER:{limit:async()=>({success:false})}});
  assert.equal(response.status,429);
});

test('chat rejects missing bearer token', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'مرحبا'}]})
  }),env);
  assert.equal(response.status,401);
});

test('chat validates payload after session authentication', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{'content-type':'application/json',authorization:'Bearer '+token},
    body:JSON.stringify({input:[]})
  }),env);
  assert.equal(response.status,400);
});

test('chat rejects a session signed with a different secret', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:'test'}]})
  }),{...env,SADEEQI_SESSION_SECRET:'different-secret-that-is-also-long-enough'});
  assert.equal(response.status,401);
});

test('chat rejects oversized request bodies before provider call', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json','content-length':'7000000'},
    body:' '.repeat(7000000)
  }),env);
  assert.equal(response.status,413);
});

test('chat rejects unsupported multimodal content parts', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:[{type:'input_file',file_id:'arbitrary'}]}]})
  }),env);
  assert.equal(response.status,400);
});

test('chat rejects remote image URLs and accepts only inline image data', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:[{type:'input_image',image_url:'https://attacker.example/image.png'}]}]})
  }),env);
  assert.equal(response.status,400);
});

test('chat rejects inline images above the supported 3 MiB source-image budget', async () => {
  const issued = await worker.fetch(request('https://example.workers.dev/v1/session',{method:'POST'}),env);
  const {token} = await issued.json();
  const oversizedImage = 'data:image/jpeg;base64,' + 'A'.repeat(4 * 1024 * 1024 + 1);
  const response = await worker.fetch(request('https://example.workers.dev/v1/chat',{
    method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
    body:JSON.stringify({input:[{role:'user',content:[{type:'input_image',image_url:oversizedImage}]}]})
  }),env);
  assert.equal(response.status,400);
});

test('non-API paths are not exposed', async () => {
  const response = await worker.fetch(request('https://example.workers.dev/admin'),env);
  assert.equal(response.status,404);
});

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
    assert.equal(data.version, '1.4.0');
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

import test from 'node:test';
import assert from 'node:assert/strict';
import { encryptSocialToken, decryptSocialToken } from './token-vault.js';

const key = btoa(String.fromCharCode(...new Uint8Array(32).fill(7)));

test('AES-256-GCM token vault encrypts and decrypts with owner binding', async () => {
  const context = { provider: 'telegram', userId: 'user-123' };
  const encrypted = await encryptSocialToken('secret-access-token', key, context);
  assert.equal(encrypted.includes('secret-access-token'), false);
  assert.equal(await decryptSocialToken(encrypted, key, context), 'secret-access-token');
  await assert.rejects(() => decryptSocialToken(encrypted, key, { ...context, userId: 'user-456' }));
  await assert.rejects(() => decryptSocialToken(encrypted, key, { ...context, provider: 'discord' }));
});

test('AES-GCM uses a fresh nonce for every encryption', async () => {
  const context = { provider: 'meta', userId: 'user-123' };
  const first = await encryptSocialToken('same-token', key, context);
  const second = await encryptSocialToken('same-token', key, context);
  assert.notEqual(first, second);
});

test('token vault rejects missing, weak, malformed keys and invalid envelopes', async () => {
  await assert.rejects(() => encryptSocialToken('token', '', { provider: 'meta', userId: 'u' }));
  await assert.rejects(() => encryptSocialToken('token', btoa('short'), { provider: 'meta', userId: 'u' }));
  await assert.rejects(() => decryptSocialToken('{}', key, { provider: 'meta', userId: 'u' }));
});

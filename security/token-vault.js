const encoder = new TextEncoder();
const decoder = new TextDecoder();

function decodeKey(value) {
  if (typeof value !== 'string') throw new TypeError('SOCIAL_TOKEN_ENCRYPTION_KEY is required.');
  const normalized = value.trim();
  let bytes;
  try {
    const binary = atob(normalized);
    bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
  } catch {
    throw new TypeError('Encryption key must be base64-encoded.');
  }
  if (bytes.length !== 32) throw new TypeError('Encryption key must decode to exactly 32 bytes.');
  return bytes;
}

async function importKey(value, usage) {
  return crypto.subtle.importKey('raw', decodeKey(value), { name: 'AES-GCM' }, false, usage);
}

function toBase64(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  const binary = atob(value);
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function additionalData(provider, userId) {
  if (!/^[a-z0-9_-]{1,40}$/i.test(provider) || typeof userId !== 'string' || !userId || userId.length > 128) {
    throw new TypeError('Invalid token owner or provider.');
  }
  return encoder.encode('sadeeqi-token-v1:' + provider.toLowerCase() + ':' + userId);
}

export async function encryptSocialToken(plaintext, key, { provider, userId }) {
  if (typeof plaintext !== 'string' || !plaintext || plaintext.length > 16384) {
    throw new TypeError('Token must contain 1 to 16384 characters.');
  }
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cryptoKey = await importKey(key, ['encrypt']);
  const ciphertext = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv, additionalData: additionalData(provider, userId), tagLength: 128 },
    cryptoKey,
    encoder.encode(plaintext)
  );
  return JSON.stringify({
    version: 1,
    algorithm: 'A256GCM',
    iv: toBase64(iv),
    ciphertext: toBase64(new Uint8Array(ciphertext))
  });
}

export async function decryptSocialToken(envelope, key, { provider, userId }) {
  let record;
  try { record = JSON.parse(envelope); } catch { throw new TypeError('Invalid encrypted token envelope.'); }
  if (!record || record.version !== 1 || record.algorithm !== 'A256GCM' ||
      typeof record.iv !== 'string' || typeof record.ciphertext !== 'string') {
    throw new TypeError('Unsupported encrypted token envelope.');
  }
  const iv = fromBase64(record.iv);
  const ciphertext = fromBase64(record.ciphertext);
  if (iv.length !== 12 || ciphertext.length < 17 || ciphertext.length > 16400) {
    throw new TypeError('Invalid encrypted token payload.');
  }
  const cryptoKey = await importKey(key, ['decrypt']);
  const plaintext = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv, additionalData: additionalData(provider, userId), tagLength: 128 },
    cryptoKey,
    ciphertext
  );
  return decoder.decode(plaintext);
}

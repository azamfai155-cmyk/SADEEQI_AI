// Image inputs are data URLs only. Remote URLs are rejected because the model provider
// may dereference them server-side, creating an SSRF surface outside this Worker.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_DATA_MIME = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

function hasMatchingImageSignature(mime, encoded) {
  let binary;
  try { binary = atob(encoded); } catch { return false; }
  if (binary.length < 12) return false;
  const byte = index => binary.charCodeAt(index);
  const ascii = (start, end) => binary.slice(start, end);
  if (mime === 'image/png') return byte(0) === 0x89 && ascii(1, 4) === 'PNG' &&
    byte(4) === 0x0d && byte(5) === 0x0a && byte(6) === 0x1a && byte(7) === 0x0a;
  if (mime === 'image/jpeg') return byte(0) === 0xff && byte(1) === 0xd8 && byte(2) === 0xff;
  if (mime === 'image/gif') return ascii(0, 6) === 'GIF87a' || ascii(0, 6) === 'GIF89a';
  if (mime === 'image/webp') return ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP';
  return false;
}

export function isSafeImageInput(value) {
  if (typeof value !== 'string' || value.length > 7000 * 1024) return false;
  const match = /^data:(image\/(?:jpeg|png|webp|gif));base64,([A-Za-z0-9+/]+={0,2})$/.exec(value);
  if (!match || !ALLOWED_DATA_MIME.has(match[1])) return false;
  const encoded = match[2];
  const estimatedBytes = Math.floor(encoded.length * 3 / 4) -
    (encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0);
  return estimatedBytes >= 12 && estimatedBytes <= MAX_IMAGE_BYTES &&
    hasMatchingImageSignature(match[1], encoded);
}

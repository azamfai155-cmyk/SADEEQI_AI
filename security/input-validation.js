// Conservative input checks for image references. This is NOT antivirus or a complete SSRF defense.
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_DATA_MIME = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/gif']);

function hasMatchingImageSignature(mime, encoded) {
  let binary;
  try {
    binary = atob(encoded);
  } catch {
    return false;
  }
  if (binary.length < 12) return false;
  const byte = index => binary.charCodeAt(index);
  const ascii = (start, end) => binary.slice(start, end);
  if (mime === 'image/png') {
    return byte(0) === 0x89 && ascii(1, 4) === 'PNG' &&
      byte(4) === 0x0d && byte(5) === 0x0a && byte(6) === 0x1a && byte(7) === 0x0a;
  }
  if (mime === 'image/jpeg') {
    return byte(0) === 0xff && byte(1) === 0xd8 && byte(2) === 0xff;
  }
  if (mime === 'image/gif') {
    return ascii(0, 6) === 'GIF87a' || ascii(0, 6) === 'GIF89a';
  }
  if (mime === 'image/webp') {
    return ascii(0, 4) === 'RIFF' && ascii(8, 12) === 'WEBP';
  }
  return false;
}

function isPrivateIpv4(host) {
  const parts = host.split('.');
  if (parts.length !== 4 || parts.some(part => !/^\d{1,3}$/.test(part) || Number(part) > 255)) return false;
  const [a, b] = parts.map(Number);
  return a === 0 || a === 10 || a === 127 ||
    (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) ||
    (a >= 224);
}

export function isSafeImageInput(value) {
  if (typeof value !== 'string' || value.length > 7000 * 1024) return false;

  if (value.startsWith('data:')) {
    const match = /^data:(image\/(?:jpeg|png|webp|gif));base64,([A-Za-z0-9+/]+={0,2})$/.exec(value);
    if (!match || !ALLOWED_DATA_MIME.has(match[1])) return false;
    const encoded = match[2];
    const estimatedBytes = Math.floor(encoded.length * 3 / 4) - (encoded.endsWith('==') ? 2 : encoded.endsWith('=') ? 1 : 0);
    return estimatedBytes >= 12 && estimatedBytes <= MAX_IMAGE_BYTES && hasMatchingImageSignature(match[1], encoded);
  }

  if (value.length > 2048) return false;
  try {
    const url = new URL(value);
    if (url.protocol !== 'https:' || url.username || url.password) return false;
    if (url.port && url.port !== '443') return false;
    // DNS treats a trailing root dot as equivalent (e.g. localhost. == localhost).
    // Normalize it before local-name and IPv4 checks to avoid simple suffix bypasses.
    const host = url.hostname.toLowerCase().replace(/^\[|\]$/g, '').replace(/\.$/, '');
    if (!host || host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local')) return false;
    if (host.includes(':')) return false; // Deny IPv6 literals; use a trusted hostname.
    if (isPrivateIpv4(host)) return false;
    return true;
  } catch {
    return false;
  }
}

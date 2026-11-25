import test from 'node:test';
import assert from 'node:assert/strict';
import { isSafeImageInput } from './input-validation.js';

test('image input accepts supported base64 image data with matching signatures', () => {
  assert.equal(isSafeImageInput('data:image/jpeg;base64,/9j/4AAQSkZJRgAB'), true);
  assert.equal(isSafeImageInput('data:image/png;base64,iVBORw0KGgoAAAANSUhEUg=='), true);
});

test('image input rejects all remote URLs, including public and private hosts', () => {
  assert.equal(isSafeImageInput('https://images.example.com/photo.png'), false);
  assert.equal(isSafeImageInput('https://localhost./image.png'), false);
  assert.equal(isSafeImageInput('https://127.0.0.1./private.png'), false);
  assert.equal(isSafeImageInput('https://192.168.1.10./private.png'), false);
  assert.equal(isSafeImageInput('http://images.example.com/photo.png'), false);
  assert.equal(isSafeImageInput('https://user:pass@images.example.com/photo.png'), false);
});

test('image input rejects unsupported data types, bad signatures and oversized values', () => {
  assert.equal(isSafeImageInput('data:text/plain;base64,SGVsbG8='), false);
  assert.equal(isSafeImageInput('data:image/svg+xml;base64,PHN2Zz4='), false);
  assert.equal(isSafeImageInput('data:image/png;base64,SGVsbG8gV29ybGQh'), false);
  assert.equal(isSafeImageInput('x'.repeat(7000 * 1024 + 1)), false);
});

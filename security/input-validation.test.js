import test from 'node:test';
import assert from 'node:assert/strict';
import { isSafeImageInput } from './input-validation.js';

test('image URL validation rejects localhost names with a trailing DNS root dot', () => {
  assert.equal(isSafeImageInput('https://localhost./image.png'), false);
  assert.equal(isSafeImageInput('https://service.localhost./image.png'), false);
});

test('image URL validation rejects private IPv4 literals with a trailing DNS root dot', () => {
  assert.equal(isSafeImageInput('https://127.0.0.1./private.png'), false);
  assert.equal(isSafeImageInput('https://192.168.1.10./private.png'), false);
});

test('image URL validation continues to allow ordinary HTTPS hostnames', () => {
  assert.equal(isSafeImageInput('https://images.example.com/photo.png'), true);
});

test('image URL validation rejects non-HTTPS URLs and credentials', () => {
  assert.equal(isSafeImageInput('http://images.example.com/photo.png'), false);
  assert.equal(isSafeImageInput('https://user:pass@images.example.com/photo.png'), false);
});

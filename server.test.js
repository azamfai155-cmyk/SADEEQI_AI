import test from 'node:test';
import assert from 'node:assert/strict';
import { app } from '../src/server.js';
import http from 'node:http';

test('health endpoint exposes service metadata without secrets', async () => {
  const server = http.createServer(app);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  try {
    const response = await fetch(`http://127.0.0.1:${port}/health`);
    const data = await response.json();
    assert.equal(response.status, 200);
    assert.equal(data.service, 'sadeeqi-ai');
    assert.equal(data.version, '1.2.0');
    assert.equal('api_key' in data, false);
  } finally { await new Promise(resolve => server.close(resolve)); }
});

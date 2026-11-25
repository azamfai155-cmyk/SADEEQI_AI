import test from 'node:test';
import assert from 'node:assert/strict';
import { handleTaskRequest } from './task-store.js';
import { encryptSocialToken } from '../../security/token-vault.js';

const env = {
  SUPABASE_URL: 'https://project.example.supabase.co',
  SUPABASE_SERVICE_ROLE_KEY: 'server-only-service-role-key',
  TASK_DATA_ENCRYPTION_KEY: btoa(String.fromCharCode(...new Uint8Array(32).fill(9)))
};
const auth = { ok: true, userId: 'user-123' };
const request = (path, method = 'GET', body) => new Request('https://worker.example' + path, {
  method,
  headers: body === undefined ? {} : { 'content-type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body)
});

test('task API requires verified per-user identity and configured persistence', async () => {
  const noUser = await handleTaskRequest(request('/v1/tasks'), env, { ok: true, userId: null });
  assert.equal(noUser.status, 401);
  const noDb = await handleTaskRequest(request('/v1/tasks'), {}, auth);
  assert.equal(noDb.status, 503);
});

test('creates task and stores verified owner id on Supabase', async () => {
  const originalFetch = globalThis.fetch;
  let calledUrl = '';
  let sent;
  globalThis.fetch = async (url, init) => {
    calledUrl = String(url);
    sent = JSON.parse(init.body);
    return new Response(JSON.stringify([sent]), { status: 201 });
  };
  try {
    const result = await handleTaskRequest(request('/v1/tasks', 'POST', {
      title: 'Research options', goal: 'Compare sources',
      steps: [{ id: 'search', title: 'Search the web' }]
    }), env, auth);
    const body = await result.json();
    assert.equal(result.status, 201);
    assert.match(calledUrl, /\/rest\/v1\/sadeeqi_tasks$/);
    assert.equal(sent.owner_id, 'user-123');
    assert.equal(sent.title, 'Encrypted task');
    assert.equal(sent.goal, 'Encrypted task payload');
    assert.equal(sent.task_data.algorithm, 'A256GCM');
    assert.equal(JSON.stringify(sent.task_data).includes('Compare sources'), false);
    assert.equal(body.task.state, 'queued');
    assert.equal(body.task.steps[0].state, 'pending');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('lists only rows filtered to the authenticated owner', async () => {
  const originalFetch = globalThis.fetch;
  let calledUrl = '';
  globalThis.fetch = async url => {
    calledUrl = String(url);
    return new Response(JSON.stringify([]), { status: 200 });
  };
  try {
    const result = await handleTaskRequest(request('/v1/tasks'), env, auth);
    assert.equal(result.status, 200);
    assert.match(calledUrl, /owner_id=eq\.user-123/);
    assert.deepEqual((await result.json()).tasks, []);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('task updates fetch and mutate only the authenticated owner task', async () => {
  const originalFetch = globalThis.fetch;
  const id = '8c95c1ae-8e43-4ec6-84c7-c8ac1b74b33a';
  const task = {
    id, ownerId: 'user-123', title: 'Research', goal: 'Find sources',
    state: 'queued', steps: [], approval: null,
    createdAt: '2026-11-18T00:00:00.000Z',
    updatedAt: '2026-11-18T00:00:00.000Z', lastError: null
  };
  const urls = [];
  const encryptedTask = JSON.parse(await encryptSocialToken(JSON.stringify(task), env.TASK_DATA_ENCRYPTION_KEY, { provider: 'task_data', userId: 'user-123' }));
  globalThis.fetch = async (url, init) => {
    urls.push(String(url));
    if (init.method === 'PATCH') {
      const update = JSON.parse(init.body);
      return new Response(JSON.stringify([{ task_data: update.task_data, ...update }]), { status: 200 });
    }
    return new Response(JSON.stringify([{ id, owner_id: 'user-123', updated_at: task.updatedAt, task_data: encryptedTask }]), { status: 200 });
  };
  try {
    const result = await handleTaskRequest(request('/v1/tasks/' + id, 'PATCH', { operation: 'start' }), env, auth);
    assert.equal(result.status, 200);
    assert.match(urls[0], /owner_id=eq\.user-123/);
    assert.match(urls[1], /owner_id=eq\.user-123/);
    assert.match(urls[1], /updated_at=eq\.2026-11-18T00%3A00%3A00\.000Z/);
    assert.equal(urls.length, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('concurrent task update returns conflict instead of overwriting newer state', async () => {
  const originalFetch = globalThis.fetch;
  const id = '8c95c1ae-8e43-4ec6-84c7-c8ac1b74b33a';
  const task = {
    id, ownerId: 'user-123', title: 'Research', goal: 'Find sources',
    state: 'queued', steps: [], approval: null,
    createdAt: '2026-11-18T00:00:00.000Z',
    updatedAt: '2026-11-18T00:00:00.000Z', lastError: null
  };
  let calls = 0;
  const encryptedTask = JSON.parse(await encryptSocialToken(JSON.stringify(task), env.TASK_DATA_ENCRYPTION_KEY, { provider: 'task_data', userId: 'user-123' }));
  globalThis.fetch = async () => {
    calls += 1;
    if (calls === 1) return new Response(JSON.stringify([{ id, owner_id: 'user-123', updated_at: task.updatedAt, task_data: encryptedTask }]), { status: 200 });
    return new Response(JSON.stringify([]), { status: 200 });
  };
  try {
    const result = await handleTaskRequest(
      request('/v1/tasks/' + id, 'PATCH', { operation: 'start' }), env, auth
    );
    assert.equal(result.status, 409);
    assert.match((await result.json()).error, /not persisted/);
    assert.equal(calls, 2);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('unknown task operations are rejected without database mutation', async () => {
  const originalFetch = globalThis.fetch;
  const id = '8c95c1ae-8e43-4ec6-84c7-c8ac1b74b33a';
  const task = { id, ownerId: 'user-123', title: 'Research', goal: 'Find sources', state: 'queued', steps: [], approval: null, createdAt: 'x', updatedAt: 'x', lastError: null };
  const encryptedTask = JSON.parse(await encryptSocialToken(JSON.stringify(task), env.TASK_DATA_ENCRYPTION_KEY, { provider: 'task_data', userId: 'user-123' }));
  globalThis.fetch = async () => new Response(JSON.stringify([{ id, owner_id: 'user-123', updated_at: 'x', task_data: encryptedTask }]), { status: 200 });
  try {
    const result = await handleTaskRequest(request('/v1/tasks/' + id, 'PATCH', { operation: 'delete_everything' }), env, auth);
    assert.equal(result.status, 400);
  } finally {
    globalThis.fetch = originalFetch;
  }
});


test('task API refuses persistence without application encryption key', async () => {
  const { TASK_DATA_ENCRYPTION_KEY, ...withoutEncryption } = env;
  const result = await handleTaskRequest(request('/v1/tasks'), withoutEncryption, auth);
  assert.equal(result.status, 503);
  assert.match((await result.json()).error, /encryption is not configured/);
});

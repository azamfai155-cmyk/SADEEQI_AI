import {
  createAgentTask, transitionTask, requestTaskApproval, resolveTaskApproval
} from './agent-core.js';
import { encryptSocialToken, decryptSocialToken } from '../../security/token-vault.js';

function response(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }
  });
}

function databaseConfig(env) {
  const url = (env.SUPABASE_URL || '').replace(/\/$/, '');
  const key = env.SUPABASE_SERVICE_ROLE_KEY || '';
  return url && key ? { url, key } : null;
}

async function dbRequest(env, path, init = {}) {
  const config = databaseConfig(env);
  if (!config) throw new Error('Persistent task storage is not configured.');
  const result = await fetch(config.url + '/rest/v1/' + path, {
    ...init,
    headers: {
      apikey: config.key,
      Authorization: 'Bearer ' + config.key,
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
      ...(init.headers || {})
    },
    signal: AbortSignal.timeout(10000)
  });
  const text = await result.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* handled below */ }
  if (!result.ok) {
    const error = new Error(data?.message || data?.hint || 'Persistent task storage request failed.');
    error.status = result.status;
    throw error;
  }
  return data;
}

function encryptionKey(env) {
  if (!env.TASK_DATA_ENCRYPTION_KEY) throw new Error('Task data encryption is not configured.');
  return env.TASK_DATA_ENCRYPTION_KEY;
}

async function taskRow(task, env) {
  const encrypted = await encryptSocialToken(
    JSON.stringify(task),
    encryptionKey(env),
    { provider: 'task_data', userId: task.ownerId }
  );
  return {
    id: task.id,
    owner_id: task.ownerId,
    state: task.state,
    // Do not duplicate sensitive titles/goals in plaintext database columns.
    title: 'Encrypted task',
    goal: 'Encrypted task payload',
    task_data: JSON.parse(encrypted),
    updated_at: task.updatedAt
  };
}

async function decodeTaskRow(row, env) {
  const envelope = row?.task_data;
  if (!envelope || envelope.version !== 1 || envelope.algorithm !== 'A256GCM') {
    throw new Error('Task data is not encrypted; refusing to expose legacy plaintext.');
  }
  const plaintext = await decryptSocialToken(
    JSON.stringify(envelope),
    encryptionKey(env),
    { provider: 'task_data', userId: row.owner_id }
  );
  const task = JSON.parse(plaintext);
  if (!task || task.ownerId !== row.owner_id || task.id !== row.id) {
    throw new Error('Encrypted task ownership validation failed.');
  }
  return task;
}

export async function handleTaskRequest(request, env, auth) {
  if (!auth?.userId) return response({ error: 'A verified Supabase user session is required for persistent tasks.' }, 401);
  if (!databaseConfig(env)) return response({ error: 'Persistent task storage is not configured. No task was saved.' }, 503);
  if (!env.TASK_DATA_ENCRYPTION_KEY) return response({ error: 'Task data encryption is not configured. No task was saved.' }, 503);

  const url = new URL(request.url);
  const collection = url.pathname === '/v1/tasks';
  const match = url.pathname.match(/^\/v1\/tasks\/([0-9a-f-]{36})$/i);

  try {
    if (collection && request.method === 'GET') {
      const rows = await dbRequest(env,
        'sadeeqi_tasks?owner_id=eq.' + encodeURIComponent(auth.userId) + '&order=updated_at.desc&limit=100');
      const tasks = await Promise.all((rows || []).map(row => decodeTaskRow(row, env)));
      return response({ tasks });
    }

    if (collection && request.method === 'POST') {
      let body;
      try { body = await request.json(); } catch { return response({ error: 'Invalid JSON.' }, 400); }
      const task = createAgentTask({
        id: crypto.randomUUID(),
        ownerId: auth.userId,
        title: body?.title,
        goal: body?.goal,
        steps: body?.steps || []
      });
      await dbRequest(env, 'sadeeqi_tasks', {
        method: 'POST',
        body: JSON.stringify(await taskRow(task, env))
      });
      return response({ task }, 201);
    }

    if (match && request.method === 'PATCH') {
      let body;
      try { body = await request.json(); } catch { return response({ error: 'Invalid JSON.' }, 400); }
      const id = match[1];
      const found = await dbRequest(env,
        'sadeeqi_tasks?id=eq.' + encodeURIComponent(id) +
        '&owner_id=eq.' + encodeURIComponent(auth.userId) + '&limit=1');
      if (!found?.length) return response({ error: 'Task not found.' }, 404);

      let task = await decodeTaskRow(found[0], env);
      switch (body?.operation) {
        case 'start': task = transitionTask(task, 'running'); break;
        case 'pause': task = transitionTask(task, 'paused'); break;
        case 'resume': task = transitionTask(task, 'queued'); break;
        case 'complete': task = transitionTask(task, 'completed'); break;
        case 'fail':
          task = transitionTask(task, 'failed', { reason: String(body.reason || 'unspecified_error').slice(0, 500) });
          break;
        case 'request_approval': task = requestTaskApproval(task, body.action); break;
        case 'resolve_approval':
          if (typeof body.approved !== 'boolean') return response({ error: 'approved must be a boolean.' }, 400);
          task = resolveTaskApproval(task, body.approved);
          break;
        default: return response({ error: 'Unsupported task operation.' }, 400);
      }

      const expectedUpdatedAt = found[0].updated_at || task.updatedAt;
      if (typeof expectedUpdatedAt !== 'string' || !expectedUpdatedAt) {
        return response({ error: 'Task version is missing; reload before updating.' }, 409);
      }
      const rows = await dbRequest(env,
        'sadeeqi_tasks?id=eq.' + encodeURIComponent(id) +
        '&owner_id=eq.' + encodeURIComponent(auth.userId) +
        '&updated_at=eq.' + encodeURIComponent(expectedUpdatedAt), {
          method: 'PATCH',
          body: JSON.stringify(await taskRow(task, env))
        });
      if (!rows?.length) return response({ error: 'Task update was not persisted.' }, 409);
      return response({ task });
    }

    return response({ error: 'Method not allowed.' }, 405);
  } catch (error) {
    if (error instanceof TypeError || /Invalid task transition|Unknown task state|cannot request approval|No pending approval|prohibited by policy|Unknown task|Task step/.test(error.message)) {
      return response({ error: error.message }, 400);
    }
    return response({ error: error.message || 'Task operation failed.' }, error.status === 404 ? 404 : 503);
  }
}

import {
  createAgentTask, transitionTask, requestTaskApproval, resolveTaskApproval
} from './agent-core.js';

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
    }
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

function taskRow(task) {
  return {
    id: task.id,
    owner_id: task.ownerId,
    state: task.state,
    title: task.title,
    goal: task.goal,
    task_data: task,
    updated_at: task.updatedAt
  };
}

export async function handleTaskRequest(request, env, auth) {
  if (!auth?.userId) return response({ error: 'A verified Supabase user session is required for persistent tasks.' }, 401);
  if (!databaseConfig(env)) return response({ error: 'Persistent task storage is not configured. No task was saved.' }, 503);

  const url = new URL(request.url);
  const collection = url.pathname === '/v1/tasks';
  const match = url.pathname.match(/^\/v1\/tasks\/([0-9a-f-]{36})$/i);

  try {
    if (collection && request.method === 'GET') {
      const rows = await dbRequest(env,
        'sadeeqi_tasks?owner_id=eq.' + encodeURIComponent(auth.userId) + '&order=updated_at.desc&limit=100');
      return response({ tasks: (rows || []).map(row => row.task_data) });
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
      const rows = await dbRequest(env, 'sadeeqi_tasks', {
        method: 'POST',
        body: JSON.stringify(taskRow(task))
      });
      return response({ task: rows?.[0]?.task_data || task }, 201);
    }

    if (match && request.method === 'PATCH') {
      let body;
      try { body = await request.json(); } catch { return response({ error: 'Invalid JSON.' }, 400); }
      const id = match[1];
      const found = await dbRequest(env,
        'sadeeqi_tasks?id=eq.' + encodeURIComponent(id) +
        '&owner_id=eq.' + encodeURIComponent(auth.userId) + '&limit=1');
      if (!found?.length) return response({ error: 'Task not found.' }, 404);

      let task = found[0].task_data;
      switch (body?.operation) {
        case 'start':
          task = transitionTask(task, 'running');
          break;
        case 'pause':
          task = transitionTask(task, 'paused');
          break;
        case 'resume':
          task = transitionTask(task, 'queued');
          break;
        case 'complete':
          task = transitionTask(task, 'completed');
          break;
        case 'fail':
          task = transitionTask(task, 'failed', { reason: String(body.reason || 'unspecified_error').slice(0, 500) });
          break;
        case 'request_approval':
          task = requestTaskApproval(task, body.action);
          break;
        case 'resolve_approval':
          if (typeof body.approved !== 'boolean') return response({ error: 'approved must be a boolean.' }, 400);
          task = resolveTaskApproval(task, body.approved);
          break;
        default:
          return response({ error: 'Unsupported task operation.' }, 400);
      }

      // Optimistic concurrency: update only if the row still has the version
      // we just read. A competing update changes updated_at and makes this PATCH
      // affect zero rows instead of silently overwriting newer task state.
      const expectedUpdatedAt = found[0].updated_at || found[0].task_data?.updatedAt;
      if (typeof expectedUpdatedAt !== 'string' || !expectedUpdatedAt) {
        return response({ error: 'Task version is missing; reload before updating.' }, 409);
      }
      const rows = await dbRequest(env,
        'sadeeqi_tasks?id=eq.' + encodeURIComponent(id) +
        '&owner_id=eq.' + encodeURIComponent(auth.userId) +
        '&updated_at=eq.' + encodeURIComponent(expectedUpdatedAt), {
          method: 'PATCH',
          body: JSON.stringify(taskRow(task))
        });
      if (!rows?.length) return response({ error: 'Task update was not persisted.' }, 409);
      return response({ task: rows[0].task_data });
    }

    return response({ error: 'Method not allowed.' }, 405);
  } catch (error) {
    if (error instanceof TypeError || /Invalid task transition|Unknown task state|cannot request approval|No pending approval|prohibited by policy|Unknown task|Task step/.test(error.message)) {
      return response({ error: error.message }, 400);
    }
    return response({ error: error.message || 'Task operation failed.' }, error.status === 404 ? 404 : 503);
  }
}

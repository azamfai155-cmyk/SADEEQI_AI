const TASK_STATES = Object.freeze([
  'queued', 'running', 'waiting_approval', 'paused', 'completed', 'failed', 'cancelled'
]);

const TRANSITIONS = Object.freeze({
  queued: new Set(['running', 'waiting_approval', 'cancelled']),
  running: new Set(['waiting_approval', 'paused', 'completed', 'failed', 'cancelled']),
  waiting_approval: new Set(['queued', 'running', 'cancelled']),
  paused: new Set(['queued', 'cancelled']),
  completed: new Set(),
  failed: new Set(['queued', 'cancelled']),
  cancelled: new Set()
});

const APPROVAL_ACTIONS = new Set([
  'send_message', 'send_email', 'publish_content', 'make_call',
  'purchase', 'payment', 'place_order', 'trade', 'transfer_funds',
  'delete_data', 'delete_file', 'change_security', 'connect_account'
]);

const PROHIBITED_ACTIONS = new Set([
  'steal_credentials', 'exfiltrate_secrets', 'deploy_malware',
  'bypass_authentication', 'impersonate_human'
]);

export function classifyAction(action) {
  const normalized = typeof action === 'string' ? action.trim().toLowerCase() : '';
  if (!normalized) return { decision: 'deny', reason: 'missing_action' };
  if (PROHIBITED_ACTIONS.has(normalized)) {
    return { decision: 'deny', reason: 'prohibited_action' };
  }
  if (APPROVAL_ACTIONS.has(normalized)) {
    return { decision: 'approval_required', reason: 'sensitive_action' };
  }
  // Unknown tools fail closed until explicitly registered and reviewed.
  return { decision: 'approval_required', reason: 'unregistered_action' };
}

export function createAgentTask({ id, ownerId, title, goal, steps = [], now = new Date().toISOString() }) {
  if (typeof id !== 'string' || !id.trim()) throw new TypeError('Task id is required.');
  if (typeof ownerId !== 'string' || !ownerId.trim()) throw new TypeError('Task owner is required.');
  if (typeof title !== 'string' || !title.trim() || title.length > 160) {
    throw new TypeError('Task title must contain 1 to 160 characters.');
  }
  if (typeof goal !== 'string' || !goal.trim() || goal.length > 8000) {
    throw new TypeError('Task goal must contain 1 to 8000 characters.');
  }
  if (!Array.isArray(steps) || steps.length > 40 || steps.some(step =>
    !step || typeof step.id !== 'string' || typeof step.title !== 'string' ||
    !step.id.trim() || !step.title.trim()
  )) throw new TypeError('Task steps must be a list of at most 40 named steps.');

  return {
    id: id.trim(),
    ownerId: ownerId.trim(),
    title: title.trim(),
    goal: goal.trim(),
    state: 'queued',
    steps: steps.map(step => ({
      id: step.id.trim(),
      title: step.title.trim(),
      state: 'pending',
      result: null
    })),
    approval: null,
    createdAt: now,
    updatedAt: now,
    lastError: null
  };
}

export function transitionTask(task, nextState, { now = new Date().toISOString(), reason = null } = {}) {
  if (!task || !TASK_STATES.includes(task.state)) throw new TypeError('Invalid task.');
  if (!TASK_STATES.includes(nextState)) throw new TypeError('Unknown task state.');
  if (!TRANSITIONS[task.state].has(nextState)) {
    throw new Error(`Invalid task transition: ${task.state} -> ${nextState}`);
  }
  return {
    ...task,
    state: nextState,
    updatedAt: now,
    lastError: nextState === 'failed' ? (reason || 'unspecified_error') : null
  };
}

export function requestTaskApproval(task, action, { now = new Date().toISOString() } = {}) {
  const policy = classifyAction(action);
  if (policy.decision === 'deny') throw new Error('This action is prohibited by policy.');
  if (!['queued', 'running', 'waiting_approval'].includes(task?.state)) {
    throw new Error('Task cannot request approval in its current state.');
  }
  return {
    ...task,
    state: 'waiting_approval',
    approval: { action, decision: 'pending', requestedAt: now, reason: policy.reason },
    updatedAt: now
  };
}

export function resolveTaskApproval(task, approved, { now = new Date().toISOString() } = {}) {
  if (task?.state !== 'waiting_approval' || task.approval?.decision !== 'pending') {
    throw new Error('No pending approval exists.');
  }
  return {
    ...task,
    state: approved === true ? 'queued' : 'cancelled',
    approval: { ...task.approval, decision: approved === true ? 'approved' : 'rejected', resolvedAt: now },
    updatedAt: now
  };
}

export function updateTaskStep(task, stepId, state, result = null, { now = new Date().toISOString() } = {}) {
  if (!task || task.state !== 'running') throw new Error('Only running tasks can update steps.');
  if (!['pending', 'running', 'completed', 'failed', 'skipped'].includes(state)) {
    throw new TypeError('Invalid step state.');
  }
  let found = false;
  const steps = task.steps.map(step => {
    if (step.id !== stepId) return step;
    found = true;
    return { ...step, state, result: result === null ? null : String(result).slice(0, 4000) };
  });
  if (!found) throw new Error('Task step not found.');
  return { ...task, steps, updatedAt: now };
}

export const agentCoreStates = TASK_STATES;

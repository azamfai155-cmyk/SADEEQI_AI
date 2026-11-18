import test from 'node:test';
import assert from 'node:assert/strict';
import {
  classifyAction, createAgentTask, transitionTask,
  requestTaskApproval, resolveTaskApproval, updateTaskStep
} from './agent-core.js';

const task = () => createAgentTask({
  id: 'task-1', ownerId: 'user-1', title: 'Research task',
  goal: 'Find and compare sources',
  steps: [{ id: 'search', title: 'Search sources' }]
});

test('new tasks are queued with normalized pending steps', () => {
  const value = task();
  assert.equal(value.state, 'queued');
  assert.equal(value.steps[0].state, 'pending');
});

test('sensitive actions require approval and prohibited actions are denied', () => {
  assert.equal(classifyAction('purchase').decision, 'approval_required');
  assert.equal(classifyAction('send_email').decision, 'approval_required');
  assert.equal(classifyAction('steal_credentials').decision, 'deny');
  assert.equal(classifyAction('unknown_tool').decision, 'approval_required');
});

test('task transitions reject illegal state changes', () => {
  const running = transitionTask(task(), 'running');
  assert.equal(running.state, 'running');
  assert.throws(() => transitionTask(task(), 'completed'), /Invalid task transition/);
});

test('approval must be explicitly resolved before a sensitive task resumes', () => {
  const waiting = requestTaskApproval(transitionTask(task(), 'running'), 'make_call');
  assert.equal(waiting.state, 'waiting_approval');
  assert.equal(waiting.approval.decision, 'pending');
  const approved = resolveTaskApproval(waiting, true);
  assert.equal(approved.state, 'queued');
  assert.equal(approved.approval.decision, 'approved');
  const rejected = resolveTaskApproval(waiting, false);
  assert.equal(rejected.state, 'cancelled');
});

test('prohibited actions cannot be approved', () => {
  assert.throws(() => requestTaskApproval(task(), 'deploy_malware'), /prohibited/);
});

test('step updates require a running task and a known step', () => {
  const running = transitionTask(task(), 'running');
  const updated = updateTaskStep(running, 'search', 'completed', '3 sources');
  assert.equal(updated.steps[0].state, 'completed');
  assert.equal(updated.steps[0].result, '3 sources');
  assert.throws(() => updateTaskStep(task(), 'search', 'completed'), /running tasks/);
  assert.throws(() => updateTaskStep(running, 'missing', 'completed'), /not found/);
});

test('task creation validates owner, title, goal and bounded step count', () => {
  assert.throws(() => createAgentTask({ id: 'x', title: 'x', goal: 'y' }), /owner/);
  assert.throws(() => createAgentTask({ id: 'x', ownerId: 'u', title: '', goal: 'y' }), /title/);
});

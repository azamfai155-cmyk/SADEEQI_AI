import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { signalProcessGroup } from './process-control.mjs';

const bridgePath = fileURLToPath(new URL('./bridge.mjs', import.meta.url));

function invoke(...args) {
  return spawnSync(process.execPath, [bridgePath, ...args], {
    encoding: 'utf8',
    timeout: 20_000,
    maxBuffer: 512 * 1024
  });
}

test('rejects unknown task names', () => {
  const result = invoke('not_a_real_task');
  assert.equal(result.status, 2);
  const payload = JSON.parse(result.stdout);
  assert.equal(payload.ok, false);
  assert.equal(payload.error, 'UNKNOWN_TASK');
  assert.deepEqual(payload.allowedTasks, ['git_status', 'backend_tests']);
});

test('rejects shell-like strings instead of interpreting them', () => {
  const result = invoke('git_status; echo unsafe');
  assert.equal(result.status, 2);
  const payload = JSON.parse(result.stdout);
  assert.equal(payload.ok, false);
  assert.equal(payload.error, 'UNKNOWN_TASK');
});

test('runs the fixed git status task and returns structured output', () => {
  const result = invoke('git_status');
  assert.equal(result.status, 0, result.stderr);
  const payload = JSON.parse(result.stdout);
  assert.equal(payload.ok, true);
  assert.equal(payload.task, 'git_status');
  assert.equal(typeof payload.stdout, 'string');
  assert.match(payload.note, /No remote ChatGPT connection/);
});

test('signals the complete POSIX process group', () => {
  const calls = [];
  const child = { pid: 321, kill: () => assert.fail('must signal the process group') };
  signalProcessGroup(child, 'SIGTERM', {
    platform: 'linux',
    killGroup: (pid, signal) => calls.push([pid, signal])
  });
  assert.deepEqual(calls, [[-321, 'SIGTERM']]);
});

test('falls back to the child when process-group signaling is denied', () => {
  const calls = [];
  const child = { pid: 654, kill: (signal) => calls.push(signal) };
  signalProcessGroup(child, 'SIGKILL', {
    platform: 'linux',
    killGroup: () => { throw Object.assign(new Error('denied'), { code: 'EPERM' }); }
  });
  assert.deepEqual(calls, ['SIGKILL']);
});

test('does not signal an individual child after an already-gone process group', () => {
  const calls = [];
  const child = { pid: 987, kill: (signal) => calls.push(signal) };
  signalProcessGroup(child, 'SIGTERM', {
    platform: 'linux',
    killGroup: () => { throw Object.assign(new Error('gone'), { code: 'ESRCH' }); }
  });
  assert.deepEqual(calls, []);
});

test('uses direct child signaling on Windows', () => {
  const calls = [];
  const child = { pid: 123, kill: (signal) => calls.push(signal) };
  signalProcessGroup(child, 'SIGTERM', {
    platform: 'win32',
    killGroup: () => assert.fail('must not signal a POSIX process group')
  });
  assert.deepEqual(calls, ['SIGTERM']);
});

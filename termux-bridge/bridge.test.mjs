import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

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

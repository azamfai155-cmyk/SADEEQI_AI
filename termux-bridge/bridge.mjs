#!/usr/bin/env node
/**
 * SADEEQI Termux Bridge — local read-only starter.
 *
 * This is deliberately a CLI, not an HTTP server. It has no network listener,
 * no remote polling, and no arbitrary shell execution.
 *
 * Usage from the repository root:
 *   node termux-bridge/bridge.mjs git_status
 *   node termux-bridge/bridge.mjs backend_tests
 */
import { spawn } from 'node:child_process';
import { realpath } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = await realpath(path.resolve(SCRIPT_DIR, '..'));
const MAX_OUTPUT_BYTES = 256 * 1024;
const DEFAULT_TIMEOUT_MS = 120_000;

const TASKS = Object.freeze({
  git_status: {
    description: 'عرض حالة Git دون تعديل الملفات أو الفروع',
    executable: 'git',
    args: ['status', '--short', '--branch'],
    timeoutMs: 15_000
  },
  backend_tests: {
    description: 'تشغيل اختبارات backend المعرفة في package.json',
    executable: 'npm',
    args: ['test'],
    timeoutMs: DEFAULT_TIMEOUT_MS
  }
});

function emit(payload, exitCode = 0) {
  process.stdout.write(`${JSON.stringify(payload, null, 2)}\\n`);
  process.exitCode = exitCode;
}

function safeEnvironment() {
  // Keep only runtime essentials; never forward API keys, tokens or .env values.
  const allowed = ['PATH', 'HOME', 'PREFIX', 'TMPDIR', 'TERM', 'LANG', 'LC_ALL'];
  const env = {};
  for (const key of allowed) {
    const value = process.env[key];
    if (typeof value === 'string') env[key] = value;
  }
  env.CI = '1';
  env.NO_COLOR = '1';
  env.npm_config_audit = 'false';
  env.npm_config_fund = 'false';
  return env;
}

async function runTask(taskName) {
  const task = TASKS[taskName];
  if (!task) {
    emit({
      ok: false,
      error: 'UNKNOWN_TASK',
      allowedTasks: Object.keys(TASKS)
    }, 2);
    return;
  }

  // Re-resolve immediately before execution so a replaced project path is rejected.
  const root = await realpath(PROJECT_ROOT);
  if (root !== PROJECT_ROOT) {
    emit({ ok: false, error: 'PROJECT_ROOT_CHANGED' }, 3);
    return;
  }

  const startedAt = new Date().toISOString();
  const child = spawn(task.executable, task.args, {
    cwd: root,
    env: safeEnvironment(),
    shell: false,
    windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe']
  });

  let stdout = '';
  let stderr = '';
  let outputBytes = 0;
  let overflow = false;
  let timedOut = false;
  let completed = false;
  let forceKillTimer;

  // Always escalate to SIGKILL if SIGTERM does not stop the child.
  // This is shared by timeout and output-limit termination paths.
  const terminateChild = () => {
    if (completed || child.exitCode !== null || child.signalCode !== null) return;
    child.kill('SIGTERM');
    if (!forceKillTimer) {
      forceKillTimer = setTimeout(() => {
        if (!completed) child.kill('SIGKILL');
      }, 3000);
      forceKillTimer.unref();
    }
  };

  const timer = setTimeout(() => {
    timedOut = true;
    terminateChild();
  }, task.timeoutMs);

  const clearTimers = () => {
    clearTimeout(timer);
    if (forceKillTimer) clearTimeout(forceKillTimer);
  };

  const collect = (target) => (chunk) => {
    outputBytes += chunk.length;
    if (outputBytes > MAX_OUTPUT_BYTES) {
      overflow = true;
      terminateChild();
      return;
    }
    if (target === 'stdout') stdout += chunk.toString('utf8');
    else stderr += chunk.toString('utf8');
  };

  child.stdout.on('data', collect('stdout'));
  child.stderr.on('data', collect('stderr'));

  child.on('error', (error) => {
    if (completed) return;
    completed = true;
    clearTimers();
    emit({
      ok: false,
      task: taskName,
      error: 'PROCESS_START_FAILED',
      message: error.message,
      startedAt,
      finishedAt: new Date().toISOString()
    }, 4);
  });

  child.on('close', (code, signal) => {
    if (completed) return;
    completed = true;
    clearTimers();
    const ok = code === 0 && !timedOut && !overflow;
    emit({
      ok,
      task: taskName,
      description: task.description,
      exitCode: code,
      signal,
      timedOut,
      outputLimitExceeded: overflow,
      stdout,
      stderr,
      startedAt,
      finishedAt: new Date().toISOString(),
      note: 'Local execution only. No remote ChatGPT connection is created.'
    }, ok ? 0 : 1);
  });
}

const taskName = process.argv[2];
if (process.argv.length !== 3 || !taskName || taskName.startsWith('-')) {
  emit({
    ok: false,
    usage: 'node termux-bridge/bridge.mjs <task>',
    allowedTasks: Object.keys(TASKS)
  }, 2);
} else {
  await runTask(taskName);
}

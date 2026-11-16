/**
 * One-time, task-scoped approval phrases for local interactive execution.
 * The phrase authorizes only the named fixed task; it is not a shell command.
 */
export function approvalPhrase(taskName) {
  if (typeof taskName !== 'string' || !/^[a-z][a-z0-9_]{0,47}$/.test(taskName)) {
    throw new TypeError('Invalid task name');
  }
  return `أوافق على تشغيل المهمة: ${taskName}`;
}

export function isApprovalGranted(taskName, response) {
  return typeof response === 'string' && response.trim() === approvalPhrase(taskName);
}

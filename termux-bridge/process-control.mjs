/**
 * Send a signal to a child process group on POSIX, or to the child on Windows.
 * killGroup is injectable so fallback behavior can be tested without signaling
 * real processes.
 */
export function signalProcessGroup(child, signal, {
  platform = process.platform,
  killGroup = (pid, requestedSignal) => process.kill(pid, requestedSignal)
} = {}) {
  try {
    if (platform !== 'win32' && child.pid) killGroup(-child.pid, signal);
    else child.kill(signal);
  } catch (error) {
    if (error.code !== 'ESRCH') child.kill(signal);
  }
}

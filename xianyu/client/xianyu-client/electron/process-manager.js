const { spawn } = require("node:child_process");

const {
  PROCESS_TERMINATE_TIMEOUT_MS,
  TASKKILL_COMMAND,
  TASKKILL_FORCE_ARGUMENT,
  TASKKILL_PID_ARGUMENT,
  TASKKILL_TREE_ARGUMENT,
  WINDOWS_PLATFORM_NAME
} = require("./constants");

const managedProcesses = new Map();

/**
 * Determine whether a child process has already exited.
 *
 * @param {import("node:child_process").ChildProcess} child Child process to inspect.
 * @returns {boolean} True when the child has exited or emitted a termination signal.
 */
function hasProcessExited(child) {
  return !child || child.exitCode !== null || child.signalCode !== null;
}

/**
 * Wait for a child process to exit within a bounded period.
 *
 * @param {import("node:child_process").ChildProcess} child Child process to wait for.
 * @param {number} timeoutMs Maximum wait duration in milliseconds.
 * @returns {Promise<boolean>} True when the child exited before the timeout.
 */
function waitForProcessExit(child, timeoutMs = PROCESS_TERMINATE_TIMEOUT_MS) {
  if (hasProcessExited(child)) {
    return Promise.resolve(true);
  }

  return new Promise((resolve) => {
    let settled = false;
    let timer = null;

    const finish = (exited) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      child.removeListener("exit", handleExit);
      resolve(exited);
    };
    const handleExit = () => finish(true);

    timer = setTimeout(() => finish(false), timeoutMs);
    child.once("exit", handleExit);
    if (hasProcessExited(child)) {
      finish(true);
    }
  });
}

/**
 * Run taskkill and wait for the process tree operation to complete.
 *
 * @param {number} pid Root process identifier.
 * @param {number} timeoutMs Maximum wait duration in milliseconds.
 * @returns {Promise<boolean>} True when taskkill completed successfully.
 */
function runTaskkill(pid, timeoutMs) {
  return new Promise((resolve) => {
    let settled = false;
    let killer = null;
    let timer = null;

    const finish = (succeeded) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      resolve(succeeded);
    };

    try {
      killer = spawn(
        TASKKILL_COMMAND,
        [TASKKILL_PID_ARGUMENT, String(pid), TASKKILL_TREE_ARGUMENT, TASKKILL_FORCE_ARGUMENT],
        { stdio: "ignore", windowsHide: true }
      );
    } catch {
      finish(false);
      return;
    }

    timer = setTimeout(() => finish(false), timeoutMs);
    killer.once("error", () => finish(false));
    killer.once("exit", (code) => finish(code === 0));
  });
}

/**
 * Terminate a child process and all of its descendants.
 *
 * @param {import("node:child_process").ChildProcess} child Child process tree to terminate.
 * @param {number} timeoutMs Maximum wait duration for each termination step.
 * @returns {Promise<boolean>} True when the root child has exited.
 */
async function terminateProcessTree(child, timeoutMs = PROCESS_TERMINATE_TIMEOUT_MS) {
  if (hasProcessExited(child)) {
    return true;
  }

  let treeTerminationSucceeded = false;
  if (process.platform === WINDOWS_PLATFORM_NAME) {
    treeTerminationSucceeded = await runTaskkill(child.pid, timeoutMs);
  } else {
    treeTerminationSucceeded = child.kill("SIGTERM");
  }

  if (await waitForProcessExit(child, timeoutMs)) {
    return true;
  }

  if (!treeTerminationSucceeded && !hasProcessExited(child)) {
    child.kill("SIGKILL");
  }
  return waitForProcessExit(child, timeoutMs);
}

/**
 * Register a managed child process under a stable name.
 *
 * @param {string} name Stable process name.
 * @param {import("node:child_process").ChildProcess} child Child process to manage.
 * @returns {void}
 */
function registerProcess(name, child) {
  if (!name || !child) {
    return;
  }
  managedProcesses.set(name, child);
}

/**
 * Remove a managed child process when it still matches the registered instance.
 *
 * @param {string} name Stable process name.
 * @param {import("node:child_process").ChildProcess} child Expected child process.
 * @returns {void}
 */
function unregisterProcess(name, child) {
  if (managedProcesses.get(name) === child) {
    managedProcesses.delete(name);
  }
}

/**
 * Terminate and unregister one managed child process.
 *
 * @param {string} name Stable process name.
 * @returns {Promise<boolean>} True when the process is no longer running.
 */
async function terminateRegisteredProcess(name) {
  const child = managedProcesses.get(name);
  if (!child) {
    return true;
  }

  const exited = await terminateProcessTree(child);
  unregisterProcess(name, child);
  return exited;
}

/**
 * Terminate every registered child process and clear the registry.
 *
 * @returns {Promise<void>} Resolves after every managed process has been processed.
 */
async function terminateAllProcesses() {
  const processes = Array.from(managedProcesses.entries());
  await Promise.allSettled(
    processes.map(([, child]) => terminateProcessTree(child))
  );
  for (const [name, child] of processes) {
    unregisterProcess(name, child);
  }
}

module.exports = {
  hasProcessExited,
  registerProcess,
  terminateAllProcesses,
  terminateProcessTree,
  terminateRegisteredProcess,
  unregisterProcess,
  waitForProcessExit
};

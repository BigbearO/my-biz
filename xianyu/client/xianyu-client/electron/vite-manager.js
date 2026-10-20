const http = require("node:http");
const path = require("node:path");
const { spawn } = require("node:child_process");

const {
  DEV_RENDERER_HOST,
  DEV_RENDERER_PORT,
  DEV_RENDERER_URL,
  DEV_SERVER_EXITED_MESSAGE,
  DEV_SERVER_PROBE_TIMEOUT_MS,
  DEV_SERVER_READY_INTERVAL_MS,
  DEV_SERVER_READY_TIMEOUT_MESSAGE,
  DEV_SERVER_READY_TIMEOUT_MS,
  DEV_SERVER_START_ERROR_MESSAGE,
  HTTP_SUCCESS_MAX_STATUS,
  HTTP_SUCCESS_MIN_STATUS
} = require("./constants");
const { hasProcessExited } = require("./process-manager");

const PROJECT_ROOT = path.join(__dirname, "..");
const VITE_CLI_PATH = path.join(PROJECT_ROOT, "node_modules", "vite", "bin", "vite.js");
const ELECTRON_RUN_AS_NODE_ENV_NAME = "ELECTRON_RUN_AS_NODE";
const ELECTRON_RUN_AS_NODE_ENABLED_VALUE = "1";

/**
 * Probe the development server once.
 *
 * @returns {Promise<boolean>} Whether the server returned a successful HTTP status.
 */
function probeDevServer() {
  return new Promise((resolve) => {
    let settled = false;
    const resolveProbe = (ready) => {
      if (settled) {
        return;
      }
      settled = true;
      resolve(ready);
    };
    const request = http.get(
      DEV_RENDERER_URL,
      { timeout: DEV_SERVER_PROBE_TIMEOUT_MS },
      (response) => {
        response.resume();
        response.destroy();
        const ready = response.statusCode >= HTTP_SUCCESS_MIN_STATUS
          && response.statusCode <= HTTP_SUCCESS_MAX_STATUS;
        resolveProbe(ready);
      }
    );
    request.once("timeout", () => request.destroy());
    request.once("error", () => resolveProbe(false));
  });
}

/**
 * Start Vite as an Electron-managed child process.
 *
 * @returns {import("node:child_process").ChildProcess} Running Vite process.
 */
function startViteDevelopmentServer() {
  const child = spawn(
    process.execPath,
    [
      VITE_CLI_PATH,
      "--host",
      DEV_RENDERER_HOST,
      "--port",
      String(DEV_RENDERER_PORT),
      "--strictPort"
    ],
    {
      cwd: PROJECT_ROOT,
      env: {
        ...process.env,
        [ELECTRON_RUN_AS_NODE_ENV_NAME]: ELECTRON_RUN_AS_NODE_ENABLED_VALUE
      },
      stdio: ["ignore", "pipe", "pipe"],
      windowsHide: true
    }
  );

  child.stdout.on("data", (chunk) => process.stdout.write(chunk));
  child.stderr.on("data", (chunk) => process.stderr.write(chunk));
  return child;
}

/**
 * Wait until Vite is reachable or fails during startup.
 *
 * @param {import("node:child_process").ChildProcess} child Running Vite process.
 * @returns {Promise<void>} Resolves when Vite accepts HTTP requests.
 */
async function waitForViteServer(child) {
  const startupFailure = new Promise((_, reject) => {
    child.once("error", (error) => {
      reject(new Error(`${DEV_SERVER_START_ERROR_MESSAGE}: ${error.message}`));
    });
    child.once("exit", (code, signal) => {
      reject(new Error(`${DEV_SERVER_EXITED_MESSAGE}: code=${code}, signal=${signal}`));
    });
  });

  const ready = (async () => {
    const deadline = Date.now() + DEV_SERVER_READY_TIMEOUT_MS;
    while (Date.now() < deadline) {
      if (hasProcessExited(child)) {
        throw new Error(DEV_SERVER_EXITED_MESSAGE);
      }
      if (await probeDevServer()) {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, DEV_SERVER_READY_INTERVAL_MS));
    }
    throw new Error(DEV_SERVER_READY_TIMEOUT_MESSAGE);
  })();

  await Promise.race([ready, startupFailure]);
}

module.exports = {
  startViteDevelopmentServer,
  waitForViteServer
};

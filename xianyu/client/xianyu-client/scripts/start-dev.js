const { spawn } = require("node:child_process");
const http = require("node:http");

const DEV_SERVER_URL = "http://127.0.0.1:5173";
const DEV_SERVER_READY_INTERVAL_MS = 100;
const DEV_SERVER_READY_TIMEOUT_MS = 30000;
const DEV_SERVER_PROBE_TIMEOUT_MS = 1000;
const HTTP_SUCCESS_MIN_STATUS = 200;
const HTTP_SUCCESS_MAX_STATUS = 299;
const DEV_SERVER_READY_TIMEOUT_MESSAGE = "Vite dev server did not become ready";
const children = [];
let electronStarted = false;

/**
 * Spawn an npm command and inherit terminal output.
 *
 * @param {string[]} args npm command arguments.
 * @returns {import("node:child_process").ChildProcess} Spawned process.
 */
function spawnNpm(args) {
  const npmCli = process.env.npm_execpath;
  if (!npmCli) {
    throw new Error("Development script must be started through npm");
  }
  const child = spawn(process.execPath, [npmCli, ...args], {
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true
  });
  children.push(child);
  return child;
}

/**
 * Probe the fixed Vite address once.
 *
 * @returns {Promise<boolean>} Whether the dev server is responding.
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
    const request = http.get(DEV_SERVER_URL, { timeout: DEV_SERVER_PROBE_TIMEOUT_MS }, (response) => {
      response.resume();
      response.destroy();
      const ready = response.statusCode >= HTTP_SUCCESS_MIN_STATUS && response.statusCode <= HTTP_SUCCESS_MAX_STATUS;
      resolveProbe(ready);
    });
    request.once("timeout", () => request.destroy());
    request.once("error", () => resolveProbe(false));
  });
}

/**
 * Wait until the fixed Vite address responds successfully.
 *
 * @returns {Promise<void>} Resolves when Vite is ready.
 */
async function waitForDevServer() {
  const deadline = Date.now() + DEV_SERVER_READY_TIMEOUT_MS;
  while (Date.now() < deadline) {
    if (await probeDevServer()) {
      return;
    }
    await new Promise((resolve) => setTimeout(resolve, DEV_SERVER_READY_INTERVAL_MS));
  }
  throw new Error(DEV_SERVER_READY_TIMEOUT_MESSAGE);
}

/**
 * Start Electron with the known local dev server URL.
 *
 * @returns {void}
 */
function startElectron() {
  if (electronStarted) {
    return;
  }
  electronStarted = true;
  process.env.VITE_DEV_SERVER_URL = DEV_SERVER_URL;
  const electron = spawnNpm(["run", "dev:electron"]);
  electron.stdout.pipe(process.stdout);
  electron.stderr.pipe(process.stderr);
  electron.once("exit", (code) => {
    stopChildren();
    process.exit(code === null ? 1 : code);
  });
}

/**
 * Kill the renderer and Electron process trees before script exit.
 *
 * @returns {void}
 */
function stopChildren() {
  for (const child of children) {
    if (child.exitCode === null) {
      if (process.platform === "win32") {
        spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], { stdio: "ignore" });
      } else {
        child.kill("SIGTERM");
      }
    }
  }
}

const renderer = spawnNpm(["run", "dev:renderer"]);
renderer.stdout.on("data", (chunk) => {
  process.stdout.write(chunk);
});

renderer.stderr.pipe(process.stderr);
renderer.once("exit", (code) => {
  stopChildren();
  process.exit(code === null ? 1 : code);
});

waitForDevServer()
  .then(startElectron)
  .catch((error) => {
    console.error(error.message);
    stopChildren();
    process.exit(1);
  });

process.once("SIGINT", () => {
  stopChildren();
  process.exit(0);
});


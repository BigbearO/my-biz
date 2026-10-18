const { createInterface } = require("node:readline");
const net = require("node:net");
const fs = require("node:fs/promises");
const path = require("node:path");
const { spawn } = require("node:child_process");

const {
  HANDSHAKE_BEGIN,
  HANDSHAKE_END,
  HANDSHAKE_TIMEOUT_MS,
  TCP_PROBE_TIMEOUT_MS
} = require("./constants");

const RUNTIME_FILE_NAME = "runtime.json";
const STARTING_RUNTIME_STATE = "starting";

/**
 * Resolve the Python executable and arguments for the current runtime mode.
 *
 * @returns {{command: string, args: string[]}} Executable command and module arguments.
 */
function resolvePythonLauncher() {
  const configuredCommand = process.env.GOODFISH_PYTHON_EXE;
  if (configuredCommand) {
    return { command: configuredCommand, args: ["-m", "xianyu_client"] };
  }

  if (process.env.NODE_ENV === "production") {
    const packagedExecutable = path.join(process.resourcesPath, "python", "xianyu-client.exe");
    if (fsSyncExists(packagedExecutable)) {
      return { command: packagedExecutable, args: [] };
    }
  }

  const venvPython = path.join(__dirname, "..", "python", ".venv", "Scripts", "python.exe");
  if (fsSyncExists(venvPython)) {
    return { command: venvPython, args: ["-m", "xianyu_client"] };
  }

  return { command: "python", args: ["-m", "xianyu_client"] };
}

/**
 * Check a file synchronously during launcher resolution.
 *
 * @param {string} candidate Absolute path to inspect.
 * @returns {boolean} True when the candidate exists.
 */
function fsSyncExists(candidate) {
  try {
    require("node:fs").accessSync(candidate);
    return true;
  } catch {
    return false;
  }
}

/**
 * Append one delimited line to the Electron log without writing secrets.
 *
 * @param {string} dataDir Goodfish application data directory.
 * @param {string} message Text to append.
 * @returns {Promise<void>} Resolves after the line is written.
 */
async function appendElectronLog(dataDir, message) {
  const logDir = path.join(dataDir, "logs");
  await fs.mkdir(logDir, { recursive: true });
  await fs.appendFile(path.join(logDir, "electron.log"), `${new Date().toISOString()} ${message}\n`, "utf8");
}

/**
 * Confirm a local port is reachable over TCP.
 *
 * @param {number} port Port assigned to the Python service.
 * @returns {Promise<void>} Resolves when the port accepts a connection.
 */
function probeLocalPort(port) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({ host: "127.0.0.1", port });
    const finish = (error) => {
      socket.destroy();
      if (error) {
        reject(error);
      } else {
        resolve();
      }
    };
    socket.setTimeout(TCP_PROBE_TIMEOUT_MS, () => finish(new Error("TCP probe timed out")));
    socket.once("connect", () => finish());
    socket.once("error", finish);
  });
}

/**
 * Parse the single-line JSON handshake emitted by Python.
 *
 * @param {string} line Raw stdout line.
 * @returns {object|null} Runtime payload, or null when the line is not a handshake.
 */
function parseHandshakeLine(line) {
  const beginIndex = line.indexOf(HANDSHAKE_BEGIN);
  const endIndex = line.indexOf(HANDSHAKE_END, beginIndex + HANDSHAKE_BEGIN.length);
  if (beginIndex < 0 || endIndex < 0) {
    return null;
  }
  const jsonStart = beginIndex + HANDSHAKE_BEGIN.length;
  try {
    return JSON.parse(line.slice(jsonStart, endIndex));
  } catch {
    return null;
  }
}

/**
 * Terminate a process tree on the current platform.
 *
 * @param {import("node:child_process").ChildProcess} child Process to terminate.
 * @returns {Promise<void>} Resolves after termination is issued.
 */
function terminateProcessTree(child) {
  if (!child || child.exitCode !== null) {
    return Promise.resolve();
  }
  if (process.platform === "win32") {
    spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], { stdio: "ignore" });
    return Promise.resolve();
  }
  child.kill("SIGTERM");
  return Promise.resolve();
}

/**
 * Start and authenticate the Python local service process.
 *
 * @param {{dataDir: string, localToken: string, logLevel: string}} options Runtime environment.
 * @returns {Promise<{process: import("node:child_process").ChildProcess, port: number, pid: number, version: string}>}
 */
async function startPythonService({ dataDir, localToken, logLevel }) {
  await fs.mkdir(path.join(dataDir, "logs"), { recursive: true });
  await fs.writeFile(
    path.join(dataDir, RUNTIME_FILE_NAME),
    JSON.stringify({ state: STARTING_RUNTIME_STATE }),
    "utf8"
  );

  const { command, args } = resolvePythonLauncher();
  const child = spawn(command, args, {
    cwd: path.join(__dirname, "..", "python"),
    env: {
      ...process.env,
      GOODFISH_DATA_DIR: dataDir,
      GOODFISH_LOCAL_TOKEN: localToken,
      GOODFISH_LOG_LEVEL: logLevel,
      GOODFISH_ENV: process.env.NODE_ENV || "development"
    },
    stdio: ["ignore", "pipe", "pipe"],
    windowsHide: true
  });

  createInterface({ input: child.stderr }).on("line", async (line) => {
    await appendElectronLog(dataDir, `python stderr: ${line}`).catch(() => undefined);
  });

  const handshakePromise = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("Python handshake timed out")), HANDSHAKE_TIMEOUT_MS);
    createInterface({ input: child.stdout }).on("line", (line) => {
      const payload = parseHandshakeLine(line);
      if (payload) {
        clearTimeout(timer);
        resolve(payload);
      }
    });
    child.once("exit", (code) => {
      clearTimeout(timer);
      reject(new Error(`Python service exited before handshake: ${code}`));
    });
  });

  try {
    const payload = await handshakePromise;
    await probeLocalPort(Number(payload.port));
    await appendElectronLog(dataDir, `python handshake ok port=${payload.port} pid=${payload.pid}`);
    return { process: child, port: Number(payload.port), pid: Number(payload.pid), version: payload.version };
  } catch (primaryError) {
    const fallbackPayload = await readRuntimeFallback(dataDir).catch(() => null);
    if (fallbackPayload?.port) {
      await probeLocalPort(Number(fallbackPayload.port)).catch(() => {
        throw primaryError;
      });
      await appendElectronLog(dataDir, "python handshake recovered from runtime.json");
      return {
        process: child,
        port: Number(fallbackPayload.port),
        pid: Number(fallbackPayload.pid),
        version: fallbackPayload.version
      };
    }
    await terminateProcessTree(child);
    throw primaryError;
  }
}

/**
 * Read runtime.json when the stdout handshake is unavailable.
 *
 * @param {string} dataDir Goodfish application data directory.
 * @returns {Promise<object|null>} Parsed runtime payload, or null when unusable.
 */
async function readRuntimeFallback(dataDir) {
  try {
    const content = await fs.readFile(path.join(dataDir, RUNTIME_FILE_NAME), "utf8");
    const payload = JSON.parse(content);
    return payload.state === STARTING_RUNTIME_STATE ? null : payload;
  } catch {
    return null;
  }
}

module.exports = {
  appendElectronLog,
  parseHandshakeLine,
  probeLocalPort,
  startPythonService,
  terminateProcessTree
};


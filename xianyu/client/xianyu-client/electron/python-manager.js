const { createInterface } = require("node:readline");
const net = require("node:net");
const fs = require("node:fs/promises");
const path = require("node:path");
const { spawn } = require("node:child_process");

const {
  CLOUD_BASE_URL_DEFAULT,
  CLOUD_BASE_URL_ENV_NAME,
  DEVELOPMENT_RUNTIME_ENV,
  HANDSHAKE_BEGIN,
  HANDSHAKE_END,
  HANDSHAKE_TIMEOUT_MS,
  LOCAL_TOKEN_ENV_NAME,
  LOG_LEVEL_ENV_NAME,
  PROCESS_NAME_PYTHON,
  PRODUCTION_RUNTIME_ENV,
  PYTHON_EXECUTABLE_NOT_FOUND_MESSAGE,
  PYTHON_HANDSHAKE_EXITED_MESSAGE,
  PYTHON_HANDSHAKE_TIMEOUT_MESSAGE,
  PYTHON_PARENT_PIPE_ENABLED_VALUE,
  PYTHON_PARENT_PIPE_ENV_NAME,
  RUNTIME_ENV_NAME,
  TCP_PROBE_TIMEOUT_MS
} = require("./constants");
const {
  registerProcess,
  terminateProcessTree,
  unregisterProcess
} = require("./process-manager");

const RUNTIME_FILE_NAME = "runtime.json";
const STARTING_RUNTIME_STATE = "starting";
const LOGS_DIRECTORY_NAME = "logs";
const ELECTRON_LOG_FILE_NAME = "electron.log";
const PYTHON_DIRECTORY_NAME = "python";
const PYTHON_MODULE_NAME = "xianyu_client";
const PACKAGED_PYTHON_EXECUTABLE_NAME = "xianyu-client.exe";
const ENV_DATA_DIRECTORY_NAME = "GOODFISH_DATA_DIR";
const LOCAL_SERVICE_PROBE_HOST = "127.0.0.1";

/**
 * Resolve the Python executable and arguments for the current runtime mode.
 *
 * @param {boolean} isPackaged Whether Electron is running from a packaged application.
 * @returns {{command: string, args: string[]}} Executable command and module arguments.
 */
function resolvePythonLauncher(isPackaged) {
  const configuredCommand = process.env.GOODFISH_PYTHON_EXE;
  if (configuredCommand) {
    return { command: configuredCommand, args: ["-m", PYTHON_MODULE_NAME] };
  }

  if (isPackaged) {
    const packagedExecutable = path.join(
      process.resourcesPath,
      PYTHON_DIRECTORY_NAME,
      PACKAGED_PYTHON_EXECUTABLE_NAME
    );
    if (fsSyncExists(packagedExecutable)) {
      return { command: packagedExecutable, args: [] };
    }
    throw new Error(PYTHON_EXECUTABLE_NOT_FOUND_MESSAGE);
  }

  const venvPython = path.join(__dirname, "..", PYTHON_DIRECTORY_NAME, ".venv", "Scripts", "python.exe");
  if (fsSyncExists(venvPython)) {
    return { command: venvPython, args: ["-m", PYTHON_MODULE_NAME] };
  }

  return { command: "python", args: ["-m", PYTHON_MODULE_NAME] };
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
  const logDir = path.join(dataDir, LOGS_DIRECTORY_NAME);
  await fs.mkdir(logDir, { recursive: true });
  await fs.appendFile(path.join(logDir, ELECTRON_LOG_FILE_NAME), `${new Date().toISOString()} ${message}\n`, "utf8");
}

/**
 * Confirm a local port is reachable over TCP.
 *
 * @param {number} port Port assigned to the Python service.
 * @returns {Promise<void>} Resolves when the port accepts a connection.
 */
function probeLocalPort(port) {
  return new Promise((resolve, reject) => {
    const socket = net.connect({ host: LOCAL_SERVICE_PROBE_HOST, port });
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
 * Start and authenticate the Python local service process.
 *
 * @param {{dataDir: string, localToken: string, logLevel: string, isPackaged: boolean}} options Runtime environment.
 * @returns {Promise<{process: import("node:child_process").ChildProcess, port: number, pid: number, version: string}>}
 */
async function startPythonService({ dataDir, localToken, logLevel, isPackaged }) {
  await fs.mkdir(path.join(dataDir, LOGS_DIRECTORY_NAME), { recursive: true });
  await fs.writeFile(
    path.join(dataDir, RUNTIME_FILE_NAME),
    JSON.stringify({ state: STARTING_RUNTIME_STATE }),
    "utf8"
  );

  const { command, args } = resolvePythonLauncher(isPackaged);
  const child = spawn(command, args, {
    cwd: isPackaged ? process.resourcesPath : path.join(__dirname, "..", PYTHON_DIRECTORY_NAME),
    env: {
      ...process.env,
      [ENV_DATA_DIRECTORY_NAME]: dataDir,
      [LOCAL_TOKEN_ENV_NAME]: localToken,
      [LOG_LEVEL_ENV_NAME]: logLevel,
      [RUNTIME_ENV_NAME]: isPackaged ? PRODUCTION_RUNTIME_ENV : DEVELOPMENT_RUNTIME_ENV,
      [PYTHON_PARENT_PIPE_ENV_NAME]: PYTHON_PARENT_PIPE_ENABLED_VALUE,
      [CLOUD_BASE_URL_ENV_NAME]: process.env[CLOUD_BASE_URL_ENV_NAME] || CLOUD_BASE_URL_DEFAULT
    },
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true
  });
  registerProcess(PROCESS_NAME_PYTHON, child);

  createInterface({ input: child.stderr }).on("line", async (line) => {
    await appendElectronLog(dataDir, `python stderr: ${line}`).catch(() => undefined);
  });

  const handshakePromise = new Promise((resolve, reject) => {
    let settled = false;
    let timer = null;
    const finish = (handler, value) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      child.removeListener("error", handleError);
      child.removeListener("exit", handleExit);
      handler(value);
    };
    const handleError = (error) => finish(reject, error);
    const handleExit = (code) => finish(
      reject,
      new Error(`${PYTHON_HANDSHAKE_EXITED_MESSAGE}: ${code}`)
    );

    timer = setTimeout(
      () => finish(reject, new Error(PYTHON_HANDSHAKE_TIMEOUT_MESSAGE)),
      HANDSHAKE_TIMEOUT_MS
    );
    child.once("error", handleError);
    child.once("exit", handleExit);
    createInterface({ input: child.stdout }).on("line", (line) => {
      const payload = parseHandshakeLine(line);
      if (payload) {
        finish(resolve, payload);
      }
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
      try {
        await probeLocalPort(Number(fallbackPayload.port));
      } catch {
        await terminateProcessTree(child);
        unregisterProcess(PROCESS_NAME_PYTHON, child);
        throw primaryError;
      }
      await appendElectronLog(dataDir, "python handshake recovered from runtime.json");
      return {
        process: child,
        port: Number(fallbackPayload.port),
        pid: Number(fallbackPayload.pid),
        version: fallbackPayload.version
      };
    }
    await terminateProcessTree(child);
    unregisterProcess(PROCESS_NAME_PYTHON, child);
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
  startPythonService
};

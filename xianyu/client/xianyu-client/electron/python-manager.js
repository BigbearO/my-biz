const { createInterface } = require("node:readline");
const { StringDecoder } = require("node:string_decoder");
const net = require("node:net");
const fs = require("node:fs/promises");
const path = require("node:path");
const { spawn } = require("node:child_process");

const {
  CLOUD_BASE_URL_DEFAULT,
  CLOUD_BASE_URL_ENV_NAME,
  DEVELOPMENT_RUNTIME_ENV,
  EXIT_CODE_SUCCESS,
  HANDSHAKE_BEGIN,
  HANDSHAKE_END,
  HANDSHAKE_TIMEOUT_MS,
  LINE_BREAK,
  LOCAL_TOKEN_ENV_NAME,
  LOG_LEVEL_ENV_NAME,
  PROCESS_NAME_PYTHON,
  PRODUCTION_RUNTIME_ENV,
  PYTHON_EXECUTABLE_NOT_FOUND_MESSAGE,
  PYTHON_HANDSHAKE_EXITED_MESSAGE,
  PYTHON_HANDSHAKE_TIMEOUT_MESSAGE,
  PYTHON_IO_ENCODING_ENV_NAME,
  PYTHON_IO_ENCODING_UTF8_VALUE,
  PYTHON_PARENT_PIPE_ENABLED_VALUE,
  PYTHON_PARENT_PIPE_ENV_NAME,
  PYTHON_STDERR_TAIL_LOG_PREFIX,
  PYTHON_UNBUFFERED_ENABLED_VALUE,
  PYTHON_UNBUFFERED_ENV_NAME,
  PYTHON_UTF8_MODE_ENABLED_VALUE,
  PYTHON_UTF8_MODE_ENV_NAME,
  READLINE_INFINITE_CRLF_DELAY,
  RUNTIME_ENV_NAME,
  STDERR_TAIL_LINE_LIMIT,
  STDIO_ENCODING,
  TCP_PROBE_TIMEOUT_MS,
  TRAILING_CARRIAGE_RETURN_PATTERN
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

let appendLogChain = Promise.resolve();

/**
 * Append one delimited line to the Electron log without writing secrets.
 *
 * Writes are chained so concurrent callers cannot interleave lines inside the
 * same file, which previously scrambled multi-line Python tracebacks.
 *
 * @param {string} dataDir Goodfish application data directory.
 * @param {string} message Text to append.
 * @returns {Promise<void>} Resolves after the line is written.
 */
async function appendElectronLog(dataDir, message) {
  const logDir = path.join(dataDir, LOGS_DIRECTORY_NAME);
  const line = `${new Date().toISOString()} ${message}${LINE_BREAK}`;
  appendLogChain = appendLogChain
    .catch(() => undefined)
    .then(async () => {
      await fs.mkdir(logDir, { recursive: true });
      await fs.appendFile(path.join(logDir, ELECTRON_LOG_FILE_NAME), line, STDIO_ENCODING);
    });
  return appendLogChain;
}

/**
 * Collect Python stderr while optionally mirroring raw bytes to the console.
 *
 * Development runs stream the original bytes to Electron's stderr so the
 * UTF-8 output reaches the terminal unchanged; packaged runs only keep a
 * bounded tail used to explain a failed handshake.
 *
 * @param {boolean} isPackaged Whether Electron is running from a packaged application.
 * @returns {{handleChunk: (chunk: Buffer) => void, flushTail: (dataDir: string) => Promise<void>}}
 */
function createStderrCollector(isPackaged) {
  const decoder = new StringDecoder(STDIO_ENCODING);
  const tail = [];
  let pending = "";
  let flushed = false;

  const pushLine = (line) => {
    if (!line) {
      return;
    }
    tail.push(line);
    if (tail.length > STDERR_TAIL_LINE_LIMIT) {
      tail.shift();
    }
  };

  const handleChunk = (chunk) => {
    if (!isPackaged) {
      try {
        process.stderr.write(chunk);
      } catch {
        // The console may be unavailable for windowed Electron; keep collecting.
      }
    }
    pending += decoder.write(chunk);
    const lines = pending.split(LINE_BREAK);
    pending = lines.pop() ?? "";
    for (const line of lines) {
      pushLine(line.replace(TRAILING_CARRIAGE_RETURN_PATTERN, ""));
    }
  };

  const readTail = () => {
    const trailing = pending.replace(TRAILING_CARRIAGE_RETURN_PATTERN, "");
    const lines = trailing ? [...tail, trailing] : [...tail];
    return lines.join(LINE_BREAK);
  };

  const flushTail = async (dataDir) => {
    if (flushed) {
      return;
    }
    flushed = true;
    const content = readTail();
    if (!content) {
      return;
    }
    await appendElectronLog(
      dataDir,
      `${PYTHON_STDERR_TAIL_LOG_PREFIX}${LINE_BREAK}${content}`
    ).catch(() => undefined);
  };

  return { handleChunk, flushTail };
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
    STDIO_ENCODING
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
      [CLOUD_BASE_URL_ENV_NAME]: process.env[CLOUD_BASE_URL_ENV_NAME] || CLOUD_BASE_URL_DEFAULT,
      [PYTHON_UTF8_MODE_ENV_NAME]: PYTHON_UTF8_MODE_ENABLED_VALUE,
      [PYTHON_IO_ENCODING_ENV_NAME]: PYTHON_IO_ENCODING_UTF8_VALUE,
      [PYTHON_UNBUFFERED_ENV_NAME]: PYTHON_UNBUFFERED_ENABLED_VALUE
    },
    stdio: ["pipe", "pipe", "pipe"],
    windowsHide: true
  });
  registerProcess(PROCESS_NAME_PYTHON, child);

  const stderrCollector = createStderrCollector(isPackaged);
  child.stderr.on("data", stderrCollector.handleChunk);
  child.once("exit", (code) => {
    if (code !== EXIT_CODE_SUCCESS) {
      void stderrCollector.flushTail(dataDir);
    }
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
    createInterface({
      input: child.stdout,
      crlfDelay: READLINE_INFINITE_CRLF_DELAY,
      encoding: STDIO_ENCODING
    }).on("line", (line) => {
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
    await stderrCollector.flushTail(dataDir);
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
    const content = await fs.readFile(path.join(dataDir, RUNTIME_FILE_NAME), STDIO_ENCODING);
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

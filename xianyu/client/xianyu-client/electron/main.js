const { app, BrowserWindow, dialog, ipcMain } = require("electron");
const crypto = require("node:crypto");
const path = require("node:path");

const {
  APP_NAME,
  APP_STARTUP_ERROR_MESSAGE,
  APP_STARTUP_ERROR_TITLE,
  APP_UNEXPECTED_ERROR_MESSAGE,
  CLOUD_CAPTCHA_CHANNEL,
  CLOUD_LOGIN_CHANNEL,
  DEFAULT_LOG_LEVEL,
  DEV_MODE_ARGUMENT,
  DEV_RENDERER_URL,
  ELECTRON_DATA_DIR_NAME,
  EXIT_CODE_FAILURE,
  EXIT_CODE_SUCCESS,
  LOCAL_API_DETAIL_KEY,
  LOCAL_API_ERROR_BODY_LIMIT,
  LOCAL_API_ERROR_STATUS_KEY,
  LOCAL_API_ERROR_STATUS_TEMPLATE,
  LOCAL_API_JSON_CONTENT_TYPE,
  LOCAL_API_PATH_PREFIX,
  LOCAL_API_REQUEST_TIMEOUT_MS,
  LOCAL_API_TIMEOUT_ERROR_MESSAGE,
  LOCAL_REQUEST_CHANNEL,
  LOCAL_SERVICE_HOST,
  LOCAL_SERVICE_NOT_READY_ERROR_MESSAGE,
  LOCAL_TOKEN_BYTE_LENGTH,
  LOCAL_TOKEN_ENV_NAME,
  LOCAL_TOKEN_HEADER,
  LOG_LEVEL_ENV_NAME,
  PROCESS_NAME_PYTHON,
  PROCESS_NAME_VITE,
  PYTHON_GRACEFUL_SHUTDOWN_TIMEOUT_MS,
  TIMEOUT_ERROR_NAME,
  VALIDATION_DETAIL_JOINER,
  VALIDATION_DETAIL_KEY,
  WINDOW_HEIGHT,
  WINDOW_TITLE,
  WINDOW_WIDTH
} = require("./constants");
const { fetchCaptcha, login: cloudLogin } = require("./cloud-auth");
const {
  hasProcessExited,
  registerProcess,
  terminateAllProcesses,
  terminateProcessTree,
  waitForProcessExit
} = require("./process-manager");
const { appendElectronLog, startPythonService } = require("./python-manager");
const { startViteDevelopmentServer, waitForViteServer } = require("./vite-manager");

let mainWindow = null;
let pythonSession = null;
let viteProcess = null;
let shutdownPromise = null;
let isShuttingDown = false;

/**
 * Determine whether the current process should launch the Vite development server.
 *
 * @returns {boolean} True when running unpackaged with the explicit development flag.
 */
function isDevelopmentMode() {
  return !app.isPackaged && process.argv.includes(DEV_MODE_ARGUMENT);
}

/**
 * Convert an unknown error value into a safe log message.
 *
 * @param {unknown} error Error value.
 * @returns {string} Human-readable error message.
 */
function getErrorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Proxy a renderer request to the local Python API without exposing its token.
 *
 * @param {string} pathname API path beginning with `/api/`.
 * @param {RequestInit} options Standard fetch options from the renderer.
 * @returns {Promise<unknown>} Parsed JSON response.
 */
async function requestLocalApi(pathname, options = {}) {
  if (!pythonSession || typeof pathname !== "string" || !pathname.startsWith(LOCAL_API_PATH_PREFIX)) {
    throw new Error(LOCAL_SERVICE_NOT_READY_ERROR_MESSAGE);
  }
  let response;
  try {
    response = await fetch(`http://${LOCAL_SERVICE_HOST}:${pythonSession.port}${pathname}`, {
      ...options,
      headers: {
        ...(options.headers || {}),
        [LOCAL_TOKEN_HEADER]: process.env[LOCAL_TOKEN_ENV_NAME] || ""
      },
      signal: AbortSignal.timeout(LOCAL_API_REQUEST_TIMEOUT_MS)
    });
  } catch (error) {
    if (error instanceof Error && error.name === TIMEOUT_ERROR_NAME) {
      throw new Error(LOCAL_API_TIMEOUT_ERROR_MESSAGE);
    }
    throw error;
  }
  if (!response.ok) {
    throw await createLocalApiError(response, pathname);
  }
  return response.json();
}

/**
 * Build an error from a safe JSON detail response without echoing request bodies.
 *
 * @param {Response} response Failed local API response.
 * @param {string} pathname Requested API path used for logging only.
 * @returns {Promise<Error>} Error carrying a non-secret backend message.
 */
async function createLocalApiError(response, pathname) {
  const contentType = response.headers.get("content-type") || "";
  if (contentType.startsWith(LOCAL_API_JSON_CONTENT_TYPE)) {
    const payload = await response.json().catch(() => null);
    const detail = extractErrorDetail(payload);
    if (detail) {
      return new Error(detail);
    }
  } else {
    const body = await response.text().catch(() => "");
    const excerpt = body.slice(0, LOCAL_API_ERROR_BODY_LIMIT);
    await appendElectronLog(
      resolveDataDir(),
      `local api failure path=${pathname} status=${response.status} body=${excerpt}`
    ).catch(() => undefined);
  }
  return new Error(
    LOCAL_API_ERROR_STATUS_TEMPLATE.replace(LOCAL_API_ERROR_STATUS_KEY, String(response.status))
  );
}

/**
 * Extract a readable message from a FastAPI error payload.
 *
 * @param {unknown} payload Decoded JSON body returned by the local service.
 * @returns {string} Detail text, or an empty string when no message is present.
 */
function extractErrorDetail(payload) {
  if (!payload || typeof payload !== "object") {
    return "";
  }
  const detail = payload[LOCAL_API_DETAIL_KEY];
  if (typeof detail === "string") {
    return detail;
  }
  if (!Array.isArray(detail)) {
    return "";
  }
  return detail
    .map((item) => (item && typeof item === "object" ? item[VALIDATION_DETAIL_KEY] : ""))
    .filter((message) => typeof message === "string" && message)
    .join(VALIDATION_DETAIL_JOINER);
}

/**
 * Build the isolated application data directory path.
 *
 * @returns {string} Absolute Goodfish data directory.
 */
function resolveDataDir() {
  return path.join(app.getPath("appData"), APP_NAME);
}

/**
 * Resolve an Electron-only directory so its cache does not compete with local service files.
 *
 * @returns {string} Absolute Electron user data directory.
 */
function resolveElectronDataDir() {
  return path.join(resolveDataDir(), ELECTRON_DATA_DIR_NAME);
}

/**
 * Create the main application window with restrictive navigation defaults.
 *
 * @returns {BrowserWindow} The created desktop window.
 */
function createMainWindow() {
  const window = new BrowserWindow({
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    minWidth: WINDOW_WIDTH,
    minHeight: WINDOW_HEIGHT,
    title: WINDOW_TITLE,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  });

  window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  return window;
}

/**
 * Log and stop the application when a managed child exits unexpectedly.
 *
 * @param {import("node:child_process").ChildProcess} child Managed child process.
 * @param {string} processName Stable child process name.
 * @returns {void}
 */
function watchManagedProcess(child, processName) {
  child.once("exit", (code, signal) => {
    if (isShuttingDown) {
      return;
    }
    const message = `${processName} exited unexpectedly: code=${code}, signal=${signal}`;
    void appendElectronLog(resolveDataDir(), message)
      .catch(() => undefined)
      .finally(() => shutdownApplication(EXIT_CODE_FAILURE));
  });
}

/**
 * Start and wait for Vite in development mode.
 *
 * @returns {Promise<void>} Resolves when the renderer is available.
 */
async function startDevelopmentRenderer() {
  viteProcess = startViteDevelopmentServer();
  registerProcess(PROCESS_NAME_VITE, viteProcess);
  watchManagedProcess(viteProcess, PROCESS_NAME_VITE);
  await waitForViteServer(viteProcess);
}

/**
 * Start the authenticated Python local service.
 *
 * @param {string} dataDir Application data directory.
 * @param {string} localToken Per-process local API token.
 * @returns {Promise<void>} Resolves when the local service is ready.
 */
async function startLocalService(dataDir, localToken) {
  pythonSession = await startPythonService({
    dataDir,
    localToken,
    logLevel: process.env[LOG_LEVEL_ENV_NAME] || DEFAULT_LOG_LEVEL,
    isPackaged: app.isPackaged
  });
  watchManagedProcess(pythonSession.process, PROCESS_NAME_PYTHON);
  process.env[LOCAL_TOKEN_ENV_NAME] = localToken;
}

/**
 * Initialize Electron, managed child processes, and the renderer window.
 *
 * @returns {Promise<void>} Resolves when the shell is ready.
 */
async function bootstrapApplication() {
  const dataDir = resolveDataDir();
  const localToken = crypto.randomBytes(LOCAL_TOKEN_BYTE_LENGTH).toString("hex");

  if (isDevelopmentMode()) {
    await startDevelopmentRenderer();
  }

  await startLocalService(dataDir, localToken);
  ipcMain.handle(LOCAL_REQUEST_CHANNEL, (_event, pathname, options) => requestLocalApi(pathname, options));
  ipcMain.handle(CLOUD_LOGIN_CHANNEL, (_event, credentials) => cloudLogin(credentials));
  ipcMain.handle(CLOUD_CAPTCHA_CHANNEL, () => fetchCaptcha());

  mainWindow = createMainWindow();
  if (isDevelopmentMode()) {
    await mainWindow.loadURL(DEV_RENDERER_URL);
  } else {
    await mainWindow.loadFile(path.join(__dirname, "..", "dist", "index.html"));
  }
}

/**
 * Close the Python stdin pipe so the service stops after Electron disconnects.
 *
 * @param {{process: import("node:child_process").ChildProcess}} session Python process session.
 * @returns {Promise<void>} Resolves after graceful shutdown or forced termination.
 */
async function stopPythonService(session) {
  const child = session?.process;
  if (!child || hasProcessExited(child)) {
    return;
  }

  try {
    child.stdin?.end();
  } catch {
    // Closing stdin is best effort; process-tree termination below is the fallback.
  }

  if (await waitForProcessExit(child, PYTHON_GRACEFUL_SHUTDOWN_TIMEOUT_MS)) {
    return;
  }
  await terminateProcessTree(child);
}

/**
 * Shut down every process owned by Electron exactly once.
 *
 * @param {number} exitCode Process exit code.
 * @returns {Promise<void>} Resolves when shutdown has been initiated.
 */
function shutdownApplication(exitCode) {
  if (shutdownPromise) {
    return shutdownPromise;
  }

  isShuttingDown = true;
  shutdownPromise = (async () => {
    try {
      const shutdownTasks = [];
      if (pythonSession) {
        shutdownTasks.push(stopPythonService(pythonSession));
      }
      if (viteProcess) {
        shutdownTasks.push(terminateProcessTree(viteProcess));
      }
      await Promise.allSettled(shutdownTasks);
      await terminateAllProcesses();
      pythonSession = null;
      viteProcess = null;
      await appendElectronLog(resolveDataDir(), "application shutdown completed").catch(() => undefined);
    } catch (error) {
      await appendElectronLog(resolveDataDir(), `shutdown failed: ${getErrorMessage(error)}`).catch(() => undefined);
    } finally {
      app.exit(exitCode);
    }
  })();

  return shutdownPromise;
}

/**
 * Handle an unexpected main-process failure through the common shutdown path.
 *
 * @param {unknown} error Unexpected error value.
 * @returns {Promise<void>} Resolves after shutdown begins.
 */
async function handleFatalError(error) {
  const message = getErrorMessage(error);
  await appendElectronLog(resolveDataDir(), `unexpected application error: ${message}`).catch(() => undefined);
  if (!isShuttingDown) {
    dialog.showErrorBox(APP_STARTUP_ERROR_TITLE, APP_UNEXPECTED_ERROR_MESSAGE);
  }
  await shutdownApplication(EXIT_CODE_FAILURE);
}

/**
 * Handle Electron readiness and report startup failures to the user.
 *
 * @returns {Promise<void>} Resolves after startup succeeds or the app exits.
 */
async function handleAppReady() {
  try {
    await bootstrapApplication();
  } catch (error) {
    const message = getErrorMessage(error);
    await appendElectronLog(resolveDataDir(), `startup failed: ${message}`).catch(() => undefined);
    if (!isShuttingDown) {
      dialog.showErrorBox(APP_STARTUP_ERROR_TITLE, APP_STARTUP_ERROR_MESSAGE);
    }
    await shutdownApplication(EXIT_CODE_FAILURE);
  }
}

app.setName(APP_NAME);
app.setPath("userData", resolveElectronDataDir());
app.whenReady().then(handleAppReady).catch(handleFatalError);

app.on("window-all-closed", () => {
  app.quit();
});

app.on("before-quit", (event) => {
  event.preventDefault();
  if (!isShuttingDown) {
    void shutdownApplication(EXIT_CODE_SUCCESS);
  }
});

process.on("SIGINT", () => {
  void shutdownApplication(EXIT_CODE_SUCCESS);
});
process.on("SIGTERM", () => {
  void shutdownApplication(EXIT_CODE_SUCCESS);
});
process.on("SIGHUP", () => {
  void shutdownApplication(EXIT_CODE_SUCCESS);
});
process.on("uncaughtException", (error) => {
  void handleFatalError(error);
});
process.on("unhandledRejection", (error) => {
  void handleFatalError(error);
});

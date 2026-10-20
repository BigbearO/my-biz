const { app, BrowserWindow, dialog, ipcMain } = require("electron");
const crypto = require("node:crypto");
const path = require("node:path");

const {
  APP_NAME,
  LOCAL_API_ERROR_MESSAGE,
  LOCAL_API_JSON_CONTENT_TYPE,
  LOCAL_SERVICE_NOT_READY_ERROR_MESSAGE,
  LOCAL_REQUEST_CHANNEL,
  WINDOW_HEIGHT,
  WINDOW_WIDTH
} = require("./constants");
const { appendElectronLog, startPythonService, terminateProcessTree } = require("./python-manager");

let mainWindow = null;
let pythonSession = null;

/**
 * Proxy a renderer request to the local Python API without exposing its token.
 *
 * @param {string} pathname API path beginning with `/api`.
 * @param {RequestInit} options Standard fetch options from the renderer.
 * @returns {Promise<unknown>} Parsed JSON response.
 */
async function requestLocalApi(pathname, options = {}) {
  if (!pythonSession || typeof pathname !== "string" || !pathname.startsWith("/api/")) {
    throw new Error(LOCAL_SERVICE_NOT_READY_ERROR_MESSAGE);
  }
  const response = await fetch(`http://127.0.0.1:${pythonSession.port}${pathname}`, {
    ...options,
    headers: {
      ...(options.headers || {}),
      "X-Local-Token": process.env.GOODFISH_LOCAL_TOKEN || ""
    }
  });
  if (!response.ok) {
    throw await createLocalApiError(response);
  }
  return response.json();
}

/**
 * Build an error from a safe JSON detail response without echoing request bodies.
 *
 * @param {Response} response Failed local API response.
 * @returns {Promise<Error>} Error carrying a non-secret backend message.
 */
async function createLocalApiError(response) {
  try {
    if (response.headers.get("content-type")?.startsWith(LOCAL_API_JSON_CONTENT_TYPE)) {
      const payload = await response.json();
      if (typeof payload.detail === "string") {
        return new Error(payload.detail);
      }
    }
  } catch {
    return new Error(LOCAL_API_ERROR_MESSAGE);
  }
  return new Error(LOCAL_API_ERROR_MESSAGE);
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
    title: "Goodfish",
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
 * Initialize Electron, Python service, and the renderer window.
 *
 * @returns {Promise<void>} Resolves when the shell is ready.
 */
async function bootstrapApplication() {
  const dataDir = resolveDataDir();
  const localToken = crypto.randomBytes(32).toString("hex");
  pythonSession = await startPythonService({
    dataDir,
    localToken,
    logLevel: process.env.GOODFISH_LOG_LEVEL || "INFO"
  });
  process.env.GOODFISH_LOCAL_TOKEN = localToken;
  ipcMain.handle(LOCAL_REQUEST_CHANNEL, (_event, pathname, options) => requestLocalApi(pathname, options));

  mainWindow = createMainWindow();
  if (process.env.VITE_DEV_SERVER_URL) {
    await mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL);
  } else {
    await mainWindow.loadFile(path.join(__dirname, "..", "dist", "index.html"));
  }
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
    const dataDir = resolveDataDir();
    await appendElectronLog(dataDir, `startup failed: ${error.message}`).catch(() => undefined);
    dialog.showErrorBox("本地服务启动失败", "请检查本地服务环境后重新启动应用。");
    app.quit();
  }
}

app.setName(APP_NAME);
app.whenReady().then(handleAppReady);

app.on("window-all-closed", () => {
  app.quit();
});

app.on("before-quit", async (event) => {
  if (!pythonSession) {
    return;
  }
  event.preventDefault();
  const session = pythonSession;
  pythonSession = null;
  await terminateProcessTree(session.process);
  app.quit();
});


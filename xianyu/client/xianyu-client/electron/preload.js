const { contextBridge, ipcRenderer } = require("electron");

const LOCAL_REQUEST_CHANNEL = "goodfish:local-request";
const CLOUD_LOGIN_CHANNEL = "goodfish:cloud-login";
const CLOUD_CAPTCHA_CHANNEL = "goodfish:cloud-captcha";

/**
 * Expose non-secret local service metadata to the renderer.
 *
 * @returns {{baseUrl: string}} Local API origin label.
 */
function getLocalServiceInfo() {
  return { baseUrl: "local-service" };
}

/**
 * Perform a token-protected local API request without exposing the token.
 *
 * The main process always resolves with a `{ ok, data }` or `{ ok: false, error }`
 * envelope so the renderer can map a stable error code to a readable message.
 *
 * @param {string} pathname API path beginning with `/api`.
 * @param {RequestInit} [options] Standard fetch options.
 * @returns {Promise<{ok: boolean, data?: unknown, error?: {status: number, code: string, message: string}}>}
 *   Result envelope.
 */
async function request(pathname, options = {}) {
  return ipcRenderer.invoke(LOCAL_REQUEST_CHANNEL, pathname, options);
}

/**
 * Fetch one captcha challenge through the main process.
 *
 * @returns {Promise<{ok: boolean, data?: {uuid: string, img: string}, error?: {status: number, code: string, message: string}}>}
 *   Result envelope carrying the captcha identity and image.
 */
async function fetchCaptcha() {
  return ipcRenderer.invoke(CLOUD_CAPTCHA_CHANNEL);
}

/**
 * Authenticate against the management backend through the main process.
 *
 * @param {{username: string, password: string, code: string, uuid: string}} credentials Login form values.
 * @returns {Promise<{ok: boolean, data?: {token: string, clientid: string}, error?: {status: number, code: string, message: string}}>}
 *   Result envelope carrying the access token and paired client id.
 */
async function cloudLogin(credentials) {
  return ipcRenderer.invoke(CLOUD_LOGIN_CHANNEL, credentials);
}

contextBridge.exposeInMainWorld("goodfish", {
  getLocalServiceInfo,
  request,
  fetchCaptcha,
  cloudLogin
});

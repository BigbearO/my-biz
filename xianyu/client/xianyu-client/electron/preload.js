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
 * @param {string} pathname API path beginning with `/api`.
 * @param {RequestInit} [options] Standard fetch options.
 * @returns {Promise<unknown>} Parsed JSON response.
 */
async function request(pathname, options = {}) {
  return ipcRenderer.invoke(LOCAL_REQUEST_CHANNEL, pathname, options);
}

/**
 * Fetch one captcha challenge through the main process.
 *
 * @returns {Promise<{uuid: string, img: string}>} Captcha identity and image.
 */
async function fetchCaptcha() {
  return ipcRenderer.invoke(CLOUD_CAPTCHA_CHANNEL);
}

/**
 * Authenticate against the management backend through the main process.
 *
 * @param {{username: string, password: string, code: string, uuid: string}} credentials Login form values.
 * @returns {Promise<{token: string, clientid: string}>} Access token and paired client id.
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

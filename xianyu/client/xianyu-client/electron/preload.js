const { contextBridge, ipcRenderer } = require("electron");

const LOCAL_REQUEST_CHANNEL = "goodfish:local-request";

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

contextBridge.exposeInMainWorld("goodfish", {
  getLocalServiceInfo,
  request
});


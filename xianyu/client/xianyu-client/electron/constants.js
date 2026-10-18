/** Shared constants for the Electron main process. */

const APP_NAME = "goodfish";
const APP_VERSION = "0.1.0";
const HANDSHAKE_BEGIN = "#GOODFISH_PORT#";
const HANDSHAKE_END = "#END#";
const HANDSHAKE_TIMEOUT_MS = 15000;
const TCP_PROBE_TIMEOUT_MS = 1500;
const WINDOW_WIDTH = 1120;
const WINDOW_HEIGHT = 720;
const DEV_RENDERER_URL = "http://127.0.0.1:5173";
const LOCAL_REQUEST_CHANNEL = "goodfish:local-request";

module.exports = {
  APP_NAME,
  APP_VERSION,
  HANDSHAKE_BEGIN,
  HANDSHAKE_END,
  HANDSHAKE_TIMEOUT_MS,
  TCP_PROBE_TIMEOUT_MS,
  WINDOW_WIDTH,
  WINDOW_HEIGHT,
  DEV_RENDERER_URL,
  LOCAL_REQUEST_CHANNEL
};


const crypto = require("node:crypto");

const {
  CLOUD_AES_ALGORITHM,
  CLOUD_AES_KEY_BYTE_LENGTH,
  CLOUD_AUTH_CODE_PATH,
  CLOUD_AUTH_LOGIN_PATH,
  CLOUD_BASE_URL_DEFAULT,
  CLOUD_BASE_URL_ENV_NAME,
  CLOUD_CAPTCHA_ERROR_MESSAGE,
  CLOUD_CLIENT_ID,
  CLOUD_DEFAULT_ERROR_MESSAGE,
  CLOUD_ENCRYPT_KEY_HEADER,
  CLOUD_GRANT_TYPE_PASSWORD,
  CLOUD_PEM_LINE_LENGTH,
  CLOUD_REQUEST_TIMEOUT_MS,
  CLOUD_RSA_PUBLIC_KEY,
  CLOUD_SUCCESS_CODE,
  CLOUD_TIMEOUT_ERROR_MESSAGE
} = require("./constants");

const BASE64_ENCODING = "base64";
const UTF8_ENCODING = "utf8";
const JSON_CONTENT_TYPE = "application/json";
const RSA_PKCS1_PADDING = crypto.constants.RSA_PKCS1_PADDING;
const CAPTCHA_IMAGE_PREFIX = "data:image/gif;base64,";

/**
 * Resolve the cloud gateway origin from the environment.
 *
 * @returns {string} Gateway origin without a trailing slash.
 */
function resolveBaseUrl() {
  const configured = process.env[CLOUD_BASE_URL_ENV_NAME] || CLOUD_BASE_URL_DEFAULT;
  return configured.replace(/\/+$/, "");
}

/**
 * Wrap a base64 DER public key into a PEM document.
 *
 * @param {string} publicKey Base64 encoded RSA public key.
 * @returns {string} PEM document accepted by node:crypto.
 */
function toPemPublicKey(publicKey) {
  const lines = [];
  for (let index = 0; index < publicKey.length; index += CLOUD_PEM_LINE_LENGTH) {
    lines.push(publicKey.slice(index, index + CLOUD_PEM_LINE_LENGTH));
  }
  return `-----BEGIN PUBLIC KEY-----\n${lines.join("\n")}\n-----END PUBLIC KEY-----`;
}

/**
 * Encrypt one login payload with the RuoYi API encryption scheme.
 *
 * @param {object} payload Login body to protect.
 * @returns {{body: string, encryptKey: string}} AES ciphertext and RSA protected key.
 */
function encryptPayload(payload) {
  const aesKey = crypto.randomBytes(CLOUD_AES_KEY_BYTE_LENGTH).toString("hex");
  const aesKeyBytes = Buffer.from(aesKey, UTF8_ENCODING);
  const cipher = crypto.createCipheriv(CLOUD_AES_ALGORITHM, aesKeyBytes, null);
  const encrypted = Buffer.concat([
    cipher.update(JSON.stringify(payload), UTF8_ENCODING),
    cipher.final()
  ]);
  const encryptKey = crypto
    .publicEncrypt(
      { key: toPemPublicKey(CLOUD_RSA_PUBLIC_KEY), padding: RSA_PKCS1_PADDING },
      Buffer.from(aesKeyBytes.toString(BASE64_ENCODING), UTF8_ENCODING)
    )
    .toString(BASE64_ENCODING);
  return { body: encrypted.toString(BASE64_ENCODING), encryptKey };
}

/**
 * Perform one cloud JSON request with an explicit timeout.
 *
 * @param {string} pathname Absolute gateway path.
 * @param {RequestInit} options Fetch options.
 * @returns {Promise<Record<string, unknown>>} Decoded JSON body.
 */
async function requestCloudJson(pathname, options) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), CLOUD_REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${resolveBaseUrl()}${pathname}`, {
      ...options,
      signal: controller.signal
    });
    const payload = await response.json().catch(() => ({}));
    return payload && typeof payload === "object" ? payload : {};
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") {
      throw new Error(CLOUD_TIMEOUT_ERROR_MESSAGE);
    }
    throw new Error(CLOUD_DEFAULT_ERROR_MESSAGE);
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Fetch one math captcha challenge from the management backend.
 *
 * @returns {Promise<{uuid: string, img: string}>} Captcha identity and base64 image.
 */
async function fetchCaptcha() {
  const payload = await requestCloudJson(CLOUD_AUTH_CODE_PATH, { method: "GET" });
  if (payload.code !== CLOUD_SUCCESS_CODE || !payload.data) {
    throw new Error(typeof payload.msg === "string" ? payload.msg : CLOUD_CAPTCHA_ERROR_MESSAGE);
  }
  const data = payload.data;
  const image = String(data.img || "");
  const imageUrl = image.startsWith(CAPTCHA_IMAGE_PREFIX) ? image : `${CAPTCHA_IMAGE_PREFIX}${image}`;
  return { uuid: String(data.uuid || ""), img: imageUrl };
}

/**
 * Authenticate against the management backend and return the token pair.
 *
 * @param {{username: string, password: string, code: string, uuid: string}} credentials Login form values.
 * @returns {Promise<{token: string, clientid: string}>} Access token and paired client id.
 */
async function login(credentials) {
  const { body, encryptKey } = encryptPayload({
    username: credentials.username,
    password: credentials.password,
    clientId: CLOUD_CLIENT_ID,
    grantType: CLOUD_GRANT_TYPE_PASSWORD,
    code: credentials.code,
    uuid: credentials.uuid
  });
  const payload = await requestCloudJson(CLOUD_AUTH_LOGIN_PATH, {
    method: "POST",
    headers: {
      "Content-Type": JSON_CONTENT_TYPE,
      clientid: CLOUD_CLIENT_ID,
      [CLOUD_ENCRYPT_KEY_HEADER]: encryptKey
    },
    body
  });
  if (payload.code !== CLOUD_SUCCESS_CODE || !payload.data) {
    throw new Error(typeof payload.msg === "string" ? payload.msg : CLOUD_DEFAULT_ERROR_MESSAGE);
  }
  const data = payload.data;
  if (typeof data.access_token !== "string" || !data.access_token) {
    throw new Error(CLOUD_DEFAULT_ERROR_MESSAGE);
  }
  return {
    token: data.access_token,
    clientid: typeof data.client_id === "string" && data.client_id ? data.client_id : CLOUD_CLIENT_ID
  };
}

module.exports = { fetchCaptcha, login };

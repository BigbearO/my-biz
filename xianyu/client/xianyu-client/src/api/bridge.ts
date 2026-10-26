import {
  CLOUD_CAPTCHA_FAILED_MESSAGE,
  CLOUD_LOGIN_FAILED_MESSAGE,
  EMPTY_ERROR_MESSAGE,
  REQUEST_FAILED_CODE,
  REQUEST_FAILED_MESSAGE,
  REQUEST_FAILED_STATUS
} from "@/constants/error";

const IPC_ERROR_PREFIX_PATTERN = /^Error invoking remote method '.*?':\s*(?:Error:\s*)?/;

export interface BridgeFailure {
  status: number;
  code: string;
  message: string;
}

export interface BridgeEnvelope<T> {
  ok?: boolean;
  data?: T;
  error?: BridgeFailure;
}

/**
 * Error raised for every failed bridge call.
 *
 * Carries the backend code so callers can show a localized hint without
 * depending on the raw (and possibly English) backend message.
 */
export class LocalApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(failure: BridgeFailure) {
    super(failure.message);
    this.name = "LocalApiError";
    this.status = failure.status;
    this.code = failure.code;
  }
}

/**
 * Remove the `Error invoking remote method '...'` wrapper from a message.
 *
 * @param message Raw error message obtained from the IPC bridge.
 * @returns The message without any Electron IPC prefix.
 */
export function stripIpcErrorPrefix(message: string): string {
  return message.replace(IPC_ERROR_PREFIX_PATTERN, "").trim();
}

/**
 * Convert any thrown value into a bridge failure payload.
 *
 * @param error Thrown value.
 * @param fallbackMessage Message used when the thrown value carries none.
 * @returns Normalized failure payload.
 */
function toFailure(error: unknown, fallbackMessage: string): BridgeFailure {
  if (error instanceof LocalApiError) {
    return { status: error.status, code: error.code, message: error.message };
  }
  const rawMessage = error instanceof Error ? error.message : "";
  const message = stripIpcErrorPrefix(rawMessage) || fallbackMessage;
  return { status: REQUEST_FAILED_STATUS, code: REQUEST_FAILED_CODE, message };
}

/**
 * Invoke one bridge call and unwrap its envelope.
 *
 * @param call Bridge call returning a result envelope; legacy throwing calls are
 *   normalized as well.
 * @param fallbackMessage Message used when the failure carries none.
 * @returns Promise resolving to the unwrapped payload.
 */
async function invoke<T>(call: () => Promise<BridgeEnvelope<T>>, fallbackMessage: string): Promise<T> {
  let envelope: BridgeEnvelope<T>;
  try {
    envelope = await call();
  } catch (error) {
    throw new LocalApiError(toFailure(error, fallbackMessage));
  }
  if (envelope?.ok === true) {
    return envelope.data as T;
  }
  const failure = envelope?.error;
  throw new LocalApiError({
    status: typeof failure?.status === "number" ? failure.status : REQUEST_FAILED_STATUS,
    code: failure?.code || REQUEST_FAILED_CODE,
    message: stripIpcErrorPrefix(failure?.message || EMPTY_ERROR_MESSAGE) || fallbackMessage
  });
}

/**
 * Perform one token-protected local API request.
 *
 * @param pathname API path beginning with `/api`.
 * @param options Standard fetch options.
 * @returns Promise resolving to the parsed response body.
 */
export function request<T>(pathname: string, options?: RequestInit): Promise<T> {
  return invoke<T>(() => window.goodfish.request<T>(pathname, options), REQUEST_FAILED_MESSAGE);
}

/**
 * Fetch one captcha challenge through the Electron bridge.
 *
 * @returns Promise resolving to the captcha identity and image.
 */
export function fetchCaptcha(): Promise<CloudCaptcha> {
  return invoke<CloudCaptcha>(() => window.goodfish.fetchCaptcha(), CLOUD_CAPTCHA_FAILED_MESSAGE);
}

/**
 * Authenticate against the management backend through the Electron bridge.
 *
 * @param payload Login form values.
 * @returns Promise resolving to the access token and paired client id.
 */
export function cloudLogin(payload: CloudLoginPayload): Promise<CloudLoginResult> {
  return invoke<CloudLoginResult>(() => window.goodfish.cloudLogin(payload), CLOUD_LOGIN_FAILED_MESSAGE);
}

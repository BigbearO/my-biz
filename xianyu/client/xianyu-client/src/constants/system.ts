export const CREDENTIAL_STATE_READY = "ready" as const;
export const CREDENTIAL_STATE_UNDECRYPTABLE = "undecryptable" as const;
export const SERVICE_STATE_READY = "ready" as const;

export const AUTH_STATE_LOGGED_IN = "logged_in" as const;
export const AUTH_STATE_NOT_LOGGED_IN = "not_logged_in" as const;
export const AUTH_STATE_EXPIRED = "expired" as const;
export const AUTH_STATE_FROZEN = "frozen" as const;
export const AUTH_STATE_UNAVAILABLE = "unavailable" as const;

export const SYSTEM_STATUS_API_PATH = "/api/system/status";
export const SYSTEM_LOGIN_API_PATH = "/api/system/login";
export const SYSTEM_LOGOUT_API_PATH = "/api/system/logout";

export type CredentialState = typeof CREDENTIAL_STATE_READY | typeof CREDENTIAL_STATE_UNDECRYPTABLE;
export type AuthState =
  | typeof AUTH_STATE_LOGGED_IN
  | typeof AUTH_STATE_NOT_LOGGED_IN
  | typeof AUTH_STATE_EXPIRED
  | typeof AUTH_STATE_FROZEN
  | typeof AUTH_STATE_UNAVAILABLE;

export const CREDENTIAL_STATE_READY = "ready" as const;
export const CREDENTIAL_STATE_UNDECRYPTABLE = "undecryptable" as const;
export const SERVICE_STATE_READY = "ready" as const;

export type CredentialState = typeof CREDENTIAL_STATE_READY | typeof CREDENTIAL_STATE_UNDECRYPTABLE;

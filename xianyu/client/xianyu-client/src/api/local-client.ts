import type { SystemStatus } from "@/types/system";
import type { AccountCreatePayload, AccountDetail, AccountRecord, AccountUpdatePayload } from "@/types/account";
import type { ItemSyncStatus } from "@/types/item";
import { request } from "@/api/bridge";
import { ACCOUNT_DETAIL_API_PATH, ACCOUNT_LIST_API_PATH, ITEM_SYNC_API_PATH } from "@/constants/account";
import {
  SYSTEM_LOGIN_API_PATH,
  SYSTEM_LOGOUT_API_PATH,
  SYSTEM_STATUS_API_PATH
} from "@/constants/system";

/**
 * Read the current local service runtime status.
 *
 * @returns Promise resolving to the local service status payload.
 */
export async function getSystemStatus(): Promise<SystemStatus> {
  return request<SystemStatus>(SYSTEM_STATUS_API_PATH);
}

/**
 * Hand a freshly issued cloud token pair over to the local service.
 *
 * @param token Cloud access token returned by the management backend.
 * @param clientid Client identifier paired with the token.
 * @returns Promise resolving to the authorization state.
 */
export async function localLogin(token: string, clientid: string): Promise<{ authState: string }> {
  return request<{ authState: string }>(SYSTEM_LOGIN_API_PATH, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ token, clientid })
  });
}

/**
 * Clear the stored token pair in the local service.
 *
 * @returns Promise resolving to the authorization state.
 */
export async function localLogout(): Promise<{ authState: string }> {
  return request<{ authState: string }>(SYSTEM_LOGOUT_API_PATH, { method: "POST" });
}

/**
 * Read local accounts without exposing stored cookies.
 *
 * @returns Promise resolving to the current account list.
 */
export async function listAccounts(): Promise<AccountRecord[]> {
  return request<AccountRecord[]>(ACCOUNT_LIST_API_PATH);
}

/**
 * Read one account including its decrypted cookie for the detail dialog.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the account detail payload.
 */
export async function getAccountDetail(accountId: number): Promise<AccountDetail> {
  return request<AccountDetail>(`${ACCOUNT_DETAIL_API_PATH}/${accountId}/detail`);
}

/**
 * Create a local account with a cookie encrypted by the Python service.
 *
 * @param payload Account creation fields including cookie plaintext.
 * @returns Promise resolving to the saved non-secret account.
 */
export async function createAccount(payload: AccountCreatePayload): Promise<AccountRecord> {
  return request<AccountRecord>(ACCOUNT_LIST_API_PATH, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/**
 * Update account profile fields and optionally replace its cookie.
 *
 * @param accountId Local account identifier.
 * @param payload Profile fields and an optional replacement cookie.
 * @returns Promise resolving to the updated non-secret account.
 */
export async function updateAccount(
  accountId: number,
  payload: AccountUpdatePayload
): Promise<AccountRecord> {
  return request<AccountRecord>(`${ACCOUNT_DETAIL_API_PATH}/${accountId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

/**
 * Delete one idle local account.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the deleted non-secret account.
 */
export async function deleteAccount(accountId: number): Promise<AccountRecord> {
  return request<AccountRecord>(`${ACCOUNT_DETAIL_API_PATH}/${accountId}`, {
    method: "DELETE"
  });
}

/**
 * Queue one product synchronisation for an account.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the initial running state.
 */
export async function startItemSync(accountId: number): Promise<ItemSyncStatus> {
  return request<ItemSyncStatus>(`${ITEM_SYNC_API_PATH}/${accountId}`, {
    method: "POST"
  });
}

/**
 * Read the latest product synchronisation state of an account.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the stored state.
 */
export async function getItemSyncStatus(accountId: number): Promise<ItemSyncStatus> {
  return request<ItemSyncStatus>(`${ITEM_SYNC_API_PATH}/${accountId}`);
}

/**
 * Manually verify one stored cookie with the Goodfish protocol.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the account after its validation state is refreshed.
 */
export async function verifyAccount(accountId: number): Promise<AccountRecord> {
  return request<AccountRecord>(`${ACCOUNT_DETAIL_API_PATH}/${accountId}/verify`, {
    method: "POST"
  });
}

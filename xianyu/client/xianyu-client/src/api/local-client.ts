import type { SystemStatus } from "@/types/system";
import type { AccountCreatePayload, AccountRecord, AccountUpdatePayload } from "@/types/account";
import { ACCOUNT_DETAIL_API_PATH, ACCOUNT_LIST_API_PATH } from "@/constants/account";

/**
 * Read the current local service runtime status.
 *
 * @returns Promise resolving to the local service status payload.
 */
export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await window.goodfish.request("/api/system/status");
  return response as SystemStatus;
}

/**
 * Read local accounts without exposing stored cookies.
 *
 * @returns Promise resolving to the current account list.
 */
export async function listAccounts(): Promise<AccountRecord[]> {
  const response = await window.goodfish.request(ACCOUNT_LIST_API_PATH);
  return response as AccountRecord[];
}

/**
 * Create a local account with a cookie encrypted by the Python service.
 *
 * @param payload Account creation fields including cookie plaintext.
 * @returns Promise resolving to the saved non-secret account.
 */
export async function createAccount(payload: AccountCreatePayload): Promise<AccountRecord> {
  const response = await window.goodfish.request(ACCOUNT_LIST_API_PATH, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return response as AccountRecord;
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
  const response = await window.goodfish.request(`${ACCOUNT_DETAIL_API_PATH}/${accountId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  return response as AccountRecord;
}

/**
 * Delete one idle local account.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the deleted non-secret account.
 */
export async function deleteAccount(accountId: number): Promise<AccountRecord> {
  const response = await window.goodfish.request(`${ACCOUNT_DETAIL_API_PATH}/${accountId}`, {
    method: "DELETE"
  });
  return response as AccountRecord;
}

/**
 * Manually verify one stored cookie with the Goodfish protocol.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the account after its validation state is refreshed.
 */
export async function verifyAccount(accountId: number): Promise<AccountRecord> {
  const response = await window.goodfish.request(`${ACCOUNT_DETAIL_API_PATH}/${accountId}/verify`, {
    method: "POST"
  });
  return response as AccountRecord;
}


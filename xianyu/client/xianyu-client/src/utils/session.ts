import { SESSION_USERNAME_STORAGE_KEY } from "@/constants/session";

const EMPTY_USERNAME = "";

/**
 * Persist the account name used for the current login session.
 *
 * @param username Account name submitted on the login page.
 * @returns Nothing.
 */
export function saveLoginUsername(username: string): void {
  try {
    window.localStorage.setItem(SESSION_USERNAME_STORAGE_KEY, username);
  } catch {
    // Storage failures only hide the account name; the session stays valid.
  }
}

/**
 * Read the account name stored by the most recent successful login.
 *
 * @returns Stored account name, or an empty string when unavailable.
 */
export function readLoginUsername(): string {
  try {
    return window.localStorage.getItem(SESSION_USERNAME_STORAGE_KEY) ?? EMPTY_USERNAME;
  } catch {
    return EMPTY_USERNAME;
  }
}

/**
 * Remove the stored account name when the session ends.
 *
 * @returns Nothing.
 */
export function clearLoginUsername(): void {
  try {
    window.localStorage.removeItem(SESSION_USERNAME_STORAGE_KEY);
  } catch {
    // Storage failures do not block logout.
  }
}

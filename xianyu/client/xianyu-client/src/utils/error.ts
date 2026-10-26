import { LocalApiError, stripIpcErrorPrefix } from "@/api/bridge";
import { EMPTY_ERROR_MESSAGE, ERROR_CODE_MESSAGES } from "@/constants/error";

/**
 * Resolve the message shown to the operator for one failure.
 *
 * Known backend codes win so the UI stays localized even when the local service
 * answers with a short English message.
 *
 * @param error Thrown value.
 * @param fallbackMessage Message used when nothing usable is available.
 * @returns Readable, prefix-free message.
 */
export function resolveErrorMessage(error: unknown, fallbackMessage: string): string {
  if (error instanceof LocalApiError) {
    const mapped = ERROR_CODE_MESSAGES[error.code];
    if (mapped) {
      return mapped;
    }
    return stripIpcErrorPrefix(error.message) || fallbackMessage;
  }
  if (error instanceof Error) {
    return stripIpcErrorPrefix(error.message) || fallbackMessage;
  }
  if (typeof error === "string") {
    return stripIpcErrorPrefix(error) || fallbackMessage;
  }
  return fallbackMessage || EMPTY_ERROR_MESSAGE;
}

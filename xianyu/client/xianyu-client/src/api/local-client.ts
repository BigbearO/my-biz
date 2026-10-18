import type { SystemStatus } from "@/types/system";

/**
 * Read the current local service runtime status.
 *
 * @returns Promise resolving to the local service status payload.
 */
export async function getSystemStatus(): Promise<SystemStatus> {
  const response = await window.goodfish.request("/api/system/status");
  return response as SystemStatus;
}


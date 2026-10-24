/**
 * Product synchronisation state reported by the local service.
 */
export interface ItemSyncStatus {
  accountId: number;
  state: string;
  total: number;
  inserted: number;
  updated: number;
  message: string;
}

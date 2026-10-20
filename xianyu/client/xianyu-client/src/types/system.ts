import type { CredentialState } from "@/constants/system";

export interface SystemStatus {
  version: string;
  serviceState: string;
  listenCount: number;
  cloudState: string;
  authState: string;
  credentialState: CredentialState;
  cookieWritable: boolean;
}


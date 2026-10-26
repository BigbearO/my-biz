/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface BridgeFailurePayload {
  status: number;
  code: string;
  message: string;
}

interface BridgeResult<T> {
  ok: boolean;
  data?: T;
  error?: BridgeFailurePayload;
}

interface CloudLoginPayload {
  username: string;
  password: string;
  code: string;
  uuid: string;
}

interface CloudLoginResult {
  token: string;
  clientid: string;
}

interface CloudCaptcha {
  uuid: string;
  img: string;
}

interface GoodfishBridge {
  getLocalServiceInfo(): { baseUrl: string };
  request<T = unknown>(pathname: string, options?: RequestInit): Promise<BridgeResult<T>>;
  fetchCaptcha(): Promise<BridgeResult<CloudCaptcha>>;
  cloudLogin(payload: CloudLoginPayload): Promise<BridgeResult<CloudLoginResult>>;
}

declare global {
  interface Window {
    goodfish: GoodfishBridge;
  }
}

export {};

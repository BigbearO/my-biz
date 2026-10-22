/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<object, object, unknown>;
  export default component;
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
  request(pathname: string, options?: RequestInit): Promise<unknown>;
  fetchCaptcha(): Promise<CloudCaptcha>;
  cloudLogin(payload: CloudLoginPayload): Promise<CloudLoginResult>;
}

declare global {
  interface Window {
    goodfish: GoodfishBridge;
  }
}

export {};


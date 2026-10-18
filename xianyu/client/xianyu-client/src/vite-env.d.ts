/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";

  const component: DefineComponent<object, object, unknown>;
  export default component;
}

interface GoodfishBridge {
  getLocalServiceInfo(): { baseUrl: string };
  request(pathname: string, options?: RequestInit): Promise<unknown>;
}

declare global {
  interface Window {
    goodfish: GoodfishBridge;
  }
}

export {};


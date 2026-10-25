import { createRouter, createWebHashHistory } from "vue-router";

import { getSystemStatus } from "@/api/local-client";
import {
  ROUTE_PATH_ACCOUNTS,
  ROUTE_PATH_FROZEN,
  ROUTE_PATH_LOGIN,
  ROUTE_PATH_SETTINGS
} from "@/constants/layout";
import {
  AUTH_STATE_EXPIRED,
  AUTH_STATE_FROZEN,
  AUTH_STATE_LOGGED_IN,
  AUTH_STATE_UNAVAILABLE
} from "@/constants/system";
import MainLayout from "@/layout/MainLayout.vue";
import AccountList from "@/views/AccountList.vue";
import Frozen from "@/views/Frozen.vue";
import Login from "@/views/Login.vue";
import Settings from "@/views/Settings.vue";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: "/",
      component: MainLayout,
      redirect: ROUTE_PATH_ACCOUNTS,
      children: [
        {
          path: "accounts",
          name: "account-list",
          component: AccountList
        },
        {
          path: "settings",
          name: "settings",
          component: Settings
        }
      ]
    },
    {
      path: ROUTE_PATH_LOGIN,
      name: "login",
      component: Login
    },
    {
      path: ROUTE_PATH_FROZEN,
      name: "frozen",
      component: Frozen
    }
  ]
});

const FROZEN_AUTH_STATES: string[] = [AUTH_STATE_FROZEN, AUTH_STATE_EXPIRED, AUTH_STATE_UNAVAILABLE];
const PUBLIC_PATHS: string[] = [ROUTE_PATH_LOGIN, ROUTE_PATH_FROZEN];

router.beforeEach(async (to) => {
  if (PUBLIC_PATHS.includes(to.path)) {
    return true;
  }
  try {
    const status = await getSystemStatus();
    if (FROZEN_AUTH_STATES.includes(status.authState)) {
      return ROUTE_PATH_FROZEN;
    }
    if (status.authState !== AUTH_STATE_LOGGED_IN) {
      return ROUTE_PATH_LOGIN;
    }
    return true;
  } catch {
    return ROUTE_PATH_LOGIN;
  }
});

export default router;

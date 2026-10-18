import { createRouter, createWebHashHistory } from "vue-router";

import AccountList from "@/views/AccountList.vue";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: "/",
      name: "account-list",
      component: AccountList
    }
  ]
});

export default router;


<script setup lang="ts">
import { ElMessage, ElMessageBox } from "element-plus";
import { Cloud, LogOut, MonitorCog, Settings as SettingsIcon, Users } from "lucide-vue-next";
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

import { localLogout } from "@/api/local-client";
import {
  LAYOUT_APP_TITLE,
  LAYOUT_LOGOUT_BUTTON,
  LAYOUT_LOGOUT_CONFIRM_MESSAGE,
  LAYOUT_MENU_ACCOUNT,
  LAYOUT_MENU_CLOUD,
  LAYOUT_MENU_SETTINGS,
  ROUTE_PATH_ACCOUNTS,
  ROUTE_PATH_CLOUD,
  ROUTE_PATH_LOGIN,
  ROUTE_PATH_SETTINGS
} from "@/constants/layout";

const route = useRoute();
const router = useRouter();

const activeMenu = computed(() => route.path);

/**
 * Navigate to one section of the main layout.
 *
 * @param path Target route path.
 * @returns Promise resolved after navigation.
 */
async function navigate(path: string): Promise<void> {
  if (route.path === path) {
    return;
  }
  await router.replace(path);
}

/**
 * Clear the stored cloud session and return to the login page.
 *
 * @returns Promise resolved after logout completes.
 */
async function handleLogout(): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    LAYOUT_LOGOUT_CONFIRM_MESSAGE,
    LAYOUT_LOGOUT_BUTTON,
    { type: "warning" }
  ).then(() => true).catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await localLogout();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : LAYOUT_LOGOUT_BUTTON);
  }
  await router.replace(ROUTE_PATH_LOGIN);
}
</script>

<template>
  <div class="layout-shell">
    <aside class="layout-sidebar">
      <div class="layout-brand">
        <MonitorCog :size="22" />
        <span>{{ LAYOUT_APP_TITLE }}</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="layout-menu"
        background-color="transparent"
        text-color="#4b5b6b"
        active-text-color="#1f6feb"
        @select="navigate"
      >
        <el-menu-item :index="ROUTE_PATH_ACCOUNTS">
          <Users :size="18" />
          <span>{{ LAYOUT_MENU_ACCOUNT }}</span>
        </el-menu-item>
        <el-menu-item :index="ROUTE_PATH_CLOUD">
          <Cloud :size="18" />
          <span>{{ LAYOUT_MENU_CLOUD }}</span>
        </el-menu-item>
        <el-menu-item :index="ROUTE_PATH_SETTINGS">
          <SettingsIcon :size="18" />
          <span>{{ LAYOUT_MENU_SETTINGS }}</span>
        </el-menu-item>
      </el-menu>
      <div class="layout-footer">
        <el-button link :icon="LogOut" @click="handleLogout">
          {{ LAYOUT_LOGOUT_BUTTON }}
        </el-button>
      </div>
    </aside>
    <section class="layout-content">
      <RouterView />
    </section>
  </div>
</template>

<style scoped>
.layout-shell {
  display: flex;
  min-height: 100vh;
}

.layout-sidebar {
  display: flex;
  flex-direction: column;
  width: 200px;
  flex-shrink: 0;
  background: #ffffff;
  border-right: 1px solid #e2e8ef;
}

.layout-brand {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 20px 18px;
  color: #1f6feb;
  font-size: 17px;
  font-weight: 600;
}

.layout-menu {
  flex: 1;
  border-right: none;
}

.layout-footer {
  padding: 12px 18px 18px;
}

.layout-content {
  flex: 1;
  min-width: 0;
  background: #eef1f4;
}
</style>

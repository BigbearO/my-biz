<script setup lang="ts">
import { onMounted, ref } from "vue";

import { getSystemStatus } from "@/api/local-client";
import {
  LOGIN_EXIT_BUTTON,
  LOGIN_FROZEN_MESSAGE,
  LOGIN_FROZEN_TITLE,
  LOGIN_UNAVAILABLE_MESSAGE,
  LOGIN_UNAVAILABLE_TITLE
} from "@/constants/login";
import { AUTH_STATE_UNAVAILABLE } from "@/constants/system";

const title = ref(LOGIN_FROZEN_TITLE);
const message = ref(LOGIN_FROZEN_MESSAGE);

/**
 * Resolve the freeze reason from the latest local status.
 *
 * @returns Promise resolved after the status request finishes.
 */
async function resolveReason(): Promise<void> {
  try {
    const status = await getSystemStatus();
    if (status.authState === AUTH_STATE_UNAVAILABLE) {
      title.value = LOGIN_UNAVAILABLE_TITLE;
      message.value = LOGIN_UNAVAILABLE_MESSAGE;
    }
  } catch {
    title.value = LOGIN_UNAVAILABLE_TITLE;
    message.value = LOGIN_UNAVAILABLE_MESSAGE;
  }
}

/**
 * Close the window, which terminates the desktop application.
 */
function exitApplication(): void {
  window.close();
}

onMounted(() => {
  void resolveReason();
});
</script>

<template>
  <main class="frozen-page">
    <section class="frozen-card">
      <h1>{{ title }}</h1>
      <p>{{ message }}</p>
      <el-button type="danger" @click="exitApplication">{{ LOGIN_EXIT_BUTTON }}</el-button>
    </section>
  </main>
</template>

<style scoped>
.frozen-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: #f2f4f7;
}

.frozen-card {
  width: 420px;
  padding: 32px;
  text-align: center;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgb(120 20 20 / 10%);
}

.frozen-card h1 {
  margin: 0 0 12px;
  color: #b3261e;
  font-size: 20px;
}

.frozen-card p {
  margin: 0 0 24px;
  color: #5c6b7a;
  font-size: 14px;
  line-height: 1.6;
}
</style>

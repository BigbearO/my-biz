<script setup lang="ts">
import { Plus, RefreshCw } from "lucide-vue-next";
import { computed, onBeforeUnmount, onMounted, ref } from "vue";

import { getSystemStatus } from "@/api/local-client";
import { CREDENTIAL_STATE_READY } from "@/constants/system";
import type { SystemStatus } from "@/types/system";

const status = ref<SystemStatus | null>(null);
const statusError = ref("");
const loading = ref(false);
let statusTimer: number | undefined;

const serviceLabel = computed(() => {
  if (statusError.value) {
    return "不可用";
  }
  return status.value?.serviceState === "ready" ? "运行中" : "连接中";
});

const serviceVariant = computed(() => (statusError.value || status.value?.serviceState !== "ready" ? "warning" : "success"));

const credentialLabel = computed(() => (
  status.value?.credentialState === CREDENTIAL_STATE_READY ? "凭据可用" : "凭据待恢复"
));

const credentialVariant = computed(() => (
  status.value?.credentialState === CREDENTIAL_STATE_READY ? "success" : "warning"
));

/**
 * Refresh local service status and retain the latest error message.
 *
 * @returns Promise that resolves after the request finishes.
 */
async function refreshStatus(): Promise<void> {
  loading.value = true;
  try {
    status.value = await getSystemStatus();
    statusError.value = "";
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : "状态获取失败";
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void refreshStatus();
  statusTimer = window.setInterval(() => {
    void refreshStatus();
  }, 5000);
});

onBeforeUnmount(() => {
  if (statusTimer !== undefined) {
    window.clearInterval(statusTimer);
  }
});
</script>

<template>
  <main class="account-page">
    <header class="page-header">
      <div>
        <h1>账号管理</h1>
        <p class="service-summary">
          <el-tag :type="serviceVariant" effect="light">{{ serviceLabel }}</el-tag>
          <el-tag :type="credentialVariant" effect="light">{{ credentialLabel }}</el-tag>
          <span v-if="status">版本 {{ status.version }}</span>
          <span v-if="status">监听 {{ status.listenCount }}</span>
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="RefreshCw" :loading="loading" @click="refreshStatus">刷新</el-button>
        <el-button type="primary" :icon="Plus" disabled>添加账号</el-button>
      </div>
    </header>

    <el-alert v-if="statusError" class="service-error" type="warning" :title="statusError" show-icon :closable="false" />

    <section class="table-shell">
      <el-table :data="[]" height="480">
        <el-table-column prop="goodfishId" label="闲鱼 ID" min-width="140" />
        <el-table-column prop="nickname" label="闲鱼昵称" min-width="160" />
        <el-table-column prop="isValid" label="有效性" width="100" />
        <el-table-column prop="listenState" label="监听状态" width="120" />
        <el-table-column prop="remark" label="备注" min-width="160" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="220" fixed="right" />
      </el-table>
    </section>
  </main>
</template>

<style scoped>
.account-page {
  min-height: 100vh;
  padding: 24px 28px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 18px;
}

h1 {
  margin: 0 0 8px;
  font-size: 24px;
  line-height: 1.3;
}

.service-summary {
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 0;
  color: #657585;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.service-error {
  margin-bottom: 12px;
}

.table-shell {
  padding: 8px 0 0;
  background: #ffffff;
  border: 1px solid #d9e0e6;
  border-radius: 6px;
}
</style>

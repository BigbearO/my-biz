<script setup lang="ts">
import { SETTINGS_PLACEHOLDER_MESSAGE, SETTINGS_PLACEHOLDER_TITLE } from "@/constants/layout";
</script>

<template>
  <main class="placeholder-page">
    <el-empty :description="SETTINGS_PLACEHOLDER_MESSAGE">
      <template #image>
        <span class="placeholder-title">{{ SETTINGS_PLACEHOLDER_TITLE }}</span>
      </template>
    </el-empty>
  </main>
</template>

<style scoped>
.placeholder-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.placeholder-title {
  color: #5c6b7a;
  font-size: 18px;
  font-weight: 600;
}
</style>

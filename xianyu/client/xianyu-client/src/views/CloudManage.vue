<script setup lang="ts">
import { CLOUD_PLACEHOLDER_MESSAGE, CLOUD_PLACEHOLDER_TITLE } from "@/constants/layout";
</script>

<template>
  <main class="placeholder-page">
    <el-empty :description="CLOUD_PLACEHOLDER_MESSAGE">
      <template #image>
        <span class="placeholder-title">{{ CLOUD_PLACEHOLDER_TITLE }}</span>
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

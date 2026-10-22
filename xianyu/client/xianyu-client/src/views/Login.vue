<script setup lang="ts">
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import { localLogin } from "@/api/local-client";
import {
  LOGIN_CAPTCHA_EMPTY_MESSAGE,
  LOGIN_CAPTCHA_LABEL,
  LOGIN_CAPTCHA_PLACEHOLDER,
  LOGIN_CAPTCHA_REFRESH_HINT,
  LOGIN_CAPTCHA_REQUIRED_MESSAGE,
  LOGIN_FAILED_MESSAGE,
  LOGIN_FORM_WIDTH,
  LOGIN_HANDOVER_FAILED_MESSAGE,
  LOGIN_PAGE_SUBTITLE,
  LOGIN_PAGE_TITLE,
  LOGIN_PASSWORD_LABEL,
  LOGIN_PASSWORD_REQUIRED_MESSAGE,
  LOGIN_SUBMIT_BUTTON,
  LOGIN_SUBMITTING_BUTTON,
  LOGIN_SUCCESS_MESSAGE,
  LOGIN_UNAVAILABLE_MESSAGE,
  LOGIN_USERNAME_LABEL,
  LOGIN_USERNAME_REQUIRED_MESSAGE
} from "@/constants/login";
import { ROUTE_PATH_ACCOUNTS } from "@/constants/layout";
import { AUTH_STATE_LOGGED_IN, AUTH_STATE_UNAVAILABLE } from "@/constants/system";

interface LoginForm {
  username: string;
  password: string;
  code: string;
}

const router = useRouter();
const formRef = ref<FormInstance>();
const form = reactive<LoginForm>({
  username: "",
  password: "",
  code: ""
});
const captchaImage = ref("");
const captchaUuid = ref("");
const captchaLoading = ref(false);
const submitting = ref(false);

const rules: FormRules<LoginForm> = {
  username: [{ required: true, message: LOGIN_USERNAME_REQUIRED_MESSAGE, trigger: "blur" }],
  password: [{ required: true, message: LOGIN_PASSWORD_REQUIRED_MESSAGE, trigger: "blur" }],
  code: [{ required: true, message: LOGIN_CAPTCHA_REQUIRED_MESSAGE, trigger: "blur" }]
};

/**
 * Load one captcha challenge and replace the rendered image.
 *
 * @returns Promise resolved after the challenge is refreshed.
 */
async function loadCaptcha(): Promise<void> {
  captchaLoading.value = true;
  try {
    const captcha = await window.goodfish.fetchCaptcha();
    captchaUuid.value = captcha.uuid;
    captchaImage.value = captcha.img;
    form.code = "";
  } catch (error) {
    captchaImage.value = "";
    captchaUuid.value = "";
    ElMessage.error(error instanceof Error ? error.message : LOGIN_CAPTCHA_EMPTY_MESSAGE);
  } finally {
    captchaLoading.value = false;
  }
}

/**
 * Submit credentials, hand the token pair to the local service, then enter the app.
 *
 * @returns Promise resolved after navigation or failure handling.
 */
async function submitLogin(): Promise<void> {
  const activeForm = formRef.value;
  if (!activeForm || submitting.value) {
    return;
  }
  const valid = await activeForm.validate().then(() => true).catch(() => false);
  if (!valid) {
    return;
  }
  if (!captchaUuid.value) {
    ElMessage.error(LOGIN_CAPTCHA_EMPTY_MESSAGE);
    await loadCaptcha();
    return;
  }
  submitting.value = true;
  try {
    const credentials = await window.goodfish.cloudLogin({
      username: form.username.trim(),
      password: form.password,
      code: form.code.trim(),
      uuid: captchaUuid.value
    });
    try {
      const result = await localLogin(credentials.token, credentials.clientid);
      if (result.authState === AUTH_STATE_UNAVAILABLE) {
        throw new Error(LOGIN_UNAVAILABLE_MESSAGE);
      }
      if (result.authState !== AUTH_STATE_LOGGED_IN) {
        throw new Error(LOGIN_FAILED_MESSAGE);
      }
    } catch (error) {
      throw new Error(error instanceof Error ? error.message : LOGIN_HANDOVER_FAILED_MESSAGE);
    }
    ElMessage.success(LOGIN_SUCCESS_MESSAGE);
    await router.replace(ROUTE_PATH_ACCOUNTS);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : LOGIN_FAILED_MESSAGE);
    await loadCaptcha();
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  void loadCaptcha();
});
</script>

<template>
  <main class="login-page">
    <section class="login-card" :style="{ width: LOGIN_FORM_WIDTH }">
      <header class="login-header">
        <h1>{{ LOGIN_PAGE_TITLE }}</h1>
        <p>{{ LOGIN_PAGE_SUBTITLE }}</p>
      </header>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item :label="LOGIN_USERNAME_LABEL" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item :label="LOGIN_PASSWORD_LABEL" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            @keyup.enter="submitLogin"
          />
        </el-form-item>
        <el-form-item :label="LOGIN_CAPTCHA_LABEL" prop="code">
          <div class="captcha-row">
            <el-input
              v-model="form.code"
              :placeholder="LOGIN_CAPTCHA_PLACEHOLDER"
              @keyup.enter="submitLogin"
            />
            <button
              type="button"
              class="captcha-image-button"
              :title="LOGIN_CAPTCHA_REFRESH_HINT"
              :disabled="captchaLoading"
              @click="loadCaptcha"
            >
              <img v-if="captchaImage" :src="captchaImage" alt="captcha" />
              <span v-else class="captcha-placeholder">{{ LOGIN_CAPTCHA_EMPTY_MESSAGE }}</span>
            </button>
          </div>
        </el-form-item>
        <el-button
          type="primary"
          class="login-submit"
          :loading="submitting"
          @click="submitLogin"
        >
          {{ submitting ? LOGIN_SUBMITTING_BUTTON : LOGIN_SUBMIT_BUTTON }}
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #e8eef6 0%, #f6f8fb 100%);
}

.login-card {
  padding: 32px 28px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgb(31 111 235 / 12%);
}

.login-header {
  margin-bottom: 20px;
}

.login-header h1 {
  margin: 0 0 6px;
  font-size: 22px;
}

.login-header p {
  margin: 0;
  color: #6b7c8d;
  font-size: 13px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
  align-items: center;
}

.captcha-image-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 120px;
  height: 32px;
  padding: 0;
  overflow: hidden;
  background: #f4f7fb;
  border: 1px solid #dbe3ec;
  border-radius: 4px;
  cursor: pointer;
}

.captcha-image-button img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-placeholder {
  color: #8a99a8;
  font-size: 11px;
}

.login-submit {
  width: 100%;
  margin-top: 8px;
}
</style>

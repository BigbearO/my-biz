<script setup lang="ts">
import { Eye, Pencil, Plus, Radio, RefreshCw, ShieldCheck, Trash2 } from "lucide-vue-next";
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from "element-plus";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";

import {
  createAccount,
  deleteAccount,
  getAccountDetail,
  getItemSyncStatus,
  getSystemStatus,
  listAccounts,
  startItemSync,
  updateAccount,
  verifyAccount
} from "@/api/local-client";
import {
  ACCOUNT_ADD_BUTTON,
  ACCOUNT_ACTION_LABEL,
  ACCOUNT_ACTION_COLUMN_WIDTH,
  ACCOUNT_CANCEL_BUTTON,
  ACCOUNT_CREDENTIAL_READY_LABEL,
  ACCOUNT_CREDENTIAL_RECOVERY_LABEL,
  ACCOUNT_CREDENTIAL_REENTRY_LABEL,
  ACCOUNT_COOKIE_DETAIL_HINT,
  ACCOUNT_COOKIE_EDIT_HINT,
  ACCOUNT_COOKIE_HIDE_BUTTON,
  ACCOUNT_COOKIE_LABEL,
  ACCOUNT_COOKIE_MASK,
  ACCOUNT_COOKIE_MAX_LENGTH,
  ACCOUNT_COOKIE_REQUIRED_MESSAGE,
  ACCOUNT_COOKIE_SHOW_BUTTON,
  ACCOUNT_CREATE_DIALOG_TITLE,
  ACCOUNT_DETAIL_BUTTON,
  ACCOUNT_DETAIL_CLOSE_BUTTON,
  ACCOUNT_DETAIL_DIALOG_TITLE,
  ACCOUNT_DETAIL_LOAD_ERROR_MESSAGE,
  ACCOUNT_EMPTY_TEXT,
  ACCOUNT_LISTEN_BUTTON,
  ACCOUNT_LISTEN_UNSUPPORTED_MESSAGE,
  ACCOUNT_SYNC_BUTTON,
  ACCOUNT_CREATED_AT_LABEL,
  ACCOUNT_DELETE_BUTTON,
  ACCOUNT_DELETE_CONFIRM_BUTTON,
  ACCOUNT_DELETE_CONFIRM_MESSAGE,
  ACCOUNT_DELETE_CONFIRM_TITLE,
  ACCOUNT_DELETE_ERROR_MESSAGE,
  ACCOUNT_DELETE_SUCCESS_MESSAGE,
  ACCOUNT_DIALOG_WIDTH,
  ACCOUNT_EDIT_BUTTON,
  ACCOUNT_EDIT_DIALOG_TITLE,
  ACCOUNT_EMPTY_DATE_TEXT,
  ACCOUNT_GOODFISH_ID_LABEL,
  ACCOUNT_IDLE_LISTEN_LABEL,
  ACCOUNT_INVALID_LABEL,
  ACCOUNT_LISTEN_COUNT_LABEL,
  ACCOUNT_LISTEN_STATE_IDLE,
  ACCOUNT_LISTEN_STATE_LABEL,
  ACCOUNT_LOAD_ERROR_MESSAGE,
  ACCOUNT_MAX_LENGTH_MESSAGE,
  ACCOUNT_NICKNAME_LABEL,
  ACCOUNT_NICKNAME_MAX_LENGTH,
  ACCOUNT_NICKNAME_REQUIRED_MESSAGE,
  ACCOUNT_LAST_CHECK_AT_LABEL,
  ACCOUNT_PAGE_TITLE,
  ACCOUNT_REFRESH_BUTTON,
  ACCOUNT_REMARK_LABEL,
  ACCOUNT_REMARK_MAX_LENGTH,
  ACCOUNT_SAVE_BUTTON,
  ACCOUNT_SAVE_ERROR_MESSAGE,
  ACCOUNT_SAVE_SUCCESS_MESSAGE,
  ACCOUNT_SERVICE_CONNECTING_LABEL,
  ACCOUNT_SERVICE_RUNNING_LABEL,
  ACCOUNT_SERVICE_UNAVAILABLE_LABEL,
  ACCOUNT_STATUS_ERROR_MESSAGE,
  ACCOUNT_TABLE_HEIGHT,
  ACCOUNT_VALIDITY_LABEL,
  ACCOUNT_VALID_LABEL,
  ACCOUNT_VERIFY_BUTTON,
  ACCOUNT_VERIFY_ERROR_MESSAGE,
  ACCOUNT_VERIFY_SUCCESS_MESSAGE,
  ACCOUNT_VERSION_LABEL,
  ITEM_SYNC_POLL_INTERVAL_MS,
  ITEM_SYNC_POLL_MAX_ATTEMPTS,
  ITEM_SYNC_PROGRESS_MESSAGE,
  ITEM_SYNC_START_ERROR_MESSAGE,
  ITEM_SYNC_STATE_FAILED,
  ITEM_SYNC_STATE_RUNNING,
  ITEM_SYNC_TIMEOUT_MESSAGE,
  STATUS_POLL_INTERVAL_MS
} from "@/constants/account";
import { ROUTE_PATH_FROZEN, ROUTE_PATH_LOGIN } from "@/constants/layout";
import {
  AUTH_STATE_EXPIRED,
  AUTH_STATE_FROZEN,
  AUTH_STATE_LOGGED_IN,
  AUTH_STATE_UNAVAILABLE,
  CREDENTIAL_STATE_READY,
  SERVICE_STATE_READY
} from "@/constants/system";
import type { AccountDetail, AccountRecord } from "@/types/account";
import type { ItemSyncStatus } from "@/types/item";
import type { SystemStatus } from "@/types/system";

interface AccountForm {
  nickname: string;
  cookie: string;
  remark: string;
}

const status = ref<SystemStatus | null>(null);
const statusError = ref("");
const accounts = ref<AccountRecord[]>([]);
const accountsError = ref("");
const loading = ref(false);
const accountsLoading = ref(false);
const submitting = ref(false);
const verifyingAccountIds = ref<number[]>([]);
const dialogVisible = ref(false);
const editingAccountId = ref<number | null>(null);
const accountFormRef = ref<FormInstance>();
const detailVisible = ref(false);
const detailLoading = ref(false);
const detailAccount = ref<AccountDetail | null>(null);
const cookieVisible = ref(false);
const syncingAccountId = ref<number | null>(null);
const router = useRouter();
let statusTimer: number | undefined;

const accountForm = reactive<AccountForm>({
  nickname: "",
  cookie: "",
  remark: ""
});

const baseAccountRules: FormRules<AccountForm> = {
  remark: [
    { max: ACCOUNT_REMARK_MAX_LENGTH, message: ACCOUNT_MAX_LENGTH_MESSAGE, trigger: "blur" }
  ]
};

const nicknameRules = [
  { required: true, message: ACCOUNT_NICKNAME_REQUIRED_MESSAGE, trigger: "blur" },
  { max: ACCOUNT_NICKNAME_MAX_LENGTH, message: ACCOUNT_MAX_LENGTH_MESSAGE, trigger: "blur" }
];

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false
});

const cookieWritable = computed(() => status.value?.cookieWritable === true);

const serviceLabel = computed(() => {
  if (statusError.value) {
    return ACCOUNT_SERVICE_UNAVAILABLE_LABEL;
  }
  return status.value?.serviceState === SERVICE_STATE_READY
    ? ACCOUNT_SERVICE_RUNNING_LABEL
    : ACCOUNT_SERVICE_CONNECTING_LABEL;
});

const serviceVariant = computed(() => (
  statusError.value || status.value?.serviceState !== SERVICE_STATE_READY ? "warning" : "success"
));

const credentialLabel = computed(() => {
  if (status.value?.credentialState === CREDENTIAL_STATE_READY) {
    return ACCOUNT_CREDENTIAL_READY_LABEL;
  }
  return cookieWritable.value
    ? ACCOUNT_CREDENTIAL_REENTRY_LABEL
    : ACCOUNT_CREDENTIAL_RECOVERY_LABEL;
});

const credentialVariant = computed(() => (
  status.value?.credentialState === CREDENTIAL_STATE_READY ? "success" : "warning"
));

const dialogTitle = computed(() => (
  editingAccountId.value === null ? ACCOUNT_CREATE_DIALOG_TITLE : ACCOUNT_EDIT_DIALOG_TITLE
));

const formRules = computed<FormRules<AccountForm>>(() => ({
  ...baseAccountRules,
  ...(editingAccountId.value === null ? {} : { nickname: nicknameRules }),
  cookie: editingAccountId.value === null
    ? [
        { required: true, message: ACCOUNT_COOKIE_REQUIRED_MESSAGE, trigger: "blur" },
        { max: ACCOUNT_COOKIE_MAX_LENGTH, message: ACCOUNT_MAX_LENGTH_MESSAGE, trigger: "blur" }
      ]
    : [
        { max: ACCOUNT_COOKIE_MAX_LENGTH, message: ACCOUNT_MAX_LENGTH_MESSAGE, trigger: "blur" }
      ]
}));

/**
 * Refresh local service status and retain the latest safe error message.
 *
 * @returns Promise that resolves after the request finishes.
 */
async function refreshStatus(): Promise<void> {
  loading.value = true;
  try {
    status.value = await getSystemStatus();
    statusError.value = "";
    await redirectByAuthState(status.value.authState);
  } catch (error) {
    statusError.value = error instanceof Error ? error.message : ACCOUNT_STATUS_ERROR_MESSAGE;
  } finally {
    loading.value = false;
  }
}

const FROZEN_AUTH_STATES: string[] = [AUTH_STATE_FROZEN, AUTH_STATE_EXPIRED, AUTH_STATE_UNAVAILABLE];

/**
 * Redirect to the login or freeze page when the session is no longer usable.
 *
 * @param authState Authorization state returned by the local service.
 * @returns Promise resolved after navigation settles.
 */
async function redirectByAuthState(authState: string): Promise<void> {
  if (FROZEN_AUTH_STATES.includes(authState)) {
    await router.replace(ROUTE_PATH_FROZEN);
    return;
  }
  if (authState !== AUTH_STATE_LOGGED_IN) {
    await router.replace(ROUTE_PATH_LOGIN);
  }
}

/**
 * Refresh the local account list without loading cookie material.
 *
 * @returns Promise that resolves after the request finishes.
 */
async function refreshAccounts(): Promise<void> {
  accountsLoading.value = true;
  try {
    accounts.value = await listAccounts();
    accountsError.value = "";
  } catch (error) {
    accountsError.value = error instanceof Error ? error.message : ACCOUNT_LOAD_ERROR_MESSAGE;
  } finally {
    accountsLoading.value = false;
  }
}

/**
 * Refresh service status and accounts for user-triggered reloads.
 *
 * @returns Promise that resolves after both requests finish.
 */
async function refreshAll(): Promise<void> {
  await Promise.all([refreshStatus(), refreshAccounts()]);
}

/**
 * Format one API timestamp for the local account table.
 *
 * @param value ISO timestamp returned by the local API.
 * @returns Formatted date, or a placeholder for invalid values.
 */
function formatDateTime(value: string): string {
  const parsedDate = new Date(value);
  if (Number.isNaN(parsedDate.getTime())) {
    return ACCOUNT_EMPTY_DATE_TEXT;
  }
  return dateFormatter.format(parsedDate);
}

/**
 * Translate stored account state into a stable table label.
 *
 * @param account Account row currently being rendered.
 * @returns Human-readable listening-state label.
 */
function listenStateLabel(account: AccountRecord): string {
  return account.listenState === ACCOUNT_LISTEN_STATE_IDLE
    ? ACCOUNT_IDLE_LISTEN_LABEL
    : account.listenState;
}

/**
 * Report whether one account is currently being verified.
 *
 * @param account Account row rendered in the table.
 * @returns True while its manual verification request is in flight.
 */
function isVerifying(account: AccountRecord): boolean {
  return verifyingAccountIds.value.includes(account.id);
}

/**
 * Reset dialog values and validation state for another account operation.
 *
 * @returns Promise that resolves after validation messages are cleared.
 */
async function resetAccountForm(): Promise<void> {
  accountForm.nickname = "";
  accountForm.cookie = "";
  accountForm.remark = "";
  await nextTick();
  accountFormRef.value?.clearValidate();
}

/**
 * Open the create dialog when cookie encryption is available.
 *
 * @returns Promise that resolves after the dialog is ready.
 */
async function openCreateDialog(): Promise<void> {
  if (!cookieWritable.value) {
    return;
  }
  editingAccountId.value = null;
  await resetAccountForm();
  dialogVisible.value = true;
}

/**
 * Open the edit dialog with non-secret account fields only.
 *
 * @param account Account row selected by the operator.
 * @returns Promise that resolves after the dialog is ready.
 */
async function openEditDialog(account: AccountRecord): Promise<void> {
  editingAccountId.value = account.id;
  accountForm.nickname = account.nickname;
  accountForm.cookie = "";
  accountForm.remark = account.remark;
  await nextTick();
  accountFormRef.value?.clearValidate();
  dialogVisible.value = true;
}

/**
 * Close the dialog and clear temporary account values.
 */
function closeDialog(): void {
  dialogVisible.value = false;
  void resetAccountForm();
}

/**
 * Submit the create or edit form through the token-protected local API.
 *
 * @returns Promise that resolves after the operation and list refresh finish.
 */
async function submitAccountForm(): Promise<void> {
  const form = accountFormRef.value;
  if (!form || submitting.value) {
    return;
  }
  const isValid = await form.validate().then(() => true).catch(() => false);
  if (!isValid) {
    return;
  }
  submitting.value = true;
  try {
    if (editingAccountId.value === null) {
      await createAccount({
        cookie: accountForm.cookie.trim(),
        remark: accountForm.remark.trim()
      });
    } else {
      await updateAccount(editingAccountId.value, {
        nickname: accountForm.nickname.trim(),
        remark: accountForm.remark.trim(),
        ...(accountForm.cookie.trim() ? { cookie: accountForm.cookie.trim() } : {})
      });
    }
    ElMessage.success(ACCOUNT_SAVE_SUCCESS_MESSAGE);
    dialogVisible.value = false;
    await resetAccountForm();
    await refreshAll();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : ACCOUNT_SAVE_ERROR_MESSAGE);
  } finally {
    submitting.value = false;
  }
}

/**
 * Manually verify one stored cookie and refresh the visible validation state.
 *
 * @param account Account row selected by the operator.
 * @returns Promise that resolves after the account list is refreshed.
 */
async function checkAccount(account: AccountRecord): Promise<void> {
  if (!cookieWritable.value || isVerifying(account)) {
    return;
  }
  verifyingAccountIds.value = [...verifyingAccountIds.value, account.id];
  try {
    const updatedAccount = await verifyAccount(account.id);
    accounts.value = accounts.value.map(item => item.id === updatedAccount.id ? updatedAccount : item);
    ElMessage.success(ACCOUNT_VERIFY_SUCCESS_MESSAGE);
    await refreshStatus();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : ACCOUNT_VERIFY_ERROR_MESSAGE);
    await refreshAccounts();
  } finally {
    verifyingAccountIds.value = verifyingAccountIds.value.filter(id => id !== account.id);
  }
}

/**
 * Confirm and delete one idle account.
 *
 * @param account Account row selected by the operator.
 * @returns Promise that resolves after deletion and refresh finish.
 */
async function removeAccount(account: AccountRecord): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    ACCOUNT_DELETE_CONFIRM_MESSAGE,
    ACCOUNT_DELETE_CONFIRM_TITLE,
    {
      confirmButtonText: ACCOUNT_DELETE_CONFIRM_BUTTON,
      cancelButtonText: ACCOUNT_CANCEL_BUTTON,
      type: "warning"
    }
  ).then(() => true).catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await deleteAccount(account.id);
    ElMessage.success(ACCOUNT_DELETE_SUCCESS_MESSAGE);
    await refreshAll();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : ACCOUNT_DELETE_ERROR_MESSAGE);
  }
}

/**
 * Load one account detail and open the cookie dialog in masked state.
 *
 * @param account Account row selected by the operator.
 * @returns Promise resolved after the detail request finishes.
 */
async function openDetailDialog(account: AccountRecord): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  detailAccount.value = null;
  cookieVisible.value = false;
  try {
    detailAccount.value = await getAccountDetail(account.id);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : ACCOUNT_DETAIL_LOAD_ERROR_MESSAGE);
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}

/**
 * Toggle between the masked and the full cookie value in the detail dialog.
 */
function toggleCookieVisibility(): void {
  cookieVisible.value = !cookieVisible.value;
}

/**
 * Close the detail dialog and drop the decrypted cookie from memory.
 */
function closeDetailDialog(): void {
  detailVisible.value = false;
  detailAccount.value = null;
  cookieVisible.value = false;
}

/**
 * Explain that listening is not part of this release.
 */
function handleListenUnsupported(): void {
  ElMessage.info(ACCOUNT_LISTEN_UNSUPPORTED_MESSAGE);
}

/**
 * Wait before the next synchronisation status poll.
 *
 * @param milliseconds Delay in milliseconds.
 * @returns Promise resolved after the delay.
 */
function delay(milliseconds: number): Promise<void> {
  return new Promise(resolve => {
    window.setTimeout(resolve, milliseconds);
  });
}

/**
 * Poll the local service until the synchronisation leaves the running state.
 *
 * @param accountId Local account identifier.
 * @returns Promise resolving to the final synchronisation state.
 */
async function pollItemSync(accountId: number): Promise<ItemSyncStatus> {
  for (let attempt = 0; attempt < ITEM_SYNC_POLL_MAX_ATTEMPTS; attempt += 1) {
    await delay(ITEM_SYNC_POLL_INTERVAL_MS);
    const currentSyncStatus = await getItemSyncStatus(accountId);
    if (currentSyncStatus.state !== ITEM_SYNC_STATE_RUNNING) {
      return currentSyncStatus;
    }
  }
  throw new Error(ITEM_SYNC_TIMEOUT_MESSAGE);
}

/**
 * Queue one product synchronisation and report its outcome to the operator.
 *
 * @returns Promise resolved after the synchronisation and list refresh finish.
 */
async function handleSyncItems(): Promise<void> {
  const account = detailAccount.value;
  if (!account || syncingAccountId.value !== null) {
    return;
  }
  syncingAccountId.value = account.id;
  try {
    await startItemSync(account.id);
    ElMessage.info(ITEM_SYNC_PROGRESS_MESSAGE);
    const result = await pollItemSync(account.id);
    if (result.state === ITEM_SYNC_STATE_FAILED) {
      ElMessage.error(result.message || ITEM_SYNC_START_ERROR_MESSAGE);
    } else {
      ElMessage.success(result.message);
    }
    await refreshAccounts();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : ITEM_SYNC_START_ERROR_MESSAGE);
  } finally {
    syncingAccountId.value = null;
  }
}

onMounted(() => {
  void refreshAll();
  statusTimer = window.setInterval(() => {
    void refreshStatus();
  }, STATUS_POLL_INTERVAL_MS);
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
        <h1>{{ ACCOUNT_PAGE_TITLE }}</h1>
        <p class="service-summary">
          <el-tag :type="serviceVariant" effect="light">{{ serviceLabel }}</el-tag>
          <el-tag :type="credentialVariant" effect="light">{{ credentialLabel }}</el-tag>
          <span v-if="status">{{ ACCOUNT_VERSION_LABEL }} {{ status.version }}</span>
          <span v-if="status">{{ ACCOUNT_LISTEN_COUNT_LABEL }} {{ status.listenCount }}</span>
        </p>
      </div>
      <div class="header-actions">
        <el-button :icon="RefreshCw" :loading="loading" @click="refreshAll">
          {{ ACCOUNT_REFRESH_BUTTON }}
        </el-button>
        <el-button
          type="primary"
          :icon="Plus"
          :disabled="!cookieWritable"
          @click="openCreateDialog"
        >
          {{ ACCOUNT_ADD_BUTTON }}
        </el-button>
      </div>
    </header>

    <el-alert
      v-if="statusError"
      class="service-error"
      type="warning"
      :title="statusError"
      show-icon
      :closable="false"
    />
    <el-alert
      v-if="accountsError"
      class="service-error"
      type="warning"
      :title="accountsError"
      show-icon
      :closable="false"
    />

    <section class="table-shell">
      <el-table
        v-loading="accountsLoading"
        :data="accounts"
        :empty-text="accountsError || ACCOUNT_EMPTY_TEXT"
        :height="ACCOUNT_TABLE_HEIGHT"
        border
      >
        <el-table-column prop="goodfishId" :label="ACCOUNT_GOODFISH_ID_LABEL" min-width="140" />
        <el-table-column prop="nickname" :label="ACCOUNT_NICKNAME_LABEL" min-width="160" />
        <el-table-column :label="ACCOUNT_VALIDITY_LABEL" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isValid ? 'success' : 'info'" effect="light">
              {{ row.isValid ? ACCOUNT_VALID_LABEL : ACCOUNT_INVALID_LABEL }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="ACCOUNT_LISTEN_STATE_LABEL" width="120">
          <template #default="{ row }">
            {{ listenStateLabel(row) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="remark"
          :label="ACCOUNT_REMARK_LABEL"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column :label="ACCOUNT_CREATED_AT_LABEL" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="ACCOUNT_LAST_CHECK_AT_LABEL" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.lastCheckAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="ACCOUNT_ACTION_LABEL" :width="ACCOUNT_ACTION_COLUMN_WIDTH" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Eye" @click="openDetailDialog(row)">
              {{ ACCOUNT_DETAIL_BUTTON }}
            </el-button>
            <el-button link type="success" :icon="Radio" @click="handleListenUnsupported">
              {{ ACCOUNT_LISTEN_BUTTON }}
            </el-button>
            <el-button
              link
              type="success"
              :icon="ShieldCheck"
              :loading="isVerifying(row)"
              :disabled="!cookieWritable"
              @click="checkAccount(row)"
            >
              {{ ACCOUNT_VERIFY_BUTTON }}
            </el-button>
            <el-button link type="primary" :icon="Pencil" @click="openEditDialog(row)">
              {{ ACCOUNT_EDIT_BUTTON }}
            </el-button>
            <el-button link type="danger" :icon="Trash2" @click="removeAccount(row)">
              {{ ACCOUNT_DELETE_BUTTON }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :width="ACCOUNT_DIALOG_WIDTH"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <el-form ref="accountFormRef" :model="accountForm" :rules="formRules" label-width="96px">
        <el-form-item
          v-if="editingAccountId !== null"
          :label="ACCOUNT_NICKNAME_LABEL"
          prop="nickname"
        >
          <el-input v-model="accountForm.nickname" :maxlength="ACCOUNT_NICKNAME_MAX_LENGTH" />
        </el-form-item>
        <el-form-item :label="ACCOUNT_COOKIE_LABEL" prop="cookie">
          <el-input
            v-model="accountForm.cookie"
            type="textarea"
            :rows="4"
            :maxlength="ACCOUNT_COOKIE_MAX_LENGTH"
            :disabled="editingAccountId !== null && !cookieWritable"
            :placeholder="editingAccountId === null ? '' : ACCOUNT_COOKIE_EDIT_HINT"
          />
        </el-form-item>
        <el-form-item :label="ACCOUNT_REMARK_LABEL" prop="remark">
          <el-input
            v-model="accountForm.remark"
            type="textarea"
            :rows="2"
            :maxlength="ACCOUNT_REMARK_MAX_LENGTH"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog">{{ ACCOUNT_CANCEL_BUTTON }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAccountForm">
          {{ ACCOUNT_SAVE_BUTTON }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      :title="ACCOUNT_DETAIL_DIALOG_TITLE"
      :width="ACCOUNT_DIALOG_WIDTH"
      :close-on-click-modal="false"
      @close="closeDetailDialog"
    >
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions v-if="detailAccount" :column="1" border>
          <el-descriptions-item :label="ACCOUNT_GOODFISH_ID_LABEL">
            {{ detailAccount.goodfishId }}
          </el-descriptions-item>
          <el-descriptions-item :label="ACCOUNT_NICKNAME_LABEL">
            {{ detailAccount.nickname }}
          </el-descriptions-item>
          <el-descriptions-item :label="ACCOUNT_VALIDITY_LABEL">
            {{ detailAccount.isValid ? ACCOUNT_VALID_LABEL : ACCOUNT_INVALID_LABEL }}
          </el-descriptions-item>
          <el-descriptions-item :label="ACCOUNT_LISTEN_STATE_LABEL">
            {{ listenStateLabel(detailAccount) }}
          </el-descriptions-item>
          <el-descriptions-item :label="ACCOUNT_REMARK_LABEL">
            {{ detailAccount.remark || ACCOUNT_EMPTY_TEXT }}
          </el-descriptions-item>
          <el-descriptions-item :label="ACCOUNT_COOKIE_LABEL">
            <div class="cookie-block">
              {{ cookieVisible ? detailAccount.cookie : ACCOUNT_COOKIE_MASK }}
            </div>
            <el-button link type="primary" @click="toggleCookieVisibility">
              {{ cookieVisible ? ACCOUNT_COOKIE_HIDE_BUTTON : ACCOUNT_COOKIE_SHOW_BUTTON }}
            </el-button>
            <p class="cookie-hint">{{ ACCOUNT_COOKIE_DETAIL_HINT }}</p>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button :loading="syncingAccountId !== null" @click="handleSyncItems">
          {{ ACCOUNT_SYNC_BUTTON }}
        </el-button>
        <el-button type="primary" @click="closeDetailDialog">
          {{ ACCOUNT_DETAIL_CLOSE_BUTTON }}
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.account-page {
  min-height: 100vh;
  padding: 24px 28px;
}

.detail-body {
  min-height: 160px;
}

.cookie-block {
  max-height: 140px;
  padding: 8px 10px;
  overflow: auto;
  color: #35485c;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-all;
  background: #f5f8fb;
  border: 1px solid #e2e8ef;
  border-radius: 4px;
}

.cookie-hint {
  margin: 8px 0 0;
  color: #8a99a8;
  font-size: 12px;
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

<script setup lang="ts">
import { useAccountManager } from './useAccountManager'
import AccountTable from './components/AccountTable.vue'
import AddAccountDialog from './components/AddAccountDialog.vue'
import ManualAddDialog from './components/ManualAddDialog.vue'
import QRLoginDialog from './components/QRLoginDialog.vue'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'
import ProxyConfigDialog from './components/ProxyConfigDialog.vue'
import LoginCredentialDialog from './components/LoginCredentialDialog.vue'

import IconQrCode from '@/components/icons/IconQrCode.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconSync from '@/components/icons/IconSync.vue'
import { getAccountDisplayName } from '@/utils/accountDisplay'

const {
  loading,
  refreshingProfileId,
  polishingAccountId,
  savingPolishTask,
  accounts,
  dialogs,
  currentAccount,
  deleteAccountId,
  proxyAccount,
  credentialAccount,
  polishTaskAccount,
  polishTask,
  polishTaskForm,
  loadAccounts,
  showAddDialog,
  showManualAddDialog,
  showQRLoginDialog,
  editAccount,
  openProxyDialog,
  openCredentialDialog,
  refreshAccountProfile,
  runItemPolish,
  openPolishTaskDialog,
  savePolishTaskConfig,
  copyUnb,
  openConnection,
  deleteAccount
} = useAccountManager();

loadAccounts();
</script>

<template>
  <div class="accounts">
    <!-- Page Header -->
    <header class="accounts__header">
      <div class="accounts__title-row">
        <h1 class="accounts__title mobile-hidden">闲鱼账号</h1>
      </div>
      <div class="accounts__actions desktop-only">
        <button class="btn btn--primary" @click="showQRLoginDialog">
          <IconQrCode />
          <span>扫码添加</span>
        </button>
        <button class="btn btn--secondary" @click="showManualAddDialog">
          <IconPlus />
          <span>手动添加</span>
        </button>
      </div>
    </header>

    <!-- Content Card -->
    <section class="accounts__content">
      <div class="accounts__toolbar">
        <span class="accounts__list-title">账号列表</span>
        <button
          class="btn btn--ghost"
          :class="{ 'btn--loading': loading }"
          @click="loadAccounts"
          :disabled="loading"
        >
          <IconSync />
          <span>刷新</span>
        </button>
      </div>

      <div class="accounts__table-wrap">
        <AccountTable
          :accounts="accounts"
          :loading="loading"
          :refreshing-profile-id="refreshingProfileId"
          :polishing-account-id="polishingAccountId"
          @edit="editAccount"
          @refresh-profile="refreshAccountProfile"
          @polish="runItemPolish"
          @polish-task="openPolishTaskDialog"
          @proxy="openProxyDialog"
          @credential="openCredentialDialog"
          @copy-unb="copyUnb"
          @connection="openConnection"
          @delete="deleteAccount"
        />
      </div>
    </section>

    <!-- Mobile Bottom Actions -->
    <footer class="accounts__footer mobile-only">
      <button class="btn btn--primary btn--full" @click="showQRLoginDialog">
        <IconQrCode />
        <span>扫码添加</span>
      </button>
      <button class="btn btn--secondary btn--full" @click="showManualAddDialog">
        <IconPlus />
        <span>手动添加</span>
      </button>
    </footer>

    <!-- Dialogs -->
    <AddAccountDialog
      v-model="dialogs.add"
      :account="currentAccount"
      @success="loadAccounts"
    />
    <ManualAddDialog v-model="dialogs.manualAdd" @success="loadAccounts" />
    <QRLoginDialog v-model="dialogs.qrLogin" @success="loadAccounts" />
    <DeleteConfirmDialog
      v-model="dialogs.deleteConfirm"
      :account-id="deleteAccountId"
      @success="loadAccounts"
    />
    <ProxyConfigDialog
      v-model="dialogs.proxy"
      :account="proxyAccount"
      @success="loadAccounts"
    />
    <LoginCredentialDialog
      v-model="dialogs.credential"
      :account="credentialAccount"
      @success="loadAccounts"
    />

    <div
      v-if="dialogs.polishTask"
      class="polish-dialog"
      @click.self="dialogs.polishTask = false"
    >
      <section class="polish-dialog__panel">
        <header class="polish-dialog__header">
          <div>
            <h2>定时擦亮</h2>
            <p>{{ getAccountDisplayName(polishTaskAccount) }}</p>
          </div>
          <button class="polish-dialog__close" @click="dialogs.polishTask = false">×</button>
        </header>

        <label class="polish-field polish-field--switch">
          <span>启用定时擦亮</span>
          <input v-model="polishTaskForm.enabled" type="checkbox" />
        </label>

        <div class="polish-dialog__grid">
          <label class="polish-field">
            <span>每日执行时段</span>
            <select v-model.number="polishTaskForm.runHour">
              <option v-for="hour in 24" :key="hour - 1" :value="hour - 1">
                {{ String(hour - 1).padStart(2, '0') }}:00
              </option>
            </select>
          </label>
          <label class="polish-field">
            <span>随机延迟</span>
            <input
              v-model.number="polishTaskForm.randomDelayMaxMinutes"
              type="number"
              min="0"
              max="180"
            />
          </label>
        </div>

        <div v-if="polishTask" class="polish-dialog__meta">
          <span>下次执行：{{ polishTask.nextRunTime || '-' }}</span>
          <span>上次执行：{{ polishTask.lastRunTime || '-' }}</span>
        </div>

        <footer class="polish-dialog__footer">
          <button class="btn btn--secondary" @click="dialogs.polishTask = false">取消</button>
          <button class="btn btn--primary" :disabled="savingPolishTask" @click="savePolishTaskConfig">
            <span>{{ savingPolishTask ? '保存中' : '保存设置' }}</span>
          </button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped src="./accounts.css"></style>

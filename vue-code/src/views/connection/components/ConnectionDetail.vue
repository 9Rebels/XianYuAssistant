<script setup lang="ts">
import { ref, watch, computed, onBeforeUnmount } from 'vue'
import { ElMessageBox } from 'element-plus'
import { clearCaptchaWait, getConnectionStatus, passwordLogin, retryAutoCaptcha, startConnection, stopConnection } from '@/api/websocket'
import { queryOperationLogs, type OperationLog } from '@/api/operation-log'
import { useAppNotificationStore } from '@/stores/appNotification'
import { formatTime, showSuccess, showError, showInfo } from '@/utils'
import CredentialModal from './CredentialModal.vue'
import ManualUpdateCookieModal from './ManualUpdateCookieModal.vue'
import QRUpdateDialog from './QRUpdateDialog.vue'

import IconWifi from '@/components/icons/IconWifi.vue'
import IconWifiOff from '@/components/icons/IconWifiOff.vue'
import IconKey from '@/components/icons/IconKey.vue'
import IconPlay from '@/components/icons/IconPlay.vue'
import IconStop from '@/components/icons/IconStop.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconLog from '@/components/icons/IconLog.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconLink from '@/components/icons/IconLink.vue'

interface ConnectionStatus {
  xianyuAccountId: number
  connected: boolean
  status: string
  cookieStatus?: number
  cookieText?: string
  mH5Tk?: string
  mh5Tk?: string
  websocketToken?: string
  tokenExpireTime?: number
  needCaptcha?: boolean
  captchaUrl?: string
}

interface Props {
  accountId: number | null
}

const props = defineProps<Props>()
const notificationStore = useAppNotificationStore()

const connectionStatus = ref<ConnectionStatus | null>(null)
const statusLoading = ref(false)
const operationLogs = ref<OperationLog[]>([])
let statusInterval: number | null = null

const showManualUpdateCookieDialog = ref(false)
const showQRUpdateDialog = ref(false)
const showCredentialDialog = ref(false)
const latestCaptchaUrl = ref('')
const autoCaptchaRetrying = ref(false)
const passwordLoginRunning = ref(false)

const isHealthyStatus = (status?: ConnectionStatus | null) => {
  return status?.connected === true && Number(status.cookieStatus) === 1
}

const loadConnectionStatus = async (silent = false) => {
  if (!props.accountId) return
  if (!silent) statusLoading.value = true
  try {
    const response = await getConnectionStatus(props.accountId)
    if (response.code === 0 || response.code === 200) {
      const status = response.data as ConnectionStatus
      connectionStatus.value = status
      if (isHealthyStatus(status)) {
        latestCaptchaUrl.value = ''
        notificationStore.resolveManualVerificationIfHealthy(status.xianyuAccountId, status.connected, status.cookieStatus)
      }
    } else {
      throw new Error(response.msg || '获取连接状态失败')
    }
  } catch (error: any) {
    console.error('加载状态失败:', error.message)
  } finally {
    statusLoading.value = false
  }
}

const loadOperationLogs = async () => {
  if (!props.accountId) return
  try {
    const response = await queryOperationLogs({
      accountId: props.accountId,
      page: 1,
      pageSize: 20
    })
    if (response.code === 0 || response.code === 200) {
      const data = response.data
      operationLogs.value = (data?.logs || []).filter(
        (log: OperationLog) => log.operationModule === 'COOKIE' || log.operationModule === 'TOKEN'
      )
    }
  } catch (error: any) {
    console.error('加载操作日志失败:', error.message)
  }
}

const handleStartConnection = async () => {
  if (!props.accountId) return
  statusLoading.value = true
  try {
    const response = await startConnection(props.accountId)
    if (response.code === 0 || response.code === 200) {
      showSuccess('连接启动成功')
      await loadConnectionStatus()
    } else if (response.code === 1001 && response.data?.needCaptcha) {
      latestCaptchaUrl.value = response.data.captchaUrl || ''
      showError(response.data?.message || '自动滑块失败，请人工更新 Cookie')
      await loadConnectionStatus(true)
    } else {
      throw new Error(response.msg || '启动连接失败')
    }
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      showError('启动连接失败: ' + error.message)
    }
  } finally {
    statusLoading.value = false
  }
}

const handleStopConnection = async () => {
  if (!props.accountId) return
  try {
    await ElMessageBox.confirm(
      '断开连接后将无法接收消息和执行自动化流程，确定要断开连接吗？',
      '确认断开连接',
      { confirmButtonText: '确定断开', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }

  statusLoading.value = true
  try {
    const response = await stopConnection(props.accountId)
    if (response.code === 0 || response.code === 200) {
      showSuccess('连接已断开')
      await loadConnectionStatus()
    } else {
      throw new Error(response.msg || '断开连接失败')
    }
  } catch (error: any) {
    showError('断开连接失败: ' + error.message)
  } finally {
    statusLoading.value = false
  }
}

const handleRefresh = async () => {
  await Promise.all([loadConnectionStatus(), loadOperationLogs()])
  showInfo('状态已刷新')
}

const handleManualUpdateCookieSuccess = async () => {
  await loadConnectionStatus()
}

const handleQRUpdateSuccess = async () => {
  await loadConnectionStatus()
}

const handleCaptchaConfirm = () => {
  showManualUpdateCookieDialog.value = true
  showInfo('请人工更新 Cookie 后再重新连接')
}

const handleClearCaptchaWait = async () => {
  if (!props.accountId) return
  try {
    await clearCaptchaWait(props.accountId)
    latestCaptchaUrl.value = ''
    showSuccess('验证等待状态已清除')
    await loadConnectionStatus()
  } catch (error: any) {
    showError('清除失败: ' + (error.message || '未知错误'))
  }
}

const handleRetryAutoCaptcha = async () => {
  if (!props.accountId || autoCaptchaRetrying.value) return
  autoCaptchaRetrying.value = true
  try {
    const response = await retryAutoCaptcha(props.accountId)
    if (response.code === 0 || response.code === 200) {
      latestCaptchaUrl.value = ''
      showSuccess(response.data || response.msg || '自动滑块成功，Cookie 已更新')
    } else {
      throw new Error(response.msg || '自动滑块失败，请人工更新 Cookie')
    }
  } catch (error: any) {
    showError(error.message || '自动滑块失败，请人工更新 Cookie')
  } finally {
    autoCaptchaRetrying.value = false
    await Promise.all([loadConnectionStatus(true), loadOperationLogs()])
  }
}

const showPasswordLoginManualVerification = (message: string) => {
  if (!props.accountId) return
  notificationStore.showManualVerification({
    accountId: props.accountId,
    type: 'verification_required',
    verificationType: '人脸/扫码/二维码验证',
    message,
    detail: '账号密码登录未完成，请按截图页面提示完成人脸、扫码、二维码或短信验证。'
  })
}

const handlePasswordLogin = async () => {
  if (!props.accountId || passwordLoginRunning.value) return
  if (isAccountHealthy.value) {
    latestCaptchaUrl.value = ''
    notificationStore.resolveManualVerification(props.accountId)
    showInfo('账号已连接且 Cookie 有效，无需账号密码登录')
    return
  }
  passwordLoginRunning.value = true
  try {
    const response = await passwordLogin(props.accountId)
    if (response.code === 0 || response.code === 200) {
      latestCaptchaUrl.value = ''
      notificationStore.resolveManualVerification(props.accountId)
      showSuccess(response.data || response.msg || '账号密码登录成功，Cookie 已更新')
    } else {
      throw new Error(response.msg || '账号密码登录失败，请按弹窗完成验证')
    }
  } catch (error: any) {
    const message = error?.message || '账号密码登录失败，请按弹窗完成验证'
    showError(message)
    showPasswordLoginManualVerification(message)
  } finally {
    passwordLoginRunning.value = false
    await Promise.all([loadConnectionStatus(true), loadOperationLogs()])
  }
}

const handleCaptchaCompletedStorage = async (event: StorageEvent) => {
  if (event.key !== 'captcha-completed-account' || !props.accountId) return
  const completedAccountId = Number((event.newValue || '').split(':')[0])
  if (completedAccountId && completedAccountId !== props.accountId) return
  latestCaptchaUrl.value = ''
  await Promise.all([loadConnectionStatus(true), loadOperationLogs()])
}

const getCookieStatusText = (status?: number) => {
  if (status === undefined || status === null) return '未知'
  const map: Record<number, string> = { 1: '有效', 2: '过期', 3: '失效' }
  return map[status] || '未知'
}

const getCookieStatusColor = (status?: number) => {
  if (status === 1) return 'var(--c-success)'
  if (status === 2) return 'var(--c-warning)'
  if (status === 3) return 'var(--c-danger)'
  return 'var(--c-text-3)'
}

const formatTimestamp = (timestamp?: number) => {
  if (!timestamp) return '未设置'
  return formatTime(timestamp).replace(/\//g, '-')
}

const isTokenExpired = (timestamp?: number) => {
  if (!timestamp) return false
  return Date.now() > timestamp
}

const getTokenStatusText = (timestamp?: number) => {
  if (!timestamp) return '未设置'
  return isTokenExpired(timestamp) ? '已过期' : '有效'
}

const getTokenStatusColor = (timestamp?: number) => {
  if (!timestamp) return 'var(--c-text-3)'
  return isTokenExpired(timestamp) ? 'var(--c-danger)' : 'var(--c-success)'
}

const getMH5TkStatusText = (mH5Tk?: string) => {
  if (!mH5Tk) return '未设置'
  return '有效'
}

const getMH5TkStatusColor = (mH5Tk?: string) => {
  if (!mH5Tk) return 'var(--c-text-3)'
  return 'var(--c-success)'
}

const h5Token = computed(() => connectionStatus.value?.mH5Tk || connectionStatus.value?.mh5Tk)

const getOperationStatusText = (status: number) => {
  const map: Record<number, string> = { 1: '成功', 2: '失败', 3: '部分成功' }
  return map[status] || '未知'
}

const getOperationStatusColor = (status: number) => {
  if (status === 1) return 'var(--c-success)'
  if (status === 2) return 'var(--c-danger)'
  if (status === 3) return 'var(--c-warning)'
  return 'var(--c-text-3)'
}

const canSyncGoods = computed(() => connectionStatus.value?.cookieStatus === 1)
const canAutoReply = computed(() => connectionStatus.value?.connected === true)
const isAccountHealthy = computed(() => isHealthyStatus(connectionStatus.value))
watch(() => props.accountId, (newId) => {
  if (newId) {
    loadConnectionStatus()
    loadOperationLogs()
    if (statusInterval) clearInterval(statusInterval)
    statusInterval = window.setInterval(() => {
      if (props.accountId) {
        loadConnectionStatus(true)
        loadOperationLogs()
      }
    }, 10000)
  } else {
    connectionStatus.value = null
    operationLogs.value = []
    if (statusInterval) {
      clearInterval(statusInterval)
      statusInterval = null
    }
  }
}, { immediate: true })

window.addEventListener('storage', handleCaptchaCompletedStorage)

onBeforeUnmount(() => {
  if (statusInterval) clearInterval(statusInterval)
  window.removeEventListener('storage', handleCaptchaCompletedStorage)
})
</script>

<template>
  <div class="detail-panel">
    <div v-if="!accountId" class="detail-empty">
      <div class="detail-empty__icon"><IconLink /></div>
      <p class="detail-empty__text">请选择一个账号查看连接状态</p>
    </div>

    <div v-else class="detail-scroll" :class="{ 'detail-scroll--loading': statusLoading }">
      <div v-if="connectionStatus" class="detail-body">
        <div class="status-header">
          <div class="status-header__left">
            <div class="status-icon" :class="connectionStatus.connected ? 'status-icon--on' : 'status-icon--off'">
              <component :is="connectionStatus.connected ? IconWifi : IconWifiOff" />
            </div>
            <div class="status-header__info">
              <span class="status-header__title">连接状态</span>
              <span v-if="connectionStatus.cookieStatus !== 1" class="status-header__sub status-header__sub--warning">
                Cookie已过期，请点击<span class="status-header__link">凭证详情</span>按钮更新Cookie
              </span>
              <span v-else class="status-header__sub">账号 ID: {{ connectionStatus.xianyuAccountId }}</span>
            </div>
          </div>
          <span class="status-badge" :class="connectionStatus.connected ? 'status-badge--on' : 'status-badge--off'">
            <component :is="connectionStatus.connected ? IconCheck : IconAlert" />
            {{ connectionStatus.connected ? '已连接' : '未连接' }}
          </span>
        </div>

        <div class="status-cards">
          <div class="status-card" :class="canSyncGoods ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="canSyncGoods ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">同步商品信息</span>
              <span class="status-card__desc">{{ canSyncGoods ? '可正常同步商品信息' : 'Cookie无效，无法同步商品信息' }}</span>
            </div>
          </div>

          <div class="status-card" :class="canAutoReply ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="canAutoReply ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">自动发货与回复</span>
              <span class="status-card__desc">{{ canAutoReply ? '可正常自动发货与回复' : '未连接，无法自动发货与回复' }}</span>
            </div>
          </div>
        </div>

        <div v-if="connectionStatus.needCaptcha" class="captcha-banner">
          <div class="captcha-banner__body">
            <span class="captcha-banner__title">账号正在等待滑块验证</span>
            <span class="captcha-banner__desc">自动滑块失败，请人工更新 Cookie 后再重新连接。</span>
          </div>
          <div class="captcha-banner__actions">
            <button
              class="btn btn--primary btn--small"
              :disabled="autoCaptchaRetrying"
              @click="handleRetryAutoCaptcha"
            >
              {{ autoCaptchaRetrying ? '重试中...' : '重试自动滑块' }}
            </button>
            <button class="btn btn--warning btn--small" @click="handleCaptchaConfirm">更新 Cookie</button>
            <button class="btn btn--ghost btn--small" @click="handleClearCaptchaWait">清除等待</button>
          </div>
        </div>

        <div class="action-bar">
          <button
            v-if="connectionStatus.connected === true"
            class="btn btn--stop"
            @click="handleStopConnection"
          >
            <IconStop /><span>断开连接</span>
          </button>
          <button
            v-else
            class="btn btn--start"
            @click="handleStartConnection"
          >
            <IconPlay /><span>开始连接</span>
          </button>
          <button class="btn btn--ghost btn--small" @click="showCredentialDialog = true">
            <IconKey /><span>凭证详情</span>
          </button>
          <button
            class="btn btn--ghost btn--small"
            :disabled="passwordLoginRunning || statusLoading || isAccountHealthy"
            @click="handlePasswordLogin"
          >
            <IconKey /><span>{{ passwordLoginRunning ? '登录中...' : '账号密码登录' }}</span>
          </button>
          <button class="btn btn--ghost btn--small" @click="handleRefresh" :disabled="statusLoading">
            <IconRefresh /><span>刷新状态</span>
          </button>
        </div>

        <div class="log-section">
          <div class="log-section__header">
            <div class="log-section__title">
              <IconLog />
              <span>操作日志</span>
            </div>
          </div>
          <div class="log-container">
            <div v-for="log in operationLogs" :key="log.id" class="log-entry">
              <span class="log-entry__time">{{ formatTimestamp(log.createTime) }}</span>
              <span class="log-entry__module">{{ log.operationModule }}</span>
              <span class="log-entry__desc">{{ log.operationDesc }}</span>
              <span class="log-entry__status" :style="{ color: getOperationStatusColor(log.operationStatus) }">
                {{ getOperationStatusText(log.operationStatus) }}
              </span>
            </div>
            <div v-if="operationLogs.length === 0" class="log-empty">暂无Cookie/Token相关日志</div>
          </div>
        </div>
      </div>
    </div>

    <CredentialModal
      v-model="showCredentialDialog"
      :connection-status="connectionStatus"
      @qr-update="showQRUpdateDialog = true"
      @manual-update="showManualUpdateCookieDialog = true"
    />

    <ManualUpdateCookieModal
      v-if="connectionStatus"
      v-model="showManualUpdateCookieDialog"
      :account-id="accountId || 0"
      :current-cookie="connectionStatus.cookieText || ''"
      @success="handleManualUpdateCookieSuccess"
    />
    <QRUpdateDialog
      v-model="showQRUpdateDialog"
      :account-id="accountId || 0"
      @success="handleQRUpdateSuccess"
    />
  </div>
</template>

<style scoped>
.detail-panel {
  --c-bg: transparent;
  --c-surface: var(--app-surface-strong);
  --c-surface-glass: color-mix(in srgb, var(--c-surface) 84%, transparent);
  --c-surface-glass-strong: color-mix(in srgb, var(--c-surface) 90%, transparent);
  --c-surface-glass-border: color-mix(in srgb, var(--c-surface) 72%, var(--c-border));
  --c-surface-muted: var(--app-bg-muted);
  --c-border: var(--app-border);
  --c-border-strong: var(--app-border-strong);
  --c-text-1: var(--app-text);
  --c-text-2: var(--app-text-muted);
  --c-text-3: var(--app-text-soft);
  --c-accent: var(--app-accent);
  --c-danger: var(--app-danger);
  --c-success: var(--app-success);
  --c-warning: var(--app-warning);
  --c-neutral-soft: color-mix(in srgb, var(--app-text-soft) 14%, transparent);
  --c-success-soft: color-mix(in srgb, var(--app-success) 16%, transparent);
  --c-success-soft-strong: color-mix(in srgb, var(--app-success) 22%, transparent);
  --c-warning-soft: color-mix(in srgb, var(--app-warning) 16%, transparent);
  --c-warning-soft-strong: color-mix(in srgb, var(--app-warning) 22%, transparent);
  --c-danger-soft: color-mix(in srgb, var(--app-danger) 16%, transparent);
  --c-danger-soft-strong: color-mix(in srgb, var(--app-danger) 22%, transparent);
  --c-accent-soft: color-mix(in srgb, var(--app-accent) 16%, transparent);
  --c-accent-soft-strong: color-mix(in srgb, var(--app-accent) 22%, transparent);
  --c-success-border: color-mix(in srgb, var(--app-success) 30%, transparent);
  --c-success-border-strong: color-mix(in srgb, var(--app-success) 40%, transparent);
  --c-danger-border: color-mix(in srgb, var(--app-danger) 30%, transparent);
  --c-danger-border-strong: color-mix(in srgb, var(--app-danger) 40%, transparent);
  --c-warning-border: color-mix(in srgb, var(--app-warning) 28%, transparent);
  --c-accent-border: color-mix(in srgb, var(--app-accent) 24%, transparent);
  --c-log-surface: color-mix(in srgb, var(--app-bg) 30%, #0c1016);
  --c-log-border: color-mix(in srgb, var(--app-border-strong) 42%, transparent);
  --c-log-text: color-mix(in srgb, var(--app-text) 90%, var(--vt-c-white));
  --c-log-muted: color-mix(in srgb, var(--app-text-soft) 82%, var(--vt-c-white));
  --c-on-solid: var(--vt-c-white);
  --c-r-sm: 8px;
  --c-r-md: 12px;
  --c-shadow: var(--app-shadow-soft);
}

.detail-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--c-text-3);
}

.detail-empty__icon {
  width: 48px;
  height: 48px;
  opacity: 0.3;
}

.detail-empty__icon svg {
  width: 36px;
  height: 36px;
}

.detail-empty__text {
  font-size: 14px;
  margin: 0;
}

.detail-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  scrollbar-width: none;
}

.detail-scroll::-webkit-scrollbar {
  display: none;
}

.detail-scroll--loading {
  opacity: 0.5;
  pointer-events: none;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 16px;
}

.status-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: var(--c-surface-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--c-surface-glass-border);
  border-radius: 16px;
  box-shadow: var(--app-shadow-soft);
}

.status-header__left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.status-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-icon svg { width: 24px; height: 24px; }

.status-icon--on {
  background: var(--c-success-soft);
  color: var(--c-success);
}

.status-icon--off {
  background: var(--c-danger-soft);
  color: var(--c-danger);
}

.status-header__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.status-header__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: -0.01em;
}

.status-header__sub {
  font-size: 12px;
  color: var(--c-text-3);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-header__sub--warning {
  color: var(--c-warning);
  font-weight: 500;
}

.status-header__link {
  color: var(--c-danger);
  font-weight: 600;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  padding: 6px 12px;
  border-radius: 20px;
  flex-shrink: 0;
  letter-spacing: -0.01em;
}

.status-badge svg { width: 14px; height: 14px; }

.status-badge--on {
  color: var(--c-success);
  background: var(--c-success-soft);
}

.status-badge--off {
  color: var(--c-danger);
  background: var(--c-danger-soft);
}

.status-cards {
  display: flex;
  gap: 12px;
  width: 100%;
}

.captcha-banner {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--c-warning-border);
  border-radius: 14px;
  background: var(--c-warning-soft);
}

.captcha-banner__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.captcha-banner__title {
  font-size: 14px;
  font-weight: 650;
  color: var(--c-warning);
}

.captcha-banner__desc {
  font-size: 12px;
  line-height: 1.45;
  color: var(--c-text-2);
}

.captcha-banner__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.status-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid;
  background: var(--c-surface-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  flex: 1;
}

.status-card--success {
  border-color: var(--c-success-border);
  background: var(--c-success-soft);
}

.status-card--success:hover {
  border-color: var(--c-success-border-strong);
  background: var(--c-success-soft-strong);
}

.status-card--danger {
  border-color: var(--c-danger-border);
  background: var(--c-danger-soft);
}

.status-card--danger:hover {
  border-color: var(--c-danger-border-strong);
  background: var(--c-danger-soft-strong);
}

.status-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-card--success .status-card__icon {
  background: var(--c-success-soft-strong);
  color: var(--c-success);
}

.status-card--danger .status-card__icon {
  background: var(--c-danger-soft-strong);
  color: var(--c-danger);
}

.status-card__icon svg { width: 20px; height: 20px; }

.status-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-width: 0;
}

.status-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: -0.01em;
}

.status-card__desc {
  font-size: 11px;
  color: var(--c-text-3);
  line-height: 1.4;
}

.status-card--success .status-card__title { color: var(--c-success); }
.status-card--danger .status-card__title { color: var(--c-danger); }

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  -webkit-tap-highlight-color: transparent;
  letter-spacing: -0.01em;
}

.btn svg { width: 16px; height: 16px; }

.btn--small {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
}

.btn--small svg { width: 14px; height: 14px; }

.btn--start {
  background: var(--c-success);
  color: var(--c-on-solid);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-success) 30%, transparent);
}

.btn--start:hover {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--c-success) 40%, transparent);
  transform: translateY(-1px);
}

.btn--start:active { transform: scale(0.97); }

.btn--stop {
  background: var(--c-danger);
  color: var(--c-on-solid);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-danger) 30%, transparent);
}

.btn--stop:hover {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--c-danger) 40%, transparent);
  transform: translateY(-1px);
}

.btn--stop:active { transform: scale(0.97); }

.btn--warning {
  background: var(--c-warning);
  color: var(--c-on-solid);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-warning) 28%, transparent);
}

.btn--warning:hover {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--c-warning) 36%, transparent);
  transform: translateY(-1px);
}

.btn--warning:active { transform: scale(0.97); }

.btn--ghost {
  background: var(--c-accent-soft);
  color: var(--c-accent);
  border-color: var(--c-accent-border);
}

.btn--ghost:hover {
  background: var(--c-accent-soft-strong);
}

.btn--ghost:active { transform: scale(0.97); }

.btn--ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.log-section {
  margin-top: 20px;
  padding-bottom: 8px;
}

.log-section__header {
  margin-bottom: 10px;
}

.log-section__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-1);
}

.log-section__title svg { width: 16px; height: 16px; }

.log-container {
  background: var(--c-log-surface);
  color: var(--c-log-text);
  border: 1px solid var(--c-log-border);
  border-radius: 8px;
  padding: 12px;
  font-family: 'Courier New', Consolas, monospace;
  font-size: 12px;
  max-height: 180px;
  overflow-y: auto;
}

.log-entry {
  display: flex;
  gap: 8px;
  margin-bottom: 6px;
  line-height: 1.5;
}

.log-entry:last-child { margin-bottom: 0; }

.log-entry__time {
  color: var(--c-log-muted);
  font-size: 11px;
  flex-shrink: 0;
}

.log-entry__module {
  color: var(--c-accent);
  flex-shrink: 0;
  font-weight: 500;
}

.log-entry__desc {
  color: var(--c-log-text);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-entry__status {
  flex-shrink: 0;
  font-weight: 500;
}

.log-empty {
  text-align: center;
  color: var(--c-log-muted);
  padding: 16px;
  font-size: 12px;
}
</style>

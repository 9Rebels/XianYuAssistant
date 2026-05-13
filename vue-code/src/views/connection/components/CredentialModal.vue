<script setup lang="ts">
import { ref, computed } from 'vue'
import { formatTime } from '@/utils'
import IconCookie from '@/components/icons/IconCookie.vue'
import IconKey from '@/components/icons/IconKey.vue'
import IconQrCode from '@/components/icons/IconQrCode.vue'
import IconClose from '@/components/icons/IconClose.vue'

interface ConnectionStatus {
  xianyuAccountId?: number
  connected?: boolean
  status?: string
  cookieStatus?: number
  cookieText?: string
  mH5Tk?: string
  mh5Tk?: string
  websocketToken?: string
  tokenExpireTime?: number
}

interface Props {
  modelValue: boolean
  connectionStatus: ConnectionStatus | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'qr-update'): void
  (e: 'manual-update'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const h5Token = computed(() => props.connectionStatus?.mH5Tk || props.connectionStatus?.mh5Tk)

const getCookieStatusColor = (status?: number) => {
  if (status === 1) return 'var(--c-success)'
  if (status === 2) return 'var(--c-warning)'
  if (status === 3) return 'var(--c-danger)'
  return 'var(--c-text-3)'
}

const getCookieStatusText = (status?: number) => {
  if (status === 1) return '有效'
  if (status === 2) return '过期'
  if (status === 3) return '失效'
  return '未知'
}

const getTokenStatusText = (timestamp?: number) => {
  if (!timestamp) return '未设置'
  return Date.now() > timestamp ? '已过期' : '有效'
}

const getTokenStatusColor = (timestamp?: number) => {
  if (!timestamp) return 'var(--c-text-3)'
  return Date.now() > timestamp ? 'var(--c-danger)' : 'var(--c-success)'
}

const getMH5TkStatusText = (token?: string) => {
  if (!token) return '未设置'
  return '有效'
}

const getMH5TkStatusColor = (token?: string) => {
  if (!token) return 'var(--c-text-3)'
  return 'var(--c-success)'
}

const formatTimestamp = (timestamp?: number) => {
  if (!timestamp) return '未设置'
  return formatTime(timestamp).replace(/\//g, '-')
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleQRUpdate = () => {
  emit('qr-update')
}

const handleManualUpdate = () => {
  emit('manual-update')
}
</script>

<template>
  <Transition name="modal-fade">
    <div v-if="modelValue" class="modal-overlay" @click="handleClose">
      <div class="modal-container" @click.stop>
        <!-- Header -->
        <div class="modal-header">
          <h2 class="modal-title">凭证详情</h2>
          <button class="modal-close" @click="handleClose">
            <IconClose />
          </button>
        </div>

        <!-- Content -->
        <div class="modal-content">
          <!-- Action Buttons -->
          <div class="action-buttons">
            <button class="btn btn--primary" @click="handleQRUpdate">
              <IconQrCode />
              <span>扫码更新</span>
            </button>
            <button class="btn btn--secondary" @click="handleManualUpdate">
              <IconCookie />
              <span>手动更新Cookie</span>
            </button>
          </div>

          <!-- Credential Items -->
          <div class="credential-list">
            <!-- Cookie -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--cookie">
                    <IconCookie />
                  </div>
                  <span class="credential-item__name">Cookie 凭证</span>
                </div>
                <span class="credential-item__status" :style="{ color: getCookieStatusColor(connectionStatus?.cookieStatus) }">
                  {{ getCookieStatusText(connectionStatus?.cookieStatus) }}
                </span>
              </div>
              <div v-if="connectionStatus?.cookieText" class="credential-item__value">
                {{ connectionStatus.cookieText.substring(0, 80) }}...
                <span class="credential-item__meta">{{ connectionStatus.cookieText.length }} 字符</span>
              </div>
              <div v-else class="credential-item__value credential-item__value--empty">未设置</div>
            </div>

            <!-- WebSocket Token -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--token">
                    <IconKey />
                  </div>
                  <span class="credential-item__name">WebSocket Token</span>
                </div>
                <span class="credential-item__status" :style="{ color: getTokenStatusColor(connectionStatus?.tokenExpireTime) }">
                  {{ getTokenStatusText(connectionStatus?.tokenExpireTime) }}
                </span>
              </div>
              <div v-if="connectionStatus?.websocketToken" class="credential-item__value">
                {{ connectionStatus.websocketToken.substring(0, 60) }}...
                <span class="credential-item__meta">{{ connectionStatus.websocketToken.length }} 字符</span>
              </div>
              <div v-else class="credential-item__value credential-item__value--empty">未设置</div>
              <div v-if="connectionStatus?.tokenExpireTime" class="credential-item__expire">
                过期时间: {{ formatTimestamp(connectionStatus.tokenExpireTime) }}
              </div>
            </div>

            <!-- H5 Token -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--h5">
                    <IconKey />
                  </div>
                  <span class="credential-item__name">H5 Token (_m_h5_tk)</span>
                </div>
                <span class="credential-item__status" :style="{ color: getMH5TkStatusColor(h5Token) }">
                  {{ getMH5TkStatusText(h5Token) }}
                </span>
              </div>
              <div v-if="h5Token" class="credential-item__value">
                {{ h5Token.substring(0, 60) }}...
                <span class="credential-item__meta">{{ h5Token.length }} 字符</span>
              </div>
              <div v-else class="credential-item__value credential-item__value--empty">未设置</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.modal-overlay,
.modal-container {
  --c-surface: var(--app-surface-strong);
  --c-surface-glass: color-mix(in srgb, var(--c-surface) 84%, transparent);
  --c-surface-glass-strong: color-mix(in srgb, var(--c-surface) 90%, transparent);
  --c-surface-glass-border: color-mix(in srgb, var(--c-surface) 72%, var(--c-border));
  --c-border: var(--app-border);
  --c-text-1: var(--app-text);
  --c-text-2: var(--app-text-muted);
  --c-text-3: var(--app-text-soft);
  --c-accent: var(--app-accent);
  --c-accent-deep: color-mix(in srgb, var(--app-accent) 78%, var(--vt-c-black));
  --c-accent-soft: color-mix(in srgb, var(--app-accent) 16%, transparent);
  --c-success: var(--app-success);
  --c-success-soft: color-mix(in srgb, var(--app-success) 16%, transparent);
  --c-warning: var(--app-warning);
  --c-warning-soft: color-mix(in srgb, var(--app-warning) 16%, transparent);
  --c-danger: var(--app-danger);
  --c-danger-soft: color-mix(in srgb, var(--app-danger) 16%, transparent);
  --c-neutral-soft: color-mix(in srgb, var(--app-text-soft) 14%, transparent);
  --c-field-bg: color-mix(in srgb, var(--app-text) 4%, transparent);
  --c-field-bg-strong: color-mix(in srgb, var(--app-text) 7%, transparent);
  --c-field-border: color-mix(in srgb, var(--app-text) 10%, transparent);
  --c-overlay: var(--app-overlay);
  --c-on-solid: var(--vt-c-white);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--c-overlay);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-container {
  background: var(--c-surface);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: var(--app-shadow-strong);
  display: flex;
  flex-direction: column;
  max-height: 85vh;
  animation: slideUp 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--c-border);
  background: var(--c-surface-glass-strong);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  flex-shrink: 0;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--c-text-1);
  margin: 0;
  letter-spacing: -0.01em;
}

.modal-close {
  background: none;
  border: none;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}

.modal-close:hover {
  background: var(--c-field-bg-strong);
  color: var(--c-text-1);
}

.modal-close svg {
  width: 20px;
  height: 20px;
}

.modal-content {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  padding: 24px;
}

.modal-content::-webkit-scrollbar {
  display: none;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 15px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  -webkit-tap-highlight-color: transparent;
  letter-spacing: -0.01em;
  flex: 1;
}

.btn svg {
  width: 18px;
  height: 18px;
}

.btn--primary {
  background: linear-gradient(135deg, var(--c-accent) 0%, var(--c-accent-deep) 100%);
  color: var(--c-on-solid);
  box-shadow: 0 4px 12px color-mix(in srgb, var(--c-accent) 30%, transparent);
}

.btn--primary:hover {
  box-shadow: 0 6px 16px color-mix(in srgb, var(--c-accent) 40%, transparent);
}

.btn--primary:active {
  transform: scale(0.96);
}

.btn--secondary {
  background: var(--c-field-bg-strong);
  color: var(--c-text-1);
  box-shadow: none;
}

.btn--secondary:hover {
  background: color-mix(in srgb, var(--app-text) 10%, transparent);
}

.btn--secondary:active {
  transform: scale(0.96);
}

.credential-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.credential-item {
  background: var(--c-surface-glass);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--c-surface-glass-border);
  border-radius: 16px;
  padding: 16px;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.credential-item:hover {
  background: var(--c-surface-glass-strong);
  border-color: color-mix(in srgb, var(--c-surface) 80%, var(--c-border));
}

.credential-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--c-border);
}

.credential-item__left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.credential-item__icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.credential-item__icon svg {
  width: 16px;
  height: 16px;
}

.credential-item__icon--cookie {
  background: var(--c-warning-soft);
  color: var(--c-warning);
}

.credential-item__icon--token {
  background: var(--c-success-soft);
  color: var(--c-success);
}

.credential-item__icon--h5 {
  background: var(--c-accent-soft);
  color: var(--c-accent);
}

.credential-item__name {
  font-size: 15px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credential-item__status {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  background: var(--c-neutral-soft);
  flex-shrink: 0;
}

.credential-item__value {
  font-family: 'SF Mono', 'Menlo', 'Monaco', monospace;
  font-size: 12px;
  color: var(--c-text-2);
  word-break: break-all;
  line-height: 1.6;
  padding: 10px;
  background: var(--c-field-bg);
  border-radius: 10px;
  border: 1px solid var(--c-field-border);
}

.credential-item__value--empty {
  color: var(--c-text-3);
  font-style: italic;
  background: color-mix(in srgb, var(--app-text) 2%, transparent);
}

.credential-item__meta {
  display: inline-block;
  margin-left: 8px;
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 500;
}

.credential-item__expire {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--c-border);
  font-size: 12px;
  color: var(--c-text-3);
}

/* 手机端适配 */
@media screen and (max-width: 767px) {
  .modal-container {
    width: 90%;
    max-height: 90vh;
    border-radius: 16px;
  }

  .modal-header {
    padding: 16px;
  }

  .modal-title {
    font-size: 16px;
  }

  .modal-content {
    padding: 16px;
  }

  .action-buttons {
    flex-direction: column;
    gap: 10px;
    margin-bottom: 16px;
  }

  .btn {
    padding: 12px 14px;
    font-size: 14px;
  }

  .credential-item {
    padding: 12px;
  }

  .credential-item__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    margin-bottom: 10px;
    padding-bottom: 10px;
  }

  .credential-item__left {
    width: 100%;
  }

  .credential-item__status {
    align-self: flex-start;
    font-size: 11px;
    padding: 3px 8px;
  }

  .credential-item__icon {
    width: 28px;
    height: 28px;
  }

  .credential-item__icon svg {
    width: 14px;
    height: 14px;
  }

  .credential-item__name {
    font-size: 13px;
  }

  .credential-item__value {
    font-size: 11px;
    padding: 8px;
    line-height: 1.5;
  }

  .credential-item__meta {
    font-size: 10px;
  }

  .credential-item__expire {
    margin-top: 8px;
    padding-top: 8px;
    font-size: 11px;
  }
}

/* 平板端适配 */
@media screen and (min-width: 768px) and (max-width: 1023px) {
  .modal-container {
    width: 70%;
    max-height: 85vh;
  }

  .modal-content {
    padding: 20px;
  }

  .credential-item {
    padding: 14px;
  }

  .credential-item__name {
    font-size: 14px;
  }

  .credential-item__value {
    font-size: 11px;
  }
}

/* 电脑端适配 */
@media screen and (min-width: 1024px) {
  .modal-container {
    width: 60%;
    max-height: 85vh;
  }

  .modal-content {
    padding: 32px;
  }
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>

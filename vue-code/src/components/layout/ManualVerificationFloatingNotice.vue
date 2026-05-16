<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useAppNotificationStore } from '@/stores/appNotification'
import IconAlert from '@/components/icons/IconAlert.vue'

const store = useAppNotificationStore()
const now = ref(Date.now())
let timer = 0

const state = computed(() => store.manualVerification)
const visible = computed(() => Boolean(state.value.accountId))
const remainingMs = computed(() => Math.max(0, Number(state.value.expiresAt || 0) - now.value))
const remainingText = computed(() => {
  if (!state.value.expiresAt) return '等待处理'
  const totalSeconds = Math.ceil(remainingMs.value / 1000)
  if (totalSeconds <= 0) return '已超时'
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
})

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <button
    v-if="visible"
    class="manual-floating"
    :class="{ 'manual-floating--expired': remainingMs <= 0 }"
    type="button"
    @click="store.openManualVerification"
  >
    <span class="manual-floating__icon"><IconAlert /></span>
    <span class="manual-floating__body">
      <strong>人工处理 · 账号 {{ state.accountId }}</strong>
      <span>{{ state.verificationType || '身份验证' }} · 剩余 {{ remainingText }}</span>
      <small v-if="state.lastError">{{ state.lastError }}</small>
    </span>
  </button>
</template>

<style scoped>
.manual-floating {
  position: fixed;
  right: 20px;
  bottom: 22px;
  z-index: 3200;
  width: min(360px, calc(100vw - 24px));
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #e6a23c;
  border-radius: 8px;
  background: var(--app-surface-strong);
  box-shadow: var(--app-shadow-strong);
  text-align: left;
  cursor: pointer;
}

.manual-floating--expired {
  border-color: #f56c6c;
}

.manual-floating__icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, #e6a23c 16%, transparent);
  color: #e6a23c;
  flex-shrink: 0;
}

.manual-floating__icon svg {
  width: 18px;
  height: 18px;
}

.manual-floating__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.manual-floating__body strong {
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.35;
}

.manual-floating__body span,
.manual-floating__body small {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.manual-floating__body small {
  color: #f56c6c;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 767px) {
  .manual-floating {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
  }
}
</style>

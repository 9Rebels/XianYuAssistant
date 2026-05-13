<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatMonthDayTime } from '@/utils'
import { useAppNotificationStore } from '@/stores/appNotification'

const router = useRouter()
const store = useAppNotificationStore()

onMounted(() => {
  void store.start()
})

onUnmounted(() => {
  store.stop()
})

const handleToastClick = async (toastId: number, kind: string, accountId?: number, sid?: string) => {
  store.removeToast(toastId)
  if (kind === 'message' && accountId && sid) {
    await router.push({
      path: '/online-messages',
      query: { accountId: String(accountId), sid }
    })
    return
  }
  await router.push('/notifications')
}
</script>

<template>
  <div v-if="store.toasts.length" class="app-toast-stack" aria-live="polite">
    <button
      v-for="toast in store.toasts"
      :key="toast.id"
      class="app-toast"
      :class="[`app-toast--${toast.level || 'info'}`, `app-toast--${toast.kind}`]"
      type="button"
      @click="handleToastClick(toast.id, toast.kind, toast.accountId, toast.sid)"
    >
      <div class="app-toast__head">
        <strong>{{ toast.title }}</strong>
        <small>{{ formatMonthDayTime(toast.time || '') }}</small>
      </div>
      <div v-if="toast.meta" class="app-toast__meta">{{ toast.meta }}</div>
      <div class="app-toast__content">{{ toast.content }}</div>
    </button>
  </div>
</template>

<style scoped>
.app-toast-stack {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: min(360px, calc(100vw - 24px));
}

.app-toast {
  border: 1px solid var(--app-border);
  background: var(--app-surface-strong);
  border-radius: 8px;
  padding: 12px 14px;
  text-align: left;
  box-shadow: var(--app-shadow-soft);
  cursor: pointer;
}

.app-toast--error {
  border-color: #f56c6c;
}

.app-toast--warning {
  border-color: #e6a23c;
}

.app-toast__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text);
}

.app-toast__head small,
.app-toast__meta {
  color: var(--app-text-muted);
  font-size: 12px;
}

.app-toast__content {
  margin-top: 8px;
  font-size: 13px;
  color: var(--app-text-muted);
  line-height: 1.5;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

@media (max-width: 767px) {
  .app-toast-stack {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
  }
}
</style>

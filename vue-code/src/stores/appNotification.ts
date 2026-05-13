import { defineStore } from 'pinia'
import { getAllSettings } from '@/api/setting'
import { getLatestNotifications, type NotificationLog } from '@/api/notification'
import { useSseConnection } from '@/composables/useSseConnection'

export interface AppToast {
  id: number
  kind: 'system' | 'message'
  title: string
  content: string
  meta?: string
  time?: number | string
  accountId?: number
  sid?: string
  level?: 'info' | 'success' | 'warning' | 'error'
}

export interface ManualVerificationState {
  visible: boolean
  accountId: number | null
  verificationType: string
  message: string
  detail: string
  screenshotUrl: string
  updatedAt: number
}

const POLL_INTERVAL_MS = 60000
const TOAST_DURATION_MS = 10000
const MAX_TOASTS = 5

export const useAppNotificationStore = defineStore('appNotification', {
  state: () => ({
    toasts: [] as AppToast[],
    inAppEnabled: true,
    onlineMessageEnabled: true,
    lastNotificationId: 0,
    pollTimer: 0 as number,
    toastIdSeed: 1,
    initialized: false,
    manualVerification: {
      visible: false,
      accountId: null,
      verificationType: '',
      message: '',
      detail: '',
      screenshotUrl: '',
      updatedAt: 0
    } as ManualVerificationState
  }),
  actions: {
    async start() {
      if (this.pollTimer) return
      await this.loadSettings()
      await this.bootstrapLatestId()

      // SSE 实时推送
      const { connect, on } = useSseConnection()
      connect()
      on('notification', (data: any) => {
        if (data?.type === 'verification_required') {
          this.showManualVerification(data)
        } else if (data?.type === 'verification_success') {
          this.resolveManualVerification(data?.accountId)
        }
        if (!this.inAppEnabled) return
        if (data.eventType === 'test') return
        this.pushToast({
          kind: 'system',
          title: data.title || '系统通知',
          content: data.content || data.message || data.detail || '-',
          accountId: normalizeAccountId(data.accountId) || undefined,
          level: data.type === 'verification_success' ? 'success' : 'warning'
        })
      })
      on('connection', (data: any) => {
        if (data?.connected === true) {
          this.resolveManualVerification(data?.accountId)
        }
      })

      // 轮询作为兜底
      this.pollTimer = window.setInterval(() => {
        void this.pollSystemNotifications()
      }, POLL_INTERVAL_MS)
    },
    stop() {
      if (this.pollTimer) {
        window.clearInterval(this.pollTimer)
        this.pollTimer = 0
      }
    },
    async loadSettings() {
      try {
        const res = await getAllSettings()
        if (res.code !== 0 && res.code !== 200) return
        const map = new Map((res.data || []).map(item => [item.settingKey, item.settingValue]))
        this.inAppEnabled = (map.get('notify_in_app_toast_enabled') || '1') === '1'
        this.onlineMessageEnabled = (map.get('notify_in_app_online_message_enabled') || '1') === '1'
      } catch {
        this.inAppEnabled = true
        this.onlineMessageEnabled = true
      }
    },
    async bootstrapLatestId() {
      if (this.initialized) return
      this.initialized = true
      try {
        const res = await getLatestNotifications({ limit: 1 })
        const latest = res.data?.[0]
        this.lastNotificationId = latest?.id || 0
      } catch {
        this.lastNotificationId = 0
      }
    },
    async pollSystemNotifications() {
      if (!this.inAppEnabled) return
      try {
        const res = await getLatestNotifications({ afterId: this.lastNotificationId, limit: 10 })
        if (res.code !== 0 && res.code !== 200) return
        const logs = [...(res.data || [])].sort((a, b) => a.id - b.id)
        for (const log of logs) {
          this.lastNotificationId = Math.max(this.lastNotificationId, log.id)
          if (shouldToastLog(log)) {
            this.pushToast({
              kind: 'system',
              title: log.title || '系统通知',
              content: logContent(log),
              meta: channelMeta(log),
              time: log.createTime,
              level: log.status === -1 ? 'error' : 'warning'
            })
          }
        }
      } catch {
        // polling must stay quiet
      }
    },
    pushOnlineMessageToast(toast: Omit<AppToast, 'id' | 'kind' | 'level'>) {
      if (!this.inAppEnabled || !this.onlineMessageEnabled) return
      this.pushToast({
        ...toast,
        kind: 'message',
        level: 'info'
      })
    },
    pushToast(toast: Omit<AppToast, 'id'>) {
      const id = this.toastIdSeed++
      this.toasts = [{ ...toast, id }, ...this.toasts].slice(0, MAX_TOASTS)
      window.setTimeout(() => this.removeToast(id), TOAST_DURATION_MS)
    },
    removeToast(id: number) {
      this.toasts = this.toasts.filter(item => item.id !== id)
    },
    showManualVerification(data: any) {
      const accountId = normalizeAccountId(data?.accountId)
      const rawUrl = data?.screenshotUrl || data?.captchaImageUrl || defaultManualImageUrl(accountId)
      this.manualVerification = {
        visible: true,
        accountId,
        verificationType: data?.verificationType || '人工验证',
        message: data?.message || '账号登录需要人工处理',
        detail: data?.detail || '',
        screenshotUrl: appendImageVersion(rawUrl),
        updatedAt: Date.now()
      }
    },
    resolveManualVerification(accountId?: number | string | null) {
      const resolvedAccountId = normalizeAccountId(accountId)
      if (resolvedAccountId && this.manualVerification.accountId && resolvedAccountId !== this.manualVerification.accountId) {
        return
      }
      this.manualVerification.visible = false
    },
    resolveManualVerificationIfHealthy(accountId: number | string | null | undefined, connected?: boolean, cookieStatus?: number | string | null) {
      if (connected === true && Number(cookieStatus) === 1) {
        this.resolveManualVerification(accountId)
      }
    },
    refreshManualVerificationImage() {
      const accountId = this.manualVerification.accountId
      if (!accountId) return
      this.manualVerification.screenshotUrl = `/api/captcha/debug-image/latest?xianyuAccountId=${accountId}&v=${Date.now()}`
      this.manualVerification.updatedAt = Date.now()
    },
    closeManualVerification() {
      this.manualVerification.visible = false
    }
  }
})

function shouldToastLog(log: NotificationLog) {
  if (log.eventType === 'test') return false
  return log.status !== 0
}

function logContent(log: NotificationLog) {
  return log.content || log.errorMessage || '-'
}

function channelMeta(log: NotificationLog) {
  if (!log.channel || log.channel === 'local') return ''
  return `渠道：${log.channel}`
}

function normalizeAccountId(value: unknown): number | null {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

function appendImageVersion(url: string) {
  if (!url) return ''
  return `${url}${url.includes('?') ? '&' : '?'}_t=${Date.now()}`
}

function defaultManualImageUrl(accountId: number | null) {
  if (!accountId) return ''
  return `/api/captcha/debug-image/latest?xianyuAccountId=${accountId}`
}

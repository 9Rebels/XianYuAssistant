import { defineStore } from 'pinia'
import { getAllSettings } from '@/api/setting'
import { getLatestNotifications, type NotificationLog } from '@/api/notification'
import {
  confirmManualVerification,
  getConnectionStatus,
  getPendingManualVerification
} from '@/api/websocket'
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
  expiresAt: number
  confirming: boolean
  lastError: string
}

type NotificationLike = Partial<NotificationLog> & {
  accountId?: unknown
  message?: unknown
  detail?: unknown
  type?: unknown
}

const POLL_INTERVAL_MS = 60000
const TOAST_DURATION_MS = 10000
const MAX_TOASTS = 5
const MANUAL_VERIFICATION_STORAGE_KEY = 'xianyu_manual_verification'
const DEFAULT_MANUAL_TIMEOUT_MS = 5 * 60 * 1000

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
      updatedAt: 0,
      expiresAt: 0,
      confirming: false,
      lastError: ''
    } as ManualVerificationState
  }),
  actions: {
    async start() {
      if (this.pollTimer) return
      this.restoreManualVerification()
      await this.loadSettings()
      await this.bootstrapLatestId()

      // SSE 实时推送
      const { connect, on } = useSseConnection()
      connect()
      on('notification', (data: any) => {
        void this.handleRealtimeNotification(data)
      })
      on('connection', (data: any) => {
        this.resolveManualVerificationIfHealthy(data?.accountId, data?.connected, data?.cookieStatus)
      })

      await this.syncPendingManualVerification()

      // 轮询作为兜底
      this.pollTimer = window.setInterval(() => {
        void this.pollSystemNotifications()
        void this.syncPendingManualVerification()
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
          if (shouldToastLog(log) && !(await this.isRecoveredAccountStateNotification(log))) {
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
    async syncPendingManualVerification() {
      try {
        const res = await getPendingManualVerification()
        if (res.code !== 0 && res.code !== 200) return
        const pending = (res.data || [])
          .filter(item => normalizeAccountId(item?.accountId))
          .sort((a, b) => Number(b.expiresAt || 0) - Number(a.expiresAt || 0))[0]
        if (pending) {
          this.showManualVerification({
            ...pending,
            type: 'verification_required',
            visible: this.manualVerification.visible === true
          })
          return
        }
        if (this.manualVerification.accountId && this.manualVerification.expiresAt <= Date.now()) {
          this.clearManualVerification()
        }
      } catch {
        // best-effort server reconciliation
      }
    },
    async handleRealtimeNotification(data: any) {
      const recovered = await this.isRecoveredAccountStateNotification(data)
      if (data?.type === 'verification_required') {
        if (recovered) return
        this.showManualVerification(data)
      } else if (data?.type === 'verification_success') {
        this.resolveManualVerification(data?.accountId)
      }
      if (!this.inAppEnabled) return
      if (data.eventType === 'test') return
      if (recovered) return
      this.pushToast({
        kind: 'system',
        title: data.title || '系统通知',
        content: data.content || data.message || data.detail || '-',
        accountId: normalizeNotificationAccountId(data) || undefined,
        level: data.type === 'verification_success' ? 'success' : 'warning'
      })
    },
    async isRecoveredAccountStateNotification(source: NotificationLike) {
      if (!isRecoverableAccountStateNotification(source)) return false
      const accountId = normalizeNotificationAccountId(source)
      if (!accountId) return false
      try {
        const res = await getConnectionStatus(accountId)
        const status = res.data
        const recovered = status?.connected === true && Number(status.cookieStatus) === 1
        if (recovered) {
          this.resolveManualVerification(accountId)
        }
        return recovered
      } catch {
        return false
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
      const expiresAt = normalizeExpiresAt(data?.expiresAt, data?.timeoutSeconds)
      this.manualVerification = {
        visible: data?.visible !== false,
        accountId,
        verificationType: data?.verificationType || '人工验证',
        message: data?.message || '账号登录需要人工处理',
        detail: data?.detail || '',
        screenshotUrl: appendImageVersion(rawUrl),
        updatedAt: Date.now(),
        expiresAt,
        confirming: false,
        lastError: ''
      }
      this.persistManualVerification()
    },
    resolveManualVerification(accountId?: number | string | null) {
      const resolvedAccountId = normalizeAccountId(accountId)
      if (resolvedAccountId && this.manualVerification.accountId && resolvedAccountId !== this.manualVerification.accountId) {
        return
      }
      this.clearManualVerification()
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
      this.persistManualVerification()
    },
    openManualVerification() {
      if (!this.manualVerification.accountId) return
      this.manualVerification.visible = true
      this.persistManualVerification()
    },
    closeManualVerification() {
      this.manualVerification.visible = false
      this.persistManualVerification()
    },
    async confirmManualVerificationDone() {
      const accountId = this.manualVerification.accountId
      if (!accountId || this.manualVerification.confirming) return false
      this.manualVerification.confirming = true
      this.manualVerification.lastError = ''
      this.persistManualVerification()
      try {
        const res = await confirmManualVerification(accountId)
        if (res.code !== 0 && res.code !== 200) {
          throw new Error(res.msg || String(res.data || '') || '人工验证确认失败')
        }
        this.clearManualVerification()
        this.pushToast({
          kind: 'system',
          title: '人工验证已完成',
          content: String(res.data || res.msg || 'Cookie 已读取并写回'),
          accountId,
          level: 'success'
        })
        return true
      } catch (error: any) {
        this.manualVerification.confirming = false
        this.manualVerification.lastError = error?.message || '人工验证确认失败'
        this.refreshManualVerificationImage()
        this.persistManualVerification()
        return false
      }
    },
    persistManualVerification() {
      try {
        if (!this.manualVerification.accountId) {
          window.localStorage.removeItem(MANUAL_VERIFICATION_STORAGE_KEY)
          return
        }
        window.localStorage.setItem(
          MANUAL_VERIFICATION_STORAGE_KEY,
          JSON.stringify({
            ...this.manualVerification,
            visible: false,
            confirming: false
          })
        )
      } catch {
        // local persistence is best-effort
      }
    },
    restoreManualVerification() {
      try {
        const raw = window.localStorage.getItem(MANUAL_VERIFICATION_STORAGE_KEY)
        if (!raw) return
        const saved = JSON.parse(raw)
        const accountId = normalizeAccountId(saved?.accountId)
        if (!accountId) return
        this.manualVerification = {
          visible: false,
          accountId,
          verificationType: saved?.verificationType || '人工验证',
          message: saved?.message || '账号登录需要人工处理',
          detail: saved?.detail || '',
          screenshotUrl: saved?.screenshotUrl || defaultManualImageUrl(accountId),
          updatedAt: Number(saved?.updatedAt || Date.now()),
          expiresAt: Number(saved?.expiresAt || Date.now() + DEFAULT_MANUAL_TIMEOUT_MS),
          confirming: false,
          lastError: saved?.lastError || ''
        }
      } catch {
        window.localStorage.removeItem(MANUAL_VERIFICATION_STORAGE_KEY)
      }
    },
    clearManualVerification() {
      this.manualVerification = {
        visible: false,
        accountId: null,
        verificationType: '',
        message: '',
        detail: '',
        screenshotUrl: '',
        updatedAt: 0,
        expiresAt: 0,
        confirming: false,
        lastError: ''
      }
      try {
        window.localStorage.removeItem(MANUAL_VERIFICATION_STORAGE_KEY)
      } catch {
        // ignore
      }
    }
  }
})

function shouldToastLog(log: NotificationLog) {
  if (log.eventType === 'test') return false
  return log.status !== 0
}

function isRecoverableAccountStateNotification(source: NotificationLike | null | undefined) {
  return source?.eventType === 'cookie_expire'
    || source?.eventType === 'captcha_required'
    || source?.type === 'verification_required'
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

function normalizeNotificationAccountId(source: NotificationLike | null | undefined): number | null {
  const direct = normalizeAccountId(source?.accountId)
  if (direct) return direct
  const content = String(source?.content || source?.message || source?.detail || '')
  const matched = content.match(/账号ID[：:]\s*(\d+)/)
  return matched ? normalizeAccountId(matched[1]) : null
}

function appendImageVersion(url: string) {
  if (!url) return ''
  return `${url}${url.includes('?') ? '&' : '?'}_t=${Date.now()}`
}

function defaultManualImageUrl(accountId: number | null) {
  if (!accountId) return ''
  return `/api/captcha/debug-image/latest?xianyuAccountId=${accountId}`
}

function normalizeExpiresAt(value: unknown, timeoutSeconds: unknown) {
  const parsed = Number(value)
  if (Number.isFinite(parsed) && parsed > Date.now()) return parsed
  const seconds = Number(timeoutSeconds)
  if (Number.isFinite(seconds) && seconds > 0) return Date.now() + seconds * 1000
  return Date.now() + DEFAULT_MANUAL_TIMEOUT_MS
}

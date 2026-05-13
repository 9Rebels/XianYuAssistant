import { request } from '@/utils/request'

export interface NotificationLog {
  id: number
  channel: string
  eventType: string
  title: string
  content: string
  status: number
  errorMessage?: string
  createTime: string
}

export function getNotificationLogs() {
  return request<NotificationLog[]>({
    url: '/notification/logs',
    method: 'POST',
    data: {}
  })
}

export function getLatestNotifications(data: { afterId?: number | null; limit?: number }) {
  return request<NotificationLog[]>({
    url: '/notification/latest',
    method: 'POST',
    data
  })
}

export function testNotification() {
  return request<string>({
    url: '/notification/test',
    method: 'POST',
    data: {}
  })
}

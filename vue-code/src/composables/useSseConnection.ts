import { ref } from 'vue'
import { getAuthToken } from '@/utils/request'

export type SseEventType = 'message' | 'notification' | 'connection' | 'stats'

type SseHandler = (data: any) => void

const handlers = new Map<SseEventType, Set<SseHandler>>()
let eventSource: EventSource | null = null
const connected = ref(false)
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

function getBaseUrl(): string {
  const isDev = import.meta.env.DEV
  return isDev ? '/api/sse/subscribe' : '/api/sse/subscribe'
}

function doConnect() {
  if (eventSource) return

  const token = getAuthToken()
  if (!token) return

  const url = `${getBaseUrl()}?token=${encodeURIComponent(token)}`
  eventSource = new EventSource(url)

  eventSource.onopen = () => {
    connected.value = true
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  eventSource.onerror = () => {
    connected.value = false
    cleanup()
    reconnectTimer = setTimeout(doConnect, 5000)
  }

  const eventTypes: SseEventType[] = ['message', 'notification', 'connection', 'stats']
  for (const type of eventTypes) {
    eventSource.addEventListener(type, (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data)
        handlers.get(type)?.forEach(fn => fn(data))
      } catch (e) {
        // ignore parse errors
      }
    })
  }
}

function cleanup() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

export function useSseConnection() {
  const connect = () => doConnect()

  const disconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    cleanup()
    connected.value = false
  }

  const on = (type: SseEventType, handler: SseHandler): (() => void) => {
    if (!handlers.has(type)) handlers.set(type, new Set())
    handlers.get(type)!.add(handler)
    return () => { handlers.get(type)?.delete(handler) }
  }

  return { connect, disconnect, connected, on }
}

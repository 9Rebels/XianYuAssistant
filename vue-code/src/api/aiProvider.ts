import { getAuthToken } from '@/utils/request'

export interface AiProvider {
  id: number
  name: string
  apiKey: string
  baseUrl: string
  model: string
  isActive: number
  enabled: number
  sortOrder: number
}

export interface AiProviderSaveReq {
  id?: number
  name: string
  apiKey: string
  baseUrl: string
  model: string
  sortOrder?: number
}

export interface AiProviderTestReq {
  id?: number
  apiKey?: string
  baseUrl?: string
  model?: string
}

export interface AiProviderTestResp {
  ok: boolean
  durationMs: number
  responseSummary: string
  message: string
}

export interface AiProviderModelsResp {
  models: string[]
}

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getAuthToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  return headers
}

async function post<T>(url: string, data: unknown): Promise<T> {
  const resp = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(data)
  })
  const json = await resp.json()
  if (json.code !== 200 && json.code !== 0) {
    throw new Error(json.msg || json.message || '请求失败')
  }
  return json.data
}

export function listAiProviders(): Promise<AiProvider[]> {
  return post('/api/ai-provider/list', {})
}

export function saveAiProvider(data: AiProviderSaveReq): Promise<void> {
  return post('/api/ai-provider/save', data)
}

export function deleteAiProvider(id: number): Promise<void> {
  return post('/api/ai-provider/delete', { id })
}

export function activateAiProvider(id: number): Promise<void> {
  return post('/api/ai-provider/activate', { id })
}

export function testAiProvider(data: AiProviderTestReq): Promise<AiProviderTestResp> {
  return post('/api/ai-provider/test', data)
}

export function getAiProviderModels(data: AiProviderTestReq): Promise<AiProviderModelsResp> {
  return post('/api/ai-provider/models', data)
}

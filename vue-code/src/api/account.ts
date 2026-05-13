import { request } from '@/utils/request'
import type { ApiResponse, Account } from '@/types'

// 获取账号列表
export function getAccountList() {
  return request<{ accounts: Account[]; total?: number }>({
    url: '/account/list',
    method: 'POST',
    data: {}
  })
}

// 添加账号
export function addAccount(data: Partial<Account>) {
  return request({
    url: '/account/add',
    method: 'POST',
    data
  })
}

// 更新账号
export function updateAccount(data: Partial<Account>) {
  return request({
    url: '/account/update',
    method: 'POST',
    data
  })
}

// 手动刷新账号资料
export function refreshAccountProfile(accountId: number) {
  return request<{ account: Account; message: string }>({
    url: '/account/refreshProfile',
    method: 'POST',
    data: { accountId },
    timeout: 60000
  })
}

export interface ItemPolishResult {
  success: boolean
  xianyuAccountId: number
  total: number
  polished: number
  failed: number
  message: string
  recoveryAttempted?: boolean
  needCaptcha?: boolean
  needManual?: boolean
  manualVerifyUrl?: string
  captchaUrl?: string
  sessionId?: string
  results: Array<{
    xyGoodId: string
    title: string
    success: boolean
    error?: string
    recoveryAttempted?: boolean
    needCaptcha?: boolean
    needManual?: boolean
    manualVerifyUrl?: string
    captchaUrl?: string
    sessionId?: string
  }>
}

export interface ItemPolishTask {
  id?: number
  xianyuAccountId: number
  enabled: number
  runHour: number
  randomDelayMaxMinutes: number
  nextRunTime?: string
  lastRunTime?: string
  lastResult?: string
}

export function runItemPolish(accountId: number) {
  return request<ItemPolishResult>({
    url: '/item-polish/run',
    method: 'POST',
    data: { xianyuAccountId: accountId },
    timeout: 300000
  })
}

export function getItemPolishTask(accountId: number) {
  return request<{ task: ItemPolishTask | null }>({
    url: '/item-polish/task/get',
    method: 'POST',
    data: { xianyuAccountId: accountId }
  })
}

export function saveItemPolishTask(data: {
  xianyuAccountId: number
  enabled: number
  runHour: number
  randomDelayMaxMinutes: number
}) {
  return request<{ task: ItemPolishTask }>({
    url: '/item-polish/task/save',
    method: 'POST',
    data
  })
}

// 删除账号
export function deleteAccount(data: { id: number }) {
  return request({
    url: '/account/delete',
    method: 'POST',
    data: {
      accountId: data.id
    }
  })
}

// 手动添加账号
export function manualAddAccount(data: { accountNote?: string; cookie: string }) {
  return request({
    url: '/account/manualAdd',
    method: 'POST',
    data
  })
}

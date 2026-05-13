import { request } from '@/utils/request'

export interface AutoReplyRule {
  id: number
  xianyuAccountId: number
  xyGoodsId?: string | null
  ruleName?: string
  keywords?: string
  matchType: number
  replyType: number
  replyContent?: string
  imageUrls?: string
  priority: number
  enabled: number
  isDefault: number
  createTime?: string
  updateTime?: string
}

export interface AutoReplyRuleReq {
  id?: number
  xianyuAccountId: number
  xyGoodsId?: string | null
  ruleName?: string
  keywords?: string
  matchType?: number
  replyType?: number
  replyContent?: string
  imageUrls?: string
  priority?: number
  enabled?: number
  isDefault?: number
}

export function listAutoReplyRules(data: {
  xianyuAccountId: number
  xyGoodsId?: string
  includeGlobal?: boolean
}) {
  return request<AutoReplyRule[]>({
    url: '/auto-reply-rule/list',
    method: 'POST',
    data
  })
}

export function saveAutoReplyRule(data: AutoReplyRuleReq) {
  return request<AutoReplyRule>({
    url: '/auto-reply-rule/save',
    method: 'POST',
    data
  })
}

export function batchImportAutoReplyRules(rules: AutoReplyRuleReq[]) {
  return request<number>({
    url: '/auto-reply-rule/batchImport',
    method: 'POST',
    data: { rules }
  })
}

export function deleteAutoReplyRule(id: number) {
  return request<void>({
    url: '/auto-reply-rule/delete',
    method: 'POST',
    params: { id }
  })
}

import { request } from '@/utils/request';
import type { ApiResponse } from '@/types';

// 自动发货配置
export interface AutoDeliveryConfig {
  id: number;
  ruleCount?: number;
  xianyuAccountId: number;
  xianyuGoodsId: number;
  xyGoodsId: string;
  deliveryMode: number; // 1-文本发货，2-卡密发货，3-自定义发货，4-API发货
  ruleName?: string;
  matchKeyword?: string;
  matchType?: number;
  priority?: number;
  enabled?: number;
  stock?: number;
  stockWarnThreshold?: number;
  totalDelivered?: number;
  todayDelivered?: number;
  lastDeliveryTime?: string;
  autoDeliveryContent: string;
  kamiConfigIds?: string; // 卡密发货绑定的配置ID列表（逗号分隔）
  kamiDeliveryTemplate?: string; // 卡密发货文案模板，使用{kmKey}占位符
  autoDeliveryImageUrl?: string;
  postDeliveryText?: string;
  autoConfirmShipment?: number;
  deliveryDelaySeconds?: number;
  triggerPaymentEnabled?: number;
  triggerBargainEnabled?: number;
  apiAllocateUrl?: string;
  apiConfirmUrl?: string;
  apiReturnUrl?: string;
  apiHeaderValue?: string;
  apiRequestExtras?: string;
  apiDeliveryTemplate?: string;
  createTime: string;
  updateTime: string;
}

export interface SaveAutoDeliveryConfigReq {
  id?: number;
  xianyuAccountId: number;
  xianyuGoodsId?: number;
  xyGoodsId: string;
  deliveryMode: number;
  ruleName?: string;
  matchKeyword?: string;
  matchType?: number;
  priority?: number;
  enabled?: number;
  stock?: number;
  stockWarnThreshold?: number;
  autoDeliveryContent: string;
  kamiConfigIds?: string;
  kamiDeliveryTemplate?: string;
  autoDeliveryImageUrl?: string;
  postDeliveryText?: string;
  autoConfirmShipment?: number;
  deliveryDelaySeconds?: number;
  triggerPaymentEnabled?: number;
  triggerBargainEnabled?: number;
  apiAllocateUrl?: string;
  apiConfirmUrl?: string;
  apiReturnUrl?: string;
  apiHeaderValue?: string;
  apiRequestExtras?: string;
  apiDeliveryTemplate?: string;
}

// 查询配置请求
export interface GetAutoDeliveryConfigReq {
  xianyuAccountId: number;
  xyGoodsId?: string;
}

// 保存或更新自动发货配置
export function saveOrUpdateAutoDeliveryConfig(data: SaveAutoDeliveryConfigReq) {
  return request<AutoDeliveryConfig>({
    url: '/auto-delivery-config/save',
    method: 'POST',
    data
  });
}

// 查询自动发货配置
export function getAutoDeliveryConfig(data: GetAutoDeliveryConfigReq) {
  return request<AutoDeliveryConfig>({
    url: '/auto-delivery-config/get',
    method: 'POST',
    data
  });
}

// 根据账号ID查询所有配置
export function getAutoDeliveryConfigsByAccountId(xianyuAccountId: number) {
  return request<AutoDeliveryConfig[]>({
    url: '/auto-delivery-config/list',
    method: 'POST',
    params: { xianyuAccountId }
  });
}

export function getAutoDeliveryRulesByGoods(xianyuAccountId: number, xyGoodsId: string) {
  return request<AutoDeliveryConfig[]>({
    url: '/auto-delivery-config/goods-rules',
    method: 'POST',
    params: { xianyuAccountId, xyGoodsId }
  });
}

// 删除自动发货配置
export function deleteAutoDeliveryConfig(xianyuAccountId: number, xyGoodsId: string) {
  return request({
    url: '/auto-delivery-config/delete',
    method: 'POST',
    params: { xianyuAccountId, xyGoodsId }
  });
}

export function deleteAutoDeliveryRule(id: number) {
  return request<null>({
    url: '/auto-delivery-config/delete-rule',
    method: 'POST',
    params: { id }
  });
}

export function updateAutoDeliveryConfigEnabled(id: number, enabled: number) {
  return request<null>({
    url: '/auto-delivery-config/enabled',
    method: 'POST',
    params: { id, enabled }
  });
}

export function updateAutoDeliveryConfigStock(id: number, stock: number) {
  return request<null>({
    url: '/auto-delivery-config/stock',
    method: 'POST',
    params: { id, stock }
  });
}

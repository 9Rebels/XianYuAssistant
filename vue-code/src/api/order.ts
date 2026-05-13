import { request } from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface DeliveryRecordQueryReq {
  xianyuAccountId?: number
  xyGoodsId?: string
  state?: number
  pageNum?: number
  pageSize?: number
}

export interface DeliveryRecordVO {
  id: number
  xianyuAccountId?: number
  xyGoodsId: string
  goodsTitle?: string
  buyerUserName?: string
  sid?: string
  content?: string
  state: number
  failReason?: string
  confirmState: number
  orderId?: string
  createTime: string
}

export interface DeliveryRecordPageResult {
  records: DeliveryRecordVO[]
  total: number
  pageNum: number
  pageSize: number
}

export interface OrderQueryReq {
  xianyuAccountId?: number
  xyGoodsId?: string
  orderStatus?: number
  pageNum?: number
  pageSize?: number
}

export interface OrderVO {
  id: number
  accountRemark?: string
  orderId?: string
  goodsTitle?: string
  sId?: string
  createTime?: number
  payTime?: number
  deliveryTime?: number
  autoDeliverySuccess?: boolean
  orderStatus?: number
  orderStatusText?: string
  buyerUserName?: string
  orderAmountText?: string
  xyGoodsId?: string
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  receiverCity?: string
}

export interface OrderPageResult {
  records: OrderVO[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

export interface BatchRefreshReq {
  xianyuAccountId: number
  orderIds: string[]
  headless?: boolean
}

export interface OrderDetailResult {
  orderId: string
  success: boolean
  error?: string
  data?: {
    orderStatus: number
    statusText: string
    itemTitle: string
    price: string
    buyerUserName: string
    orderCreateTime: number
    orderPayTime: number
    orderDeliveryTime: number
    receiverName: string
    receiverPhone: string
    receiverAddress: string
    receiverCity: string
    canRate: boolean
  }
}

export interface BatchRefreshResult {
  total: number
  successCount: number
  failCount: number
  results: Array<{
    orderId: string
    success: boolean
    error?: string
    data?: OrderDetailResult['data']
  }>
}

export interface SoldOrderSyncReq {
  xianyuAccountId: number
}

export interface SoldOrderSyncResult {
  totalCount?: number
  fetchedCount?: number
  insertedCount?: number
  updatedCount?: number
  pageCount?: number
  nextPage?: boolean
  lastEndRow?: string
}

export function queryDeliveryRecordList(data: DeliveryRecordQueryReq) {
  return request<DeliveryRecordPageResult>({
    url: '/items/autoDeliveryRecords',
    method: 'POST',
    data
  })
}

export function queryOrderList(data: OrderQueryReq) {
  return request<OrderPageResult>({
    url: '/order/list',
    method: 'POST',
    data
  })
}

export function confirmShipment(data: { xianyuAccountId: number; orderId: string }) {
  return request<string>({
    url: '/order/confirmShipment',
    method: 'POST',
    data
  })
}

export function batchRefreshOrders(data: BatchRefreshReq) {
  return request<BatchRefreshResult>({
    url: '/order/batchRefresh',
    method: 'POST',
    data
  })
}

export function syncSoldOrders(data: SoldOrderSyncReq) {
  return request<SoldOrderSyncResult>({
    url: '/order/syncSoldOrders',
    method: 'POST',
    data
  })
}

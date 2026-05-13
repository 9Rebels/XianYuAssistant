import { request } from '@/utils/request'

export interface TrendDataPoint {
  date: string
  totalOrders: number
  pendingDeliveryOrders: number
  paidOrders: number
  shippedOrders: number
  completedOrders: number
  cancelledOrders: number
}

export interface RegionData {
  city: string
  orderCount: number
  percentage: number
}

export interface GoodsRankingData {
  xyGoodsId: string
  goodsTitle: string
  orderCount: number
  totalAmount: number
  totalAmountYuan?: number
}

export interface StatusDistribution {
  status: number
  statusText: string
  count: number
  percentage: number
}

export interface OverallStatistics {
  totalOrders: number
  pendingDeliveryOrders: number
  paidOrders: number
  shippedOrders: number
  completedOrders: number
  cancelledOrders: number
  totalAmount: number
  totalAmountYuan: number
  avgAmount: number
  avgAmountYuan: number
}

export interface CompleteStatistics {
  overall: OverallStatistics
  trend: TrendDataPoint[]
  regionDistribution: RegionData[]
  goodsRanking: GoodsRankingData[]
  statusDistribution: StatusDistribution[]
}

export function getOrderTrend(params: {
  xianyuAccountId?: number
  startDate?: number
  endDate?: number
  days?: number
}) {
  return request<TrendDataPoint[]>({
    url: '/statistics/order-trend',
    method: 'GET',
    params
  })
}

export function getRegionDistribution(params: {
  xianyuAccountId?: number
  topN?: number
}) {
  return request<RegionData[]>({
    url: '/statistics/region-distribution',
    method: 'GET',
    params
  })
}

export function getGoodsRanking(params: {
  xianyuAccountId?: number
  topN?: number
}) {
  return request<GoodsRankingData[]>({
    url: '/statistics/goods-ranking',
    method: 'GET',
    params
  })
}

export function getStatusDistribution(params: {
  xianyuAccountId?: number
}) {
  return request<StatusDistribution[]>({
    url: '/statistics/status-distribution',
    method: 'GET',
    params
  })
}

export function getOverallStatistics(params: {
  xianyuAccountId?: number
}) {
  return request<OverallStatistics>({
    url: '/statistics/overall',
    method: 'GET',
    params
  })
}

export function getCompleteStatistics(params: {
  xianyuAccountId?: number
  days?: number
  topN?: number
}) {
  return request<CompleteStatistics>({
    url: '/statistics/complete',
    method: 'GET',
    params
  })
}

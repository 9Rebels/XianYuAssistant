import { request } from '@/utils/request'

export interface DashboardStats {
  accountCount: number
  itemCount: number
  sellingItemCount: number
  offShelfItemCount: number
  soldItemCount: number
}

/**
 * 获取首页统计数据
 */
export function getDashboardStats() {
  return request<DashboardStats>({
    url: '/dashboard/stats',
    method: 'POST',
    data: {}
  })
}

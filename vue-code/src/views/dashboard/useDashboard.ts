import { ref, reactive } from 'vue'
import { getDashboardStats } from '@/api/dashboard'

export function useDashboard() {
  const loading = ref(false)
  
  const stats = reactive({
    accountCount: 0,
    goodsCount: 0,
    onlineGoodsCount: 0
  })

  const loadStatistics = async () => {
    loading.value = true
    try {
      const res = await getDashboardStats()
      if (res.code === 0 || res.code === 200) {
        if (res.data) {
          stats.accountCount = res.data.accountCount || 0
          stats.goodsCount = res.data.itemCount || 0
          stats.onlineGoodsCount = res.data.sellingItemCount || 0
        }
      }
    } catch (error) {
      console.error('加载统计数据失败:', error)
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    stats,
    loadStatistics
  }
}
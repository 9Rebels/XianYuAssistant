import { ref, reactive, computed, onUnmounted } from 'vue'
import { getAccountList } from '@/api/account'
import {
  getGoodsList,
  refreshGoodsByStatus,
  updateAutoDeliveryStatus,
  updateAutoReplyStatus,
  offShelfItem,
  republishItem,
  deleteItem,
  remoteDeleteItem,
  updateItemPrice,
  updateItemStock,
  getSyncProgress
} from '@/api/goods'
import { getSetting } from '@/api/setting'
import { showSuccess, showError, showInfo, showConfirm } from '@/utils'
import { ElMessageBox } from 'element-plus'
import { getGoodsStatusText, formatPrice, formatTime } from '@/utils'
import { getAccountDisplayName } from '@/utils/accountDisplay'
import type { Account } from '@/types'
import type { GoodsItemWithConfig, SyncProgressResponse } from '@/api/goods'


const getDeliveryTypeText = (type?: number) => {
  if (type === 2) return '卡密'
  if (type === 3) return '自定义'
  if (type === 4) return 'API'
  return '文本'
}

export function useGoodsManager() {
  const loading = ref(false)
  const refreshing = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const statusFilter = ref<string>('')
  const searchKeyword = ref('')
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const currentPage = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const featureOffShelfEnabled = ref(true)
  const featureDeleteEnabled = ref(true)

  const dialogs = reactive({
    detail: false,
    filter: false,
    configMenu: false,
    syncMenu: false
  })

  const selectedGoodsId = ref<string>('')
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
  const configAction = ref<'delivery' | 'reply' | null>(null)

  const syncProgress = ref<SyncProgressResponse | null>(null)
  const syncing = ref(false)
  let syncProgressTimer: ReturnType<typeof setInterval> | null = null
  let searchTimer: ReturnType<typeof setTimeout> | null = null

  const stopSyncPolling = () => {
    if (syncProgressTimer) {
      clearInterval(syncProgressTimer)
      syncProgressTimer = null
    }
  }

  const stopSearchTimer = () => {
    if (searchTimer) {
      clearTimeout(searchTimer)
      searchTimer = null
    }
  }

  const pollSyncProgress = async (syncId: string) => {
    try {
      const response = await getSyncProgress(syncId)
      if (response.code === 0 || response.code === 200) {
        if (response.data) {
          syncProgress.value = response.data
          if (response.data.isCompleted || !response.data.isRunning) {
            stopSyncPolling()
            syncing.value = false
            refreshing.value = false
            if (response.data.successCount && response.data.successCount > 0) {
              showSuccess(`详情同步完成: 成功${response.data.successCount}个, 失败${response.data.failedCount}个`)
            }
            await loadGoods()
          }
        }
      }
    } catch (error) {
      console.error('获取同步进度失败:', error)
    }
  }

  const startSyncPolling = (syncId: string) => {
    stopSyncPolling()
    syncing.value = true
    syncProgressTimer = setInterval(() => {
      pollSyncProgress(syncId)
    }, 1000)
  }

  onUnmounted(() => {
    stopSyncPolling()
    stopSearchTimer()
  })

  // Computed
  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
  const accountName = computed(() => {
    if (!selectedAccountId.value) return ''
    const acc = accounts.value.find(a => a.id === selectedAccountId.value)
    return getAccountDisplayName(acc, '')
  })
  const selectedAccount = computed(() =>
    accounts.value.find(account => account.id === selectedAccountId.value) || null
  )
  const selectedAccountIsFishShop = computed(() => selectedAccount.value?.fishShopUser === true)

  const loadGoodsOperationSettings = async () => {
    try {
      const [offShelfRes, deleteRes] = await Promise.all([
        getSetting({ settingKey: 'goods_off_shelf_enabled' }),
        getSetting({ settingKey: 'goods_delete_enabled' })
      ])
      featureOffShelfEnabled.value = settingEnabled(offShelfRes.data?.settingValue, true)
      featureDeleteEnabled.value = settingEnabled(deleteRes.data?.settingValue, true)
    } catch (error) {
      console.error('加载商品操作设置失败:', error)
      featureOffShelfEnabled.value = true
      featureDeleteEnabled.value = true
    }
  }

  const settingEnabled = (value: string | null | undefined, defaultValue: boolean) => {
    if (value === null || value === undefined || value === '') return defaultValue
    return value === '1' || value.toLowerCase() === 'true'
  }

  // 加载账号列表
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id || null
          await loadGoods()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  // 加载商品列表
  const loadGoods = async () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }

    loading.value = true
    try {
      const params: any = {
        xianyuAccountId: selectedAccountId.value,
        pageNum: currentPage.value,
        pageSize: pageSize.value
      }
      if (statusFilter.value !== '') {
        params.status = parseInt(statusFilter.value)
      }
      const keyword = searchKeyword.value.trim()
      if (keyword) {
        params.keyword = keyword
      }
      const response = await getGoodsList(params)
      if (response.code === 0 || response.code === 200) {
        goodsList.value = response.data?.itemsWithConfig || []
        total.value = response.data?.totalCount || 0
      }
    } catch (error: any) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      loading.value = false
    }
  }

  // 刷新商品数据
  const handleRefresh = async (syncStatus = 0) => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }
    dialogs.syncMenu = false
    refreshing.value = true
    try {
      const response = await refreshGoodsByStatus(selectedAccountId.value, syncStatus)
      if (response.code === 0 || response.code === 200) {
        if (response.data && response.data.success) {
          const label = response.data.syncLabel || (syncStatus === 2 ? '已售出' : '在售')
          statusFilter.value = syncStatus === 2 ? '2' : '0'
          currentPage.value = 1
          showSuccess(`${label}商品同步成功`)
          if (response.data.syncId) {
            startSyncPolling(response.data.syncId)
          } else {
            await loadGoods()
            refreshing.value = false
          }
        } else {
          const data = response.data
          if (data?.needCaptcha || data?.needManual) {
            showError(data.message || '自动滑块失败，请人工更新 Cookie')
          } else if (data?.recoveryAttempted) {
            showError(data.message || '已尝试自动刷新和验证，仍失败，请人工更新Cookie')
          } else {
            showError(data?.message || '刷新商品数据失败')
          }
          refreshing.value = false
        }
      }
    } catch (error: any) {
      console.error('刷新商品数据失败:', error)
      refreshing.value = false
    }
  }

  const openSyncMenu = () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }
    dialogs.syncMenu = true
  }

  // 账号变更
  const handleAccountChange = () => {
    currentPage.value = 1
    loadGoods()
  }

  // 状态筛选
  const handleStatusFilter = () => {
    currentPage.value = 1
    loadGoods()
  }

  const handleSearch = () => {
    stopSearchTimer()
    currentPage.value = 1
    loadGoods()
  }

  const handleSearchInput = () => {
    stopSearchTimer()
    searchTimer = setTimeout(() => {
      handleSearch()
    }, 350)
  }

  const handleClearSearch = () => {
    if (!searchKeyword.value) return
    searchKeyword.value = ''
    handleSearch()
  }

  const exportGoodsCsv = () => {
    if (goodsList.value.length === 0) {
      showError('当前没有可导出的商品')
      return
    }
    const rows = [
      ['商品ID', '标题', '价格', '库存', '想要', '状态', '自动发货', '自动回复', '发货类型', '创建时间', '更新时间'],
      ...goodsList.value.map(goods => [
        goods.item.xyGoodId || '',
        goods.item.title || '',
        goods.item.soldPrice || '',
        goods.item.quantity ?? '',
        goods.item.wantCount ?? '',
        getGoodsStatusText(goods.item.status).text,
        goods.xianyuAutoDeliveryOn === 1 ? '已开启' : '未开启',
        goods.xianyuAutoReplyOn === 1 ? '已开启' : '未开启',
        getDeliveryTypeText(goods.autoDeliveryType),
        formatTime(goods.item.createdTime),
        formatTime(goods.item.updatedTime)
      ])
    ]
    downloadCsv(rows, `goods-${selectedAccountId.value || 'all'}-${Date.now()}.csv`)
  }

  const downloadCsv = (rows: unknown[][], filename: string) => {
    const csv = rows.map(row => row.map(escapeCsvCell).join(',')).join('\n')
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  }

  const escapeCsvCell = (value: unknown) => {
    const text = String(value ?? '')
    return `"${text.replace(/"/g, '""')}"`
  }

  // 分页
  const handlePageChange = (page: number) => {
    currentPage.value = page
    loadGoods()
  }

  // 查看详情
  const viewDetail = (xyGoodId: string) => {
    selectedGoodsId.value = xyGoodId
    configAction.value = null
    dialogs.detail = true
  }

  // 打开配置菜单
  const configAutoDelivery = (item: GoodsItemWithConfig) => {
    selectedGoods.value = item
    dialogs.configMenu = true
  }

  const openGoodsConfig = (action: 'delivery' | 'reply') => {
    if (!selectedGoods.value) return
    selectedGoodsId.value = selectedGoods.value.item.xyGoodId
    configAction.value = action
    dialogs.configMenu = false
    dialogs.detail = true
  }

  // 切换自动发货
  const toggleAutoDelivery = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    try {
      const response = await updateAutoDeliveryStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoDeliveryOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动发货${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoDeliveryOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      item.xianyuAutoDeliveryOn = value ? 0 : 1
    }
  }

  // 切换自动回复
  const toggleAutoReply = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    try {
      const response = await updateAutoReplyStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoReplyOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动回复${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoReplyOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      item.xianyuAutoReplyOn = value ? 0 : 1
    }
  }

  const handleOffShelf = async (item: GoodsItemWithConfig) => {
    if (!selectedAccountId.value) return
    if (!featureOffShelfEnabled.value) {
      showError('商品下架功能已关闭')
      return
    }
    try {
      await showConfirm(`确定要下架商品 "${item.item.title}" 吗？`, '下架确认')
      const response = await offShelfItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('商品下架成功')
        item.item.status = 1
        await loadGoods()
      } else {
        throw new Error(response.msg || '下架失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('下架失败: ' + (error.message || '未知错误'))
    }
  }

  const toggleRemoteOffShelf = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    if (!featureOffShelfEnabled.value) {
      showError(value ? '商品上架功能已关闭' : '商品下架功能已关闭')
      return
    }
    if (value) {
      try {
        await showConfirm(`确定要恢复上架商品 "${item.item.title}" 吗？会保留原商品ID。`, '上架确认')
        const response = await republishItem({
          xianyuAccountId: selectedAccountId.value,
          xyGoodsId: item.item.xyGoodId
        })
        if (response.code === 0 || response.code === 200) {
          showSuccess('商品已恢复在售')
          item.item.status = 0
          await loadGoods()
        } else {
          throw new Error(response.msg || '上架失败')
        }
      } catch (error: any) {
        if (error === 'cancel' || error?.message === 'cancel') return
        showError('上架失败: ' + (error.message || '未知错误'))
      }
      return
    }
    try {
      await showConfirm(`确定要下架商品 "${item.item.title}" 吗？`, '下架确认')
      const response = await offShelfItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('闲鱼商品下架成功，本地商品已保留')
        item.item.status = 1
        await loadGoods()
      } else {
        throw new Error(response.msg || '下架失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('下架失败: ' + (error.message || '未知错误'))
    }
  }

  const toggleRemoteDelete = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    if (!value) {
      showInfo('闲鱼删除不可直接恢复')
      return
    }
    if (!featureDeleteEnabled.value) {
      showError('商品删除功能已关闭')
      return
    }
    try {
      await showConfirm(`确定要在闲鱼删除商品 "${item.item.title}" 吗？本地商品会保留。`, '闲鱼删除确认')
      const response = await remoteDeleteItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('闲鱼商品删除成功，本地商品已保留')
        item.item.status = 3
        await loadGoods()
      } else {
        throw new Error(response.msg || '删除失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('闲鱼删除失败: ' + (error.message || '未知错误'))
    }
  }

  const confirmDelete = async (item: GoodsItemWithConfig) => {
    if (!selectedAccountId.value) return
    try {
      await showConfirm(`确定只删除本地同步商品 "${item.item.title}" 吗？不会删除闲鱼商品。`, '本地删除确认')
      const response = await deleteItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('本地商品删除成功')
        await loadGoods()
      } else {
        throw new Error(response.msg || '删除失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('删除失败: ' + (error.message || '未知错误'))
    }
  }

  const handleUpdatePrice = async (item: GoodsItemWithConfig) => {
    if (!selectedAccountId.value) return
    if (!selectedAccountIsFishShop.value) {
      showError('当前账号不是鱼小铺，无法改价')
      return
    }
    try {
      const result = await ElMessageBox.prompt(`修改商品 "${item.item.title}" 的价格`, '改价', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: item.item.soldPrice || '',
        inputPlaceholder: '请输入新价格',
        inputPattern: /^(?!0+(?:\.0{1,2})?$)\d+(?:\.\d{1,2})?$/,
        inputErrorMessage: '请输入大于0且最多2位小数的价格'
      })
      const price = String(result.value || '').trim()
      const response = await updateItemPrice({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        price
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('商品改价成功')
        item.item.soldPrice = price
        await loadGoods()
      } else {
        throw new Error(response.msg || '改价失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('改价失败: ' + (error.message || '未知错误'))
    }
  }

  const handleUpdateStock = async (item: GoodsItemWithConfig) => {
    if (!selectedAccountId.value) return
    try {
      const result = await ElMessageBox.prompt(`调整商品 "${item.item.title}" 的库存`, '库存', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: String(item.item.quantity || 1),
        inputPlaceholder: '请输入库存',
        inputPattern: /^[1-9]\d*$/,
        inputErrorMessage: '库存必须为大于0的整数'
      })
      const quantity = Number(result.value)
      if (!Number.isInteger(quantity) || quantity <= 0) {
        showError('库存必须为大于0的整数')
        return
      }
      const response = await updateItemStock({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        quantity
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('商品库存修改成功')
        item.item.quantity = quantity
        await loadGoods()
      } else {
        throw new Error(response.msg || '改库存失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('改库存失败: ' + (error.message || '未知错误'))
    }
  }

  return {
    loading,
    refreshing,
    syncing,
    syncProgress,
    accounts,
    selectedAccountId,
    statusFilter,
    searchKeyword,
    goodsList,
    currentPage,
    pageSize,
    total,
    totalPages,
    accountName,
    selectedAccount,
    selectedAccountIsFishShop,
    featureOffShelfEnabled,
    featureDeleteEnabled,
    dialogs,
    selectedGoodsId,
    selectedGoods,
    configAction,
    loadGoodsOperationSettings,
    loadAccounts,
    loadGoods,
    handleRefresh,
    openSyncMenu,
    handleAccountChange,
    handleStatusFilter,
    handleSearch,
    handleSearchInput,
    handleClearSearch,
    exportGoodsCsv,
    handlePageChange,
    viewDetail,
    configAutoDelivery,
    openGoodsConfig,
    toggleAutoDelivery,
    toggleAutoReply,
    handleOffShelf,
    toggleRemoteOffShelf,
    toggleRemoteDelete,
    confirmDelete,
    handleUpdatePrice,
    handleUpdateStock,
    getGoodsStatusText,
    formatPrice,
    formatTime
  }
}

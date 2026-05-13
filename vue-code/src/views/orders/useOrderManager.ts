import { ref, reactive, computed, onMounted } from 'vue'
import { queryDeliveryRecordList, confirmShipment } from '@/api/order'
import { getAccountList } from '@/api/account'
import type { DeliveryRecordVO, DeliveryRecordQueryReq } from '@/api/order'
import type { Account } from '@/types'
import { showSuccess, showError, showConfirm } from '@/utils'
import { formatTime } from '@/utils'

export interface DeliveryRecordItem extends DeliveryRecordVO {
  confirming?: boolean
}

export function useOrderManager() {
  const loading = ref(false)
  const orderList = ref<DeliveryRecordItem[]>([])
  const total = ref(0)
  const accounts = ref<Account[]>([])

  const queryParams = reactive<DeliveryRecordQueryReq>({
    pageNum: 1,
    pageSize: 20
  })

  const dialogs = reactive({
    confirmShipment: false,
    filter: false
  })

  const confirmTarget = ref<DeliveryRecordItem | null>(null)

  const totalPages = computed(() => Math.ceil(total.value / (queryParams.pageSize || 20)))
  const selectedAccount = computed(() =>
    accounts.value.find(account => account.id === queryParams.xianyuAccountId)
  )
  const selectedAccountIsFishShop = computed(() => selectedAccount.value?.fishShopUser === true)

  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !queryParams.xianyuAccountId) {
          queryParams.xianyuAccountId = accounts.value[0]?.id
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  const handleAccountChange = () => {
    queryParams.pageNum = 1
    loadOrders()
  }

  const getStatusColor = (state: number) => {
    return state === 1 ? '#34c759' : '#ff3b30'
  }

  const getStatusBg = (state: number) => {
    return state === 1 ? 'rgba(52, 199, 89, 0.1)' : 'rgba(255, 59, 48, 0.1)'
  }

  const getStatusText = (state: number) => {
    return state === 1 ? '成功' : '失败'
  }

  const loadOrders = async () => {
    loading.value = true
    try {
      const response = await queryDeliveryRecordList(queryParams)
      orderList.value = (response.data?.records || []).map(item => ({
        ...item,
        confirming: false
      }))
      total.value = response.data?.total || 0
    } catch (error: any) {
      console.error('查询发货记录失败:', error)
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('查询发货记录失败: ' + (error.message || '未知错误'))
      }
      orderList.value = []
    } finally {
      loading.value = false
    }
  }

  const handleReset = () => {
    queryParams.pageNum = 1
    queryParams.xyGoodsId = undefined
    queryParams.state = undefined
    queryParams.xianyuAccountId = undefined
    loadOrders()
  }

  const handlePageChange = (page: number) => {
    queryParams.pageNum = page
    loadOrders()
  }

  const handleSizeChange = (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNum = 1
    loadOrders()
  }

  const copySId = (sid: string) => {
    navigator.clipboard.writeText(sid).then(() => {
      showSuccess('已复制')
    }).catch(() => {
      showError('复制失败')
    })
  }

  const exportOrdersCsv = () => {
    if (orderList.value.length === 0) {
      showError('当前没有可导出的发货记录')
      return
    }
    const rows = [
      ['订单ID', '商品ID', '商品名称', '买家', '发货状态', '确认状态', '发货内容', '失败原因', '创建时间'],
      ...orderList.value.map(order => [
        order.orderId || '',
        order.xyGoodsId || '',
        order.goodsTitle || '',
        order.buyerUserName || '',
        getDeliveryText(order.state),
        (order.confirmState || 0) === 1 ? '已确认' : '未确认',
        order.content || '',
        order.failReason || '',
        formatTime(order.createTime)
      ])
    ]
    downloadCsv(rows, `delivery-records-${Date.now()}.csv`)
  }

  const getDeliveryText = (state: number) => {
    if (state === 1) return '已发货'
    if (state === 0) return '待发货'
    return '失败'
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

  const handleConfirmShipment = async (row: DeliveryRecordItem) => {
    if (!row.orderId) {
      showError('订单ID为空')
      return
    }
    if ((row.confirmState || 0) === 1) {
      showSuccess('订单已确认发货，无需重复操作')
      return
    }
    try {
      await showConfirm(
        `确认订单 ${row.orderId} 已发货吗？`,
        '确认发货'
      )

      row.confirming = true
      await confirmShipment({
        xianyuAccountId: (row as any).xianyuAccountId,
        orderId: row.orderId
      })

      showSuccess('确认发货成功')
      loadOrders()
    } catch (error: any) {
      if (error === 'cancel') return
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('确认发货失败: ' + (error.message || '未知错误'))
      }
    } finally {
      row.confirming = false
    }
  }

  return {
    loading,
    orderList,
    total,
    accounts,
    selectedAccount,
    selectedAccountIsFishShop,
    queryParams,
    dialogs,
    confirmTarget,
    totalPages,
    loadAccounts,
    loadOrders,
    handleAccountChange,
    handleReset,
    handlePageChange,
    handleSizeChange,
    copySId,
    exportOrdersCsv,
    handleConfirmShipment,
    getStatusColor,
    getStatusBg,
    getStatusText,
    formatTime
  }
}

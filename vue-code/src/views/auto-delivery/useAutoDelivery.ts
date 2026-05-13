import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getGoodsList, updateAutoDeliveryStatus, updateAutoConfirmShipment } from '@/api/goods'
import {
  getAutoDeliveryConfig,
  getAutoDeliveryRulesByGoods,
  saveOrUpdateAutoDeliveryConfig,
  deleteAutoDeliveryRule,
  updateAutoDeliveryConfigEnabled,
  updateAutoDeliveryConfigStock,
  type AutoDeliveryConfig,
  type SaveAutoDeliveryConfigReq,
  type GetAutoDeliveryConfigReq
} from '@/api/auto-delivery-config'
import { getDataPanelStats, type DataPanelStats } from '@/api/data-panel'
import {
  getAutoDeliveryRecords,
  manualReturnApiDelivery,
  confirmShipment,
  triggerAutoDelivery,
  type AutoDeliveryRecordReq,
  type AutoDeliveryRecordResp,
  type ConfirmShipmentReq,
  type ManualReturnApiDeliveryReq,
  type TriggerAutoDeliveryReq
} from '@/api/auto-delivery-record'
import { formatTime as formatDateTime, showSuccess, showError, showInfo } from '@/utils'
import { getConnectionStatus } from '@/api/websocket'
import { ElMessage } from 'element-plus'
import {
  getKamiConfigsByAccountId,
  type KamiConfig
} from '@/api/kami-config'
import type { Account } from '@/types'
import type { GoodsItemWithConfig } from '@/api/goods'

const DEFAULT_API_ALLOCATE_URL = 'http://127.0.0.1:3000/api/delivery/v1/allocate'
const DEFAULT_API_CONFIRM_URL = 'http://127.0.0.1:3000/api/delivery/v1/confirm'
const DEFAULT_API_RETURN_URL = 'http://127.0.0.1:3000/api/delivery/v1/return'

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    showSuccess('已复制到剪贴板')
  }).catch(() => {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    showSuccess('已复制到剪贴板')
  })
}

export function useAutoDelivery() {
  const route = useRoute()
  const router = useRouter()

  const gotoConnection = () => router.push('/connection')
  ;(window as any).__gotoConnection = gotoConnection

  const loading = ref(false)
  const saving = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
  const currentConfig = ref<AutoDeliveryConfig | null>(null)
  const deliveryRules = ref<AutoDeliveryConfig[]>([])
  const activeRuleId = ref<number | null>(null)
  const goodsKeyword = ref('')

  // Goods list scroll loading
  const goodsCurrentPage = ref(1)
  const goodsTotal = ref(0)
  const goodsLoading = ref(false)
  const goodsListRef = ref<HTMLElement | null>(null)
  let goodsSearchTimer: ReturnType<typeof setTimeout> | null = null

  // Goods detail dialog
  const detailDialogVisible = ref(false)
  const selectedGoodsId = ref<string>('')

  // Config form
  const configForm = ref({
    deliveryMode: 1,
    ruleName: '',
    matchKeyword: '',
    matchType: 1,
    priority: 100,
    enabled: 1,
    stock: -1,
    stockWarnThreshold: 0,
    autoDeliveryContent: '',
    kamiConfigIds: '',
    kamiDeliveryTemplate: '',
    autoDeliveryImageUrl: '',
    postDeliveryText: '',
    autoConfirmShipment: 0,
    deliveryDelaySeconds: 0,
    triggerPaymentEnabled: 1,
    triggerBargainEnabled: 0,
    apiAllocateUrl: DEFAULT_API_ALLOCATE_URL,
    apiConfirmUrl: DEFAULT_API_CONFIRM_URL,
    apiReturnUrl: DEFAULT_API_RETURN_URL,
    apiHeaderValue: '',
    apiRequestExtras: '',
    apiDeliveryTemplate: ''
  })

  const kamiConfigOptions = ref<KamiConfig[]>([])
  const stats = ref<DataPanelStats | null>(null)

  const selectedKamiConfigId = computed({
    get: () => configForm.value.kamiConfigIds || '',
    set: (val: string) => { configForm.value.kamiConfigIds = val }
  })

  // Delivery records
  const recordsLoading = ref(false)
  const deliveryRecords = ref<any[]>([])
  const recordsTotal = ref(0)
  const recordsPageNum = ref(1)
  const recordsPageSize = ref(20)
  const recordsStateFilter = ref<number | null>(null)

  // Responsive
  const isMobile = ref(false)
  const mobileView = ref<'goods' | 'config'>('goods')

  // Confirm dialog
  const confirmDialog = ref({
    visible: false,
    title: '',
    message: '',
    type: 'danger' as 'danger' | 'primary',
    onConfirm: () => {}
  })

  // API hint panel
  const apiHintUrl = computed(() => '/api/order/list')

  const apiHintParams = computed(() => {
    const params: Record<string, any> = {
      xianyuAccountId: selectedAccountId.value || undefined,
      xyGoodsId: selectedGoods.value?.item.xyGoodId || undefined,
      orderStatus: 2,
      pageNum: 1,
      pageSize: 20
    }
    return params
  })

  const apiHintParamsJson = computed(() => JSON.stringify(apiHintParams.value, null, 2))

  const confirmShipmentUrl = computed(() => '/api/order/confirmShipment')

  const confirmShipmentParams = computed(() => {
    const params: Record<string, any> = {
      xianyuAccountId: selectedAccountId.value || undefined,
      orderId: '订单ID'
    }
    return params
  })

  const confirmShipmentParamsJson = computed(() => JSON.stringify(confirmShipmentParams.value, null, 2))

  const copyApiUrl = () => {
    copyToClipboard(apiHintUrl.value)
  }

  const copyApiParams = () => {
    copyToClipboard(apiHintParamsJson.value)
  }

  const copyConfirmShipmentUrl = () => {
    copyToClipboard(confirmShipmentUrl.value)
  }

  const copyConfirmShipmentParams = () => {
    copyToClipboard(confirmShipmentParamsJson.value)
  }

  // Check screen size
  const checkScreenSize = () => {
    isMobile.value = window.innerWidth < 768
    if (!isMobile.value) {
      mobileView.value = 'goods'
    }
  }

  // Mobile go back
  const goBackToGoods = () => {
    mobileView.value = 'goods'
  }

  // Format time
  const formatTime = (time: string) => {
    return formatDateTime(time)
  }

  // Format price
  const formatPrice = (price: string) => {
    return price ? `¥${price}` : '-'
  }

  // Get status text
  const getStatusText = (status: number) => {
    const map: Record<number, string> = { 0: '在售', 1: '已下架', 2: '已售出' }
    return map[status] || '未知'
  }

  // Get status class
  const getStatusClass = (status: number) => {
    const map: Record<number, string> = { 0: 'on-sale', 1: 'off-shelf', 2: 'sold' }
    return map[status] || 'off-shelf'
  }

  // Get record status
  const getRecordStatusText = (state: number) => {
    if (state === 1) return '成功'
    if (state === 0) return '待发货'
    return '失败'
  }

  const getRecordStatusClass = (state: number) => {
    if (state === 1) return 'success'
    if (state === 0) return 'pending'
    return 'fail'
  }

  // Load accounts
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []

        const accountIdFromQuery = route.query.accountId
        if (accountIdFromQuery) {
          const accountId = parseInt(accountIdFromQuery as string)
          if (accounts.value.some(acc => acc.id === accountId)) {
            selectedAccountId.value = accountId
            await loadGoods()
            return
          }
        }

        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id || null
          await loadGoods()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  // Load goods list
  const loadGoods = async () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }

    goodsLoading.value = true
    try {
      const params: {
        xianyuAccountId: number
        pageNum: number
        pageSize: number
        keyword?: string
      } = {
        xianyuAccountId: selectedAccountId.value,
        pageNum: goodsCurrentPage.value,
        pageSize: 20
      }
      const keyword = goodsKeyword.value.trim()
      if (keyword) {
        params.keyword = keyword
      }

      const response = await getGoodsList(params)
      if (response.code === 0 || response.code === 200) {
        if (goodsCurrentPage.value === 1) {
          goodsList.value = response.data?.itemsWithConfig || []
        } else {
          goodsList.value.push(...(response.data?.itemsWithConfig || []))
        }
        goodsTotal.value = response.data?.totalCount || 0

        const goodsIdFromQuery = route.query.goodsId
        if (goodsIdFromQuery && goodsCurrentPage.value === 1) {
          const targetGoods = goodsList.value.find(g => g.item.xyGoodId === goodsIdFromQuery)
          if (targetGoods) {
            await selectGoods(targetGoods)
            return
          }
        }

        if (goodsCurrentPage.value === 1 && goodsList.value.length > 0 && !selectedGoods.value && !isMobile.value) {
          await selectGoods(goodsList.value[0]!)
        }

        checkAndLoadMore()
      } else {
        throw new Error(response.msg || '获取商品列表失败')
      }
    } catch (error: any) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      goodsLoading.value = false
    }
  }

  // Check and load more
  const checkAndLoadMore = () => {
    nextTick(() => {
      if (!goodsListRef.value) return
      const { scrollHeight, clientHeight } = goodsListRef.value
      if (scrollHeight <= clientHeight && goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoods()
      }
    })
  }

  // Handle goods scroll
  const handleGoodsScroll = () => {
    if (!goodsListRef.value || goodsLoading.value) return
    const { scrollTop, scrollHeight, clientHeight } = goodsListRef.value
    if (scrollTop + clientHeight >= scrollHeight - 50) {
      if (goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoods()
      }
    }
  }

  const resetAndLoadGoods = () => {
    selectedGoods.value = null
    currentConfig.value = null
    deliveryRules.value = []
    activeRuleId.value = null
    goodsCurrentPage.value = 1
    mobileView.value = 'goods'
    loadGoods()
  }

  const handleGoodsSearch = () => {
    if (goodsSearchTimer) {
      clearTimeout(goodsSearchTimer)
      goodsSearchTimer = null
    }
    resetAndLoadGoods()
  }

  const handleGoodsSearchInput = () => {
    if (goodsSearchTimer) {
      clearTimeout(goodsSearchTimer)
    }
    goodsSearchTimer = setTimeout(() => {
      handleGoodsSearch()
    }, 350)
  }

  const clearGoodsSearch = () => {
    if (!goodsKeyword.value) return
    goodsKeyword.value = ''
    handleGoodsSearch()
  }

  // Account change
  const handleAccountChange = () => {
    resetAndLoadGoods()
  }

  // Select goods
  const selectGoods = async (goods: GoodsItemWithConfig) => {
    selectedGoods.value = goods
    recordsPageNum.value = 1
    await loadConfig()
    await loadDeliveryRecords()
    await loadKamiConfigOptions()
    await loadOverviewData()

    if (isMobile.value) {
      mobileView.value = 'config'
    }
  }

  const loadOverviewData = async () => {
    try {
      const statsRes = await getDataPanelStats()
      if (statsRes.code === 0 || statsRes.code === 200) {
        stats.value = statsRes.data || null
      }
    } catch (error) {
      console.error('加载自动发货概览失败:', error)
    }
  }

  const loadKamiConfigOptions = async () => {
    if (!selectedAccountId.value) return
    try {
      const res = await getKamiConfigsByAccountId(selectedAccountId.value)
      if (res.code === 200) {
        kamiConfigOptions.value = res.data || []
      }
    } catch {}
  }

  // Load config
  const loadConfig = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    try {
      const req: GetAutoDeliveryConfigReq = {
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId
      }

      const response = await getAutoDeliveryConfig(req)
      if (response.code === 0 || response.code === 200) {
        await loadDeliveryRules()
        currentConfig.value = response.data || null
        if (response.data) {
          fillConfigForm(response.data)
        } else {
          resetConfigForm()
        }
      } else {
        throw new Error(response.msg || '获取配置失败')
      }
    } catch (error: any) {
      console.error('加载配置失败:', error)
      currentConfig.value = null
      deliveryRules.value = []
      activeRuleId.value = null
    }
  }

  const loadDeliveryRules = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return
    const res = await getAutoDeliveryRulesByGoods(selectedAccountId.value, selectedGoods.value.item.xyGoodId)
    if (res.code === 0 || res.code === 200) {
      deliveryRules.value = res.data || []
      activeRuleId.value = currentConfig.value?.id || deliveryRules.value[0]?.id || null
    }
  }

  const fillConfigForm = (config: AutoDeliveryConfig) => {
    currentConfig.value = config
    activeRuleId.value = config.id || null
    configForm.value.deliveryMode = config.deliveryMode || 1
    configForm.value.ruleName = config.ruleName || ''
    configForm.value.matchKeyword = config.matchKeyword || ''
    configForm.value.matchType = config.matchType || 1
    configForm.value.priority = config.priority ?? 100
    configForm.value.enabled = config.enabled ?? 1
    configForm.value.stock = config.stock ?? -1
    configForm.value.stockWarnThreshold = config.stockWarnThreshold ?? 0
    configForm.value.autoDeliveryContent = config.autoDeliveryContent || ''
    configForm.value.kamiConfigIds = config.kamiConfigIds || ''
    configForm.value.kamiDeliveryTemplate = config.kamiDeliveryTemplate || ''
    configForm.value.autoDeliveryImageUrl = config.autoDeliveryImageUrl || ''
    configForm.value.postDeliveryText = config.postDeliveryText || ''
    configForm.value.autoConfirmShipment = config.autoConfirmShipment || 0
    configForm.value.deliveryDelaySeconds = config.deliveryDelaySeconds ?? 0
    configForm.value.triggerPaymentEnabled = config.triggerPaymentEnabled ?? 1
    configForm.value.triggerBargainEnabled = config.triggerBargainEnabled ?? 0
    configForm.value.apiAllocateUrl = config.apiAllocateUrl || DEFAULT_API_ALLOCATE_URL
    configForm.value.apiConfirmUrl = config.apiConfirmUrl || DEFAULT_API_CONFIRM_URL
    configForm.value.apiReturnUrl = config.apiReturnUrl || DEFAULT_API_RETURN_URL
    configForm.value.apiHeaderValue = config.apiHeaderValue || ''
    configForm.value.apiRequestExtras = config.apiRequestExtras || ''
    configForm.value.apiDeliveryTemplate = config.apiDeliveryTemplate || ''
  }

  const resetConfigForm = () => {
    currentConfig.value = null
    activeRuleId.value = null
    configForm.value.deliveryMode = 1
    configForm.value.ruleName = ''
    configForm.value.matchKeyword = ''
    configForm.value.matchType = 1
    configForm.value.priority = 100
    configForm.value.enabled = 1
    configForm.value.stock = -1
    configForm.value.stockWarnThreshold = 0
    configForm.value.autoDeliveryContent = ''
    configForm.value.kamiConfigIds = ''
    configForm.value.kamiDeliveryTemplate = ''
    configForm.value.autoDeliveryImageUrl = ''
    configForm.value.postDeliveryText = ''
    configForm.value.autoConfirmShipment = 0
    configForm.value.deliveryDelaySeconds = 0
    configForm.value.triggerPaymentEnabled = 1
    configForm.value.triggerBargainEnabled = 0
    configForm.value.apiAllocateUrl = DEFAULT_API_ALLOCATE_URL
    configForm.value.apiConfirmUrl = DEFAULT_API_CONFIRM_URL
    configForm.value.apiReturnUrl = DEFAULT_API_RETURN_URL
    configForm.value.apiHeaderValue = ''
    configForm.value.apiRequestExtras = ''
    configForm.value.apiDeliveryTemplate = ''
  }

  const selectDeliveryRule = (rule: AutoDeliveryConfig) => {
    fillConfigForm(rule)
  }

  const createDeliveryRule = () => {
    resetConfigForm()
    configForm.value.ruleName = `规则${deliveryRules.value.length + 1}`
    configForm.value.priority = (deliveryRules.value.length + 1) * 10
  }

  const copyDeliveryRule = () => {
    const source = currentConfig.value
    resetConfigForm()
    if (!source) return
    const copied = { ...source, id: 0, ruleName: `${source.ruleName || '规则'} 副本` }
    fillConfigForm(copied)
    currentConfig.value = null
    activeRuleId.value = null
  }

  const removeDeliveryRule = async (rule: AutoDeliveryConfig) => {
    if (!rule.id) return
    const res = await deleteAutoDeliveryRule(rule.id)
    if (res.code === 0 || res.code === 200) {
      showSuccess('规则已删除')
      await loadDeliveryRules()
      const nextRule = deliveryRules.value[0] || null
      nextRule ? fillConfigForm(nextRule) : resetConfigForm()
    }
  }

  // Save config
  const saveConfig = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    if (configForm.value.deliveryMode === 1 && !configForm.value.autoDeliveryContent.trim()) {
      showInfo('请输入自动发货内容')
      return
    }
    if (configForm.value.deliveryMode === 2 && !configForm.value.kamiConfigIds) {
      showInfo('请绑定卡密配置')
      return
    }
    if (configForm.value.deliveryMode === 4 && !configForm.value.apiAllocateUrl.trim()) {
      showInfo('请配置API分配接口URL')
      return
    }

    saving.value = true
    try {
      const req: SaveAutoDeliveryConfigReq = {
        id: currentConfig.value?.id || undefined,
        xianyuAccountId: selectedAccountId.value,
        xianyuGoodsId: selectedGoods.value.item.id,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        deliveryMode: configForm.value.deliveryMode,
        ruleName: configForm.value.ruleName.trim(),
        matchKeyword: configForm.value.matchKeyword.trim(),
        matchType: configForm.value.matchType,
        priority: configForm.value.priority,
        enabled: configForm.value.enabled,
        stock: configForm.value.stock,
        stockWarnThreshold: configForm.value.stockWarnThreshold,
        autoDeliveryContent: configForm.value.autoDeliveryContent.trim(),
        kamiConfigIds: configForm.value.kamiConfigIds,
        kamiDeliveryTemplate: configForm.value.kamiDeliveryTemplate.trim(),
        autoDeliveryImageUrl: configForm.value.autoDeliveryImageUrl.trim(),
        postDeliveryText: configForm.value.postDeliveryText.trim(),
        autoConfirmShipment: configForm.value.autoConfirmShipment,
        deliveryDelaySeconds: Math.max(0, Math.min(configForm.value.deliveryDelaySeconds || 0, 86400)),
        triggerPaymentEnabled: configForm.value.triggerPaymentEnabled,
        triggerBargainEnabled: configForm.value.triggerBargainEnabled,
        apiAllocateUrl: configForm.value.apiAllocateUrl.trim(),
        apiConfirmUrl: configForm.value.apiConfirmUrl.trim(),
        apiReturnUrl: configForm.value.apiReturnUrl.trim(),
        apiHeaderValue: configForm.value.apiHeaderValue.trim(),
        apiRequestExtras: configForm.value.apiRequestExtras.trim(),
        apiDeliveryTemplate: configForm.value.apiDeliveryTemplate.trim()
      }

      const response = await saveOrUpdateAutoDeliveryConfig(req)
      if (response.code === 0 || response.code === 200) {
        showSuccess('保存配置成功')
        currentConfig.value = response.data || null
        if (response.data) {
          activeRuleId.value = response.data.id
          configForm.value.stock = response.data.stock ?? configForm.value.stock
          configForm.value.enabled = response.data.enabled ?? configForm.value.enabled
          configForm.value.deliveryDelaySeconds = response.data.deliveryDelaySeconds ?? configForm.value.deliveryDelaySeconds
        }
        await loadDeliveryRules()
      } else {
        throw new Error(response.msg || '保存配置失败')
      }
    } catch (error: any) {
      console.error('保存配置失败:', error)
    } finally {
      saving.value = false
    }
  }

  const toggleRuleEnabled = async (value: boolean) => {
    configForm.value.enabled = value ? 1 : 0
    if (!currentConfig.value?.id) return
    try {
      const response = await updateAutoDeliveryConfigEnabled(currentConfig.value.id, configForm.value.enabled)
      if (response.code === 0 || response.code === 200) {
        showSuccess(`规则${value ? '启用' : '停用'}成功`)
        currentConfig.value.enabled = configForm.value.enabled
      }
    } catch (error) {
      console.error('更新规则状态失败:', error)
      configForm.value.enabled = value ? 0 : 1
    }
  }

  const saveStock = async () => {
    if (!currentConfig.value?.id) {
      await saveConfig()
      return
    }
    try {
      const response = await updateAutoDeliveryConfigStock(currentConfig.value.id, configForm.value.stock)
      if (response.code === 0 || response.code === 200) {
        showSuccess('库存已更新')
        currentConfig.value.stock = configForm.value.stock
      }
    } catch (error) {
      console.error('更新库存失败:', error)
    }
  }

  // Toggle auto delivery
  const toggleAutoDelivery = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const response = await updateAutoDeliveryStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        xianyuAutoDeliveryOn: value ? 1 : 0
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动发货${value ? '开启' : '关闭'}成功`)
        if (selectedGoods.value) {
          selectedGoods.value.xianyuAutoDeliveryOn = value ? 1 : 0
        }
        const goodsItem = goodsList.value.find(item => item.item.xyGoodId === selectedGoods.value?.item.xyGoodId)
        if (goodsItem) {
          goodsItem.xianyuAutoDeliveryOn = value ? 1 : 0
        }
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      if (selectedGoods.value) {
        selectedGoods.value.xianyuAutoDeliveryOn = value ? 0 : 1
      }
    }
  }

  const toggleAutoConfirmShipment = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const response = await updateAutoConfirmShipment({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        autoConfirmShipment: value ? 1 : 0
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动确认发货${value ? '开启' : '关闭'}成功`)
        configForm.value.autoConfirmShipment = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      configForm.value.autoConfirmShipment = value ? 0 : 1
    }
  }

  // Load delivery records
  const loadDeliveryRecords = async () => {
    if (!selectedAccountId.value || !selectedGoods.value) {
      deliveryRecords.value = []
      recordsTotal.value = 0
      return
    }

    recordsLoading.value = true
    try {
      const req: AutoDeliveryRecordReq = {
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        state: recordsStateFilter.value ?? undefined,
        pageNum: recordsPageNum.value,
        pageSize: recordsPageSize.value
      }

      const response = await getAutoDeliveryRecords(req)
      if (response.code === 0 || response.code === 200) {
        deliveryRecords.value = response.data?.records || []
        recordsTotal.value = response.data?.total || 0
      } else {
        throw new Error(response.msg || '获取记录失败')
      }
    } catch (error: any) {
      console.error('加载自动发货记录失败:', error)
      deliveryRecords.value = []
      recordsTotal.value = 0
    } finally {
      recordsLoading.value = false
    }
  }

  // Records page change
  const handleRecordsPageChange = (page: number) => {
    recordsPageNum.value = page
    loadDeliveryRecords()
  }

  const handleRecordsStateChange = () => {
    recordsPageNum.value = 1
    loadDeliveryRecords()
  }

  const exportRecordsCsv = () => {
    const rows = [
      ['订单ID', '买家', '规则', '触发来源', '状态', '确认状态', '时间', '内容', '失败原因'],
      ...deliveryRecords.value.map(record => [
        record.orderId || '',
        record.buyerUserName || '',
        record.ruleName || '',
        getTriggerSourceText(record.triggerSource),
        getRecordStatusText(record.state),
        record.confirmState === 1 ? '已确认' : '未确认',
        formatTime(record.createTime),
        record.content || '',
        record.failReason || ''
      ])
    ]
    const csv = rows.map(row => row.map(escapeCsvCell).join(',')).join('\n')
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `auto-delivery-records-${Date.now()}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  // View goods detail
  const viewGoodsDetail = () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }
    selectedGoodsId.value = selectedGoods.value.item.xyGoodId
    detailDialogVisible.value = true
  }

  // Confirm shipment
  const handleConfirmShipment = (record: any) => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }
    if ((record.confirmState || 0) === 1) {
      showSuccess('订单已确认发货，无需重复操作')
      return
    }
    if (!record.orderId) {
      showError('该记录没有订单ID，无法确认已发货')
      return
    }

    confirmDialog.value = {
      visible: true,
      title: '确认已发货',
      message: `确定要确认已发货吗？订单ID: ${record.orderId}`,
      type: 'primary',
      onConfirm: async () => {
        try {
          const req: ConfirmShipmentReq = {
            xianyuAccountId: selectedAccountId.value!,
            orderId: record.orderId
          }
          const response = await confirmShipment(req)
          if (response.code === 0 || response.code === 200) {
            showSuccess(response.data || '确认已发货成功')
            await loadDeliveryRecords()
          } else {
            if (response.msg && (response.msg.includes('Token') || response.msg.includes('令牌'))) {
              throw new Error('Cookie已过期，请重新扫码登录获取新的Cookie')
            }
            throw new Error(response.msg || '确认已发货失败')
          }
        } catch (error: any) {
          console.error('确认已发货失败:', error)
          // 只有在错误消息未显示过时才弹出提示（避免重复显示）
          if (!error.messageShown) {
            showError(error.message || '确认已发货失败')
          }
        } finally {
          confirmDialog.value.visible = false
        }
      }
    }
  }

  const showWsDisconnectedTip = () => {
    ElMessage({
      type: 'warning',
      duration: 5000,
      dangerouslyUseHTMLString: true,
      message: '请先连接服务器，<a href="#/connection" style="color:#34c759;text-decoration:underline;font-weight:600;" onclick="event.preventDefault();window.__gotoConnection&&window.__gotoConnection()">点击跳转</a>'
    })
  }

  // Trigger auto delivery
  const handleTriggerDelivery = async (record: any) => {
    if (!selectedAccountId.value || !selectedGoods.value) {
      showInfo('请先选择账号和商品')
      return
    }
    if (!record.orderId) {
      showError('该记录没有订单ID，无法触发发货')
      return
    }

    try {
      const wsStatus = await getConnectionStatus(selectedAccountId.value)
      if (!wsStatus.data?.connected) {
        showWsDisconnectedTip()
        return
      }
    } catch {
      showWsDisconnectedTip()
      return
    }
    if (configForm.value.deliveryMode === 1 && (!configForm.value.autoDeliveryContent || !configForm.value.autoDeliveryContent.trim())) {
      showError('请配置发货内容！')
      return
    }
    if (configForm.value.deliveryMode === 2 && !configForm.value.kamiConfigIds) {
      showError('请绑定卡密配置！')
      return
    }
    if (configForm.value.deliveryMode === 4 && !configForm.value.apiAllocateUrl.trim()) {
      showError('请配置API分配接口URL！')
      return
    }

    // 防止重复提交：检查是否正在处理
    if (loading.value) {
      showInfo('正在处理中，请稍候...')
      return
    }

    const isKamiMode = configForm.value.deliveryMode === 2
    const isApiMode = configForm.value.deliveryMode === 4
    const dialogMessage = isKamiMode
      ? `确认重新发货吗？\n\n⚠️ 卡密发货模式：将发送新的卡密，扣减一次卡密库存！\n订单ID: ${record.orderId}`
      : isApiMode
        ? `确认重新发货吗？\n\nAPI发货模式：将重新调用分配接口，消息最终发送失败才会回库。\n订单ID: ${record.orderId}`
      : `确认重新发货吗？订单ID: ${record.orderId}`

    confirmDialog.value = {
      visible: true,
      title: '重新发货',
      message: dialogMessage,
      type: 'danger',
      onConfirm: async () => {
        // 防止重复点击确认按钮
        if (loading.value) {
          return
        }
        
        loading.value = true
        try {
          const req: TriggerAutoDeliveryReq = {
            xianyuAccountId: selectedAccountId.value!,
            xyGoodsId: selectedGoods.value!.item.xyGoodId,
            orderId: record.orderId
          }
          const response = await triggerAutoDelivery(req)
          if (response.code === 0 || response.code === 200) {
            showSuccess(response.data || '触发发货成功')
            await loadDeliveryRecords()
            await loadConfig()
            await loadOverviewData()
          } else {
            throw new Error(response.msg || '触发发货失败')
          }
        } catch (error: any) {
          console.error('触发发货失败:', error)
          // 只有在错误消息未显示过时才弹出提示（避免重复显示）
          if (!error.messageShown) {
            showError(error.message || '触发发货失败')
          }
        } finally {
          loading.value = false
          confirmDialog.value.visible = false
        }
      }
    }
  }

  // Confirm dialog actions
  const handleDialogConfirm = () => {
    confirmDialog.value.onConfirm()
  }

  const handleDialogCancel = () => {
    confirmDialog.value.visible = false
  }

  const canManualReturnRecord = (record: any) => {
    return record?.deliveryMode === 4
      && !!record?.externalAllocationId
      && record?.externalReturnState !== 1
  }

  const getExternalReturnStatusText = (record: any) => {
    if (record?.deliveryMode !== 4 || !record?.externalAllocationId) return '-'
    if (record?.externalReturnState === 1) return '已回库'
    if (record?.externalReturnState === -1) return '回库失败'
    return '未回库'
  }

  const getExternalReturnStatusClass = (record: any) => {
    if (record?.deliveryMode !== 4 || !record?.externalAllocationId) return 'pending'
    if (record?.externalReturnState === 1) return 'success'
    if (record?.externalReturnState === -1) return 'fail'
    return 'pending'
  }

  const handleManualReturn = (record: any) => {
    if (!selectedAccountId.value || !selectedGoods.value) {
      showError('请先选择账号和商品')
      return
    }
    if (!canManualReturnRecord(record)) {
      showInfo('该记录当前无需手动回库')
      return
    }

    confirmDialog.value = {
      visible: true,
      title: '手动回库',
      message: `确认手动回库吗？\n\nAPI发货模式：将调用回库接口释放外部占用账号。\n订单ID: ${record.orderId || '-'}\n占用ID: ${record.externalAllocationId}`,
      type: 'danger',
      onConfirm: async () => {
        if (loading.value) {
          return
        }
        loading.value = true
        try {
          const req: ManualReturnApiDeliveryReq = {
            xianyuAccountId: selectedAccountId.value!,
            xyGoodsId: selectedGoods.value!.item.xyGoodId,
            recordId: record.id
          }
          const response = await manualReturnApiDelivery(req)
          if (response.code === 0 || response.code === 200) {
            showSuccess(response.data || '手动回库成功')
            await loadDeliveryRecords()
          } else {
            throw new Error(response.msg || '手动回库失败')
          }
        } catch (error: any) {
          console.error('手动回库失败:', error)
          if (!error.messageShown) {
            showError(error.message || '手动回库失败')
          }
        } finally {
          loading.value = false
          confirmDialog.value.visible = false
        }
      }
    }
  }

  const escapeCsvCell = (value: unknown) => {
    const text = String(value ?? '')
    return `"${text.replace(/"/g, '""')}"`
  }

  // Records total pages
  const recordsTotalPages = computed(() => Math.ceil(recordsTotal.value / recordsPageSize.value))

  const getTriggerSourceText = (source: string) => {
    if (source === 'payment') return '付款消息'
    if (source === 'bargain') return '小刀/讲价'
    if (source === 'manual') return '手动'
    return source || '-'
  }

  // Lifecycle
  onMounted(() => {
    loadAccounts()
    loadOverviewData()
    checkScreenSize()
    window.addEventListener('resize', checkScreenSize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', checkScreenSize)
    if (goodsSearchTimer) {
      clearTimeout(goodsSearchTimer)
    }
  })

  return {
    // State
    loading,
    saving,
    accounts,
    selectedAccountId,
    goodsList,
    selectedGoods,
    currentConfig,
    deliveryRules,
    activeRuleId,
    goodsKeyword,
    configForm,
    goodsCurrentPage,
    goodsTotal,
    goodsLoading,
    goodsListRef,
    detailDialogVisible,
    selectedGoodsId,
    deliveryRecords,
    recordsLoading,
    recordsTotal,
    recordsPageNum,
    recordsPageSize,
    recordsStateFilter,
    recordsTotalPages,
    isMobile,
    mobileView,
    confirmDialog,
    apiHintUrl,
    apiHintParams,
    apiHintParamsJson,
    confirmShipmentUrl,
    confirmShipmentParams,
    confirmShipmentParamsJson,
    kamiConfigOptions,
    selectedKamiConfigId,
    stats,

    // Methods
    loadAccounts,
    loadGoods,
    handleAccountChange,
    handleGoodsSearch,
    handleGoodsSearchInput,
    clearGoodsSearch,
    selectGoods,
    saveConfig,
    selectDeliveryRule,
    createDeliveryRule,
    copyDeliveryRule,
    removeDeliveryRule,
    toggleRuleEnabled,
    saveStock,
    toggleAutoDelivery,
    toggleAutoConfirmShipment,
    loadDeliveryRecords,
    handleRecordsPageChange,
    handleRecordsStateChange,
    exportRecordsCsv,
    viewGoodsDetail,
    handleConfirmShipment,
    handleTriggerDelivery,
    handleManualReturn,
    handleDialogConfirm,
    handleDialogCancel,
    loadOverviewData,
    copyApiUrl,
    copyApiParams,
    copyConfirmShipmentUrl,
    copyConfirmShipmentParams,
    handleGoodsScroll,
    goBackToGoods,
    formatTime,
    formatPrice,
    getStatusText,
    getStatusClass,
    getRecordStatusText,
    getRecordStatusClass,
    getExternalReturnStatusText,
    getExternalReturnStatusClass,
    getTriggerSourceText,
    canManualReturnRecord,
    checkScreenSize
  }
}

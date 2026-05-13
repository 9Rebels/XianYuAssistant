import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getGoodsList, updateAutoReplyStatus, getAutoReplyConfig, updateAutoReplyConfig, getAutoReplyRecords } from '@/api/goods'
import { chatWithAI, chatTestWithAI, putNewDataToRAG, queryRAGData, deleteRAGData, saveFixedMaterial, getFixedMaterial, syncDetailToFixedMaterial } from '@/api/ai'
import { batchImportAutoReplyRules, deleteAutoReplyRule, listAutoReplyRules, saveAutoReplyRule } from '@/api/auto-reply-rule'
import type { RAGDataItem } from '@/api/ai'
import type { AutoReplyRule, AutoReplyRuleReq } from '@/api/auto-reply-rule'
import type { AutoReplyRecord } from '@/api/goods'
import { formatTime as formatDateTime, showSuccess, showError, showInfo } from '@/utils'
import { ElMessage } from 'element-plus'
import type { Account } from '@/types'
import type { GoodsItemWithConfig } from '@/api/goods'

// 聊天消息类型
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  loading?: boolean
}

export function useAutoReply() {
  const route = useRoute()
  const router = useRouter()

  const showAiConfigTip = () => {
    ElMessage({
      type: 'warning',
      duration: 5000,
      dangerouslyUseHTMLString: true,
      message: '请完成AI配置再上传资料，<a href="#/settings" style="color:#34c759;text-decoration:underline;font-weight:600;" onclick="event.preventDefault();window.__gotoSettings&&window.__gotoSettings()">点击前往</a>'
    })
  }
  ;(window as any).__gotoSettings = () => router.push('/settings')

  const saving = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
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

  // Right panel tab: 'data' | 'chat'
  const rightTab = ref<'data' | 'rules' | 'chat'>('data')

  // Upload data form
  const dataContent = ref('')
  const uploading = ref(false)

  // Fixed material
  const fixedMaterial = ref('')
  const fixedMaterialSaving = ref(false)
  const fixedMaterialSyncing = ref(false)
  const fixedMaterialExpanded = ref(true)

  // Query existing knowledge data
  const dataList = ref<RAGDataItem[]>([])
  const dataLoading = ref(false)
  const dataVisible = ref(false)

  // Chat
  const chatMessages = ref<ChatMessage[]>([])
  const chatInput = ref('')
  const chatSending = ref(false)
  const chatListRef = ref<HTMLElement | null>(null)

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

  // Auto reply config
  const delaySeconds = ref(15)
  const configLoading = ref(false)
  const configSaving = ref(false)

  // Auto reply records
  const recordsVisible = ref(false)
  const recordsList = ref<AutoReplyRecord[]>([])
  const recordsLoading = ref(false)
  const recordsTotal = ref(0)
  const recordsPage = ref(1)
  const recordsPageSize = ref(20)
  const recordDetailVisible = ref(false)
  const recordDetail = ref<AutoReplyRecord | null>(null)
  const contextExpanded = ref(false)

  // Keyword rules
  const replyRules = ref<AutoReplyRule[]>([])
  const rulesLoading = ref(false)
  const ruleDialogVisible = ref(false)
  const ruleSaving = ref(false)
  const ruleImportRef = ref<HTMLInputElement | null>(null)
  const ruleForm = ref<AutoReplyRuleReq>({
    xianyuAccountId: 0,
    xyGoodsId: null,
    ruleName: '',
    keywords: '',
    matchType: 1,
    replyType: 1,
    replyContent: '',
    imageUrls: '',
    priority: 100,
    enabled: 1,
    isDefault: 0
  })

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
          loadGoods()
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
          selectGoods(goodsList.value[0]!)
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
    goodsCurrentPage.value = 1
    chatMessages.value = []
    dataContent.value = ''
    dataVisible.value = false
    dataList.value = []
    replyRules.value = []
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
    // 切换商品时重置聊天和资料
    chatMessages.value = []
    dataContent.value = ''
    rightTab.value = 'data'
    dataVisible.value = false
    dataList.value = []

    if (isMobile.value) {
      mobileView.value = 'config'
    }

    // 加载自动回复配置
    loadConfig()
    // 加载固定资料
    loadFixedMaterial()
    // 加载关键词规则
    loadRules()
  }

  const newRuleForm = (scope: 'goods' | 'global' = 'goods'): AutoReplyRuleReq => ({
    xianyuAccountId: selectedAccountId.value || 0,
    xyGoodsId: scope === 'goods' ? selectedGoods.value?.item.xyGoodId : null,
    ruleName: '',
    keywords: '',
    matchType: 1,
    replyType: 1,
    replyContent: '',
    imageUrls: '',
    priority: 100,
    enabled: 1,
    isDefault: 0
  })

  const loadRules = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return
    rulesLoading.value = true
    try {
      const response = await listAutoReplyRules({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        includeGlobal: true
      })
      if (response.code === 0 || response.code === 200) {
        replyRules.value = response.data || []
      }
    } catch (error: any) {
      console.error('加载关键词规则失败:', error)
      replyRules.value = []
    } finally {
      rulesLoading.value = false
    }
  }

  const openCreateRule = (scope: 'goods' | 'global' = 'goods') => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }
    ruleForm.value = newRuleForm(scope)
    ruleDialogVisible.value = true
  }

  const openEditRule = (rule: AutoReplyRule) => {
    ruleForm.value = {
      id: rule.id,
      xianyuAccountId: rule.xianyuAccountId,
      xyGoodsId: rule.xyGoodsId || null,
      ruleName: rule.ruleName || '',
      keywords: rule.keywords || '',
      matchType: rule.matchType || 1,
      replyType: rule.replyType || 1,
      replyContent: rule.replyContent || '',
      imageUrls: rule.imageUrls || '',
      priority: rule.priority ?? 100,
      enabled: rule.enabled ?? 1,
      isDefault: rule.isDefault ?? 0
    }
    ruleDialogVisible.value = true
  }

  const saveRule = async () => {
    if (!selectedAccountId.value) return
    ruleSaving.value = true
    try {
      const payload = { ...ruleForm.value, xianyuAccountId: selectedAccountId.value }
      const response = await saveAutoReplyRule(payload)
      if (response.code === 0 || response.code === 200) {
        showSuccess('规则已保存')
        ruleDialogVisible.value = false
        await loadRules()
      }
    } catch (error: any) {
      console.error('保存关键词规则失败:', error)
    } finally {
      ruleSaving.value = false
    }
  }

  const deleteRule = (rule: AutoReplyRule) => {
    confirmDialog.value = {
      visible: true,
      title: '删除规则',
      message: `确定删除「${rule.ruleName || '未命名规则'}」吗？`,
      type: 'danger',
      onConfirm: async () => {
        confirmDialog.value.visible = false
        await deleteAutoReplyRule(rule.id)
        showSuccess('规则已删除')
        await loadRules()
      }
    }
  }

  const exportRulesCsv = () => {
    const header = ['规则名', '适用范围', '商品ID', '关键词', '匹配方式', '回复方式', '回复内容', '图片URL', '优先级', '启用', '默认回复']
    const rows = replyRules.value.map(rule => [
      rule.ruleName || '',
      rule.xyGoodsId ? '当前商品' : '通用',
      rule.xyGoodsId || '',
      rule.keywords || '',
      rule.matchType === 2 ? '全部关键词' : '任意关键词',
      getRuleReplyTypeText(rule.replyType),
      rule.replyContent || '',
      rule.imageUrls || '',
      String(rule.priority ?? 100),
      rule.enabled === 1 ? '是' : '否',
      rule.isDefault === 1 ? '是' : '否'
    ])
    downloadCsv('auto-reply-rules.csv', [header, ...rows])
  }

  const triggerRuleImport = () => {
    ruleImportRef.value?.click()
  }

  const importRulesCsv = async (event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file || !selectedAccountId.value) return
    try {
      const text = await file.text()
      const rules = parseRulesCsv(text)
      if (rules.length === 0) {
        showInfo('没有可导入的规则')
        return
      }
      const response = await batchImportAutoReplyRules(rules)
      if (response.code === 0 || response.code === 200) {
        showSuccess(response.msg || `已导入 ${response.data || rules.length} 条规则`)
        await loadRules()
      }
    } finally {
      input.value = ''
    }
  }

  const parseRulesCsv = (text: string): AutoReplyRuleReq[] => {
    const rows = parseCsvRows(text).filter(row => row.some(cell => cell.trim()))
    const body = rows[0]?.[0]?.includes('规则名') ? rows.slice(1) : rows
    return body.map(row => ({
      xianyuAccountId: selectedAccountId.value!,
      ruleName: row[0] || '',
      xyGoodsId: row[1] === '通用' ? null : (row[2] || selectedGoods.value?.item.xyGoodId || null),
      keywords: row[3] || '',
      matchType: row[4] === '全部关键词' ? 2 : 1,
      replyType: parseReplyType(row[5] || ''),
      replyContent: row[6] || '',
      imageUrls: row[7] || '',
      priority: Number(row[8] || 100),
      enabled: row[9] === '否' ? 0 : 1,
      isDefault: row[10] === '是' ? 1 : 0
    }))
  }

  const parseCsvRows = (text: string): string[][] => {
    const rows: string[][] = []
    let row: string[] = []
    let cell = ''
    let quoted = false
    for (let i = 0; i < text.length; i++) {
      const char = text[i]
      const next = text[i + 1]
      if (char === '"' && quoted && next === '"') {
        cell += '"'
        i++
      } else if (char === '"') {
        quoted = !quoted
      } else if (char === ',' && !quoted) {
        row.push(cell)
        cell = ''
      } else if ((char === '\n' || char === '\r') && !quoted) {
        if (char === '\r' && next === '\n') i++
        row.push(cell)
        rows.push(row)
        row = []
        cell = ''
      } else {
        cell += char
      }
    }
    row.push(cell)
    rows.push(row)
    return rows
  }

  const downloadCsv = (filename: string, rows: string[][]) => {
    const csv = rows.map(row => row.map(escapeCsvCell).join(',')).join('\n')
    const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    link.click()
    URL.revokeObjectURL(link.href)
  }

  const escapeCsvCell = (value: string) => `"${value.replace(/"/g, '""')}"`

  const parseReplyType = (text: string) => {
    if (text === '图片') return 2
    if (text === '文字+图片') return 3
    return 1
  }

  const getRuleReplyTypeText = (type: number) => {
    if (type === 2) return '图片'
    if (type === 3) return '文字+图片'
    return '文字'
  }

  const getRuleMatchTypeText = (type: number) => type === 2 ? '全部关键词' : '任意关键词'

  const getRuleScopeText = (rule: AutoReplyRule) => rule.xyGoodsId ? '当前商品' : '通用'

  // Load fixed material
  const loadFixedMaterial = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    try {
      const response = await getFixedMaterial({
        accountId: selectedAccountId.value,
        goodsId: selectedGoods.value.item.xyGoodId
      })
      const data = await response.json()
      if (data.code === 0 || data.code === 200) {
        fixedMaterial.value = data.data?.fixedMaterial || ''
        fixedMaterialExpanded.value = !fixedMaterial.value
      }
    } catch (error: any) {
      console.error('加载固定资料失败:', error)
    }
  }

  // Save fixed material
  const handleSaveFixedMaterial = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    fixedMaterialSaving.value = true
    try {
      const response = await saveFixedMaterial({
        accountId: selectedAccountId.value,
        goodsId: selectedGoods.value.item.xyGoodId,
        fixedMaterial: fixedMaterial.value
      })
      const data = await response.json()
      if (data.code === 0 || data.code === 200) {
        showSuccess('固定资料保存成功')
      } else {
        showError(data.msg || '保存失败')
      }
    } catch (error: any) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('保存固定资料失败: ' + error.message)
      }
    } finally {
      fixedMaterialSaving.value = false
    }
  }

  // Sync detail to fixed material
  const handleSyncDetailToFixedMaterial = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    fixedMaterialSyncing.value = true
    try {
      const response = await syncDetailToFixedMaterial({
        accountId: selectedAccountId.value,
        goodsId: selectedGoods.value.item.xyGoodId
      })
      const data = await response.json()
      if (data.code === 0 || data.code === 200) {
        showSuccess('商品详情已同步到固定资料')
        await loadFixedMaterial()
      } else {
        showError(data.msg || '同步失败')
      }
    } catch (error: any) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('同步商品详情失败: ' + error.message)
      }
    } finally {
      fixedMaterialSyncing.value = false
    }
  }

  // Toggle fixed material expanded
  const toggleFixedMaterialExpanded = () => {
    fixedMaterialExpanded.value = !fixedMaterialExpanded.value
  }

  // Load auto reply config
  const loadConfig = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    configLoading.value = true
    try {
      const response = await getAutoReplyConfig({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        delaySeconds.value = response.data?.ragDelaySeconds ?? 15
      }
    } catch (error: any) {
      console.error('加载自动回复配置失败:', error)
    } finally {
      configLoading.value = false
    }
  }

  // Update delay seconds
  const updateDelaySeconds = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    // 验证范围
    let seconds = delaySeconds.value
    if (seconds < 5) seconds = 5
    if (seconds > 120) seconds = 120
    delaySeconds.value = seconds

    configSaving.value = true
    try {
      const response = await updateAutoReplyConfig({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        ragDelaySeconds: seconds
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('延时设置已保存')
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('更新延时失败:', error)
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError(error.message || '操作失败')
      }
    } finally {
      configSaving.value = false
    }
  }

  // Toggle auto reply
  const toggleAutoReply = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const requestContextOn = selectedGoods.value.xianyuAutoReplyContextOn ?? (value ? 1 : 0)

      const response = await updateAutoReplyStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        xianyuAutoReplyOn: value ? 1 : 0,
        xianyuAutoReplyContextOn: requestContextOn
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动回复${value ? '开启' : '关闭'}成功`)
        if (selectedGoods.value) {
          selectedGoods.value.xianyuAutoReplyOn = value ? 1 : 0
          if (value && selectedGoods.value.xianyuAutoReplyContextOn == null) {
            selectedGoods.value.xianyuAutoReplyContextOn = 1
          }
        }
        const goodsItem = goodsList.value.find(item => item.item.xyGoodId === selectedGoods.value?.item.xyGoodId)
        if (goodsItem) {
          goodsItem.xianyuAutoReplyOn = value ? 1 : 0
          if (value && goodsItem.xianyuAutoReplyContextOn == null) {
            goodsItem.xianyuAutoReplyContextOn = 1
          }
        }
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      if (selectedGoods.value) {
        selectedGoods.value.xianyuAutoReplyOn = value ? 0 : 1
      }
    }
  }

  // Toggle context switch
  const toggleContextOn = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const response = await updateAutoReplyStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        xianyuAutoReplyOn: selectedGoods.value.xianyuAutoReplyOn,
        xianyuAutoReplyContextOn: value ? 1 : 0
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`携带上下文${value ? '开启' : '关闭'}成功`)
        if (selectedGoods.value) {
          selectedGoods.value.xianyuAutoReplyContextOn = value ? 1 : 0
        }
        const goodsItem = goodsList.value.find(item => item.item.xyGoodId === selectedGoods.value?.item.xyGoodId)
        if (goodsItem) {
          goodsItem.xianyuAutoReplyContextOn = value ? 1 : 0
        }
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      if (selectedGoods.value) {
        selectedGoods.value.xianyuAutoReplyContextOn = value ? 0 : 1
      }
    }
  }

  // Upload knowledge data
  const handleUploadData = async () => {
    if (!selectedGoods.value) {
      showInfo('请先选择商品')
      return
    }
    if (!dataContent.value.trim()) {
      showInfo('请输入资料内容')
      return
    }

    uploading.value = true
    try {
      const response = await putNewDataToRAG({
        content: dataContent.value.trim(),
        goodsId: selectedGoods.value.item.xyGoodId
      })
      if (!response.ok) {
        if (response.status === 405 || response.status === 404) {
          throw new Error('请前往系统设置->AI服务配置中完成配置')
        }
        throw new Error(`上传资料失败: ${response.status}`)
      }
      const result = await response.json()
      if (result.code === 0 || result.code === 200) {
        showSuccess('添加成功')
        dataContent.value = ''
        if (dataVisible.value) {
          handleQueryData()
        }
      } else if (result.code === 1001) {
        showAiConfigTip()
      } else {
        // 检查是否是AI未配置的错误
        const errorMsg = result.msg || '上传资料失败'
        if (errorMsg.includes('AI') || errorMsg.includes('API') || errorMsg.includes('配置')) {
          throw new Error('请前往系统设置->AI服务配置中完成配置')
        }
        throw new Error(errorMsg)
      }
    } catch (error: any) {
      console.error('上传资料失败:', error)
      // 如果错误消息包含配置相关提示，使用友好提示
      const errorMsg = error.message || '上传资料失败'
      if (errorMsg.includes('配置') || errorMsg.includes('AI') || errorMsg.includes('API')) {
        showError('请前往系统设置->AI服务配置中完成配置')
      } else {
        showError(errorMsg)
      }
    } finally {
      uploading.value = false
    }
  }

  // Query existing knowledge data
  const handleQueryData = async () => {
    if (!selectedGoods.value) {
      showInfo('请先选择商品')
      return
    }

    dataLoading.value = true
    try {
      const response = await queryRAGData({
        goodsId: selectedGoods.value.item.xyGoodId
      })
      if (!response.ok) {
        if (response.status === 405 || response.status === 404) {
          throw new Error('AI 功能未开启，请前往系统设置->AI服务配置中完成配置')
        }
        throw new Error(`查询资料失败: ${response.status}`)
      }
      const result = await response.json()
      if (result.code === 0 || result.code === 200) {
        dataList.value = result.data || []
      } else {
        // 检查是否是AI未配置的错误
        const errorMsg = result.msg || '查询资料失败'
        if (errorMsg.includes('AI') || errorMsg.includes('API') || errorMsg.includes('配置')) {
          throw new Error('请前往系统设置->AI服务配置中完成配置')
        }
        throw new Error(errorMsg)
      }
    } catch (error: any) {
      console.error('查询资料失败:', error)
      // 如果错误消息包含配置相关提示，使用友好提示
      const errorMsg = error.message || '查询资料失败'
      if (errorMsg.includes('配置') || errorMsg.includes('AI') || errorMsg.includes('API')) {
        showError('请前往系统设置->AI服务配置中完成配置')
      } else {
        showError(errorMsg)
      }
      dataList.value = []
    } finally {
      dataLoading.value = false
    }
  }

  // Delete knowledge data
  const handleDeleteData = (documentId: string) => {
    confirmDialog.value = {
      visible: true,
      title: '删除资料',
      message: '确定要删除该资料吗？删除后不可恢复。',
      type: 'danger',
      onConfirm: async () => {
        confirmDialog.value.visible = false
        try {
          const response = await deleteRAGData({ documentId })
          if (!response.ok) {
            if (response.status === 405 || response.status === 404) {
              throw new Error('请前往系统设置->AI服务配置中完成配置')
            }
            throw new Error(`删除资料失败: ${response.status}`)
          }
          const result = await response.json()
          if (result.code === 0 || result.code === 200) {
            showSuccess('资料删除成功')
            // 从列表中移除已删除项
            dataList.value = dataList.value.filter(item => item.documentId !== documentId)
          } else {
            // 检查是否是AI未配置的错误
            const errorMsg = result.msg || '删除资料失败'
            if (errorMsg.includes('AI') || errorMsg.includes('API') || errorMsg.includes('配置')) {
              throw new Error('请前往系统设置->AI服务配置中完成配置')
            }
            throw new Error(errorMsg)
          }
        } catch (error: any) {
          console.error('删除资料失败:', error)
          // 如果错误消息包含配置相关提示，使用友好提示
          const errorMsg = error.message || '删除资料失败'
          if (errorMsg.includes('配置') || errorMsg.includes('AI') || errorMsg.includes('API')) {
            showError('请前往系统设置->AI服务配置中完成配置')
          } else {
            showError(errorMsg)
          }
        }
      }
    }
  }

  // Generate unique ID
  const genId = () => Date.now().toString(36) + Math.random().toString(36).substring(2, 7)

  // Scroll chat to bottom
  const scrollChatToBottom = () => {
    nextTick(() => {
      if (chatListRef.value) {
        chatListRef.value.scrollTop = chatListRef.value.scrollHeight
      }
    })
  }

  // Send chat message
  const handleSendChat = async () => {
    if (!selectedGoods.value) {
      showInfo('请先选择商品')
      return
    }
    if (!chatInput.value.trim()) return
    if (chatSending.value) return

    const userMsg: ChatMessage = {
      id: genId(),
      role: 'user',
      content: chatInput.value.trim(),
      timestamp: Date.now()
    }
    chatMessages.value.push(userMsg)
    const inputText = chatInput.value.trim()
    chatInput.value = ''
    scrollChatToBottom()

    // Add assistant placeholder
    const assistantMsg: ChatMessage = {
      id: genId(),
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      loading: true
    }
    chatMessages.value.push(assistantMsg)
    scrollChatToBottom()

    chatSending.value = true
    try {
      const response = await chatTestWithAI({
        msg: inputText,
        goodsId: selectedGoods.value.item.xyGoodId,
        accountId: selectedAccountId.value!
      })

      if (!response.ok) {
        if (response.status === 405 || response.status === 404) {
          throw new Error('请前往系统设置->AI服务配置中完成配置')
        }
        throw new Error(`请求失败: ${response.status}`)
      }

      // 处理 SSE 流式响应
      assistantMsg.loading = false

      const reader = response.body?.getReader()
      const decoder = new TextDecoder()

      if (reader) {
        let buffer = ''
        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })

          // 处理 SSE 格式: data:xxx\n\n
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              if (data === '[DONE]') continue
              try {
                // 尝试解析 JSON，提取 reply/content/text 字段
                const parsed = JSON.parse(data)
                assistantMsg.content += parsed.reply || parsed.content || parsed.text || ''
              } catch {
                // 直接作为文本追加
                assistantMsg.content += data
              }
              scrollChatToBottom()
            }
          }
        }

        // 处理剩余 buffer
        if (buffer.startsWith('data:')) {
          const data = buffer.substring(5).trim()
          if (data && data !== '[DONE]') {
            try {
              const parsed = JSON.parse(data)
              assistantMsg.content += parsed.reply || parsed.content || parsed.text || ''
            } catch {
              assistantMsg.content += data
            }
          }
        }
      } else {
        // 没有 reader（不支持流式读取），直接读取文本
        const text = await response.text()
        assistantMsg.content = text || '暂无回复'
      }

      // 如果流式读取后内容仍为空
      if (!assistantMsg.content) {
        assistantMsg.content = '暂无回复'
      }

      scrollChatToBottom()
    } catch (error: any) {
      console.error('AI 对话失败:', error)
      assistantMsg.loading = false
      assistantMsg.content = '对话失败，请稍后重试'
      scrollChatToBottom()
    } finally {
      chatSending.value = false
    }
  }

  // Handle chat input keydown (Enter to send)
  const handleChatKeydown = (e: KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSendChat()
    }
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

  // Load auto reply records
  const loadRecords = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    recordsLoading.value = true
    try {
      const response = await getAutoReplyRecords({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        pageNum: recordsPage.value,
        pageSize: recordsPageSize.value
      })
      if (response.code === 0 || response.code === 200) {
        recordsList.value = response.data?.list || []
        recordsTotal.value = response.data?.totalCount || 0
      }
    } catch (error: any) {
      console.error('加载自动回复记录失败:', error)
      recordsList.value = []
    } finally {
      recordsLoading.value = false
    }
  }

  // Toggle records panel
  const toggleRecords = () => {
    recordsVisible.value = !recordsVisible.value
    if (recordsVisible.value) {
      recordsPage.value = 1
      loadRecords()
    }
  }

  // View record detail
  const viewRecordDetail = (record: AutoReplyRecord) => {
    recordDetail.value = record
    recordDetailVisible.value = true
    contextExpanded.value = false
  }

  // Records page change
  const handleRecordsPageChange = (page: number) => {
    recordsPage.value = page
    loadRecords()
  }

  // Parse trigger context JSON
  const parseTriggerContext = (jsonStr: string | null | undefined) => {
    if (!jsonStr) return null
    try {
      return JSON.parse(jsonStr)
    } catch {
      return null
    }
  }

  // Confirm dialog actions
  const handleDialogConfirm = () => {
    confirmDialog.value.onConfirm()
  }

  const handleDialogCancel = () => {
    confirmDialog.value.visible = false
  }

  // Lifecycle
  onMounted(() => {
    loadAccounts()
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
    saving,
    accounts,
    selectedAccountId,
    goodsList,
    selectedGoods,
    goodsKeyword,
    goodsTotal,
    goodsLoading,
    goodsListRef,
    detailDialogVisible,
    selectedGoodsId,
    rightTab,
    dataContent,
    uploading,
    fixedMaterial,
    fixedMaterialSaving,
    fixedMaterialSyncing,
    fixedMaterialExpanded,
    dataList,
    dataLoading,
    dataVisible,
    chatMessages,
    chatInput,
    chatSending,
    chatListRef,
    isMobile,
    mobileView,
    confirmDialog,
    delaySeconds,
    configLoading,
    configSaving,
    recordsVisible,
    recordsList,
    recordsLoading,
    recordsTotal,
    recordsPage,
    recordsPageSize,
    recordDetailVisible,
    recordDetail,
    contextExpanded,
    replyRules,
    rulesLoading,
    ruleDialogVisible,
    ruleSaving,
    ruleImportRef,
    ruleForm,

    // Methods
    handleAccountChange,
    handleGoodsSearch,
    handleGoodsSearchInput,
    clearGoodsSearch,
    selectGoods,
    toggleAutoReply,
    toggleContextOn,
    handleUploadData,
    handleQueryData,
    handleDeleteData,
    handleSendChat,
    handleChatKeydown,
    handleGoodsScroll,
    goBackToGoods,
    viewGoodsDetail,
    handleDialogConfirm,
    handleDialogCancel,
    formatTime,
    formatPrice,
    getStatusText,
    getStatusClass,
    checkScreenSize,
    loadConfig,
    updateDelaySeconds,
    toggleRecords,
    loadRecords,
    viewRecordDetail,
    handleRecordsPageChange,
    parseTriggerContext,
    handleSaveFixedMaterial,
    handleSyncDetailToFixedMaterial,
    toggleFixedMaterialExpanded,
    loadRules,
    openCreateRule,
    openEditRule,
    saveRule,
    deleteRule,
    exportRulesCsv,
    triggerRuleImport,
    importRulesCsv,
    getRuleReplyTypeText,
    getRuleMatchTypeText,
    getRuleScopeText
  }
}

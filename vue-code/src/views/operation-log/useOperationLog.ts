import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getAccountList } from '@/api/account'
import {
  queryOperationLogs,
  deleteOldLogs,
  queryRuntimeLogFiles,
  queryRuntimeLogs,
  clearRuntimeLogs
} from '@/api/operation-log'
import type { OperationLog, RuntimeLogFile } from '@/api/operation-log'
import type { Account } from '@/types'
import { formatTime, showSuccess, showError, showInfo, showConfirm } from '@/utils'
import { getAccountAvatarText, getAccountDisplayName } from '@/utils/accountDisplay'

// Operation type config
const operationTypes = [
  { label: '全部', value: '' },
  { label: '扫码登录', value: 'LOGIN' },
  { label: 'WS连接', value: 'WEBSOCKET_CONNECT' },
  { label: 'WS断开', value: 'WEBSOCKET_DISCONNECT' },
  { label: '发送消息', value: 'SEND_MESSAGE' },
  { label: '接收消息', value: 'RECEIVE_MESSAGE' },
  { label: '自动发货', value: 'AUTO_DELIVERY' },
  { label: '自动回复', value: 'AUTO_REPLY' },
  { label: '确认收货', value: 'CONFIRM_SHIPMENT' },
  { label: 'Token刷新', value: 'TOKEN_REFRESH' },
  { label: 'Cookie更新', value: 'COOKIE_UPDATE' },
  { label: '商品同步', value: 'GOODS_SYNC' },
  { label: '消息同步', value: 'MESSAGE_SYNC' }
]

const operationModules = [
  { label: '全部', value: '' },
  { label: '账号', value: 'ACCOUNT' },
  { label: '消息', value: 'MESSAGE' },
  { label: '订单', value: 'ORDER' },
  { label: '商品', value: 'GOODS' },
  { label: '系统', value: 'SYSTEM' }
]

const operationStatuses = [
  { label: '全部', value: '' },
  { label: '成功', value: 1 },
  { label: '失败', value: 0 },
  { label: '部分成功', value: 2 }
]

export function useOperationLog() {
  const loading = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const logs = ref<OperationLog[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(20)
  const runtimeLogLoading = ref(false)
  const runtimeLogLines = ref<string[]>([])
  const runtimeLogFile = ref('')
  const runtimeLogSize = ref(0)
  const runtimeLogUpdatedAt = ref(0)
  const runtimeLogFull = ref(false)
  const runtimeLogFiles = ref<RuntimeLogFile[]>([])
  const selectedRuntimeLogFile = ref('')

  // Filters
  const filterType = ref('')
  const filterModule = ref('')
  const filterStatus = ref<string | number>('')

  // Responsive
  const isMobile = ref(false)
  const mobileView = ref<'accounts' | 'logs'>('accounts')
  const selectedAccountForMobile = ref<Account | null>(null)

  // Detail dialog
  const detailDialogVisible = ref(false)
  const detailLog = ref<OperationLog | null>(null)

  // Delete dialog
  const deleteDialogVisible = ref(false)
  const deleteDays = ref('')

  // Check screen size
  const checkScreenSize = () => {
    isMobile.value = window.innerWidth < 768
    if (!isMobile.value) {
      mobileView.value = 'accounts'
    }
  }

  // Mobile go back
  const goBackToAccounts = () => {
    mobileView.value = 'accounts'
  }

  // Current account
  const currentAccount = computed(() => {
    return accounts.value.find(acc => acc.id === selectedAccountId.value)
  })

  // Total pages
  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  // Get account avatar
  const getAccountAvatar = (account: Account) => {
    return getAccountAvatarText(account)
  }

  // Get account name
  const getAccountName = (account: Account) => {
    return getAccountDisplayName(account)
  }

  // Get operation type text
  const getOperationTypeText = (type: string) => {
    const item = operationTypes.find(t => t.value === type)
    return item?.label || type
  }

  // Get operation type CSS class
  const getOperationTypeClass = (type: string) => {
    const map: Record<string, string> = {
      'LOGIN': 'login',
      'WEBSOCKET_CONNECT': 'ws-connect',
      'WEBSOCKET_DISCONNECT': 'ws-disconnect',
      'SEND_MESSAGE': 'send-msg',
      'RECEIVE_MESSAGE': 'recv-msg',
      'AUTO_DELIVERY': 'auto-delivery',
      'AUTO_REPLY': 'auto-reply',
      'CONFIRM_SHIPMENT': 'confirm-ship',
      'TOKEN_REFRESH': 'token-refresh',
      'COOKIE_UPDATE': 'cookie-update',
      'GOODS_SYNC': 'goods-sync',
      'MESSAGE_SYNC': 'msg-sync'
    }
    return map[type] || 'default'
  }

  // Get status text
  const getStatusText = (status: number) => {
    const statusMap: Record<number, string> = { 0: '失败', 1: '成功', 2: '部分成功' }
    return statusMap[status] || '未知'
  }

  // Get status CSS class
  const getStatusClass = (status: number) => {
    const map: Record<number, string> = { 0: 'fail', 1: 'success', 2: 'partial' }
    return map[status] || 'fail'
  }

  // Format time
  // Format duration
  const formatDuration = (ms?: number) => {
    if (!ms) return '-'
    if (ms < 1000) return `${ms}ms`
    return `${(ms / 1000).toFixed(2)}s`
  }

  // Load accounts
  const loadAccounts = async () => {
    loading.value = true
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id ?? null
          loadLogs()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    } finally {
      loading.value = false
    }
  }

  // Select account
  const selectAccount = (accountId: number, account?: Account) => {
    selectedAccountId.value = accountId
    page.value = 1
    loadLogs()

    if (isMobile.value && account) {
      selectedAccountForMobile.value = account
      mobileView.value = 'logs'
    }
  }

  // Handle account select change (for dropdown)
  const handleAccountSelectChange = () => {
    page.value = 1
    loadLogs()
  }

  // Load logs
  const loadLogs = async () => {
    if (!selectedAccountId.value) return

    loading.value = true
    try {
      const response = await queryOperationLogs({
        accountId: selectedAccountId.value,
        operationType: filterType.value || undefined,
        operationModule: filterModule.value || undefined,
        operationStatus: filterStatus.value !== '' ? Number(filterStatus.value) : undefined,
        page: page.value,
        pageSize: pageSize.value
      })

      if (response.code === 0 || response.code === 200) {
        logs.value = response.data?.logs || []
        total.value = response.data?.total || 0
      } else {
        throw new Error(response.msg || '加载失败')
      }
    } catch (error: any) {
      console.error('加载操作记录失败:', error)
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('加载失败: ' + error.message)
      }
    } finally {
      loading.value = false
    }
  }

  const selectDefaultRuntimeLogFile = (files: RuntimeLogFile[]) => {
    const allLog = files.find(item => item.file.endsWith('/all.log') || item.file === 'all.log')
    return allLog?.file || files[0]?.file || ''
  }

  const loadRuntimeLogFiles = async () => {
    try {
      const response = await queryRuntimeLogFiles()
      if (response.code === 0 || response.code === 200) {
        runtimeLogFiles.value = response.data?.files || []
        if (!selectedRuntimeLogFile.value) {
          selectedRuntimeLogFile.value = selectDefaultRuntimeLogFile(runtimeLogFiles.value)
        }
      } else {
        throw new Error(response.msg || '加载日志文件列表失败')
      }
    } catch (error: any) {
      console.error('加载日志文件列表失败:', error)
      runtimeLogFiles.value = []
      if (!error.messageShown) {
        showError('加载日志文件列表失败: ' + error.message)
      }
    }
  }

  const loadRuntimeLogs = async (full = runtimeLogFull.value) => {
    runtimeLogLoading.value = true
    try {
      if (runtimeLogFiles.value.length === 0) {
        await loadRuntimeLogFiles()
      }
      const file = selectedRuntimeLogFile.value || undefined
      const response = await queryRuntimeLogs({ file, lines: 200, full })
      if (response.code === 0 || response.code === 200) {
        runtimeLogLines.value = response.data?.lines || []
        runtimeLogFile.value = response.data?.file || ''
        selectedRuntimeLogFile.value = runtimeLogFile.value
        runtimeLogSize.value = response.data?.size || 0
        runtimeLogUpdatedAt.value = response.data?.lastModified || 0
        runtimeLogFull.value = response.data?.full || false
      } else {
        throw new Error(response.msg || '加载运行日志失败')
      }
    } catch (error: any) {
      console.error('加载运行日志失败:', error)
      runtimeLogLines.value = []
      if (!error.messageShown) {
        showError('加载运行日志失败: ' + error.message)
      }
    } finally {
      runtimeLogLoading.value = false
    }
  }

  const handleRuntimeLogFileChange = () => {
    runtimeLogFull.value = false
    loadRuntimeLogs(false)
  }

  const showFullRuntimeLog = () => {
    runtimeLogFull.value = true
    loadRuntimeLogs(true)
  }

  const showRecentRuntimeLog = () => {
    runtimeLogFull.value = false
    loadRuntimeLogs(false)
  }

  const handleClearRuntimeLog = async () => {
    if (!runtimeLogFile.value) {
      showInfo('当前没有可清空的运行日志')
      return
    }
    try {
      await showConfirm(`确定清空运行日志「${runtimeLogFile.value}」吗？`, '清空运行日志')
      const response = await clearRuntimeLogs({ file: selectedRuntimeLogFile.value || runtimeLogFile.value })
      if (response.code === 0 || response.code === 200) {
        showSuccess('运行日志已清空')
        runtimeLogLines.value = []
        runtimeLogFile.value = response.data?.file || runtimeLogFile.value
        runtimeLogSize.value = response.data?.size || 0
        runtimeLogUpdatedAt.value = response.data?.lastModified || Date.now()
        await loadRuntimeLogFiles()
      } else {
        throw new Error(response.msg || '清空运行日志失败')
      }
    } catch (error: any) {
      if (error === 'cancel') return
      console.error('清空运行日志失败:', error)
      if (!error.messageShown) {
        showError('清空运行日志失败: ' + error.message)
      }
    }
  }

  // Filter
  const handleFilter = () => {
    page.value = 1
    loadLogs()
  }

  // Reset filter
  const handleResetFilter = () => {
    filterType.value = ''
    filterModule.value = ''
    filterStatus.value = ''
    page.value = 1
    loadLogs()
  }

  // Page change
  const handlePageChange = (newPage: number) => {
    page.value = newPage
    loadLogs()
  }

  // Refresh
  const handleRefresh = () => {
    loadLogs()
    loadRuntimeLogs()
    showInfo('已刷新')
  }

  // View detail
  const viewDetail = (log: OperationLog) => {
    detailLog.value = log
    detailDialogVisible.value = true
  }

  // Close detail
  const closeDetail = () => {
    detailDialogVisible.value = false
    detailLog.value = null
  }

  // Delete old logs - open dialog
  const openDeleteDialog = () => {
    deleteDays.value = ''
    deleteDialogVisible.value = true
  }

  // Confirm delete old logs
  const confirmDeleteOld = async () => {
    const days = parseInt(deleteDays.value)
    if (isNaN(days) || days <= 0) {
      showError('请输入有效的天数')
      return
    }

    try {
      const response = await deleteOldLogs(days)
      if (response.code === 0 || response.code === 200) {
        showSuccess(`成功删除${response.data}条记录`)
        deleteDialogVisible.value = false
        loadLogs()
      } else {
        throw new Error(response.msg || '删除失败')
      }
    } catch (error: any) {
      console.error('删除旧日志失败:', error)
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('删除失败: ' + error.message)
      }
    }
  }

  // Lifecycle
  onMounted(() => {
    loadAccounts()
    loadRuntimeLogFiles().then(() => loadRuntimeLogs())
    checkScreenSize()
    window.addEventListener('resize', checkScreenSize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', checkScreenSize)
  })

  return {
    // State
    loading,
    accounts,
    selectedAccountId,
    logs,
    total,
    page,
    pageSize,
    runtimeLogLoading,
    runtimeLogLines,
    runtimeLogFile,
    runtimeLogSize,
    runtimeLogUpdatedAt,
    runtimeLogFull,
    runtimeLogFiles,
    selectedRuntimeLogFile,
    totalPages,
    filterType,
    filterModule,
    filterStatus,
    isMobile,
    mobileView,
    selectedAccountForMobile,
    detailDialogVisible,
    detailLog,
    deleteDialogVisible,
    deleteDays,
    currentAccount,

    // Constants
    operationTypes,
    operationModules,
    operationStatuses,

    // Methods
    selectAccount,
    handleAccountSelectChange,
    loadLogs,
    loadRuntimeLogFiles,
    loadRuntimeLogs,
    handleRuntimeLogFileChange,
    showFullRuntimeLog,
    showRecentRuntimeLog,
    handleClearRuntimeLog,
    handleFilter,
    handleResetFilter,
    handlePageChange,
    handleRefresh,
    viewDetail,
    closeDetail,
    openDeleteDialog,
    confirmDeleteOld,
    goBackToAccounts,
    getAccountAvatar,
    getAccountName,
    getOperationTypeText,
    getOperationTypeClass,
    getStatusText,
    getStatusClass,
    formatTime,
    formatDuration,
    checkScreenSize
  }
}

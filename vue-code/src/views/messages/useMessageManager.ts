import { ref, computed } from 'vue'
import { getAccountList } from '@/api/account'
import { getMessageList } from '@/api/message'
import { sendMessage } from '@/api/websocket'
import { sendImageMessage } from '@/api/image'
import { formatMonthDayTime, showSuccess, showError, showInfo } from '@/utils'
import { isSystemEventMessage } from '@/views/online-messages/onlineMessageRender'
import type { Account } from '@/types'
import type { ChatMessage } from '@/api/message'

const AUTO_REFRESH_PRESET_OPTIONS = [5, 10, 15] as const
const DEFAULT_CUSTOM_REFRESH_SECONDS = 30
const MIN_CUSTOM_REFRESH_SECONDS = 3
const MAX_CUSTOM_REFRESH_SECONDS = 300

type RefreshIntervalMode = '5' | '10' | '15' | 'custom'

export function useMessageManager() {
  const loading = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const messageList = ref<ChatMessage[]>([])
  const currentPage = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const filterCurrentAccount = ref(false)

  // 快速回复
  const quickReplyVisible = ref(false)
  const quickReplyMessage = ref('')
  const quickReplySending = ref(false)
  const currentReplyMessage = ref<ChatMessage | null>(null)
  const quickReplyImage = ref('')

  // 手机端
  const isMobile = ref(false)

  // 自动刷新
  const autoRefreshEnabled = ref(false)
  const refreshIntervalMode = ref<RefreshIntervalMode>('10')
  const customRefreshSeconds = ref(DEFAULT_CUSTOM_REFRESH_SECONDS)
  let autoRefreshTimer: number | null = null

  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

  const selectedAutoRefreshSeconds = computed(() => {
    if (refreshIntervalMode.value === 'custom') {
      return clampRefreshSeconds(customRefreshSeconds.value)
    }
    return Number(refreshIntervalMode.value)
  })

  const getCurrentAccountUnb = computed(() => {
    if (!selectedAccountId.value) return ''
    const account = accounts.value.find(acc => acc.id === selectedAccountId.value)
    return account ? account.unb : ''
  })

  const clampRefreshSeconds = (value: number) => {
    if (!Number.isFinite(value)) return DEFAULT_CUSTOM_REFRESH_SECONDS
    return Math.min(MAX_CUSTOM_REFRESH_SECONDS, Math.max(MIN_CUSTOM_REFRESH_SECONDS, Math.floor(value)))
  }

  const normalizeCustomRefreshSeconds = () => {
    customRefreshSeconds.value = clampRefreshSeconds(Number(customRefreshSeconds.value))
  }

  const clearAutoRefreshTimer = () => {
    if (autoRefreshTimer !== null) {
      window.clearInterval(autoRefreshTimer)
      autoRefreshTimer = null
    }
  }

  const startAutoRefreshTimer = () => {
    clearAutoRefreshTimer()
    if (!autoRefreshEnabled.value) return
    autoRefreshTimer = window.setInterval(() => {
      void loadMessages({ silent: true })
    }, selectedAutoRefreshSeconds.value * 1000)
  }

  const setAutoRefreshEnabled = (enabled: boolean) => {
    autoRefreshEnabled.value = enabled
    if (enabled) {
      startAutoRefreshTimer()
    } else {
      clearAutoRefreshTimer()
    }
  }

  const updateAutoRefreshInterval = () => {
    if (refreshIntervalMode.value === 'custom') {
      normalizeCustomRefreshSeconds()
    }
    startAutoRefreshTimer()
  }

  const cleanupAutoRefresh = () => {
    clearAutoRefreshTimer()
  }

  // 判断是否为用户消息
  const isUserMessage = (row: ChatMessage) => {
    if (isSystemEventMessage(row)) return false
    return row.senderUserId !== getCurrentAccountUnb.value
  }

  // 消息类型
  const getContentTypeText = (contentType: number, row: ChatMessage) => {
    if (contentType === 999) return '手动回复'
    if (contentType === 888) return '自动回复'
    if (isSystemEventMessage(row)) return '系统提示'
    if (!isUserMessage(row)) return '我发送的'
    if (contentType === 1) return '用户消息'
    return `系统消息(${contentType})`
  }

  // 消息类型颜色
  const getContentTypeColor = (contentType: number, row: ChatMessage) => {
    if (contentType === 999) return '#5856d6'
    if (contentType === 888) return '#af52de'
    if (isSystemEventMessage(row)) return '#8e8e93'
    if (!isUserMessage(row)) return '#007aff'
    if (contentType === 1) return '#34c759'
    return '#ff9500'
  }

  const getContentTypeBg = (contentType: number, row: ChatMessage) => {
    if (contentType === 999) return 'rgba(88, 86, 214, 0.1)'
    if (contentType === 888) return 'rgba(175, 82, 222, 0.1)'
    if (isSystemEventMessage(row)) return 'rgba(142, 142, 147, 0.12)'
    if (!isUserMessage(row)) return 'rgba(0, 122, 255, 0.1)'
    if (contentType === 1) return 'rgba(52, 199, 89, 0.1)'
    return 'rgba(255, 149, 0, 0.1)'
  }

  // 格式化消息时间
  const formatMessageTime = (timestamp: number) => {
    if (!timestamp) return '-'
    const date = new Date(timestamp)
    const now = new Date()
    const diff = now.getTime() - date.getTime()
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
    return formatMonthDayTime(timestamp)
  }

  // 加载账号列表
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id ?? null
          await loadMessages()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  // 加载消息列表
  const loadMessages = async (options: { silent?: boolean } = {}) => {
    if (!selectedAccountId.value) {
      if (!options.silent) {
        showInfo('请先选择账号')
      }
      return
    }
    if (loading.value) return

    loading.value = true
    try {
      const response = await getMessageList({
        xianyuAccountId: selectedAccountId.value,
        pageNum: currentPage.value,
        pageSize: pageSize.value,
        filterCurrentAccount: filterCurrentAccount.value
      })
      if (response.code === 0 || response.code === 200) {
        messageList.value = response.data?.list || []
        total.value = response.data?.totalCount || 0
      } else {
        throw new Error(response.msg || '获取消息列表失败')
      }
    } catch (error: any) {
      console.error('加载消息列表失败:', error)
      messageList.value = []
    } finally {
      loading.value = false
    }
  }

  // 账号变更
  const handleAccountChange = () => {
    currentPage.value = 1
    loadMessages()
  }

  // 分页
  const handlePageChange = (page: number) => {
    currentPage.value = page
    loadMessages()
  }

  // 打开快速回复
  const openQuickReply = (message: ChatMessage) => {
    currentReplyMessage.value = message
    quickReplyMessage.value = ''
    quickReplyImage.value = ''
    quickReplyVisible.value = true
  }

  // 发送快速回复
  const handleQuickReply = async () => {
    if (!quickReplyMessage.value.trim() && !quickReplyImage.value) {
      showInfo('请输入回复内容或上传图片')
      return
    }
    if (!currentReplyMessage.value || !selectedAccountId.value) {
      showError('消息信息不完整')
      return
    }
    if (!currentReplyMessage.value.sid) {
      showError('会话ID不存在')
      return
    }
    if (!currentReplyMessage.value.senderUserId) {
      showError('接收方ID不存在')
      return
    }
    quickReplySending.value = true
    try {
      // 发送文本消息
      if (quickReplyMessage.value.trim()) {
        const response = await sendMessage({
          xianyuAccountId: selectedAccountId.value,
          cid: currentReplyMessage.value.sid,
          toId: currentReplyMessage.value.senderUserId,
          text: quickReplyMessage.value.trim(),
          xyGoodsId: currentReplyMessage.value.xyGoodsId
        })
        if (response.code !== 0 && response.code !== 200) {
          showError('文本消息发送失败')
        }
      }

      // 发送图片消息
      if (quickReplyImage.value && quickReplyImage.value.split(',').some((s: string) => s.trim())) {
        const urls = quickReplyImage.value.split(',').map((s: string) => s.trim()).filter((s: string) => s)
        for (const url of urls) {
          const imageResponse = await sendImageMessage({
            xianyuAccountId: selectedAccountId.value,
            cid: currentReplyMessage.value.sid,
            toId: currentReplyMessage.value.senderUserId,
            imageUrl: url,
            xyGoodsId: currentReplyMessage.value.xyGoodsId
          })
          if (imageResponse.code !== 0 && imageResponse.code !== 200) {
            showError('图片消息发送失败')
          }
        }
      }

      showSuccess('消息发送成功')
      quickReplyVisible.value = false
      quickReplyMessage.value = ''
      quickReplyImage.value = ''
      currentReplyMessage.value = null
      loadMessages()
    } catch (error: any) {
      console.error('发送消息失败:', error)
    } finally {
      quickReplySending.value = false
    }
  }

  return {
    loading,
    accounts,
    selectedAccountId,
    messageList,
    currentPage,
    pageSize,
    total,
    totalPages,
    filterCurrentAccount,
    quickReplyVisible,
    quickReplyMessage,
    quickReplySending,
    currentReplyMessage,
    quickReplyImage,
    isMobile,
    autoRefreshPresetOptions: AUTO_REFRESH_PRESET_OPTIONS,
    autoRefreshEnabled,
    refreshIntervalMode,
    customRefreshSeconds,
    selectedAutoRefreshSeconds,
    getCurrentAccountUnb,
    loadAccounts,
    loadMessages,
    handleAccountChange,
    handlePageChange,
    openQuickReply,
    handleQuickReply,
    setAutoRefreshEnabled,
    updateAutoRefreshInterval,
    cleanupAutoRefresh,
    isUserMessage,
    getContentTypeText,
    getContentTypeColor,
    getContentTypeBg,
    formatMessageTime
  }
}

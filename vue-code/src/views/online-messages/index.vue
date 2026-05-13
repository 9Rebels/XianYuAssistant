<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getContextMessages, getOnlineConversations, type ChatMessage, type OnlineConversation } from '@/api/message'
import { sendMessage } from '@/api/websocket'
import { sendImageMessage, uploadImage } from '@/api/image'
import { getConnectionStatus, startConnection } from '@/api/websocket'
import { getGoodsDetail, type GoodsItemWithConfig } from '@/api/goods'
import { getSetting } from '@/api/setting'
import { formatMonthDayTime, showError, showInfo, showSuccess } from '@/utils'
import { getAccountAvatarText, getAccountDisplayName } from '@/utils/accountDisplay'
import { getAuthToken } from '@/utils/request'
import { useAppNotificationStore } from '@/stores/appNotification'
import { GOOFISH_IM_EMBED_ENABLED_KEY, GOOFISH_IM_URL } from '@/constants/goofishIm'
import type { Account } from '@/types'
import GoodsDetail from '@/views/goods/components/GoodsDetail.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconImage from '@/components/icons/IconImage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconSend from '@/components/icons/IconSend.vue'
import IconSmile from '@/components/icons/IconSmile.vue'
import IconWifi from '@/components/icons/IconWifi.vue'
import IconWifiOff from '@/components/icons/IconWifiOff.vue'
import {
  conversationPreview,
  isOutgoingMessage,
  isReviewInviteCard,
  isSystemEventMessage,
  isSystemConversation,
  isTradeActionMessage,
  type RenderedKind,
  type RenderedCard,
  renderEmojiParts,
  renderMessage,
  shouldShowMessage
} from './onlineMessageRender'
import { useSseConnection } from '@/composables/useSseConnection'
import './online-messages.css'

const accounts = ref<Account[]>([])
const route = useRoute()
const notificationStore = useAppNotificationStore()
const selectedAccountId = ref<number | null>(null)
const conversations = ref<OnlineConversation[]>([])
const selectedConversation = ref<OnlineConversation | null>(null)
const messages = ref<ChatMessage[]>([])
const loadingConversations = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const uploadingImage = ref(false)
const connecting = ref(false)
const loadingGoodsDetail = ref(false)
const searchKeyword = ref('')
const replyText = ref('')
const imageUrl = ref('')
const connectionConnected = ref(false)
const autoRefreshEnabled = ref(true)
const goofishImEmbedEnabled = ref(false)
const activeMessageView = ref<'local' | 'official'>('local')
const mobilePanel = ref<'list' | 'chat'>('list')
const isMobile = ref(false)
const messagesRef = ref<HTMLElement | null>(null)
const replyTextareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const detailDialogVisible = ref(false)
const selectedGoodsId = ref('')
const goodsDetail = ref<GoodsItemWithConfig | null>(null)
const imagePreviewVisible = ref(false)
const imagePreviewUrl = ref('')
const emojiPanelVisible = ref(false)
let refreshTimer: number | null = null
let realtimeRefreshing = false
let realtimeTick = 0
let offSseMessage: (() => void) | null = null
let offSseConnection: (() => void) | null = null
const knownConversationTimes = new Map<string, number>()
const flashingConversationKeys = ref(new Set<string>())
const flashingAccountIds = ref(new Set<number>())

const CONVERSATION_LIMIT = 40
const MESSAGE_LIMIT = 50
const AUTO_REFRESH_INTERVAL_MS = 60000
const MESSAGE_BOTTOM_THRESHOLD = 96
const EMOJI_OPTIONS = ['😀', '😁', '😂', '😊', '😍', '😎', '😭', '😡', '👍', '🙏', '🤝', '👌', '🎉', '❤️', '🌹', '🔥', '💰', '📦', '🚚', '✅', '❌', '❓']

const currentAccountUnb = computed(() => {
  const account = accounts.value.find(item => item.id === selectedAccountId.value)
  return account?.unb || ''
})

const currentAccountName = computed(() => {
  const account = accounts.value.find(item => item.id === selectedAccountId.value)
  return getAccountDisplayName(account, '未选择账号')
})

const currentAccountAvatar = computed(() => {
  const account = accounts.value.find(item => item.id === selectedAccountId.value)
  return account?.avatar || ''
})

const selectedConversationImage = computed(() => {
  return selectedConversation.value?.goodsCoverPic || selectedConversation.value?.cardImageUrl || ''
})

const selectedGoodsImages = computed(() => {
  const detail = goodsDetail.value?.item
  if (!detail) return []
  const images: string[] = []
  if (detail.infoPic) {
    try {
      const parsed = JSON.parse(detail.infoPic)
      if (Array.isArray(parsed)) {
        parsed.forEach(item => {
          if (item?.url) images.push(item.url)
        })
      }
    } catch {
      // ignore malformed image list
    }
  }
  if (!images.length && detail.coverPic) images.push(detail.coverPic)
  return images.slice(0, 4)
})

const filteredConversations = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return conversations.value
  return conversations.value.filter(item => {
    return [
      item.peerUserName,
      item.peerUserId,
      item.lastMessage,
      item.goodsTitle,
      item.xyGoodsId
    ].some(value => (value || '').toLowerCase().includes(keyword))
  })
})

const visibleMessages = computed(() => {
  return messages.value
    .filter(message => shouldShowMessage(message, selectedConversation.value))
    .map(message => ({ message, rendered: renderMessage(message) }))
})

const goofishImEmbedActive = computed(() => {
  return goofishImEmbedEnabled.value && activeMessageView.value === 'official'
})

const canSend = computed(() => {
  return !!selectedAccountId.value
    && !!selectedConversation.value?.sid
    && !!resolveReceiverId(selectedConversation.value)
    && !isSystemConversation(selectedConversation.value)
    && (!!replyText.value.trim() || !!imageUrl.value.trim())
    && !sending.value
    && !uploadingImage.value
})

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    mobilePanel.value = 'list'
  }
}

const isNearMessageBottom = () => {
  const el = messagesRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight <= MESSAGE_BOTTOM_THRESHOLD
}

const hasMessageListChanged = (nextMessages: ChatMessage[]) => {
  if (messages.value.length !== nextMessages.length) return true
  return nextMessages.some((message, index) => {
    const current = messages.value[index]
    return !current
      || current.id !== message.id
      || current.messageTime !== message.messageTime
      || current.completeMsg !== message.completeMsg
  })
}

const loadAccounts = async () => {
  const response = await getAccountList()
  if (response.code === 0 || response.code === 200) {
    accounts.value = response.data?.accounts || []
    const queryAccountId = Number(route.query.accountId)
    if (queryAccountId && accounts.value.some(item => item.id === queryAccountId)) {
      selectedAccountId.value = queryAccountId
      return
    }
    if (!selectedAccountId.value && accounts.value.length > 0) {
      selectedAccountId.value = accounts.value[0]?.id || null
    }
  }
}

const loadConnectionStatus = async () => {
  if (!selectedAccountId.value) {
    connectionConnected.value = false
    return
  }
  try {
    const response = await getConnectionStatus(selectedAccountId.value)
    connectionConnected.value = !!response.data?.connected
  } catch {
    connectionConnected.value = false
  }
}

const loadConversations = async (silent = false) => {
  if (!selectedAccountId.value) {
    if (!silent) showInfo('请先选择账号')
    return
  }
  if (!silent) loadingConversations.value = true
  try {
    const response = await getOnlineConversations({
      xianyuAccountId: selectedAccountId.value,
      limit: CONVERSATION_LIMIT
    })
    if (response.code === 0 || response.code === 200) {
      const list = response.data || []
      conversations.value = list
      rememberConversationTimes(list)
      syncSelectedConversation()
      await selectConversationFromQuery(list)
    }
  } catch (error) {
    if (!silent) {
      console.error('加载在线会话失败:', error)
    }
  } finally {
    loadingConversations.value = false
  }
}

const syncSelectedConversation = () => {
  if (!selectedConversation.value) return
  const latest = conversations.value.find(item => item.sid === selectedConversation.value?.sid)
  if (latest) {
    selectedConversation.value = latest
  }
}

const loadMessages = async (silent = false, forceStickToBottom = false) => {
  if (!selectedAccountId.value || !selectedConversation.value?.sid) return
  if (!silent) loadingMessages.value = true
  try {
    const shouldStickToBottom = forceStickToBottom || isNearMessageBottom()
    const response = await getContextMessages({
      xianyuAccountId: selectedAccountId.value,
      sid: selectedConversation.value.sid,
      limit: MESSAGE_LIMIT,
      offset: 0
    })
    if (response.code === 0 || response.code === 200) {
      const nextMessages = [...(response.data || [])].sort((a, b) => (a.messageTime || 0) - (b.messageTime || 0))
      if (!hasMessageListChanged(nextMessages)) return
      messages.value = nextMessages
      if (shouldStickToBottom) {
        await scrollToBottom()
      }
    }
  } catch (error) {
    if (!silent) {
      console.error('加载会话消息失败:', error)
    }
  } finally {
    loadingMessages.value = false
  }
}

const loadGoodsDetail = async () => {
  goodsDetail.value = null
  const xyGoodsId = selectedConversation.value?.xyGoodsId
  if (!xyGoodsId) return

  loadingGoodsDetail.value = true
  try {
    const response = await getGoodsDetail(xyGoodsId)
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value = response.data?.itemWithConfig || null
    }
  } catch (error) {
    console.error('加载在线消息商品详情失败:', error)
  } finally {
    loadingGoodsDetail.value = false
  }
}

const refreshAll = async (silent = false) => {
  await Promise.all([loadConversations(silent), loadConnectionStatus()])
  if (selectedConversation.value) {
    await loadMessages(silent, !silent)
  }
}

const refreshRealtime = async () => {
  if (!selectedAccountId.value || realtimeRefreshing || document.hidden) return
  realtimeRefreshing = true
  try {
    const previousSelectedMessageTime = selectedConversation.value?.lastMessageTime || 0
    await Promise.all([loadConversationsForRealtime(selectedAccountId.value, true), loadConnectionStatus()])

    const latestSelectedMessageTime = selectedConversation.value?.lastMessageTime || 0
    if (selectedConversation.value && latestSelectedMessageTime > previousSelectedMessageTime) {
      await loadMessages(true)
    }

    realtimeTick += 1
  } finally {
    realtimeRefreshing = false
  }
}

const selectConversation = async (conversation: OnlineConversation) => {
  selectedConversation.value = conversation
  emojiPanelVisible.value = false
  mobilePanel.value = 'chat'
  clearConversationFlash(conversation)
  await Promise.all([loadMessages(), loadGoodsDetail()])
}

const handleAccountChange = async () => {
  selectedConversation.value = null
  messages.value = []
  goodsDetail.value = null
  emojiPanelVisible.value = false
  mobilePanel.value = 'list'
  await refreshAll()
}

const selectAccount = async (accountId: number) => {
  if (selectedAccountId.value === accountId) return
  selectedAccountId.value = accountId
  clearAccountFlash(accountId)
  await handleAccountChange()
}

const handleConnectionClick = async () => {
  if (!selectedAccountId.value) {
    showInfo('请先选择账号')
    return
  }
  if (connectionConnected.value) return
  connecting.value = true
  try {
    const response = await startConnection(selectedAccountId.value)
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '启动连接失败')
    }
    showSuccess('连接启动中')
    await loadConnectionStatus()
  } catch (error: any) {
    showError(error.message || '启动连接失败')
  } finally {
    connecting.value = false
  }
}

const settingEnabled = (value: string | null | undefined, defaultValue: boolean) => {
  if (value === null || value === undefined || value === '') return defaultValue
  return value === '1' || value.toLowerCase() === 'true'
}

const loadGoofishImConfig = async () => {
  try {
    const response = await getSetting({ settingKey: GOOFISH_IM_EMBED_ENABLED_KEY })
    goofishImEmbedEnabled.value = settingEnabled(response.data?.settingValue, false)
    if (!goofishImEmbedEnabled.value) activeMessageView.value = 'local'
  } catch (error) {
    console.error('加载官方IM嵌入配置失败:', error)
    goofishImEmbedEnabled.value = false
    activeMessageView.value = 'local'
  }
}

const switchMessageView = (view: 'local' | 'official') => {
  activeMessageView.value = view
}

const openOfficialIm = () => {
  window.open(GOOFISH_IM_URL, '_blank', 'noopener,noreferrer')
}

const sendReply = async () => {
  if (!selectedAccountId.value || !selectedConversation.value) return
  if (isSystemConversation(selectedConversation.value)) {
    showInfo('系统通知仅支持查看')
    emojiPanelVisible.value = false
    return
  }
  const receiverId = resolveReceiverId(selectedConversation.value)
  if (!receiverId) {
    showError('该会话缺少接收方ID，暂时无法发送')
    return
  }
  if (!replyText.value.trim() && !imageUrl.value.trim()) {
    showInfo('请输入消息或图片URL')
    return
  }

  sending.value = true
  try {
    if (replyText.value.trim()) {
      const response = await sendMessage({
        xianyuAccountId: selectedAccountId.value,
        cid: selectedConversation.value.sid,
        toId: receiverId,
        text: replyText.value.trim(),
        xyGoodsId: selectedConversation.value.xyGoodsId
      })
      if (response.code !== 0 && response.code !== 200) {
        throw new Error(response.msg || '发送文字消息失败')
      }
    }

    const urls = imageUrl.value.split(',').map(item => item.trim()).filter(Boolean)
    for (const url of urls) {
      const response = await sendImageMessage({
        xianyuAccountId: selectedAccountId.value,
        cid: selectedConversation.value.sid,
        toId: receiverId,
        imageUrl: url,
        xyGoodsId: selectedConversation.value.xyGoodsId
      })
      if (response.code !== 0 && response.code !== 200) {
        throw new Error(response.msg || '发送图片消息失败')
      }
    }

    replyText.value = ''
    imageUrl.value = ''
    emojiPanelVisible.value = false
    showSuccess('消息已发送')
    await refreshAll(true)
  } catch (error: any) {
    console.error('发送在线消息失败:', error)
    if (!error.messageShown) showError(error.message || '发送失败')
  } finally {
    sending.value = false
  }
}

const handleReplyKeydown = async (event: KeyboardEvent) => {
  if (event.key !== 'Enter' || event.shiftKey) return
  if (sending.value || uploadingImage.value) return
  const conversation = selectedConversation.value
  if (!conversation || isSystemConversation(conversation)) return
  event.preventDefault()
  await sendReply()
}

const toggleEmojiPanel = async () => {
  if (sending.value || uploadingImage.value) return
  if (selectedConversation.value && isSystemConversation(selectedConversation.value)) {
    showInfo('系统通知仅支持查看')
    emojiPanelVisible.value = false
    return
  }
  emojiPanelVisible.value = !emojiPanelVisible.value
  if (emojiPanelVisible.value) {
    await nextTick()
    replyTextareaRef.value?.focus()
  }
}

const insertEmoji = async (emoji: string) => {
  if (sending.value || uploadingImage.value) return
  if (selectedConversation.value && isSystemConversation(selectedConversation.value)) return

  const textarea = replyTextareaRef.value
  if (!textarea) {
    replyText.value += emoji
    return
  }

  const start = textarea.selectionStart ?? replyText.value.length
  const end = textarea.selectionEnd ?? start
  replyText.value = `${replyText.value.slice(0, start)}${emoji}${replyText.value.slice(end)}`
  await nextTick()
  textarea.focus()
  const cursor = start + emoji.length
  textarea.setSelectionRange(cursor, cursor)
}

const openImagePicker = () => {
  if (!selectedAccountId.value) {
    showInfo('请先选择账号')
    return
  }
  if (selectedConversation.value && isSystemConversation(selectedConversation.value)) {
    showInfo('系统通知仅支持查看')
    return
  }
  fileInputRef.value?.click()
}

const handleImageFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedAccountId.value) return
  if (!file.type.startsWith('image/')) {
    showError('只能上传图片文件')
    input.value = ''
    return
  }

  uploadingImage.value = true
  try {
    const response = await uploadImage(selectedAccountId.value, file)
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '图片上传失败')
    }
    if (!response.data) {
      throw new Error('图片上传失败：未返回图片地址')
    }
    const urls = imageUrl.value.split(',').map(item => item.trim()).filter(Boolean)
    imageUrl.value = [...urls, response.data].join(',')
    showSuccess('图片已上传，可点击发送')
  } catch (error: any) {
    showError(error.message || '图片上传失败')
  } finally {
    uploadingImage.value = false
    input.value = ''
  }
}

const openGoodsDetail = (conversation?: OnlineConversation | null) => {
  const xyGoodsId = conversation?.xyGoodsId
  if (!xyGoodsId) return
  selectedGoodsId.value = xyGoodsId
  detailDialogVisible.value = true
}

const openImagePreview = (url: string) => {
  imagePreviewUrl.value = url
  imagePreviewVisible.value = true
}

const closeImagePreview = () => {
  imagePreviewVisible.value = false
  imagePreviewUrl.value = ''
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  }
}

const messageSide = (message: ChatMessage, renderedKind?: RenderedKind) => {
  if (isSystemEventMessage(message)) {
    return 'system'
  }
  if (renderedKind === 'card') {
    if (selectedConversation.value && isSystemConversation(selectedConversation.value)) {
      return 'system'
    }
    return isOutgoingMessage(message, currentAccountUnb.value) ? 'mine' : 'peer'
  }
  return isOutgoingMessage(message, currentAccountUnb.value) ? 'mine' : 'peer'
}

const isMine = (message: ChatMessage, renderedKind?: RenderedKind) => {
  return messageSide(message, renderedKind) === 'mine'
}

const lastMineVisibleMessageId = computed(() => {
  for (let index = visibleMessages.value.length - 1; index >= 0; index -= 1) {
    const item = visibleMessages.value[index]
    if (item && isMine(item.message, item.rendered.kind)) return item.message.id
  }
  return 0
})

const shouldShowMessageReadStatus = (message: ChatMessage, renderedKind?: RenderedKind) => {
  return message.id === lastMineVisibleMessageId.value
    && isMine(message, renderedKind)
    && shouldShowReadStatus(selectedConversation.value)
}

const formatTime = (timestamp?: number) => {
  if (!timestamp) return '-'
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  const full = formatMonthDayTime(timestamp)
  return full ? full.slice(0, 5) : '-'
}

const formatFullTime = (timestamp?: number) => {
  return timestamp ? formatMonthDayTime(timestamp) : ''
}

const displayName = (conversation: OnlineConversation) => {
  if (isSystemConversation(conversation)) return conversation.peerUserName || '工作台通知'
  return conversation.peerUserName || conversation.peerUserId || '未知联系人'
}

const avatarText = (conversation: OnlineConversation) => {
  return displayName(conversation).slice(0, 1).toUpperCase()
}

const avatarPalette: Array<[string, string]> = [
  ['#111827', '#4b5563'],
  ['#7c2d12', '#ea580c'],
  ['#065f46', '#10b981'],
  ['#1e3a8a', '#3b82f6'],
  ['#581c87', '#a855f7'],
  ['#9f1239', '#f43f5e'],
  ['#134e4a', '#14b8a6']
]

const avatarGradient = (seed?: string) => {
  const value = seed || 'default'
  const index = Array.from(value).reduce((sum, char) => sum + char.charCodeAt(0), 0) % avatarPalette.length
  const [from, to] = avatarPalette[index] ?? avatarPalette[0]!
  return `linear-gradient(135deg, ${from}, ${to})`
}

const conversationAvatarStyle = (conversation: OnlineConversation) => {
  if (conversation.peerAvatar) return {}
  return { background: avatarGradient(conversation.peerUserId || conversation.peerUserName || conversation.sid) }
}

const messageAvatarStyle = (message: ChatMessage, renderedKind?: RenderedKind) => {
  if (messageAvatarUrl(message, renderedKind)) return {}
  return { background: avatarGradient(message.senderUserId || message.senderUserName || selectedConversation.value?.sid) }
}

const messageSenderName = (message: ChatMessage, renderedKind?: RenderedKind) => {
  if (isMine(message, renderedKind)) return currentAccountName.value
  if (isTradeActionMessage(message)) {
    return selectedConversation.value?.peerUserName || selectedConversation.value?.peerUserId || '买家'
  }
  return message.senderUserName || selectedConversation.value?.peerUserName || selectedConversation.value?.peerUserId || '买家'
}

const messageAvatarText = (message: ChatMessage, renderedKind?: RenderedKind) => {
  return messageSenderName(message, renderedKind).slice(0, 1).toUpperCase()
}

const accountName = (account: Account) => {
  return getAccountDisplayName(account)
}

const accountAvatarText = (account: Account) => {
  return getAccountAvatarText(account)
}

const resolveReceiverId = (conversation?: OnlineConversation | null) => {
  if (!conversation || isSystemConversation(conversation)) return ''
  if (conversation.peerUserId) return conversation.peerUserId
  const atIndex = conversation.sid.indexOf('@')
  return atIndex > 0 ? conversation.sid.slice(0, atIndex) : ''
}

const messagePreview = (conversation: OnlineConversation) => {
  if (conversation.cardTitle) return `[${conversation.cardTag || '卡片'}] ${conversation.cardTitle}`
  return conversationPreview(conversation) || '暂无消息内容'
}

const messagePreviewParts = (conversation: OnlineConversation) => {
  return renderEmojiParts(messagePreview(conversation))
}

const conversationImage = (conversation: OnlineConversation) => {
  return conversation.goodsCoverPic || conversation.cardImageUrl || ''
}

const conversationBadges = (conversation: OnlineConversation) => {
  return [conversation.orderStatusText, conversation.autoDeliveryStateText].filter(Boolean).slice(0, 2)
}

const conversationPrice = (conversation: OnlineConversation) => {
  return conversation.orderAmountText || formatPrice(conversation.goodsPrice)
}

const shouldShowReadStatus = (conversation?: OnlineConversation | null) => {
  return !!conversation
    && !isSystemConversation(conversation)
    && (conversation.readStatus === 0 || conversation.readStatus === 1)
}

const readStatusText = (conversation?: OnlineConversation | null) => {
  if (!conversation) return ''
  return conversation.readStatusText || (conversation.readStatus === 1 ? '已读' : '未读')
}

const messageTextParts = (text: string) => renderEmojiParts(text)

const audioSource = (message: ChatMessage) => {
  if (!message.id) return message.media?.url || ''
  const token = getAuthToken()
  const query = token ? `?token=${encodeURIComponent(token)}` : ''
  return `/api/msg/audio/${message.id}${query}`
}

const cardSubtitle = (message: ChatMessage, card: RenderedCard) => {
  if (isReviewInviteCard(message, card)) {
    return card.subtitle || '说说这次的交易体验，帮助更多人'
  }
  return card.subtitle
}

const messageAvatarUrl = (message: ChatMessage, renderedKind?: RenderedKind) => {
  if (isMine(message, renderedKind)) return currentAccountAvatar.value
  return selectedConversation.value?.peerAvatar || ''
}

const getConversationKey = (conversation: OnlineConversation, accountId = conversation.xianyuAccountId) => {
  return `${accountId}:${conversation.sid}`
}

const accountLabelById = (accountId: number) => {
  const account = accounts.value.find(item => item.id === accountId)
  return account ? accountName(account) : `账号${accountId}`
}

const rememberConversationTimes = (list: OnlineConversation[]) => {
  list.forEach(conversation => {
    knownConversationTimes.set(getConversationKey(conversation), conversation.lastMessageTime || 0)
  })
}

const loadConversationsForRealtime = async (accountId: number, updateCurrentList = false) => {
  const response = await getOnlineConversations({ xianyuAccountId: accountId, limit: CONVERSATION_LIMIT })
  if (response.code !== 0 && response.code !== 200) return
  const list = response.data || []
  detectNewConversations(list, accountId)
  rememberConversationTimes(list)
  if (updateCurrentList && selectedAccountId.value === accountId) {
    conversations.value = list
    syncSelectedConversation()
  }
}

const refreshForMessageEvent = async (data: any) => {
  const accountId = Number(data?.accountId ?? data?.xianyuAccountId)
  if (!accountId || accountId !== selectedAccountId.value) return

  const eventSid = String(data?.sid || data?.sId || '')
  const currentSid = selectedConversation.value?.sid || ''
  await loadConversationsForRealtime(accountId, true)
  if (currentSid && eventSid === currentSid) {
    await loadMessages(true, true)
  }
}

const checkOtherAccountsForRealtime = async () => {
  const accountIds = accounts.value
    .map(account => account.id)
    .filter(accountId => accountId !== selectedAccountId.value)
  await Promise.all(accountIds.map(accountId => loadConversationsForRealtime(accountId, false).catch(() => undefined)))
}

const detectNewConversations = (list: OnlineConversation[], accountId: number) => {
  list.forEach(conversation => {
    const lastTime = conversation.lastMessageTime || 0
    const key = getConversationKey(conversation, accountId)
    const knownTime = knownConversationTimes.get(key)
    if (!knownTime || lastTime <= knownTime) return
    markConversationNew(conversation, accountId)
  })
}

const markConversationNew = (conversation: OnlineConversation, accountId: number) => {
  const key = getConversationKey(conversation, accountId)
  flashingConversationKeys.value = new Set([...flashingConversationKeys.value, key])
  flashingAccountIds.value = new Set([...flashingAccountIds.value, accountId])
  pushNewMessageToast(conversation, accountId)
}

const pushNewMessageToast = (conversation: OnlineConversation, accountId: number) => {
  notificationStore.pushOnlineMessageToast({
    accountId,
    sid: conversation.sid,
    title: displayName(conversation),
    content: messagePreview(conversation),
    meta: accountLabelById(accountId),
    time: conversation.lastMessageTime
  })
}

const clearConversationFlash = (conversation: OnlineConversation) => {
  const next = new Set(flashingConversationKeys.value)
  next.delete(getConversationKey(conversation))
  flashingConversationKeys.value = next
}

const clearAccountFlash = (accountId: number) => {
  const next = new Set(flashingAccountIds.value)
  next.delete(accountId)
  flashingAccountIds.value = next
}

const isConversationFlashing = (conversation: OnlineConversation) => {
  return flashingConversationKeys.value.has(getConversationKey(conversation))
}

const selectConversationFromQuery = async (list: OnlineConversation[]) => {
  const sid = typeof route.query.sid === 'string' ? route.query.sid : ''
  if (!sid || selectedConversation.value?.sid === sid) return
  const target = list.find(item => item.sid === sid)
  if (target) {
    await selectConversation(target)
  }
}

const toggleAutoRefresh = () => {
  autoRefreshEnabled.value = !autoRefreshEnabled.value
  if (autoRefreshEnabled.value) {
    startRefreshTimer()
    void refreshRealtime()
  } else {
    stopRefreshTimer()
  }
}

const formatPrice = (price?: string) => {
  if (!price) return '-'
  const num = Number(price)
  if (Number.isNaN(num)) return price
  return `¥${num.toFixed(2)}`
}

const startRefreshTimer = () => {
  stopRefreshTimer()
  if (!autoRefreshEnabled.value) return
  refreshTimer = window.setInterval(() => {
    void refreshRealtime()
  }, AUTO_REFRESH_INTERVAL_MS)
}

const stopRefreshTimer = () => {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(async () => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  await loadGoofishImConfig()
  await loadAccounts()
  await refreshAll()
  startRefreshTimer()

  // SSE 实时消息推送
  const { connect, on } = useSseConnection()
  connect()
  offSseMessage = on('message', data => {
    void refreshForMessageEvent(data)
  })
  offSseConnection = on('connection', () => {
    void refreshAll(true)
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
  stopRefreshTimer()
  offSseMessage?.()
  offSseConnection?.()
  offSseMessage = null
  offSseConnection = null
})

watch(() => route.query, async () => {
  const queryAccountId = Number(route.query.accountId)
  if (queryAccountId && queryAccountId !== selectedAccountId.value) {
    selectedAccountId.value = queryAccountId
    await handleAccountChange()
    return
  }
  await selectConversationFromQuery(conversations.value)
})
</script>

<template>
  <div class="om">
    <header class="om__header">
      <div class="om__title-block">
        <div class="om__title-icon">
          <IconChat />
        </div>
        <div>
          <h1 class="om__title">在线消息</h1>
          <p class="om__subtitle">按会话处理买家咨询，实时消息依赖连接管理中的 WebSocket 连接。</p>
        </div>
      </div>

      <div class="om__header-actions">
        <label class="om__account">
          <span class="om__label">账号</span>
          <select v-model="selectedAccountId" class="om__select" @change="handleAccountChange">
            <option :value="null" disabled>选择账号</option>
            <option v-for="account in accounts" :key="account.id" :value="account.id">
              {{ getAccountDisplayName(account) }}
            </option>
          </select>
        </label>
        <button
          class="om__connection"
          :class="{ 'om__connection--on': connectionConnected }"
          type="button"
          :disabled="connectionConnected || connecting"
          @click="handleConnectionClick"
        >
          <IconWifi v-if="connectionConnected" />
          <IconWifiOff v-else />
          <span>{{ connectionConnected ? '已连接' : (connecting ? '连接中' : '未连接') }}</span>
        </button>
        <button class="om__icon-btn" type="button" aria-label="刷新在线消息" @click="refreshAll()">
          <IconRefresh />
        </button>
        <button
          class="om__auto-toggle"
          :class="{ 'om__auto-toggle--on': autoRefreshEnabled }"
          type="button"
          :aria-pressed="autoRefreshEnabled"
          :title="autoRefreshEnabled ? '自动刷新已开启' : '自动刷新已关闭'"
          @click="toggleAutoRefresh"
        >
          <span class="om__auto-dot"></span>
          <span>{{ autoRefreshEnabled ? '自动' : '手动' }}</span>
        </button>
      </div>
    </header>

    <div v-if="goofishImEmbedEnabled" class="om__view-tabs" role="tablist" aria-label="消息视图">
      <button
        class="om__view-tab"
        :class="{ 'om__view-tab--active': activeMessageView === 'local' }"
        type="button"
        role="tab"
        :aria-selected="activeMessageView === 'local'"
        @click="switchMessageView('local')"
      >
        本地消息
      </button>
      <button
        class="om__view-tab"
        :class="{ 'om__view-tab--active': activeMessageView === 'official' }"
        type="button"
        role="tab"
        :aria-selected="activeMessageView === 'official'"
        @click="switchMessageView('official')"
      >
        官方IM
      </button>
    </div>

    <main v-if="!goofishImEmbedActive" class="om__workspace">
      <aside class="om__conversations" :class="{ 'om__conversations--hidden': isMobile && mobilePanel === 'chat' }">
        <div class="om__list-head">
          <div>
            <div class="om__list-title">消息</div>
            <div class="om__list-count">{{ filteredConversations.length }} 个会话</div>
          </div>
          <div class="om__account-strip">
            <button
              v-for="account in accounts"
              :key="account.id"
              class="om__account-avatar"
              :class="{
                'om__account-avatar--active': account.id === selectedAccountId,
                'om__account-avatar--new': flashingAccountIds.has(account.id)
              }"
              type="button"
              :title="accountName(account)"
              @click="selectAccount(account.id)"
            >
              <img v-if="account.avatar" :src="account.avatar" alt="" loading="lazy" />
              <span v-else>{{ accountAvatarText(account) }}</span>
            </button>
          </div>
        </div>

        <div class="om__search">
          <IconSearch />
          <input v-model="searchKeyword" type="search" placeholder="搜索联系人、商品或消息" />
        </div>

        <div class="om__conversation-list" :class="{ 'om__loading': loadingConversations }">
          <button
            v-for="conversation in filteredConversations"
            :key="conversation.sid"
            class="om__conversation"
            :class="{
              'om__conversation--active': conversation.sid === selectedConversation?.sid,
              'om__conversation--system': isSystemConversation(conversation),
              'om__conversation--new': isConversationFlashing(conversation)
            }"
            type="button"
            @click="selectConversation(conversation)"
          >
            <div
              class="om__avatar"
              :class="{ 'om__avatar--new': isConversationFlashing(conversation) }"
              :style="conversationAvatarStyle(conversation)"
            >
              <img v-if="conversation.peerAvatar" :src="conversation.peerAvatar" alt="" loading="lazy" />
              <span v-else>{{ avatarText(conversation) }}</span>
            </div>
            <div class="om__conversation-main">
              <div class="om__conversation-top">
                <span class="om__peer">{{ displayName(conversation) }}</span>
                <span class="om__time">{{ formatTime(conversation.lastMessageTime) }}</span>
              </div>
              <div v-if="isSystemConversation(conversation)" class="om__system-tag">系统通知</div>
              <div class="om__conversation-preview">
                <template v-for="(part, index) in messagePreviewParts(conversation)" :key="`${conversation.sid}-preview-${index}`">
                  <span v-if="part.type === 'emoji'" class="om__emoji" :title="part.title">{{ part.text }}</span>
                  <template v-else>{{ part.text }}</template>
                </template>
              </div>
              <div v-if="conversation.goodsTitle || conversation.xyGoodsId" class="om__goods-line">
                {{ conversation.goodsTitle || conversation.xyGoodsId }}
              </div>
              <div v-if="conversationBadges(conversation).length" class="om__badge-row">
                <span v-for="badge in conversationBadges(conversation)" :key="badge" class="om__status-badge">{{ badge }}</span>
              </div>
            </div>
            <div v-if="conversationImage(conversation)" class="om__conversation-side">
              <img
                class="om__goods-thumb om__goods-thumb--clickable"
                :src="conversationImage(conversation)"
                alt=""
                loading="lazy"
                @click.stop="openGoodsDetail(conversation)"
              />
              <span v-if="conversationPrice(conversation) !== '-'" class="om__side-price">{{ conversationPrice(conversation) }}</span>
            </div>
          </button>

          <div v-if="!loadingConversations && filteredConversations.length === 0" class="om__empty-list">
            <IconChat />
            <span>暂无会话</span>
          </div>
        </div>
      </aside>

      <section class="om__chat" :class="{ 'om__chat--hidden': isMobile && mobilePanel === 'list' }">
        <template v-if="selectedConversation">
          <div class="om__chat-head">
            <button v-if="isMobile" class="om__back-btn" type="button" aria-label="返回会话列表" @click="mobilePanel = 'list'">
              <IconChevronLeft />
            </button>
            <div class="om__avatar om__avatar--small" :style="conversationAvatarStyle(selectedConversation)">
              <img v-if="selectedConversation.peerAvatar" :src="selectedConversation.peerAvatar" alt="" loading="lazy" />
              <span v-else>{{ avatarText(selectedConversation) }}</span>
            </div>
            <button
              class="om__chat-title-block"
              :class="{ 'om__chat-title-block--clickable': !!selectedConversation.xyGoodsId }"
              type="button"
              @click="openGoodsDetail(selectedConversation)"
            >
              <div class="om__chat-title">{{ displayName(selectedConversation) }}</div>
              <div class="om__chat-meta">
                {{ isSystemConversation(selectedConversation) ? '系统通知' : (selectedConversation.goodsTitle || selectedConversation.xyGoodsId || currentAccountName) }}
              </div>
            </button>
            <div
              v-if="selectedConversation.orderStatusText || selectedConversation.autoDeliveryStateText"
              class="om__chat-badges"
            >
              <span v-if="selectedConversation.orderStatusText" class="om__status-badge">{{ selectedConversation.orderStatusText }}</span>
              <span v-if="selectedConversation.autoDeliveryStateText" class="om__status-badge">{{ selectedConversation.autoDeliveryStateText }}</span>
            </div>
            <button
              v-if="selectedConversationImage"
              class="om__chat-thumb-btn"
              type="button"
              aria-label="查看商品详情"
              @click="openGoodsDetail(selectedConversation)"
            >
              <img class="om__chat-thumb" :src="selectedConversationImage" alt="" />
            </button>
          </div>

          <div ref="messagesRef" class="om__messages" :class="{ 'om__loading': loadingMessages }">
            <div
              v-for="item in visibleMessages"
              :key="item.message.id"
              class="om__message"
              :class="{
                'om__message--mine': isMine(item.message, item.rendered.kind),
                'om__message--system-card': messageSide(item.message, item.rendered.kind) === 'system',
                'om__message--card': item.rendered.kind === 'card'
              }"
            >
              <div class="om__message-avatar" :style="messageAvatarStyle(item.message, item.rendered.kind)">
                <img v-if="messageAvatarUrl(item.message, item.rendered.kind)" :src="messageAvatarUrl(item.message, item.rendered.kind)" alt="" loading="lazy" />
                <span v-else>{{ messageAvatarText(item.message, item.rendered.kind) }}</span>
              </div>
              <div class="om__message-body">
                <div class="om__message-name">{{ messageSenderName(item.message, item.rendered.kind) }}</div>
                <div class="om__bubble" :class="{ 'om__bubble--card': item.rendered.kind === 'card' }">
                  <template v-if="item.rendered.kind === 'image'">
                    <div class="om__image-grid">
                      <button
                        v-for="url in item.rendered.imageUrls"
                        :key="url"
                        class="om__image-link"
                        type="button"
                        @click="openImagePreview(url)"
                      >
                        <img class="om__message-image" :src="url" alt="图片消息" loading="lazy" />
                      </button>
                    </div>
                    <div v-if="item.rendered.text && item.rendered.text !== '[图片]'" class="om__bubble-content om__bubble-content--caption">
                      <template v-for="(part, index) in messageTextParts(item.rendered.text)" :key="`${item.message.id}-caption-${index}`">
                        <span v-if="part.type === 'emoji'" class="om__emoji" :title="part.title">{{ part.text }}</span>
                        <template v-else>{{ part.text }}</template>
                      </template>
                    </div>
                  </template>
                  <template v-else-if="item.rendered.kind === 'audio'">
                    <div class="om__audio-message">
                      <audio v-if="item.rendered.media?.url" :src="audioSource(item.message)" controls preload="none"></audio>
                      <div v-else class="om__audio-placeholder">
                        <template v-for="(part, index) in messageTextParts(item.rendered.text)" :key="`${item.message.id}-audio-${index}`">
                          <span v-if="part.type === 'emoji'" class="om__emoji" :title="part.title">{{ part.text }}</span>
                          <template v-else>{{ part.text }}</template>
                        </template>
                      </div>
                    </div>
                  </template>
                  <a
                    v-else-if="item.rendered.kind === 'card' && item.rendered.card"
                    class="om__card-message"
                    :class="{
                      'om__card-message--plain': !item.rendered.card.imageUrl,
                      'om__card-message--review-invite': isReviewInviteCard(item.message, item.rendered.card)
                    }"
                    :href="item.rendered.card.url || undefined"
                    :target="item.rendered.card.url ? '_blank' : undefined"
                    rel="noreferrer"
                  >
                    <div class="om__card-body">
                      <div class="om__card-title">{{ item.rendered.card.title }}</div>
                      <div v-if="cardSubtitle(item.message, item.rendered.card)" class="om__card-subtitle">
                        {{ cardSubtitle(item.message, item.rendered.card) }}
                      </div>
                      <div v-if="item.rendered.card.orderId" class="om__card-meta">订单 {{ item.rendered.card.orderId }}</div>
                      <div
                        v-if="!isReviewInviteCard(item.message, item.rendered.card) && (item.rendered.card.tag || item.rendered.card.actionText)"
                        class="om__card-divider"
                      ></div>
                      <div
                        v-if="!isReviewInviteCard(item.message, item.rendered.card) && (item.rendered.card.tag || item.rendered.card.actionText)"
                        class="om__card-footer"
                        :class="{
                          'om__card-footer--action-only': !item.rendered.card.tag && !!item.rendered.card.actionText,
                          'om__card-footer--tag-only': !!item.rendered.card.tag && !item.rendered.card.actionText
                        }"
                      >
                        <span v-if="item.rendered.card.tag">{{ item.rendered.card.tag }}</span>
                        <strong v-if="item.rendered.card.actionText">{{ item.rendered.card.actionText }}</strong>
                      </div>
                    </div>
                    <span v-if="isReviewInviteCard(item.message, item.rendered.card)" class="om__review-action">
                      {{ item.rendered.card.actionText || '去评价' }}
                    </span>
                    <img v-if="item.rendered.card.imageUrl" class="om__card-image" :src="item.rendered.card.imageUrl" alt="" loading="lazy" />
                  </a>
                  <div v-else class="om__bubble-content" :class="{ 'om__bubble-content--notice': item.rendered.kind === 'notice' }">
                    <template v-for="(part, index) in messageTextParts(item.rendered.text)" :key="`${item.message.id}-text-${index}`">
                      <span v-if="part.type === 'emoji'" class="om__emoji" :title="part.title">{{ part.text }}</span>
                      <template v-else>{{ part.text }}</template>
                    </template>
                  </div>
                  <div class="om__bubble-time">{{ formatFullTime(item.message.messageTime) }}</div>
                </div>
                <div
                  v-if="shouldShowMessageReadStatus(item.message, item.rendered.kind)"
                  class="om__message-read-status"
                  :class="{ 'om__message-read-status--read': selectedConversation.readStatus === 1 }"
                >
                  {{ readStatusText(selectedConversation) }}
                </div>
              </div>
            </div>
            <div v-if="!loadingMessages && visibleMessages.length === 0" class="om__empty-chat">
              <IconChat />
              <strong>暂无历史消息</strong>
              <span>收到新消息后会自动沉淀到这里。</span>
            </div>
          </div>

          <form class="om__composer" @submit.prevent="sendReply">
            <textarea
              ref="replyTextareaRef"
              v-model="replyText"
              class="om__textarea"
              rows="3"
              :placeholder="isSystemConversation(selectedConversation) ? '系统通知仅支持查看' : '输入消息，Enter 发送，Shift+Enter 换行'"
              :disabled="sending || isSystemConversation(selectedConversation)"
              @keydown="handleReplyKeydown"
            ></textarea>
            <div class="om__composer-row">
              <div class="om__emoji-wrap">
                <button
                  class="om__tool-btn"
                  type="button"
                  :class="{ 'om__tool-btn--active': emojiPanelVisible }"
                  :disabled="uploadingImage || sending || isSystemConversation(selectedConversation)"
                  aria-label="选择表情"
                  :aria-expanded="emojiPanelVisible"
                  @click="toggleEmojiPanel"
                >
                  <IconSmile />
                </button>
                <div v-if="emojiPanelVisible" class="om__emoji-panel" role="listbox" aria-label="常用表情">
                  <button
                    v-for="emoji in EMOJI_OPTIONS"
                    :key="emoji"
                    class="om__emoji-option"
                    type="button"
                    @click="insertEmoji(emoji)"
                  >
                    {{ emoji }}
                  </button>
                </div>
              </div>
              <button
                class="om__upload-btn"
                type="button"
                :disabled="uploadingImage || sending || isSystemConversation(selectedConversation)"
                aria-label="上传图片"
                @click="openImagePicker"
              >
                <IconImage />
              </button>
              <label class="om__image-field">
                <input
                  v-model="imageUrl"
                  type="text"
                  :placeholder="uploadingImage ? '图片上传中...' : '图片URL'"
                  aria-label="图片URL"
                  :disabled="sending || uploadingImage || isSystemConversation(selectedConversation)"
                />
              </label>
              <input ref="fileInputRef" class="om__file-input" type="file" accept="image/*" @change="handleImageFileChange" />
              <button class="om__send-btn" type="submit" :disabled="!canSend">
                <IconSend />
                <span>{{ sending ? '发送中' : '发送' }}</span>
              </button>
            </div>
          </form>
        </template>

        <div v-else class="om__placeholder">
          <IconChat />
          <strong>尚未选择任何联系人</strong>
          <span>从左侧选择一个会话开始处理买家消息。</span>
        </div>
      </section>

      <aside class="om__goods-panel">
        <template v-if="selectedConversation?.xyGoodsId">
          <div class="om__goods-panel-head">
            <span>商品详情</span>
            <button type="button" @click="openGoodsDetail(selectedConversation)">弹窗查看</button>
          </div>
          <div v-if="loadingGoodsDetail" class="om__goods-panel-empty">加载中...</div>
          <div v-else-if="goodsDetail" class="om__goods-detail">
            <div class="om__goods-gallery" :class="{ 'om__goods-gallery--single': selectedGoodsImages.length <= 1 }">
              <img v-for="url in selectedGoodsImages" :key="url" :src="url" alt="" loading="lazy" @click="openGoodsDetail(selectedConversation)" />
            </div>
            <div v-if="selectedConversation.orderStatusText || selectedConversation.autoDeliveryStateText" class="om__goods-status-row">
              <span v-if="selectedConversation.orderStatusText" class="om__status-badge">{{ selectedConversation.orderStatusText }}</span>
              <span v-if="selectedConversation.autoDeliveryStateText" class="om__status-badge">{{ selectedConversation.autoDeliveryStateText }}</span>
            </div>
            <div class="om__goods-detail-title">{{ goodsDetail.item.title }}</div>
            <div class="om__goods-detail-price">{{ selectedConversation.orderAmountText || formatPrice(goodsDetail.item.soldPrice) }}</div>
            <div class="om__goods-detail-meta">ID: {{ goodsDetail.item.xyGoodId }}</div>
            <div v-if="selectedConversation.orderId" class="om__goods-detail-meta">订单: {{ selectedConversation.orderId }}</div>
            <div v-if="goodsDetail.item.detailInfo" class="om__goods-detail-desc">{{ goodsDetail.item.detailInfo }}</div>
          </div>
          <div v-else class="om__goods-panel-empty">暂无商品详情</div>
        </template>
        <div v-else class="om__goods-panel-empty">选择商品会话后显示商品详情</div>
      </aside>
    </main>

    <main v-else class="om__official">
      <div class="om__official-card">
        <div class="om__official-icon">
          <IconChat />
        </div>
        <div class="om__official-copy">
          <div class="om__official-title">官方闲鱼 IM</div>
          <div class="om__official-subtitle">
            将在新窗口打开 {{ GOOFISH_IM_URL }}。该页面使用当前浏览器里的 goofish.com 登录态，不会跟随系统内选中的闲鱼账号 Cookie。
          </div>
          <div class="om__official-warning">
            如果要切换官方 IM 账号，请先在浏览器的新窗口中切换或退出对应闲鱼账号；系统内的“账号”下拉只影响本地消息与自动化连接。
          </div>
          <button class="om__official-btn om__official-btn--primary" type="button" @click="openOfficialIm">
            <span>打开官方 IM 新窗口</span>
          </button>
        </div>
      </div>
    </main>
    <GoodsDetail
      v-model="detailDialogVisible"
      :goods-id="selectedGoodsId"
      :account-id="selectedAccountId"
    />
    <teleport to="body">
      <div
        v-if="imagePreviewVisible"
        class="om__image-preview"
        role="dialog"
        aria-modal="true"
        @click.self="closeImagePreview"
      >
        <button class="om__image-preview-close" type="button" aria-label="关闭图片预览" @click="closeImagePreview">×</button>
        <img class="om__image-preview-img" :src="imagePreviewUrl" alt="图片预览" />
      </div>
    </teleport>
  </div>
</template>

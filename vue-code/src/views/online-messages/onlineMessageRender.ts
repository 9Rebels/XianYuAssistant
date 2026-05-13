import type { ChatMessage, OnlineConversation } from '@/api/message'

export interface RenderedCard {
  title: string
  subtitle: string
  actionText: string
  imageUrl: string
  url: string
  tag: string
  orderId: string
  taskId: string
  isReviewInvite?: boolean
}

export interface RenderedMedia {
  type: string
  url: string
  durationMs: number
}

export type RenderedKind = 'text' | 'image' | 'audio' | 'card' | 'notice'

export interface RenderedTextPart {
  type: 'text' | 'emoji'
  text: string
  title?: string
}

export interface RenderedMessage {
  kind: RenderedKind
  text: string
  imageUrls: string[]
  card: RenderedCard | null
  media: RenderedMedia | null
}

const IMAGE_CONTENT_TYPES = new Set([2, 886, 997])
const OUTGOING_CONTENT_TYPES = new Set([886, 887, 888, 997, 999])
const NOTICE_CONTENT_TYPES = new Set([14])
const CARD_CONTENT_TYPES = new Set([25, 26, 28, 32])
const CARD_PLACEHOLDERS = ['[卡片消息]', '卡片消息']
const URL_PATTERN = /https?:\/\/[^\s,，"'<>]+/g
const SYSTEM_NAME_PATTERN = /工作台通知|系统通知|通知消息|闲鱼通知/
const REVIEW_INVITE_PATTERN = /快给ta一个评价|给ta.*评价|评价吧/
const TRADE_ACTION_NAME_PATTERN = /^(交易消息|买家已拍下，待付款|我已拍下，待付款|我已付款，等待你发货|买家确认收货，交易成功|我完成了评价)$/
const REVIEW_INVITE_SUBTITLE = '说说这次的交易体验，帮助更多人'
const EMOJI_MAP: Record<string, string> = {
  捂脸哭: '😂',
  笑哭: '😂',
  大笑: '😄',
  笑: '😊',
  微笑: '😊',
  呲牙: '😁',
  害羞: '☺️',
  调皮: '😜',
  可爱: '😊',
  酷: '😎',
  惊讶: '😮',
  疑问: '🤔',
  思考: '🤔',
  流泪: '😢',
  大哭: '😭',
  生气: '😠',
  怒: '😠',
  尴尬: '😅',
  捂脸: '🤦',
  汗: '😅',
  奸笑: '😏',
  色: '😍',
  爱心: '❤️',
  赞: '👍',
  强: '👍',
  握手: '🤝',
  OK: '👌',
  ok: '👌',
  胜利: '✌️',
  玫瑰: '🌹',
  礼物: '🎁',
  抱拳: '🙏'
}
const EMOJI_CODE_PATTERN = /\[([\u4e00-\u9fa5A-Za-z0-9_ -]{1,12})\]/g

export const isSystemEventMessage = (message: ChatMessage) => {
  return message.messageKind === 'system_tip' || message.contentType === 14 || isReviewInviteMessage(message)
}

export const isOutgoingMessage = (message: ChatMessage, currentAccountUnb: string) => {
  if (isSystemEventMessage(message)) return false
  if (currentAccountUnb && message.senderUserId === currentAccountUnb) return true
  return OUTGOING_CONTENT_TYPES.has(message.contentType)
}

export const isSystemConversation = (conversation: OnlineConversation) => {
  const name = conversation.peerUserName || ''
  if (SYSTEM_NAME_PATTERN.test(name)) return true
  return !conversation.peerUserId
    && !conversation.xyGoodsId
    && !conversation.goodsTitle
    && conversation.lastContentType === 14
}

export const shouldShowMessage = (message: ChatMessage, conversation?: OnlineConversation | null) => {
  if (!conversation || isSystemConversation(conversation)) return true
  if (isSystemEventMessage(message)) return true
  return !SYSTEM_NAME_PATTERN.test(message.senderUserName || '')
}

export const isReviewInviteMessage = (message: ChatMessage): boolean => {
  return [
    message.displayText,
    message.msgContent,
    message.senderUserName
  ].some(isReviewInviteText)
}

export const isReviewInviteCard = (message: ChatMessage, card: RenderedCard | null): boolean => {
  if (isReviewInviteMessage(message)) return true
  if (!card) return false
  if (card.isReviewInvite) return true
  return [card.title, card.subtitle, card.actionText].some(isReviewInviteText)
}

export const isTradeActionMessage = (message: ChatMessage): boolean => {
  return message.contentType === 26 || TRADE_ACTION_NAME_PATTERN.test(message.senderUserName || '')
}

export const renderMessage = (message: ChatMessage): RenderedMessage => {
  const text = normalizeText(message.displayText || message.msgContent)
  const backendCard = normalizeBackendCard(message)
  if (backendCard) {
    return { kind: 'card', text: text || backendCard.title, imageUrls: [], card: backendCard, media: null }
  }

  const backendImageUrls = uniqueUrls(message.imageUrls || [])
  if (backendImageUrls.length > 0) {
    return { kind: 'image', text: text || '[图片]', imageUrls: backendImageUrls, card: null, media: null }
  }

  const backendMedia = normalizeBackendMedia(message)
  if (backendMedia) {
    return { kind: 'audio', text: text || '[语音]', imageUrls: [], card: null, media: backendMedia }
  }

  const card = extractCard(message)
  if (card) {
    return { kind: 'card', text: text || card.title, imageUrls: [], card, media: null }
  }

  const imageUrls = extractImageUrls(message)
  if (imageUrls.length > 0) {
    return { kind: 'image', text: text || '[图片]', imageUrls, card: null, media: null }
  }

  if (message.messageKind === 'audio') {
    return { kind: 'audio', text: text || '[语音]', imageUrls: [], card: null, media: null }
  }

  if (isSystemEventMessage(message) || NOTICE_CONTENT_TYPES.has(message.contentType)) {
    return { kind: 'notice', text: text || '系统提示', imageUrls: [], card: null, media: null }
  }

  return { kind: 'text', text: text || '[空消息]', imageUrls: [], card: null, media: null }
}

export const conversationPreview = (conversation: OnlineConversation) => {
  if (conversation.cardTitle) return `[卡片] ${conversation.cardTitle}`
  if (conversation.lastContentType && NOTICE_CONTENT_TYPES.has(conversation.lastContentType)) {
    return `[系统通知] ${normalizeText(conversation.lastMessage) || '系统通知'}`
  }
  if (conversation.lastContentType && IMAGE_CONTENT_TYPES.has(conversation.lastContentType)) {
    return '[图片]'
  }
  return normalizeText(conversation.lastMessage) || '[空消息]'
}

export const renderEmojiParts = (text: string): RenderedTextPart[] => {
  if (!text) return []
  const parts: RenderedTextPart[] = []
  let lastIndex = 0
  for (const match of text.matchAll(EMOJI_CODE_PATTERN)) {
    const index = match.index || 0
    const name = match[1] || ''
    const emoji = EMOJI_MAP[name]
    if (!emoji) continue
    if (index > lastIndex) {
      parts.push({ type: 'text', text: text.slice(lastIndex, index) })
    }
    parts.push({ type: 'emoji', text: emoji, title: name })
    lastIndex = index + match[0].length
  }
  if (lastIndex < text.length) {
    parts.push({ type: 'text', text: text.slice(lastIndex) })
  }
  return parts.length ? parts : [{ type: 'text', text }]
}

const extractImageUrls = (message: ChatMessage) => {
  if (CARD_CONTENT_TYPES.has(message.contentType)) return []
  const source = `${message.msgContent || ''}\n${message.completeMsg || ''}`
  const urls = Array.from(source.matchAll(URL_PATTERN)).map(match => cleanUrl(match[0]))
  const imageUrls = urls.filter(url => isImageUrl(url))
  if (IMAGE_CONTENT_TYPES.has(message.contentType) && imageUrls.length === 0) {
    return urls.slice(0, 4)
  }
  return Array.from(new Set(imageUrls)).slice(0, 4)
}

const normalizeBackendCard = (message: ChatMessage): RenderedCard | null => {
  const card = message.card
  if (!card) return null
  const title = stripHtml(card.title || '')
  const subtitle = stripHtml(card.subtitle || '')
  const actionText = stripHtml(card.actionText || '')
  const imageUrl = cleanUrl(card.imageUrl || '')
  const url = card.url || ''
  const tag = stripHtml(card.tag || '')
  const looksLikeCard = !!title
    || !!subtitle
    || !!imageUrl
    || !!url
    || message.messageKind === 'card'
    || message.messageKind === 'trade_card'
  if (!looksLikeCard) return null
  const isReviewInvite = isReviewInviteMessage(message) || [title, subtitle, actionText].some(isReviewInviteText)
  return {
    title: title || '卡片消息',
    subtitle: isReviewInvite && !subtitle ? REVIEW_INVITE_SUBTITLE : subtitle,
    actionText: isReviewInvite && !actionText ? '去评价' : actionText,
    imageUrl,
    url,
    tag: isGenericCardText(tag) ? '' : tag,
    orderId: card.orderId || '',
    taskId: card.taskId || '',
    isReviewInvite
  }
}

const normalizeBackendMedia = (message: ChatMessage): RenderedMedia | null => {
  const media = message.media
  if (!media || media.type !== 'audio') return null
  return {
    type: media.type,
    url: media.url || '',
    durationMs: media.durationMs || 0
  }
}

const extractCard = (message: ChatMessage): RenderedCard | null => {
  const text = normalizeText(message.msgContent)
  const objects = collectObjects(message.completeMsg)
  const title = pickText(objects, ['title', 'mainTitle', 'itemTitle', 'itemName', 'goodsTitle', 'subject', 'bizTitle'])
  const subtitle = pickText(objects, ['firstLineText', 'subTitle', 'subtitle', 'desc', 'content', 'reminderContent', 'tip', 'price', 'statusText', 'orderStatus', 'bizDesc'])
  const actionText = pickText(objects, ['buttonText', 'actionText', 'actionName', 'btnText', 'text'])
  const imageUrl = pickUrl(objects, ['imgUrl', 'picUrl', 'image', 'imageUrl', 'cover', 'coverPic', 'itemPic', 'itemImage', 'goodsCoverPic'])
  const url = pickBusinessUrl(objects)
  const tag = pickText(objects, ['tag', 'cardTypeName', 'bizTypeName'])
  const looksLikeCard = CARD_PLACEHOLDERS.some(item => text.includes(item))
    || CARD_CONTENT_TYPES.has(message.contentType)
    || !!title

  if (!looksLikeCard) return null

  const resolvedTitle = stripHtml(title || (text && !isCardPlaceholder(text) ? text : '卡片消息'))
  const resolvedSubtitle = resolveCardSubtitle(subtitle, text, resolvedTitle)
  const resolvedActionText = stripHtml(actionText || (url ? '查看详情' : ''))
  const resolvedTag = stripHtml(tag || '')
  const isReviewInvite = isReviewInviteMessage(message)
    || [resolvedTitle, resolvedSubtitle, resolvedActionText].some(isReviewInviteText)

  return {
    title: resolvedTitle,
    subtitle: isReviewInvite && !resolvedSubtitle ? REVIEW_INVITE_SUBTITLE : resolvedSubtitle,
    actionText: isReviewInvite && !resolvedActionText ? '去评价' : resolvedActionText,
    imageUrl,
    url,
    tag: isGenericCardText(resolvedTag) ? '' : resolvedTag,
    orderId: '',
    taskId: '',
    isReviewInvite
  }
}

const uniqueUrls = (urls: string[]) => {
  return Array.from(new Set(urls.map(cleanUrl).filter(Boolean))).slice(0, 8)
}

const resolveCardSubtitle = (subtitle: string, text: string, title: string) => {
  const normalizedSubtitle = stripHtml(subtitle || '')
  if (normalizedSubtitle && !isDuplicateCardText(normalizedSubtitle, title)) {
    return normalizedSubtitle
  }
  if (text && !isCardPlaceholder(text) && !isDuplicateCardText(text, title)) {
    return text
  }
  return ''
}

const isDuplicateCardText = (candidate: string, title: string) => {
  return stripHtml(candidate) === stripHtml(title)
}

const isGenericCardText = (value: string) => {
  return ['卡片消息', '商品/订单卡片', ''].includes(stripHtml(value))
}

const isReviewInviteText = (value?: string) => {
  return REVIEW_INVITE_PATTERN.test(stripHtml(value || ''))
}

const collectObjects = (completeMsg?: string) => {
  const roots: unknown[] = []
  const parsed = parseJson(completeMsg)
  if (parsed) roots.push(parsed)

  const result: Record<string, unknown>[] = []
  const queue = [...roots]
  let visited = 0
  while (queue.length > 0 && visited < 180) {
    visited += 1
    const item = queue.shift()
    if (Array.isArray(item)) {
      queue.push(...item)
      continue
    }
    if (isRecord(item)) {
      result.push(item)
      Object.values(item).forEach(value => {
        const parsedValue = typeof value === 'string' ? parseJson(value) : null
        queue.push(parsedValue || value)
      })
    }
  }
  return result
}

const pickText = (objects: Record<string, unknown>[], keys: string[]) => {
  for (const object of objects) {
    for (const key of keys) {
      const value = object[key]
      if (typeof value === 'string' && value.trim()) {
        return stripHtml(value)
      }
    }
  }
  return ''
}

const pickUrl = (objects: Record<string, unknown>[], keys: string[]) => {
  for (const object of objects) {
    for (const key of keys) {
      const value = object[key]
      if (typeof value === 'string') {
        const match = value.match(URL_PATTERN)
        if (match?.[0]) return cleanUrl(match[0])
      }
    }
  }
  return ''
}

const pickBusinessUrl = (objects: Record<string, unknown>[]) => {
  const preferred = pickUrl(objects, ['pcJumpUrl', 'targetUrl', 'reminderUrl', 'jumpUrl', 'actionUrl', 'itemUrl', 'detailUrl'])
  if (isBusinessUrl(preferred)) return preferred

  for (const object of objects) {
    const value = object.url
    if (typeof value !== 'string') continue
    const match = value.match(URL_PATTERN)
    if (match?.[0]) {
      const url = cleanUrl(match[0])
      if (isBusinessUrl(url)) return url
    }
  }
  return ''
}

const parseJson = (value?: string) => {
  if (!value) return null
  const trimmed = value.trim()
  if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    return null
  }
}

const normalizeText = (value?: string) => stripHtml((value || '').replace(/^\[图片\]/, '').trim())

const stripHtml = (value: string) => value.replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim()

const cleanUrl = (url: string) => url.replace(/[)\]}，,。]+$/, '')

const isImageUrl = (url: string) => {
  return /\.(png|jpe?g|gif|webp|bmp|avif)(\?|$)/i.test(url) || /alicdn\.com|tbcdn\.cn|mmstat\.com/i.test(url)
}

const isBusinessUrl = (url: string) => {
  if (!url) return false
  if (/\.(zip|js|css|json|png|jpe?g|webp|gif)(\?|$)/i.test(url)) return false
  if (/dinamicx\.alibabausercontent\.com|template|schema/i.test(url)) return false
  return true
}

const isCardPlaceholder = (text: string) => CARD_PLACEHOLDERS.some(item => text.includes(item))

const isRecord = (value: unknown): value is Record<string, unknown> => {
  return typeof value === 'object' && value !== null
}

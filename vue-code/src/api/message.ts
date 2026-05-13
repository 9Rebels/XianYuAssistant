import { request } from '@/utils/request';
import type { ApiResponse } from '@/types';

// 消息信息
export interface MessageMedia {
  type?: string;
  url?: string;
  durationMs?: number;
  width?: number;
  height?: number;
}

export interface MessageCard {
  title?: string;
  subtitle?: string;
  actionText?: string;
  imageUrl?: string;
  url?: string;
  tag?: string;
  orderId?: string;
  taskId?: string;
}

export interface ChatMessage {
  id: number;
  xianyuAccountId: number;
  lwp: string;
  pnmId: string;
  sid: string;  // 注意：后端返回的是全小写的 sid
  contentType: number;
  msgContent: string;
  completeMsg: string;
  messageKind?: 'text' | 'image' | 'audio' | 'card' | 'system_tip' | 'trade_card' | 'notice';
  displayText?: string;
  imageUrls?: string[];
  media?: MessageMedia | null;
  card?: MessageCard | null;
  senderUserName: string;
  senderUserId: string;
  senderAppV: string;
  senderOsType: string;
  reminderUrl: string;
  xyGoodsId: string;
  messageTime: number;
  createTime: string;
}

// 消息列表响应
export interface MessageListResponse {
  list: ChatMessage[];
  totalCount: number;
  totalPage: number;
  pageNum: number;
  pageSize: number;
}

export interface OnlineConversation {
  sid: string;
  xianyuAccountId: number;
  peerUserId?: string;
  peerUserName?: string;
  peerAvatar?: string;
  lastMessage?: string;
  lastContentType?: number;
  lastMessageTime?: number;
  xyGoodsId?: string;
  goodsTitle?: string;
  goodsCoverPic?: string;
  goodsPrice?: string;
  goodsStatus?: number;
  orderId?: string;
  orderStatus?: number;
  orderStatusText?: string;
  orderAmountText?: string;
  autoDeliveryState?: number;
  autoDeliveryStateText?: string;
  cardTitle?: string;
  cardSubtitle?: string;
  cardImageUrl?: string;
  cardActionText?: string;
  cardTag?: string;
  unreadCount?: number;
  readStatus?: number;
  readStatusText?: string;
  readTimestamp?: number;
  messageCount: number;
}

// 获取消息列表
export function getMessageList(data: {
  xianyuAccountId: number;
  xyGoodsId?: string;
  pageNum?: number;
  pageSize?: number;
  filterCurrentAccount?: boolean; // 过滤当前账号消息
}) {
  return request<MessageListResponse>({
    url: '/msg/list',
    method: 'POST',
    data
  });
}

// 根据会话ID获取上下文消息
export function getContextMessages(data: {
  xianyuAccountId: number;
  sid: string;
  limit?: number;
  offset?: number;
}) {
  return request<ChatMessage[]>({
    url: '/msg/context',
    method: 'POST',
    data: {
      xianyuAccountId: data.xianyuAccountId,
      sid: data.sid,
      limit: data.limit || 20,
      offset: data.offset || 0
    }
  });
}

export function getOnlineConversations(data: {
  xianyuAccountId: number;
  limit?: number;
}) {
  return request<OnlineConversation[]>({
    url: '/msg/online/conversations',
    method: 'GET',
    params: data
  });
}

// 发送消息
export function sendMessage(data: {
  xianyuAccountId: number;
  cid: string;
  toId: string;
  text: string;
  xyGoodsId?: string;
}) {
  return request<string>({
    url: '/websocket/sendMessage',
    method: 'POST',
    data
  });
}

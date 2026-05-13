import { request } from '@/utils/request';

const GOODS_REFRESH_TIMEOUT_MS = 300000;

// 商品信息
export interface GoodsItem {
  id: number;
  xyGoodId: string;
  xianyuAccountId: number;
  title: string;
  coverPic: string;
  infoPic: string;
  detailInfo: string;
  detailUrl: string;
  soldPrice: string;
  quantity?: number;
  exposureCount?: number;
  viewCount?: number;
  wantCount?: number;
  status: number;
  createdTime: string;
  updatedTime: string;
}

// 带配置的商品信息
export interface GoodsItemWithConfig {
  item: GoodsItem;
  xianyuAutoDeliveryOn: number;
  xianyuAutoReplyOn: number;
  xianyuAutoReplyContextOn: number;
  autoDeliveryType?: number;
  autoDeliveryContent?: string;
}

// 商品列表响应
export interface GoodsListResponse {
  itemsWithConfig: GoodsItemWithConfig[];
  totalCount: number;
  totalPage: number;
  pageNum: number;
  pageSize: number;
}

// 商品详情响应
export interface GoodsDetailResponse {
  itemWithConfig: GoodsItemWithConfig;
}

// 刷新商品响应
export interface RefreshItemsResponse {
  success: boolean;
  totalCount: number;
  successCount: number;
  removedCount?: number;
  updatedItemIds: string[];
  message: string;
  syncId?: string;
  syncStatus?: number;
  syncLabel?: string;
  recoveryAttempted?: boolean;
  needCaptcha?: boolean;
  needManual?: boolean;
  manualVerifyUrl?: string;
  captchaUrl?: string;
  sessionId?: string;
}

export interface SyncProgressResponse {
  syncId: string;
  accountId: number;
  totalCount: number;
  completedCount: number;
  successCount: number;
  failedCount: number;
  isCompleted: boolean;
  isRunning: boolean;
  currentItemId: string;
  message: string;
  startTime: number;
  estimatedRemainingTime: number;
}

export interface PublishItemCategory {
  catId: string;
  catName: string;
  channelCatId: string;
  tbCatId: string;
}

export interface PublishItemAddress {
  prov: string;
  city: string;
  area: string;
  divisionId: number;
  gps: string;
  poiId: string;
  poiName: string;
}

export interface PublishItemLabel {
  channelCateName?: string;
  valueId?: string;
  channelCateId: string;
  valueName?: string;
  tbCatId: string;
  subPropertyId?: string;
  labelType?: string;
  subValueId?: string;
  labelId?: string;
  propertyName: string;
  isUserClick?: string;
  isUserCancel?: string;
  from?: string;
  propertyId: string;
  labelFrom?: string;
  text: string;
  properties: string;
}

export interface PublishItemRequest {
  xianyuAccountId: number;
  title: string;
  desc: string;
  price: string;
  origPrice?: string;
  quantity: string;
  scheduled?: boolean;
  scheduledTime?: string;
  freeShipping: boolean;
  shippingType: 'FREE_SHIPPING' | 'CHARGE_SHIPPING' | 'FIXED_PRICE' | 'NO_SHIPPING';
  supportSelfPick: boolean;
  postFee?: string;
  imageUrls: string[];
  itemCat: PublishItemCategory;
  itemAddr: PublishItemAddress;
  itemLabels: PublishItemLabel[];
  itemProperties?: PublishItemSpec[];
  itemSkuList?: PublishItemSku[];
}

export interface PublishItemSpec {
  propertyName: string;
  supportImage?: boolean;
  propertyValues: Array<{ propertyValue: string }>;
}

export interface PublishItemSku {
  propertyKey?: string;
  propertyValue?: string;
  secondPropertyKey?: string;
  secondPropertyValue?: string;
  price: string;
  quantity: string;
}

export interface PublishItemResponse {
  success: boolean;
  message: string;
  itemId?: string;
  itemUrl?: string;
  scheduledTaskId?: number;
  scheduledTime?: string;
  recoveryAttempted?: boolean;
  needCaptcha?: boolean;
  needManual?: boolean;
  manualVerifyUrl?: string;
  captchaUrl?: string;
  sessionId?: string;
}

export interface PoiCacheItem extends PublishItemAddress {
  id?: number;
  xianyuAccountId?: number;
  latitude?: string;
  longitude?: string;
  address?: string;
  isDefault?: number;
}

export interface PoiCandidate extends PublishItemAddress {
  latitude?: string;
  longitude?: string;
  address?: string;
}

// 获取商品列表
export function getGoodsList(data: {
  xianyuAccountId: number;
  status?: number;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request<GoodsListResponse>({
    url: '/items/list',
    method: 'POST',
    data
  });
}

// 刷新商品数据
export function refreshGoods(xianyuAccountId: number) {
  return request<RefreshItemsResponse>({
    url: '/items/refresh',
    method: 'POST',
    data: { xianyuAccountId },
    timeout: GOODS_REFRESH_TIMEOUT_MS
  });
}

export function refreshGoodsByStatus(xianyuAccountId: number, syncStatus: number) {
  return request<RefreshItemsResponse>({
    url: '/items/refresh',
    method: 'POST',
    data: { xianyuAccountId, syncStatus },
    timeout: GOODS_REFRESH_TIMEOUT_MS
  });
}

// 获取商品详情
export function getGoodsDetail(xyGoodId: string) {
  return request<GoodsDetailResponse>({
    url: '/items/detail',
    method: 'POST',
    data: { xyGoodId }
  });
}

// 更新自动发货状态
export function updateAutoDeliveryStatus(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  xianyuAutoDeliveryOn: number;
}) {
  return request({
    url: '/items/updateAutoDeliveryStatus',
    method: 'POST',
    data
  });
}

export function updateAutoConfirmShipment(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  autoConfirmShipment: number;
}) {
  return request({
    url: '/items/updateAutoConfirmShipment',
    method: 'POST',
    data
  });
}

// 更新自动回复状态
export function updateAutoReplyStatus(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  xianyuAutoReplyOn: number;
  xianyuAutoReplyContextOn?: number;
}) {
  return request({
    url: '/items/updateAutoReplyStatus',
    method: 'POST',
    data
  });
}

// 删除商品
export function deleteItem(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
}) {
  return request({
    url: '/items/delete',
    method: 'POST',
    data
  });
}

export function offShelfItem(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
}) {
  return request({
    url: '/items/offShelf',
    method: 'POST',
    data
  });
}

export function republishItem(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
}) {
  return request<string>({
    url: '/items/republish',
    method: 'POST',
    data,
    timeout: GOODS_REFRESH_TIMEOUT_MS
  });
}

export function remoteDeleteItem(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
}) {
  return request<string>({
    url: '/items/remoteDelete',
    method: 'POST',
    data
  });
}

export function updateItemPrice(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  price: string;
}) {
  return request<string>({
    url: '/items/updatePrice',
    method: 'POST',
    data
  });
}

export function updateItemStock(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  quantity: number;
}) {
  return request<string>({
    url: '/items/updateStock',
    method: 'POST',
    data
  });
}

// 自动回复配置响应
export interface AutoReplyConfigResponse {
  ragDelaySeconds: number;
  globalAiReplyTemplate?: string;
  globalAiReplyEnabled?: boolean;
}

// 获取自动回复配置
export function getAutoReplyConfig(data: {
  xianyuAccountId: number;
  xyGoodsId?: string;
}) {
  return request<AutoReplyConfigResponse>({
    url: '/items/getRagAutoReplyConfig',
    method: 'POST',
    data
  });
}

// 更新自动回复配置
export function updateAutoReplyConfig(data: {
  xianyuAccountId: number;
  xyGoodsId?: string;
  ragDelaySeconds?: number;
  globalAiReplyTemplate?: string;
  globalAiReplyEnabled?: boolean;
}) {
  return request<AutoReplyConfigResponse>({
    url: '/items/updateRagAutoReplyConfig',
    method: 'POST',
    data
  });
}

// 自动回复记录
export interface AutoReplyRecord {
  id: number;
  xianyuAccountId: number;
  xianyuGoodsId: number;
  xyGoodsId: string;
  sId: string;
  pnmId: string;
  buyerUserId: string;
  buyerUserName: string;
  buyerMessage: string;
  replyContent: string;
  replyType: number;
  matchedKeyword: string;
  triggerContext: string;
  state: number;
  createTime: string;
}

// 自动回复记录列表响应
export interface AutoReplyRecordListResponse {
  list: AutoReplyRecord[];
  totalCount: number;
  pageNum: number;
  pageSize: number;
}

// 获取自动回复记录
export function getAutoReplyRecords(data: {
  xianyuAccountId: number;
  xyGoodsId: string;
  pageNum?: number;
  pageSize?: number;
}) {
  return request<AutoReplyRecordListResponse>({
    url: '/items/autoReplyRecords',
    method: 'POST',
    data
  });
}

export function getSyncProgress(syncId: string) {
  return request<SyncProgressResponse>({
    url: `/items/syncProgress/${syncId}`,
    method: 'GET'
  });
}

export function checkSyncing(accountId: number) {
  return request<boolean>({
    url: `/items/syncing/${accountId}`,
    method: 'GET'
  });
}

export function publishItem(data: PublishItemRequest) {
  return request<PublishItemResponse>({
    url: '/items/publish',
    method: 'POST',
    data,
    timeout: GOODS_REFRESH_TIMEOUT_MS
  });
}

export function getPoiCache(data: {
  xianyuAccountId: number;
  divisionId: number;
}) {
  return request<PoiCacheItem | null>({
    url: '/poi/cache',
    method: 'POST',
    data
  });
}

export function getDefaultPoiCache(xianyuAccountId: number) {
  return request<PoiCacheItem | null>({
    url: '/poi/default',
    method: 'POST',
    data: { xianyuAccountId }
  });
}

export function fetchPoiCandidates(data: {
  xianyuAccountId: number;
  divisionId: number;
  prov: string;
  city: string;
  area: string;
  longitude?: string;
  latitude?: string;
}) {
  return request<PoiCandidate[]>({
    url: '/poi/fetch',
    method: 'POST',
    data
  });
}

export function savePoiCache(data: PoiCacheItem & {
  xianyuAccountId: number;
  defaultPoi?: boolean;
}) {
  return request<PoiCacheItem>({
    url: '/poi/save',
    method: 'POST',
    data
  });
}

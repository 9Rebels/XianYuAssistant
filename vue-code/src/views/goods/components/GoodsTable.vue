<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getGoodsStatusText, formatPrice } from '@/utils'
import type { GoodsItemWithConfig } from '@/api/goods'

import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconTrash from '@/components/icons/IconTrash.vue'
import IconPackage from '@/components/icons/IconPackage.vue'
import IconSend from '@/components/icons/IconSend.vue'
import IconRobot from '@/components/icons/IconRobot.vue'
import IconImage from '@/components/icons/IconImage.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconSparkle from '@/components/icons/IconSparkle.vue'
import IconEdit from '@/components/icons/IconEdit.vue'

interface Props {
  goodsList: GoodsItemWithConfig[]
  loading?: boolean
  isFishShopAccount?: boolean
  offShelfEnabled?: boolean
  deleteEnabled?: boolean
}

interface Emits {
  (e: 'view', xyGoodId: string): void
  (e: 'toggleAutoDelivery', item: GoodsItemWithConfig, value: boolean): void
  (e: 'toggleAutoReply', item: GoodsItemWithConfig, value: boolean): void
  (e: 'toggleRemoteOffShelf', item: GoodsItemWithConfig, value: boolean): void
  (e: 'toggleRemoteDelete', item: GoodsItemWithConfig, value: boolean): void
  (e: 'configAutoDelivery', item: GoodsItemWithConfig): void
  (e: 'offShelf', item: GoodsItemWithConfig): void
  (e: 'delete', item: GoodsItemWithConfig): void
  (e: 'updatePrice', item: GoodsItemWithConfig): void
  (e: 'updateStock', item: GoodsItemWithConfig): void
}

withDefaults(defineProps<Props>(), {
  isFishShopAccount: false,
  offShelfEnabled: true,
  deleteEnabled: true
})
const emit = defineEmits<Emits>()

const isMobile = ref(false)
const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
})

const getStatusColor = (status: number) => {
  const info = getGoodsStatusText(status)
  switch (info.type) {
    case 'success': return '#34c759'
    case 'warning': return '#ff9500'
    case 'info': return '#86868b'
    default: return '#007aff'
  }
}

const getStatusBg = (status: number) => {
  const info = getGoodsStatusText(status)
  switch (info.type) {
    case 'success': return 'rgba(52, 199, 89, 0.1)'
    case 'warning': return 'rgba(255, 149, 0, 0.1)'
    case 'info': return 'rgba(134, 134, 139, 0.1)'
    default: return 'rgba(0, 122, 255, 0.1)'
  }
}


const getDeliveryTypeText = (type?: number) => {
  if (type === 2) return '卡密'
  if (type === 3) return '自定义'
  if (type === 4) return 'API'
  return '文本'
}

const getDeliveryTypeClass = (type?: number) => {
  if (type === 2) return 'delivery-type-tag--kami'
  if (type === 3) return 'delivery-type-tag--custom'
  if (type === 4) return 'delivery-type-tag--api'
  return 'delivery-type-tag--text'
}

const getGoodsCardDeliveryTypeClass = (type?: number) => {
  if (type === 2) return 'goods-card__type-tag--kami'
  if (type === 3) return 'goods-card__type-tag--custom'
  if (type === 4) return 'goods-card__type-tag--api'
  return 'goods-card__type-tag--text'
}

// 图片加载失败处理
const handleImgError = (e: Event) => {
  const img = e.target as HTMLImageElement
  img.style.display = 'none'
}
</script>

<template>
  <!-- Mobile: Card View -->
  <div v-if="isMobile" class="card-list" :class="{ 'card-list--loading': loading }">
    <div
      v-for="item in goodsList"
      :key="item.item.xyGoodId"
      class="goods-card"
      @click="emit('view', item.item.xyGoodId)"
    >
      <!-- 图片区域（含悬浮标题） -->
      <div class="goods-card__image-wrap">
        <img
          v-if="item.item.coverPic"
          :src="item.item.coverPic"
          class="goods-card__image"
          @error="handleImgError"
        />
        <div v-else class="goods-card__image-placeholder">
          <IconImage />
        </div>

        <!-- 状态标签 -->
        <span
          class="goods-card__status"
          :style="{
            color: getStatusColor(item.item.status),
            background: getStatusBg(item.item.status)
          }"
        >
          {{ getGoodsStatusText(item.item.status).text }}
        </span>

        <!-- 玻璃渐变悬浮层：标题 + 价格 -->
        <div class="goods-card__overlay">
          <div class="goods-card__title-wrap">
            <div class="goods-card__title">{{ item.item.title }}</div>
          </div>
          <div class="goods-card__stats">
            <div class="goods-card__stat">
              <span class="goods-card__stat-label">想要</span>
              <span class="goods-card__stat-value">{{ item.item.wantCount ?? '-' }}</span>
            </div>
          </div>
          <div class="goods-card__meta">
            <span class="goods-card__price">{{ formatPrice(item.item.soldPrice) }}</span>
            <div class="goods-card__switches">
              <button
                class="goods-card__switch"
                :class="{ 'goods-card__switch--on': item.xianyuAutoDeliveryOn === 1 }"
                @click.stop="emit('toggleAutoDelivery', item, item.xianyuAutoDeliveryOn !== 1)"
              >
                <IconSend />
                <span>发货</span>
              </button>
              <span
                v-if="item.xianyuAutoDeliveryOn === 1"
                class="goods-card__type-tag"
                :class="getGoodsCardDeliveryTypeClass(item.autoDeliveryType)"
              >
                {{ getDeliveryTypeText(item.autoDeliveryType) }}
              </span>
              <button
                class="goods-card__switch"
                :class="{ 'goods-card__switch--on': item.xianyuAutoReplyOn === 1 }"
                @click.stop="emit('toggleAutoReply', item, item.xianyuAutoReplyOn !== 1)"
              >
                <IconRobot />
                <span>回复</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="goods-card__actions">
        <button
          class="goods-card__action goods-card__action--config"
          @click.stop="emit('configAutoDelivery', item)"
        >
          <IconSparkle />
          <span>配置</span>
        </button>
        <button
          v-if="isFishShopAccount"
          class="goods-card__action goods-card__action--price"
          @click.stop="emit('updatePrice', item)"
        >
          <IconEdit />
          <span>改价</span>
        </button>
        <button
          class="goods-card__action goods-card__action--stock"
          @click.stop="emit('updateStock', item)"
        >
          <IconEdit />
          <span>库存 {{ item.item.quantity && item.item.quantity > 0 ? item.item.quantity : '-' }}</span>
        </button>
        <button
          v-if="offShelfEnabled"
          class="goods-card__action goods-card__action--off-shelf"
          @click.stop="emit('toggleRemoteOffShelf', item, item.item.status !== 0)"
        >
          <IconPackage />
          <span>{{ item.item.status === 0 ? '下架' : '上架' }}</span>
        </button>
        <button
          v-if="deleteEnabled"
          class="goods-card__action goods-card__action--off-shelf"
          @click.stop="emit('toggleRemoteDelete', item, item.item.status !== 3)"
        >
          <IconPackage />
          <span>{{ item.item.status === 3 ? '已删' : '闲鱼删' }}</span>
        </button>
        <button
          class="goods-card__action goods-card__action--delete"
          @click.stop="emit('delete', item)"
        >
          <IconTrash />
          <span>本地删</span>
        </button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && goodsList.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无商品数据</p>
    </div>
  </div>

  <!-- Desktop/Tablet: Table View -->
  <div v-else class="table-container" :class="{ 'table-container--loading': loading }">
    <table class="table" v-if="goodsList.length > 0">
      <thead class="table__head">
        <tr>
          <th class="table__th table__th--image">图片</th>
          <th class="table__th">商品标题</th>
          <th class="table__th table__th--price">价格</th>
          <th class="table__th table__th--stock">库存</th>
          <th class="table__th table__th--metric">想要</th>
          <th class="table__th table__th--status">状态</th>
          <th class="table__th table__th--switch">自动发货</th>
          <th class="table__th table__th--switch">发货类型</th>
          <th class="table__th table__th--switch">自动回复</th>
          <th class="table__th table__th--switch">在售</th>
          <th class="table__th table__th--switch">闲鱼删除</th>
          <th class="table__th table__th--actions">操作</th>
        </tr>
      </thead>
      <tbody class="table__body">
        <tr v-for="item in goodsList" :key="item.item.xyGoodId" class="table__tr">
          <td class="table__td table__td--image">
            <div class="goods-thumb">
              <img
                v-if="item.item.coverPic"
                :src="item.item.coverPic"
                class="goods-thumb__img"
                @error="handleImgError"
                @click="emit('view', item.item.xyGoodId)"
              />
              <div v-else class="goods-thumb__placeholder">
                <IconImage />
              </div>
            </div>
          </td>
          <td class="table__td table__td--title" @click="emit('view', item.item.xyGoodId)">
            <div class="goods-title-cell">
              <span class="goods-title-cell__name">{{ item.item.title }}</span>
              <span class="goods-title-cell__id">ID: {{ item.item.xyGoodId }}</span>
            </div>
          </td>
          <td class="table__td table__td--price">
            <span class="price-text">{{ formatPrice(item.item.soldPrice) }}</span>
          </td>
          <td class="table__td table__td--stock">
            <button class="stock-pill" @click="emit('updateStock', item)">
              {{ item.item.quantity && item.item.quantity > 0 ? item.item.quantity : '-' }}
            </button>
          </td>
          <td class="table__td table__td--metric">
            <span class="metric-text">{{ item.item.wantCount ?? '-' }}</span>
          </td>
          <td class="table__td table__td--status">
            <span
              class="status-tag"
              :style="{
                color: getStatusColor(item.item.status),
                background: getStatusBg(item.item.status)
              }"
            >
              {{ getGoodsStatusText(item.item.status).text }}
            </span>
          </td>
          <td class="table__td table__td--switch">
            <button
              class="toggle-btn"
              :class="{ 'toggle-btn--on': item.xianyuAutoDeliveryOn === 1 }"
              @click="emit('toggleAutoDelivery', item, item.xianyuAutoDeliveryOn !== 1)"
            >
              <span class="toggle-btn__track">
                <span class="toggle-btn__thumb"></span>
              </span>
            </button>
          </td>
          <td class="table__td table__td--switch">
            <span
              v-if="item.xianyuAutoDeliveryOn === 1"
              class="delivery-type-tag"
              :class="getDeliveryTypeClass(item.autoDeliveryType)"
            >
              {{ getDeliveryTypeText(item.autoDeliveryType) }}
            </span>
            <span v-else class="delivery-type-tag delivery-type-tag--off">-</span>
          </td>
          <td class="table__td table__td--switch">
            <button
              class="toggle-btn"
              :class="{ 'toggle-btn--on': item.xianyuAutoReplyOn === 1 }"
              @click="emit('toggleAutoReply', item, item.xianyuAutoReplyOn !== 1)"
            >
              <span class="toggle-btn__track">
                <span class="toggle-btn__thumb"></span>
              </span>
            </button>
          </td>
          <td class="table__td table__td--switch">
            <button
              v-if="offShelfEnabled"
              class="toggle-btn"
              :class="{ 'toggle-btn--on': item.item.status === 0 }"
              @click="emit('toggleRemoteOffShelf', item, item.item.status !== 0)"
            >
              <span class="toggle-btn__track">
                <span class="toggle-btn__thumb"></span>
              </span>
            </button>
            <span v-else class="delivery-type-tag delivery-type-tag--off">-</span>
          </td>
          <td class="table__td table__td--switch">
            <button
              v-if="deleteEnabled"
              class="toggle-btn"
              :class="{ 'toggle-btn--on': item.item.status === 3 }"
              @click="emit('toggleRemoteDelete', item, item.item.status !== 3)"
            >
              <span class="toggle-btn__track">
                <span class="toggle-btn__thumb"></span>
              </span>
            </button>
            <span v-else class="delivery-type-tag delivery-type-tag--off">-</span>
          </td>
          <td class="table__td table__td--actions">
            <button class="table__action table__action--detail" @click="emit('view', item.item.xyGoodId)">
              <IconCheck />
              <span>详情</span>
            </button>
            <button
              v-if="isFishShopAccount"
              class="table__action table__action--price"
              @click="emit('updatePrice', item)"
            >
              <IconEdit />
              <span>改价</span>
            </button>
            <button
              class="table__action table__action--delete"
              @click="emit('delete', item)"
            >
              <IconTrash />
              <span>本地删除</span>
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Empty State -->
    <div v-if="!loading && goodsList.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无商品数据</p>
    </div>
  </div>
</template>

<style scoped>
/* ============================================================
   Shared Tokens
   ============================================================ */
.card-list,
.table-container {
  --c-bg: transparent;
  --c-surface: #ffffff;
  --c-border: rgba(0, 0, 0, 0.06);
  --c-border-strong: rgba(0, 0, 0, 0.1);
  --c-text-1: #1d1d1f;
  --c-text-2: #6e6e73;
  --c-text-3: #86868b;
  --c-accent: #007aff;
  --c-danger: #ff3b30;
  --c-success: #34c759;
  --c-price: #ff3b30;
  --c-r-sm: 8px;
  --c-r-md: 12px;
  --c-ease: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

/* ============================================================
   Mobile Card View
   ============================================================ */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  padding-bottom: 24px;
  min-height: 100%;
}

.goods-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border-strong);
  border-radius: var(--c-r-md);
  overflow: hidden;
  transition: all var(--c-ease);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  cursor: pointer;
}

@media (hover: hover) {
  .goods-card:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    border-color: rgba(0, 0, 0, 0.15);
  }
}

.goods-card:active {
  transform: scale(0.98);
}

.goods-card__image-wrap {
  position: relative;
  width: 100%;
  height: 220px;
  background: rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.goods-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.goods-card__image-placeholder {
  color: var(--c-text-3);
  opacity: 0.3;
}

.goods-card__image-placeholder svg {
  width: 32px;
  height: 32px;
}

.goods-card__status {
  position: absolute;
  top: 8px;
  right: 8px;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 12px;
  line-height: 1;
  z-index: 2;
}

/* 玻璃渐变悬浮层 */
.goods-card__overlay {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 1;
  backdrop-filter: blur(6px) saturate(1.4);
  -webkit-backdrop-filter: blur(6px) saturate(1.4);
  background: rgba(0, 0, 0, 0.15);
  -webkit-mask-image: linear-gradient(to bottom, transparent 0%, black 30%);
  mask-image: linear-gradient(to bottom, transparent 0%, black 30%);
}

/* 渐变过渡区：标题区域 */
.goods-card__title-wrap {
  padding: 24px 12px 6px;
}

.goods-card__title {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.4);
}

/* 底部价格 + 按钮行 */
.goods-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px 12px;
}

.goods-card__stats {
  display: grid;
  grid-template-columns: minmax(88px, 1fr);
  gap: 6px;
  padding: 0 12px 6px;
}

.goods-card__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.goods-card__stat-label {
  font-size: 10px;
  line-height: 1;
  color: rgba(255, 255, 255, 0.72);
}

.goods-card__stat-value {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.2;
  color: #fff;
  font-variant-numeric: tabular-nums;
}

.goods-card__price {
  font-size: 16px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  text-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}

.goods-card__id {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
  font-family: 'SF Mono', 'Menlo', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.goods-card__switches {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-shrink: 0;
}

.goods-card__switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  color: rgba(255, 255, 255, 0.75);
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 13px;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.goods-card__switch svg {
  width: 12px;
  height: 12px;
}

.goods-card__switch--on {
  color: #fff;
  background: rgba(52, 199, 89, 0.5);
  border-color: rgba(52, 199, 89, 0.6);
}

/* 底部操作栏 */
.goods-card__actions {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid var(--c-border);
  background: var(--c-surface);
}

.goods-card__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  height: 32px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--c-r-sm);
  border: 1px solid;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.goods-card__action svg {
  width: 13px;
  height: 13px;
}

.goods-card__action--config {
  color: var(--c-accent);
  border-color: rgba(0, 122, 255, 0.2);
}

@media (hover: hover) {
  .goods-card__action--config:hover {
    background: rgba(0, 122, 255, 0.06);
  }
}

.goods-card__action--price {
  color: #5856d6;
  border-color: rgba(88, 86, 214, 0.2);
}

@media (hover: hover) {
  .goods-card__action--price:hover {
    background: rgba(88, 86, 214, 0.08);
  }
}

.goods-card__action--stock {
  color: #007aff;
  border-color: rgba(0, 122, 255, 0.2);
}

@media (hover: hover) {
  .goods-card__action--stock:hover {
    background: rgba(0, 122, 255, 0.08);
  }
}

.goods-card__action--off-shelf {
  color: #ff9500;
  border-color: rgba(255, 149, 0, 0.2);
}

@media (hover: hover) {
  .goods-card__action--off-shelf:hover {
    background: rgba(255, 149, 0, 0.08);
  }
}

.goods-card__action--delete {
  color: var(--c-danger);
  border-color: rgba(255, 59, 48, 0.2);
}

@media (hover: hover) {
  .goods-card__action--delete:hover {
    background: rgba(255, 59, 48, 0.06);
  }
}

.goods-card__action:active {
  transform: scale(0.97);
}

/* ============================================================
   Desktop Table View
   ============================================================ */
.table-container {
  min-height: 100%;
}

.table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}

/* Table Head */
.table__head {
  position: sticky;
  top: 0;
  z-index: 2;
}

.table__th {
  text-align: left;
  padding: 10px 16px;
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-3);
  letter-spacing: 0.01em;
  background: #fafafa;
  border-bottom: 1px solid var(--c-border-strong);
  white-space: nowrap;
  user-select: none;
}

.table__th--image { width: 64px; }
.table__th--price { width: 100px; text-align: right; }
.table__th--stock { width: 80px; text-align: center; }
.table__th--metric { width: 72px; text-align: center; }
.table__th--status { width: 80px; }
.table__th--switch { width: 90px; text-align: center; }
.table__th--actions { width: 190px; text-align: center; }

/* Table Body */
.table__tr {
  transition: background var(--c-ease);
}

.table__tr:not(:last-child) .table__td {
  border-bottom: 1px solid var(--c-border);
}

@media (hover: hover) {
  .table__tr:hover .table__td {
    background: rgba(0, 0, 0, 0.02);
  }
}

.table__td {
  padding: 10px 16px;
  color: var(--c-text-1);
  white-space: nowrap;
  background: transparent;
  transition: background var(--c-ease);
  line-height: 1.5;
}

/* Thumbnail */
.goods-thumb {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  overflow: hidden;
  background: rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  justify-content: center;
}

.goods-thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  transition: transform var(--c-ease);
}

@media (hover: hover) {
  .goods-thumb__img:hover {
    transform: scale(1.08);
  }
}

.goods-thumb__placeholder {
  color: var(--c-text-3);
  opacity: 0.3;
}

.goods-thumb__placeholder svg {
  width: 18px;
  height: 18px;
}

/* Title Cell */
.table__td--title {
  cursor: pointer;
}

.goods-title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.goods-title-cell__name {
  font-weight: 500;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 300px;
}

.goods-title-cell__id {
  font-size: 11px;
  color: var(--c-text-3);
  font-family: 'SF Mono', 'Menlo', monospace;
}

/* Price */
.table__td--price {
  text-align: right;
}

.price-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-price);
  font-variant-numeric: tabular-nums;
}

.table__td--stock {
  text-align: center;
}

.table__td--metric {
  text-align: center;
}

.metric-text {
  display: inline-flex;
  min-width: 34px;
  justify-content: center;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--c-text-1);
}

.stock-pill {
  min-width: 42px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(0, 122, 255, 0.2);
  border-radius: 8px;
  background: rgba(0, 122, 255, 0.06);
  color: #007aff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

@media (hover: hover) {
  .stock-pill:hover {
    background: rgba(0, 122, 255, 0.12);
  }
}

/* Status */
.table__td--status {}

.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
  line-height: 1;
}

/* Toggle Switch */
.table__td--switch {
  text-align: center;
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px;
  -webkit-tap-highlight-color: transparent;
}

.toggle-btn__track {
  width: 44px;
  height: 26px;
  border-radius: 13px;
  background: rgba(0, 0, 0, 0.12);
  position: relative;
  transition: background var(--c-ease);
  display: block;
}

.toggle-btn--on .toggle-btn__track {
  background: var(--c-success);
}

.toggle-btn__thumb {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform var(--c-ease);
}

.toggle-btn--on .toggle-btn__thumb {
  transform: translateX(18px);
}

/* Action Buttons in Table */
.table__td--actions {
  text-align: center;
}

.table__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  padding: 0 8px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.table__action svg {
  width: 13px;
  height: 13px;
}

.table__action--config {
  color: var(--c-accent);
}

@media (hover: hover) {
  .table__action--config:hover {
    background: rgba(0, 122, 255, 0.08);
  }
}

.table__action--detail {
  color: var(--c-success);
}

@media (hover: hover) {
  .table__action--detail:hover {
    background: rgba(52, 199, 89, 0.08);
  }
}

.table__action--price {
  color: #5856d6;
}

@media (hover: hover) {
  .table__action--price:hover {
    background: rgba(88, 86, 214, 0.08);
  }
}

.table__action--off-shelf {
  color: #ff9500;
}

@media (hover: hover) {
  .table__action--off-shelf:hover {
    background: rgba(255, 149, 0, 0.08);
  }
}

.table__action--delete {
  color: var(--c-danger);
}

@media (hover: hover) {
  .table__action--delete:hover {
    background: rgba(255, 59, 48, 0.08);
  }
}

.table__action:active {
  transform: scale(0.95);
}

/* ============================================================
   Empty State
   ============================================================ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 16px;
  gap: 12px;
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  opacity: 0.35;
}

.empty-state__icon svg {
  width: 36px;
  height: 36px;
}

.empty-state__text {
  font-size: 14px;
  color: var(--c-text-3);
}

/* ============================================================
   Loading State
   ============================================================ */
.card-list--loading,
.table-container--loading {
  opacity: 0.5;
  pointer-events: none;
}

/* ============================================================
   Responsive
   ============================================================ */
@media screen and (max-width: 480px) {
  .card-list {
    padding: 12px;
    gap: 8px;
  }

  .goods-card__image-wrap {
    height: 190px;
  }

  .goods-card__title {
    font-size: 12px;
  }

  .goods-card__price {
    font-size: 15px;
  }

  .goods-card__stats {
    gap: 4px;
  }

  .goods-card__stat {
    padding: 5px 6px;
  }

  .goods-card__stat-value {
    font-size: 11px;
  }

  .goods-card__switch {
    height: 24px;
    padding: 0 7px;
    font-size: 11px;
  }

  .goods-card__action {
    height: 30px;
    font-size: 11px;
  }
}

/* ============================================================
   Delivery Type Tag
   ============================================================ */
.delivery-type-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  white-space: nowrap;
}

.delivery-type-tag--text {
  color: var(--c-accent);
  background: rgba(0, 122, 255, 0.08);
}

.delivery-type-tag--kami {
  color: #9b59b6;
  background: rgba(155, 89, 182, 0.08);
}

.delivery-type-tag--custom {
  color: #ff9500;
  background: rgba(255, 149, 0, 0.1);
}

.delivery-type-tag--api {
  color: #34c759;
  background: rgba(52, 199, 89, 0.1);
}

.delivery-type-tag--off {
  color: var(--c-text-3);
}

.goods-card__type-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-size: 10px;
  font-weight: 500;
  border-radius: 4px;
  white-space: nowrap;
}

.goods-card__type-tag--text {
  color: var(--c-accent);
  background: rgba(0, 122, 255, 0.1);
}

.goods-card__type-tag--kami {
  color: #9b59b6;
  background: rgba(155, 89, 182, 0.1);
}

.goods-card__type-tag--custom {
  color: #ff9500;
  background: rgba(255, 149, 0, 0.12);
}

.goods-card__type-tag--api {
  color: #34c759;
  background: rgba(52, 199, 89, 0.12);
}
</style>

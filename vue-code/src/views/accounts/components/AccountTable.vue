<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getAccountStatusText, formatTime } from '@/utils'
import { getAccountAvatarText, getAccountDisplayName } from '@/utils/accountDisplay'
import type { Account } from '@/types'

import IconEdit from '@/components/icons/IconEdit.vue'
import IconTrash from '@/components/icons/IconTrash.vue'
import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconCopy from '@/components/icons/IconCopy.vue'
import IconLink from '@/components/icons/IconLink.vue'
import IconSparkle from '@/components/icons/IconSparkle.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconKey from '@/components/icons/IconKey.vue'

interface Props {
  accounts: Account[]
  loading?: boolean
  refreshingProfileId?: number | null
  polishingAccountId?: number | null
}

interface Emits {
  (e: 'edit', account: Account): void
  (e: 'refreshProfile', account: Account): void
  (e: 'polish', account: Account): void
  (e: 'polishTask', account: Account): void
  (e: 'proxy', account: Account): void
  (e: 'credential', account: Account): void
  (e: 'copyUnb', account: Account): void
  (e: 'connection', account: Account): void
  (e: 'delete', id: number): void
}

const props = defineProps<Props>()
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
  const info = getAccountStatusText(status)
  switch (info.type) {
    case 'success': return '#34c759'
    case 'warning': return '#ff9500'
    case 'danger': return '#ff3b30'
    default: return '#007aff'
  }
}

const getStatusBg = (status: number) => {
  const info = getAccountStatusText(status)
  switch (info.type) {
    case 'success': return 'rgba(52, 199, 89, 0.1)'
    case 'warning': return 'rgba(255, 149, 0, 0.1)'
    case 'danger': return 'rgba(255, 59, 48, 0.1)'
    default: return 'rgba(0, 122, 255, 0.1)'
  }
}

const getDisplayName = (account: Account) => {
  return getAccountDisplayName(account)
}

const getAvatarInitial = (account: Account) => {
  return getAccountAvatarText(account)
}

const getFishShopLabel = (account: Account) => {
  if (!account.fishShopUser || !account.fishShopLevel) return ''
  return `鱼小铺 ${account.fishShopLevel}`
}

const formatAccountCount = (value?: string | number) => {
  if (value === undefined || value === null || value === '') return '-'
  return String(value)
}

const shortUnb = (unb?: string) => {
  if (!unb) return '-'
  return unb.length > 12 ? `${unb.slice(0, 4)}...${unb.slice(-4)}` : unb
}

const isRefreshingProfile = (account: Account) => {
  return props.refreshingProfileId === account.id
}

const isPolishing = (account: Account) => {
  return props.polishingAccountId === account.id
}
</script>

<template>
  <!-- Mobile: Card View -->
  <div v-if="isMobile" class="card-list" :class="{ 'card-list--loading': loading }">
    <div
      v-for="(account, index) in accounts"
      :key="account.id"
      class="account-card"
    >
      <div class="account-card__header">
        <div class="account-card__avatar">
          <img v-if="account.avatar" :src="account.avatar" :alt="getDisplayName(account)" />
          <span v-else>{{ getAvatarInitial(account) }}</span>
        </div>
        <div class="account-card__info">
          <div class="account-card__name-row">
            <span class="account-card__name">{{ getDisplayName(account) }}</span>
            <span v-if="getFishShopLabel(account)" class="fish-shop-tag">{{ getFishShopLabel(account) }}</span>
          </div>
          <span class="account-card__unb">
            {{ account.ipLocation || '未知地区' }} · UNB: {{ shortUnb(account.unb) }}
          </span>
        </div>
        <span
          class="account-card__status"
          :style="{
            color: getStatusColor(account.status),
            background: getStatusBg(account.status)
          }"
        >
          <component :is="getAccountStatusText(account.status).type === 'success' ? IconCheck : IconAlert" />
          {{ getAccountStatusText(account.status).text }}
        </span>
      </div>

      <p v-if="account.introduction" class="account-card__intro">{{ account.introduction }}</p>

      <div class="account-card__stats">
        <div class="account-card__stat">
          <span>{{ formatAccountCount(account.followers) }}</span>
          <label>粉丝</label>
        </div>
        <div class="account-card__stat">
          <span>{{ formatAccountCount(account.following) }}</span>
          <label>关注</label>
        </div>
        <div class="account-card__stat">
          <span>{{ formatAccountCount(account.soldCount) }}</span>
          <label>卖出</label>
        </div>
        <div class="account-card__stat">
          <span>{{ formatAccountCount(account.purchaseCount) }}</span>
          <label>买入</label>
        </div>
      </div>

      <div class="account-card__body">
        <div class="account-card__row">
          <span class="account-card__label">序号</span>
          <span class="account-card__value">{{ index + 1 }}</span>
        </div>
        <div class="account-card__row">
          <span class="account-card__label">卖家等级</span>
          <span class="account-card__value">{{ account.sellerLevel || '-' }}</span>
        </div>
        <div class="account-card__row">
          <span class="account-card__label">鱼小铺</span>
          <span class="account-card__value">{{ getFishShopLabel(account) || '-' }}</span>
        </div>
        <div class="account-card__row">
          <span class="account-card__label">好评率</span>
          <span class="account-card__value">{{ account.praiseRatio ? `${account.praiseRatio}%` : '-' }}</span>
        </div>
      </div>

      <div class="account-card__footer">
        <button
          class="account-card__btn account-card__btn--refresh"
          :disabled="isRefreshingProfile(account)"
          @click="emit('refreshProfile', account)"
        >
          <IconRefresh />
          <span>{{ isRefreshingProfile(account) ? '刷新中' : '刷新资料' }}</span>
        </button>
        <button
          class="account-card__btn account-card__btn--polish"
          :disabled="isPolishing(account)"
          @click="emit('polish', account)"
        >
          <IconSparkle />
          <span>{{ isPolishing(account) ? '擦亮中' : '一键擦亮' }}</span>
        </button>
        <button class="account-card__btn account-card__btn--schedule" @click="emit('polishTask', account)">
          <IconClock />
          <span>定时擦亮</span>
        </button>
        <button class="account-card__btn account-card__btn--copy" @click="emit('copyUnb', account)">
          <IconCopy />
          <span>复制UNB</span>
        </button>
        <button class="account-card__btn account-card__btn--link" @click="emit('connection', account)">
          <IconLink />
          <span>连接管理</span>
        </button>
        <button class="account-card__btn account-card__btn--edit" @click="emit('proxy', account)">
          <IconLink />
          <span>{{ account.proxyHost ? '代理 ✓' : '代理' }}</span>
        </button>
        <button class="account-card__btn account-card__btn--credential" @click="emit('credential', account)">
          <IconKey />
          <span>{{ account.loginUsername ? '登录 ✓' : '登录' }}</span>
        </button>
        <button class="account-card__btn account-card__btn--edit" @click="emit('edit', account)">
          <IconEdit />
          <span>编辑备注</span>
        </button>
        <button class="account-card__btn account-card__btn--delete" @click="emit('delete', account.id)">
          <IconTrash />
          <span>删除</span>
        </button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && accounts.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无账号数据</p>
    </div>
  </div>

  <!-- Desktop/Tablet: Table View -->
  <div v-else class="table-container" :class="{ 'table-container--loading': loading }">
    <table class="table" v-if="accounts.length > 0">
      <thead class="table__head">
        <tr>
          <th class="table__th table__th--id">序号</th>
          <th class="table__th table__th--profile">账号信息</th>
          <th class="table__th table__th--stats">数据</th>
          <th class="table__th table__th--status">状态</th>
          <th class="table__th table__th--time">更新时间</th>
          <th class="table__th table__th--actions">操作</th>
        </tr>
      </thead>
      <tbody class="table__body">
        <tr v-for="(account, index) in accounts" :key="account.id" class="table__tr">
          <td class="table__td table__td--id">{{ index + 1 }}</td>
          <td class="table__td table__td--profile">
            <div class="account-profile">
              <div class="account-profile__avatar">
                <img v-if="account.avatar" :src="account.avatar" :alt="getDisplayName(account)" />
                <span v-else>{{ getAvatarInitial(account) }}</span>
              </div>
              <div class="account-profile__main">
                <div class="account-profile__name-row">
                  <div class="account-profile__name">{{ getDisplayName(account) }}</div>
                  <span v-if="getFishShopLabel(account)" class="fish-shop-tag">{{ getFishShopLabel(account) }}</span>
                </div>
                <div class="account-profile__meta">
                  {{ account.ipLocation || '未知地区' }} · UNB: {{ account.unb }}
                </div>
                <div v-if="account.introduction" class="account-profile__intro">{{ account.introduction }}</div>
              </div>
            </div>
          </td>
          <td class="table__td table__td--stats">
            <div class="account-stats">
              <span>粉丝 {{ formatAccountCount(account.followers) }}</span>
              <span>关注 {{ formatAccountCount(account.following) }}</span>
              <span>卖出 {{ formatAccountCount(account.soldCount) }}</span>
              <span>买入 {{ formatAccountCount(account.purchaseCount) }}</span>
              <span v-if="account.sellerLevel">等级 {{ account.sellerLevel }}</span>
              <span v-if="getFishShopLabel(account)">{{ getFishShopLabel(account) }}</span>
              <span v-if="account.praiseRatio">好评 {{ account.praiseRatio }}%</span>
            </div>
          </td>
          <td class="table__td table__td--status">
            <span
              class="status-tag"
              :style="{
                color: getStatusColor(account.status),
                background: getStatusBg(account.status)
              }"
            >
              {{ getAccountStatusText(account.status).text }}
            </span>
          </td>
          <td class="table__td table__td--time">{{ formatTime(account.profileUpdatedTime || account.updatedTime) }}</td>
          <td class="table__td table__td--actions">
            <button
              class="table__action table__action--refresh"
              :disabled="isRefreshingProfile(account)"
              @click="emit('refreshProfile', account)"
            >
              <IconRefresh />
              <span>{{ isRefreshingProfile(account) ? '刷新中' : '刷新资料' }}</span>
            </button>
            <button
              class="table__action table__action--polish"
              :disabled="isPolishing(account)"
              @click="emit('polish', account)"
            >
              <IconSparkle />
              <span>{{ isPolishing(account) ? '擦亮中' : '一键擦亮' }}</span>
            </button>
            <button class="table__action table__action--schedule" @click="emit('polishTask', account)">
              <IconClock />
              <span>定时擦亮</span>
            </button>
            <button class="table__action table__action--copy" @click="emit('copyUnb', account)">
              <IconCopy />
              <span>复制UNB</span>
            </button>
            <button class="table__action table__action--link" @click="emit('connection', account)">
              <IconLink />
              <span>连接管理</span>
            </button>
            <button class="table__action table__action--edit" @click="emit('proxy', account)">
              <IconLink />
              <span>{{ account.proxyHost ? '代理 ✓' : '代理' }}</span>
            </button>
            <button class="table__action table__action--credential" @click="emit('credential', account)">
              <IconKey />
              <span>{{ account.loginUsername ? '登录 ✓' : '登录' }}</span>
            </button>
            <button class="table__action table__action--edit" @click="emit('edit', account)">
              <IconEdit />
              <span>编辑备注</span>
            </button>
            <button class="table__action table__action--delete" @click="emit('delete', account.id)">
              <IconTrash />
              <span>删除</span>
            </button>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Empty State -->
    <div v-if="!loading && accounts.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无账号数据</p>
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
  --c-r-sm: 8px;
  --c-r-md: 12px;
  --c-ease: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

/* ============================================================
   Mobile List View (iOS 26 Glass Card Style)
   ============================================================ */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  padding-bottom: 24px;
  min-height: 100%;
  background: transparent;
}

.account-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  padding: 14px;
  transition: all var(--c-ease);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (hover: hover) {
  .account-card:hover {
    background: rgba(255, 255, 255, 0.8);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.12);
    border-color: rgba(255, 255, 255, 0.6);
  }
}

.account-card:active {
  transform: scale(0.98);
}

.account-card__header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 0;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.account-card__avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #007aff 0%, #0051d5 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
  overflow: hidden;
}

.account-card__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.account-card__name-row,
.account-profile__name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.account-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}

.fish-shop-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  padding: 0 8px;
  border-radius: 999px;
  background: rgba(0, 122, 255, 0.12);
  color: #0051d5;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.account-card__unb {
  font-size: 12px;
  color: var(--c-text-3);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-card__status {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 9px;
  border-radius: 14px;
  line-height: 1;
  background: rgba(0, 122, 255, 0.15);
  color: var(--c-accent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.account-card__status svg {
  width: 12px;
  height: 12px;
}

.account-card__intro {
  margin: -3px 0 0;
  font-size: 12px;
  line-height: 1.35;
  color: var(--c-text-2);
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.account-card__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  padding: 8px 0 0;
}

.account-card__stat {
  min-width: 0;
  text-align: center;
}

.account-card__stat span {
  display: block;
  font-size: 14px;
  font-weight: 650;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-card__stat label {
  display: block;
  margin-top: 2px;
  font-size: 10px;
  color: var(--c-text-3);
}

.account-card__body {
  margin-bottom: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  padding: 9px 0;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
}

.account-card__row {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  line-height: 1.25;
}

.account-card__label-icon {
  width: 14px;
  height: 14px;
  color: var(--c-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.account-card__label-icon svg {
  width: 12px;
  height: 12px;
}

.account-card__label {
  color: var(--c-text-3);
  flex-shrink: 0;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.account-card__value {
  color: var(--c-text-1);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.account-card__footer {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding-top: 0;
  border-top: none;
}

.account-card__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  width: 100%;
  height: 36px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  border: none;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.account-card__btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.account-card__btn svg {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
}

.account-card__btn span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-card__btn--edit {
  color: white;
  background: var(--c-accent);
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.account-card__btn--refresh,
.account-card__btn--polish,
.account-card__btn--schedule,
.account-card__btn--copy,
.account-card__btn--link,
.account-card__btn--credential {
  color: var(--c-text-1);
  background: rgba(0, 0, 0, 0.06);
}

.account-card__btn--edit:active {
  background: #0051d5;
  transform: scale(0.97);
}

.account-card__btn--delete {
  grid-column: 1 / -1;
  color: white;
  background: var(--c-danger);
  box-shadow: 0 4px 12px rgba(255, 59, 48, 0.3);
}

.account-card__btn--delete:active {
  background: #e63c2e;
  transform: scale(0.97);
}

/* ============================================================
   Desktop Table View
   ============================================================ */
.table-container {
  min-height: 100%;
  overflow-x: auto;
}

.table {
  width: 100%;
  min-width: 1120px;
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

.table__th--id { width: 64px; }
.table__th--profile { min-width: 320px; }
.table__th--stats { width: 260px; }
.table__th--status { width: 96px; }
.table__th--time { width: 168px; }
.table__th--actions { width: 500px; text-align: center; }

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
  padding: 12px 16px;
  color: var(--c-text-1);
  background: transparent;
  transition: background var(--c-ease);
  line-height: 1.5;
  vertical-align: middle;
}

.table__td--id {
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.table__td--time {
  color: var(--c-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.table__td--actions {
  text-align: center;
  white-space: normal;
  min-width: 480px;
}

.account-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  max-width: 100%;
}

.account-profile__avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #007aff 0%, #0051d5 100%);
  color: #fff;
  font-weight: 650;
  overflow: hidden;
}

.account-profile__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.account-profile__main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.account-profile__name {
  font-size: 14px;
  font-weight: 650;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}

.account-profile__meta,
.account-profile__intro {
  font-size: 12px;
  color: var(--c-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 520px;
}

.account-profile__intro {
  color: var(--c-text-2);
}

.account-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  max-width: 260px;
}

.account-stats span {
  font-size: 12px;
  color: var(--c-text-2);
  white-space: nowrap;
}

/* Status Tag */
.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
  line-height: 1;
}

/* Action Buttons in Table */
.table__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.table__action:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.table__action svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.table__action span {
  white-space: nowrap;
}

.table__action--edit {
  color: var(--c-accent);
}

.table__action--refresh,
.table__action--polish,
.table__action--schedule,
.table__action--copy,
.table__action--link,
.table__action--credential {
  color: var(--c-text-1);
}

@media (hover: hover) {
  .table__action--refresh:hover,
  .table__action--polish:hover,
  .table__action--schedule:hover,
  .table__action--copy:hover,
  .table__action--link:hover,
  .table__action--credential:hover {
    background: rgba(0, 0, 0, 0.06);
  }
}

@media (hover: hover) {
  .table__action--edit:hover {
    background: rgba(0, 122, 255, 0.08);
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
    gap: 10px;
  }

  .account-card {
    padding: 12px;
  }

  .account-card__avatar {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }

  .account-card__name {
    font-size: 14px;
  }

  .account-card__unb {
    font-size: 12px;
  }

  .account-card__body {
    gap: 8px;
    padding: 8px 0;
  }

  .account-card__stats {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 6px;
  }

  .account-card__row {
    font-size: 12px;
  }

  .account-card__value {
    font-size: 13px;
  }

  .account-card__btn {
    height: 36px;
    font-size: 12px;
  }

  .account-card__footer {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .empty-state {
    padding: 40px 16px;
  }
}
</style>

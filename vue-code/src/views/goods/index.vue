<script setup lang="ts">
import { onMounted, ref, computed, inject, defineComponent, h } from 'vue'
import { useGoodsManager } from './useGoodsManager'
import './goods.css'
import '@/styles/header-selectors.css'

import IconShoppingBag from '@/components/icons/IconShoppingBag.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import IconPlus from '@/components/icons/IconPlus.vue'

import GoodsTable from './components/GoodsTable.vue'
import GoodsDetail from './components/GoodsDetail.vue'
import PublishGoodsDialog from './components/PublishGoodsDialog.vue'
import { showInfo } from '@/utils'
import { getAccountDisplayName } from '@/utils/accountDisplay'

const {
  loading,
  refreshing,
  syncing,
  syncProgress,
  accounts,
  selectedAccountId,
  statusFilter,
  searchKeyword,
  goodsList,
  currentPage,
  pageSize,
  total,
  totalPages,
  dialogs,
  selectedAccountIsFishShop,
  featureOffShelfEnabled,
  featureDeleteEnabled,
  selectedGoodsId,
  selectedGoods,
  configAction,
  loadGoodsOperationSettings,
  loadAccounts,
  loadGoods,
  handleRefresh,
  openSyncMenu,
  handleAccountChange,
  handleStatusFilter,
  handleSearch,
  handleSearchInput,
  handleClearSearch,
  exportGoodsCsv,
  handlePageChange,
  viewDetail,
  configAutoDelivery,
  openGoodsConfig,
  toggleAutoDelivery,
  toggleAutoReply,
  handleOffShelf,
  toggleRemoteOffShelf,
  toggleRemoteDelete,
  confirmDelete,
  handleUpdatePrice,
  handleUpdateStock,
} = useGoodsManager()

// 下拉刷新相关状态
const pullRefreshState = ref<'idle' | 'pulling' | 'ready' | 'refreshing'>('idle')
const pullDistance = ref(0)
const tableWrapRef = ref<HTMLElement | null>(null)
const startY = ref(0)
const isMobile = ref(false)
const publishDialogVisible = ref(false)

// 检测是否为手机模式
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

const openPublishDialog = () => {
  if (!selectedAccountId.value) {
    showInfo('请先选择账号')
    return
  }
  publishDialogVisible.value = true
}

// 计算下拉刷新的显示距离
const pullRefreshDistance = computed(() => {
  const maxDistance = 80
  return Math.min(pullDistance.value, maxDistance)
})

// 处理触摸开始
const handleTouchStart = (e: TouchEvent) => {
  if (!isMobile.value || !tableWrapRef.value) return
  
  // 只在列表顶部时才允许下拉
  if (tableWrapRef.value.scrollTop === 0) {
    startY.value = e.touches[0]?.clientY ?? 0
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  }
}

// 处理触摸移动
const handleTouchMove = (e: TouchEvent) => {
  if (!isMobile.value || !tableWrapRef.value || startY.value === 0) return
  
  if (tableWrapRef.value.scrollTop === 0) {
    const currentY = e.touches[0]?.clientY ?? 0
    const distance = currentY - startY.value
    
    if (distance > 0) {
      e.preventDefault()
      pullDistance.value = distance
      
      if (distance < 60) {
        pullRefreshState.value = 'pulling'
      } else {
        pullRefreshState.value = 'ready'
      }
    }
  }
}

// 处理触摸结束
const handleTouchEnd = async () => {
  if (!isMobile.value) return
  
  if (pullRefreshState.value === 'ready' && pullDistance.value >= 60) {
    pullRefreshState.value = 'refreshing'
    await handleRefresh()
    // 动画反弹
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  } else {
    // 回弹动画
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  }
  
  startY.value = 0
}

// 获取导航栏内容设置函数
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

// 创建导航栏选择器组件
const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          value: selectedAccountId.value,
          onChange: (e: Event) => {
            const target = e.target as HTMLSelectElement
            selectedAccountId.value = target.value ? parseInt(target.value) : null
            handleAccountChange()
          }
        }, [
          h('option', { value: '', disabled: true }, '选择账号'),
          ...accounts.value.map(acc => 
            h('option', { value: acc.id.toString() }, getAccountDisplayName(acc))
          )
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          value: statusFilter.value,
          onChange: (e: Event) => {
            const target = e.target as HTMLSelectElement
            statusFilter.value = target.value
            handleStatusFilter()
          }
        }, [
          h('option', { value: '' }, '全部状态'),
          h('option', { value: '0' }, '在售'),
          h('option', { value: '1' }, '已下架'),
          h('option', { value: '2' }, '已售出')
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ])
    ])
  }
})

onMounted(() => {
  loadAccounts()
  loadGoodsOperationSettings()
  checkMobile()
  window.addEventListener('resize', checkMobile)
  
  // 只在手机模式下设置导航栏内容
  if (setHeaderContent) {
    setHeaderContent(HeaderSelectors)
  }
})

// 分页按钮列表
const getPageButtons = () => {
  const buttons: number[] = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  const end = Math.min(totalPages.value, start + maxVisible - 1)
  start = Math.max(1, end - maxVisible + 1)
  for (let i = start; i <= end; i++) {
    buttons.push(i)
  }
  return buttons
}
</script>

<template>
  <div class="goods">
    <!-- Header -->
    <div class="goods__header">
      <div class="goods__title-row desktop-only">
        <div class="goods__title-icon">
          <IconShoppingBag />
        </div>
        <h1 class="goods__title">商品管理</h1>
      </div>

      <div class="goods__actions">
        <button
          class="btn btn--secondary desktop-only"
          :disabled="!selectedAccountId"
          @click="openPublishDialog"
        >
          <IconPlus />
          <span class="mobile-hidden">发布商品</span>
        </button>
        <button
          class="btn btn--secondary"
          :disabled="goodsList.length === 0"
          @click="exportGoodsCsv"
        >
          <span>导出CSV</span>
        </button>
        <button
          class="btn btn--primary desktop-only"
          :class="{ 'btn--loading': refreshing || syncing }"
          :disabled="refreshing || syncing || !selectedAccountId"
          @click="openSyncMenu"
        >
          <IconRefresh />
          <span class="mobile-hidden">同步闲鱼商品</span>
        </button>
        <div v-if="syncing && syncProgress" class="goods__sync-progress">
          <span class="goods__sync-text">
            详情同步: {{ syncProgress.completedCount }}/{{ syncProgress.totalCount }}
          </span>
          <div class="goods__sync-bar">
            <div 
              class="goods__sync-bar-fill" 
              :style="{ width: `${(syncProgress.completedCount / syncProgress.totalCount) * 100}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Filter Bar (Desktop Only) -->
    <div class="goods__filter-bar desktop-only">
      <!-- Account Select -->
      <div class="goods__select-wrap">
        <select
          v-model="selectedAccountId"
          class="goods__select"
          @change="handleAccountChange"
        >
          <option :value="null" disabled>选择账号</option>
          <option v-for="acc in accounts" :key="acc.id" :value="acc.id">
            {{ getAccountDisplayName(acc) }}
          </option>
        </select>
        <span class="goods__select-icon">
          <IconChevronDown />
        </span>
      </div>

      <!-- Status Filter -->
      <div class="goods__select-wrap">
        <select
          v-model="statusFilter"
          class="goods__select"
          @change="handleStatusFilter"
        >
          <option value="">全部状态</option>
          <option value="0">在售</option>
          <option value="1">已下架</option>
          <option value="2">已售出</option>
        </select>
        <span class="goods__select-icon">
          <IconChevronDown />
        </span>
      </div>

      <!-- Search -->
      <div class="goods__search-wrap">
        <IconSearch class="goods__search-icon" />
        <input
          v-model="searchKeyword"
          class="goods__search-input"
          type="search"
          placeholder="搜索标题或商品ID"
          @input="handleSearchInput"
          @keyup.enter="handleSearch"
        />
        <button
          v-if="searchKeyword"
          class="goods__search-clear"
          type="button"
          @click="handleClearSearch"
        >
          清空
        </button>
      </div>

      <!-- Count -->
      <span v-if="total > 0" class="goods__count">
        共 {{ total }} 件
      </span>
    </div>

    <!-- Search Bar (Mobile Only) -->
    <div class="goods__mobile-search mobile-only">
      <div class="goods__search-wrap goods__search-wrap--mobile">
        <IconSearch class="goods__search-icon" />
        <input
          v-model="searchKeyword"
          class="goods__search-input"
          type="search"
          placeholder="搜索标题或商品ID"
          @input="handleSearchInput"
          @keyup.enter="handleSearch"
        />
        <button
          v-if="searchKeyword"
          class="goods__search-clear"
          type="button"
          @click="handleClearSearch"
        >
          清空
        </button>
      </div>
    </div>

    <div class="goods__mobile-actions mobile-only">
      <button
        class="btn btn--primary"
        :class="{ 'btn--loading': refreshing || syncing }"
        :disabled="refreshing || syncing || !selectedAccountId"
        @click="openSyncMenu"
      >
        <IconRefresh />
        <span>{{ syncing ? '同步中' : '同步' }}</span>
      </button>
      <button
        class="btn btn--secondary"
        :disabled="!selectedAccountId"
        @click="openPublishDialog"
      >
        <IconPlus />
        <span>发布</span>
      </button>
      <button
        class="btn btn--secondary"
        :disabled="goodsList.length === 0"
        @click="exportGoodsCsv"
      >
        导出CSV
      </button>
      <span v-if="total > 0" class="goods__count">共 {{ total }} 件</span>
    </div>

    <!-- Content Card -->
    <div class="goods__content">
      <!-- Toolbar -->
      <div class="goods__toolbar">
        <span class="goods__list-title">商品列表</span>
        <span v-if="goodsList.length > 0" class="goods__count">
          {{ (currentPage - 1) * pageSize + 1 }}-{{ Math.min(currentPage * pageSize, total) }} / {{ total }}
        </span>
      </div>

      <!-- Pull Refresh Indicator (Mobile Only) -->
      <div 
        v-if="isMobile && pullDistance > 0"
        class="goods__pull-refresh"
        :style="{ height: `${pullRefreshDistance}px` }"
        :class="{
          'goods__pull-refresh--pulling': pullRefreshState === 'pulling',
          'goods__pull-refresh--ready': pullRefreshState === 'ready',
          'goods__pull-refresh--refreshing': pullRefreshState === 'refreshing'
        }"
      >
        <div class="goods__pull-refresh-content">
          <div class="goods__pull-refresh-icon">
            <IconRefresh />
          </div>
          <div class="goods__pull-refresh-text">
            {{ pullRefreshState === 'pulling' ? '下拉刷新' : pullRefreshState === 'ready' ? '释放刷新' : '刷新中...' }}
          </div>
        </div>
      </div>

      <!-- Table/Cards -->
      <div 
        ref="tableWrapRef"
        class="goods__table-wrap"
        @touchstart="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend="handleTouchEnd"
      >
        <GoodsTable
          :goods-list="goodsList"
          :loading="loading"
          :is-fish-shop-account="selectedAccountIsFishShop"
          :off-shelf-enabled="featureOffShelfEnabled"
          :delete-enabled="featureDeleteEnabled"
          @view="viewDetail"
          @toggle-auto-delivery="toggleAutoDelivery"
          @toggle-auto-reply="toggleAutoReply"
          @toggle-remote-off-shelf="toggleRemoteOffShelf"
          @toggle-remote-delete="toggleRemoteDelete"
          @config-auto-delivery="configAutoDelivery"
          @off-shelf="handleOffShelf"
          @delete="confirmDelete"
          @update-price="handleUpdatePrice"
          @update-stock="handleUpdateStock"
        />
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="goods__pagination">
        <button
          class="goods__page-btn"
          :class="{ 'goods__page-btn--disabled': currentPage <= 1 }"
          @click="handlePageChange(currentPage - 1)"
        >
          <IconChevronLeft />
        </button>

        <template v-for="page in getPageButtons()" :key="page">
          <button
            class="goods__page-btn"
            :class="{ 'goods__page-btn--active': page === currentPage }"
            @click="handlePageChange(page)"
          >
            {{ page }}
          </button>
        </template>

        <button
          class="goods__page-btn"
          :class="{ 'goods__page-btn--disabled': currentPage >= totalPages }"
          @click="handlePageChange(currentPage + 1)"
        >
          <IconChevronRight />
        </button>

        <span class="goods__page-info">{{ currentPage }} / {{ totalPages }}</span>
      </div>
    </div>

    <!-- Detail Dialog -->
    <GoodsDetail
      v-model="dialogs.detail"
      :goods-id="selectedGoodsId"
      :account-id="selectedAccountId"
      :is-fish-shop-account="selectedAccountIsFishShop"
      :off-shelf-enabled="featureOffShelfEnabled"
      :delete-enabled="featureDeleteEnabled"
      :initial-config-action="configAction"
      @refresh="loadGoods"
    />

    <PublishGoodsDialog
      v-model="publishDialogVisible"
      :accounts="accounts"
      :account-id="selectedAccountId"
    />

    <!-- Config Menu -->
    <Transition name="overlay-fade">
      <div v-if="dialogs.syncMenu" class="goods__dialog-overlay" @click.self="dialogs.syncMenu = false">
        <div class="goods__config-menu">
          <div class="goods__config-menu-header">
            <h3 class="goods__dialog-title">同步闲鱼商品</h3>
            <p class="goods__config-menu-title">选择要同步的商品分组</p>
          </div>
          <div class="goods__config-menu-body">
            <button class="goods__config-menu-item" type="button" @click="handleRefresh(0)">
              <IconRefresh />
              <span>同步在售</span>
            </button>
            <button class="goods__config-menu-item" type="button" @click="handleRefresh(2)">
              <IconShoppingBag />
              <span>同步已售出</span>
            </button>
          </div>
          <div class="goods__dialog-footer">
            <button class="goods__dialog-btn goods__dialog-btn--cancel" @click="dialogs.syncMenu = false">
              取消
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Config Menu -->
    <Transition name="overlay-fade">
      <div v-if="dialogs.configMenu && selectedGoods" class="goods__dialog-overlay" @click.self="dialogs.configMenu = false">
        <div class="goods__config-menu">
          <div class="goods__config-menu-header">
            <h3 class="goods__dialog-title">配置商品</h3>
            <p class="goods__config-menu-title">{{ selectedGoods.item.title }}</p>
          </div>
          <div class="goods__config-menu-body">
            <button class="goods__config-menu-item" type="button" @click="openGoodsConfig('delivery')">
              <IconRefresh />
              <span>配置发货</span>
            </button>
            <button class="goods__config-menu-item" type="button" @click="openGoodsConfig('reply')">
              <IconSearch />
              <span>配置回复</span>
            </button>
          </div>
          <div class="goods__dialog-footer">
            <button class="goods__dialog-btn goods__dialog-btn--cancel" @click="dialogs.configMenu = false">
              取消
            </button>
          </div>
        </div>
      </div>
    </Transition>

  </div>
</template>

<style scoped>
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.2s ease;
}

.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}
</style>

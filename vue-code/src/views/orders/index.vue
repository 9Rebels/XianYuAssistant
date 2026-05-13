<script setup lang="ts">
import { onMounted, ref, computed, inject, defineComponent, h, reactive } from 'vue'
import { useOrderManager } from './useOrderManager'
import { queryOrderList, batchRefreshOrders, syncSoldOrders, type OrderVO } from '@/api/order'
import { formatTime, showError, showSuccess } from '@/utils'
import { getAccountDisplayName } from '@/utils/accountDisplay'
import './orders.css'
import '@/styles/header-selectors.css'

import IconClipboard from '@/components/icons/IconClipboard.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconFilter from '@/components/icons/IconFilter.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import IconSync from '@/components/icons/IconSync.vue'

import OrderTable from './components/OrderTable.vue'
import OrderDetailDrawer from './components/OrderDetailDrawer.vue'

const {
  loading,
  orderList,
  total,
  accounts,
  selectedAccountIsFishShop,
  queryParams,
  totalPages,
  loadAccounts,
  loadOrders,
  handleAccountChange,
  handleReset,
  handlePageChange,
  copySId,
  exportOrdersCsv,
  handleConfirmShipment
} = useOrderManager()

const showFilterSheet = ref(false)
const isMobile = ref(false)
const activeTab = ref<'delivery' | 'orders'>('delivery')

const orderLoading = ref(false)
const orderRows = ref<OrderVO[]>([])
const orderTotal = ref(0)
const orderQuery = reactive({
  orderStatus: undefined as number | undefined,
  pageNum: 1,
  pageSize: 20
})
const selectedOrderIds = ref<Set<string>>(new Set())
const detailVisible = ref(false)
const detailOrder = ref<OrderVO | null>(null)
const soldOrderSyncing = ref(false)

const orderTotalPages = computed(() => Math.ceil(orderTotal.value / orderQuery.pageSize))
const refreshableOrderIds = computed(() =>
  orderRows.value.map(item => item.orderId).filter(Boolean) as string[]
)
const isAllRefreshableOrdersSelected = computed(() =>
  refreshableOrderIds.value.length > 0 && selectedOrderIds.value.size === refreshableOrderIds.value.length
)
const batchRefreshDisabled = computed(() =>
  orderLoading.value || !queryParams.xianyuAccountId || selectedOrderIds.value.size === 0
)
const soldOrderSyncDisabled = computed(() =>
  orderLoading.value || soldOrderSyncing.value || !queryParams.xianyuAccountId
)

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

// 导航栏注入
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          onChange: (e: Event) => {
            const val = (e.target as HTMLSelectElement).value
            queryParams.xianyuAccountId = val ? parseInt(val) : undefined
            handleAccountChange()
            if (activeTab.value === 'orders') loadBusinessOrders()
          }
        }, [
          h('option', { value: '', disabled: true, selected: !queryParams.xianyuAccountId }, '账号'),
          ...accounts.value.map(acc =>
            h('option', {
              value: acc.id.toString(),
              selected: queryParams.xianyuAccountId === acc.id
            }, getAccountDisplayName(acc))
          )
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('button', {
        class: ['header-refresh-btn', { 'header-refresh-btn--loading': loading.value }],
        disabled: activeTab.value === 'delivery' ? loading.value : orderLoading.value,
        onClick: () => activeTab.value === 'delivery' ? loadOrders() : loadBusinessOrders()
      }, [
        h(IconRefresh, { class: 'header-refresh-icon' })
      ]),
      h('button', {
        class: 'header-filter-btn',
        onClick: openFilterSheet
      }, [
        h(IconFilter, { class: 'header-filter-icon' })
      ])
    ])
  }
})

onMounted(async () => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  if (setHeaderContent) setHeaderContent(HeaderSelectors)
  await loadAccounts()
  if (setHeaderContent) setHeaderContent(HeaderSelectors)
  loadOrders()
})

const switchTab = (tab: 'delivery' | 'orders') => {
  activeTab.value = tab
  if (tab === 'orders' && orderRows.value.length === 0) {
    loadBusinessOrders()
  }
}

const loadBusinessOrders = async () => {
  if (!queryParams.xianyuAccountId) {
    showError('请先选择账号')
    return
  }
  orderLoading.value = true
  try {
    const res = await queryOrderList({
      xianyuAccountId: queryParams.xianyuAccountId,
      orderStatus: orderQuery.orderStatus,
      pageNum: orderQuery.pageNum,
      pageSize: orderQuery.pageSize
    })
    if (res.code === 0 || res.code === 200) {
      orderRows.value = res.data?.records || []
      orderTotal.value = res.data?.total || 0
      selectedOrderIds.value = new Set()
    } else {
      throw new Error(res.msg || '查询订单失败')
    }
  } catch (error: any) {
    console.error('查询订单失败:', error)
    if (!error.messageShown) showError(error.message || '查询订单失败')
    orderRows.value = []
    orderTotal.value = 0
  } finally {
    orderLoading.value = false
  }
}

const handleOrderPageChange = (page: number) => {
  orderQuery.pageNum = page
  loadBusinessOrders()
}

const handleOrderStatusChange = () => {
  orderQuery.pageNum = 1
  loadBusinessOrders()
}

const toggleOrderSelection = (orderId?: string) => {
  if (!orderId) return
  const next = new Set(selectedOrderIds.value)
  next.has(orderId) ? next.delete(orderId) : next.add(orderId)
  selectedOrderIds.value = next
}

const toggleAllOrders = () => {
  const ids = refreshableOrderIds.value
  selectedOrderIds.value = isAllRefreshableOrdersSelected.value ? new Set() : new Set(ids)
}

const openOrderDetail = (order: OrderVO) => {
  detailOrder.value = order
  detailVisible.value = true
}

const refreshSelectedOrders = async () => {
  if (!queryParams.xianyuAccountId) {
    showError('请先选择账号')
    return
  }
  const orderIds = Array.from(selectedOrderIds.value)
  if (orderIds.length === 0) {
    showError('请选择要刷新的订单')
    return
  }
  orderLoading.value = true
  try {
    const res = await batchRefreshOrders({
      xianyuAccountId: queryParams.xianyuAccountId,
      orderIds,
      headless: true
    })
    if (res.code === 0 || res.code === 200) {
      const data = res.data
      showSuccess(`批量刷新完成，成功 ${data?.successCount || 0} 条，失败 ${data?.failCount || 0} 条`)
      await loadBusinessOrders()
    } else {
      throw new Error(res.msg || '批量刷新失败')
    }
  } catch (error: any) {
    console.error('批量刷新失败:', error)
    if (!error.messageShown) showError(error.message || '批量刷新失败')
  } finally {
    orderLoading.value = false
  }
}

const handleSyncSoldOrders = async () => {
  if (!queryParams.xianyuAccountId) {
    showError('请先选择账号')
    return
  }
  if (!selectedAccountIsFishShop.value) {
    showError('当前账号不是鱼小铺，无法同步卖家订单列表')
    return
  }
  soldOrderSyncing.value = true
  try {
    const res = await syncSoldOrders({ xianyuAccountId: queryParams.xianyuAccountId })
    if (res.code === 0 || res.code === 200) {
      const data = res.data
      showSuccess(`订单列表同步完成，新增 ${data?.insertedCount || 0} 条，更新 ${data?.updatedCount || 0} 条`)
      orderQuery.pageNum = 1
      await loadBusinessOrders()
    } else {
      throw new Error(res.msg || '同步订单列表失败')
    }
  } catch (error: any) {
    console.error('同步订单列表失败:', error)
    if (!error.messageShown) showError(error.message || '同步订单列表失败')
  } finally {
    soldOrderSyncing.value = false
  }
}

const getOrderStatusText = (status?: number, fallback?: string) => {
  if (fallback) return fallback
  const map: Record<number, string> = { 1: '待付款', 2: '待发货', 3: '已发货', 4: '已完成', 5: '已取消' }
  return status ? map[status] || '未知' : '-'
}

const formatAmount = (value?: string) => value ? `¥${value}` : '-'

const formatOrderTime = (value?: number) => {
  return value ? formatTime(value) : '-'
}

const getReceiverSummary = (order: OrderVO) => {
  const receiver = [order.receiverName, order.receiverPhone].filter(Boolean).join(' ')
  return receiver || order.receiverAddress || order.receiverCity || '-'
}

const filterGoodsId = ref('')
const filterState = ref<number | undefined>(undefined)

const openFilterSheet = () => {
  filterGoodsId.value = queryParams.xyGoodsId || ''
  filterState.value = queryParams.state
  showFilterSheet.value = true
}

const applyFilter = () => {
  queryParams.xyGoodsId = filterGoodsId.value || undefined
  queryParams.state = filterState.value
  queryParams.pageNum = 1
  showFilterSheet.value = false
  loadOrders()
}

const resetFilter = () => {
  filterGoodsId.value = ''
  filterState.value = undefined
  handleReset()
  showFilterSheet.value = false
}

const getPageButtons = () => {
  const buttons: number[] = []
  const maxVisible = 5
  let start = Math.max(1, queryParams.pageNum! - Math.floor(maxVisible / 2))
  const end = Math.min(totalPages.value, start + maxVisible - 1)
  start = Math.max(1, end - maxVisible + 1)
  for (let i = start; i <= end; i++) {
    buttons.push(i)
  }
  return buttons
}

const showConfirmDialog = ref(false)
const confirmTargetOrder = ref<any>(null)

const openConfirmDialog = (order: any) => {
  confirmTargetOrder.value = order
  showConfirmDialog.value = true
}

const executeConfirmShipment = async () => {
  if (confirmTargetOrder.value) {
    await handleConfirmShipment(confirmTargetOrder.value)
  }
  showConfirmDialog.value = false
  confirmTargetOrder.value = null
}
</script>

<template>
  <div class="orders">
    <div class="orders__header">
      <div class="orders__title-row">
        <div class="orders__title-icon">
          <IconClipboard />
        </div>
        <h1 class="orders__title">发货记录</h1>
      </div>
      <div class="orders__actions">
        <div class="orders__select-wrap">
          <select
            v-model="queryParams.xianyuAccountId"
            class="orders__select"
            @change="handleAccountChange(); activeTab === 'orders' && loadBusinessOrders()"
          >
            <option :value="undefined" disabled>选择账号</option>
            <option v-for="acc in accounts" :key="acc.id" :value="acc.id">
              {{ getAccountDisplayName(acc) }}
            </option>
          </select>
          <span class="orders__select-icon">
            <IconChevronDown />
          </span>
        </div>
        <button
          class="btn btn--secondary"
          :class="{ 'btn--loading': activeTab === 'delivery' ? loading : orderLoading }"
          :disabled="activeTab === 'delivery' ? loading : orderLoading"
          @click="activeTab === 'delivery' ? loadOrders() : loadBusinessOrders()"
        >
          <IconRefresh />
          <span class="mobile-hidden">刷新</span>
        </button>
        <button
          v-if="activeTab === 'delivery'"
          class="btn btn--secondary"
          :disabled="orderList.length === 0"
          @click="exportOrdersCsv"
        >
          <span>导出CSV</span>
        </button>
        <button v-if="isMobile && activeTab === 'delivery'" class="btn btn--secondary" @click="openFilterSheet">
          <IconFilter />
          <span>筛选</span>
        </button>
      </div>
    </div>

    <div class="orders__tabs">
      <button
        class="orders__tab"
        :class="{ 'orders__tab--active': activeTab === 'delivery' }"
        @click="switchTab('delivery')"
      >
        发货记录
      </button>
      <button
        class="orders__tab"
        :class="{ 'orders__tab--active': activeTab === 'orders' }"
        @click="switchTab('orders')"
      >
        订单列表
      </button>
    </div>

    <div v-if="!isMobile && activeTab === 'delivery'" class="orders__filter-bar">
      <div class="orders__input-wrap">
        <input
          v-model="queryParams.xyGoodsId"
          class="orders__input"
          placeholder="商品ID"
          @keyup.enter="loadOrders"
        />
      </div>

      <div class="orders__select-wrap">
        <select v-model="queryParams.state" class="orders__select" @change="loadOrders">
          <option :value="undefined">全部发货状态</option>
          <option :value="0">待发货</option>
          <option :value="1">已发货</option>
          <option :value="-1">失败</option>
        </select>
        <span class="orders__select-icon">
          <IconChevronDown />
        </span>
      </div>

      <button class="btn btn--primary" @click="loadOrders">
        <IconSearch />
        <span>查询</span>
      </button>

      <button class="btn btn--ghost" @click="handleReset">
        重置
      </button>

      <span v-if="total > 0" class="orders__count">
        共 {{ total }} 条
      </span>
    </div>

    <div v-if="activeTab === 'delivery'" class="orders__content">
      <div class="orders__toolbar">
        <span class="orders__list-title">发货列表</span>
        <span v-if="orderList.length > 0" class="orders__count">
          {{ (queryParams.pageNum! - 1) * queryParams.pageSize! + 1 }}-{{ Math.min(queryParams.pageNum! * queryParams.pageSize!, total) }} / {{ total }}
        </span>
      </div>

      <div class="orders__table-wrap">
        <OrderTable
          :order-list="orderList"
          :loading="loading"
          @copy-sid="copySId"
          @confirm-shipment="openConfirmDialog"
        />
      </div>

      <div v-if="totalPages > 1" class="orders__pagination">
        <button
          class="orders__page-btn"
          :class="{ 'orders__page-btn--disabled': queryParams.pageNum! <= 1 }"
          @click="handlePageChange(queryParams.pageNum! - 1)"
        >
          <IconChevronLeft />
        </button>

        <template v-for="page in getPageButtons()" :key="page">
          <button
            class="orders__page-btn"
            :class="{ 'orders__page-btn--active': page === queryParams.pageNum }"
            @click="handlePageChange(page)"
          >
            {{ page }}
          </button>
        </template>

        <button
          class="orders__page-btn"
          :class="{ 'orders__page-btn--disabled': queryParams.pageNum! >= totalPages }"
          @click="handlePageChange(queryParams.pageNum! + 1)"
        >
          <IconChevronRight />
        </button>

        <span class="orders__page-info">{{ queryParams.pageNum }} / {{ totalPages }}</span>
      </div>
    </div>

    <div v-if="activeTab === 'orders'" class="orders__content">
      <div class="orders__toolbar orders__toolbar--wrap">
        <div class="orders__order-filter">
          <span class="orders__list-title">订单列表</span>
          <select v-model="orderQuery.orderStatus" class="orders__select" @change="handleOrderStatusChange">
            <option :value="undefined">全部订单状态</option>
            <option :value="1">待付款</option>
            <option :value="2">待发货</option>
            <option :value="3">已发货</option>
            <option :value="4">已完成</option>
            <option :value="5">已取消</option>
          </select>
        </div>
        <div class="orders__batch-actions">
          <span v-if="orderTotal > 0" class="orders__count">共 {{ orderTotal }} 条</span>
          <button
            v-if="selectedAccountIsFishShop"
            class="btn btn--secondary btn--sm"
            :class="{ 'btn--loading': soldOrderSyncing }"
            :disabled="soldOrderSyncDisabled"
            @click="handleSyncSoldOrders"
          >
            <IconSync />
            同步订单列表
          </button>
          <button class="btn btn--secondary btn--sm" :disabled="refreshableOrderIds.length === 0" @click="toggleAllOrders">
            {{ refreshableOrderIds.length === 0 ? '无可刷新订单' : isAllRefreshableOrdersSelected ? '取消全选' : '全选本页' }}
          </button>
          <button
            class="btn btn--primary btn--sm"
            :class="{ 'btn--loading': orderLoading }"
            :disabled="batchRefreshDisabled"
            @click="refreshSelectedOrders"
          >
            批量刷新
          </button>
        </div>
      </div>

      <div v-if="isMobile" class="orders__business-cards" :class="{ 'card-list--loading': orderLoading }">
        <div v-for="order in orderRows" :key="order.id" class="orders__business-card">
          <div class="orders__business-card-head">
            <label class="orders__check-row">
              <input
                type="checkbox"
                :checked="!!order.orderId && selectedOrderIds.has(order.orderId)"
                :disabled="!order.orderId"
                @change="toggleOrderSelection(order.orderId)"
              />
              <span>{{ order.orderId || '-' }}</span>
            </label>
            <span class="status-tag">{{ getOrderStatusText(order.orderStatus, order.orderStatusText) }}</span>
          </div>
          <div class="orders__business-card-title">{{ order.goodsTitle || '-' }}</div>
          <div class="orders__business-card-row">买家：{{ order.buyerUserName || '-' }}</div>
          <div class="orders__business-card-row">金额：{{ formatAmount(order.orderAmountText) }}</div>
          <div class="orders__business-card-row">收货：{{ getReceiverSummary(order) }}</div>
          <div class="orders__business-card-row orders__business-card-row--address">地址：{{ order.receiverAddress || '-' }}</div>
          <div class="orders__business-card-row">下单：{{ formatOrderTime(order.createTime) }}</div>
          <div class="orders__business-card-row">付款：{{ formatOrderTime(order.payTime) }}</div>
          <div class="orders__business-card-row">发货：{{ formatOrderTime(order.deliveryTime) }}</div>
          <button class="orders__detail-btn" type="button" @click="openOrderDetail(order)">查看详情</button>
        </div>
        <div v-if="!orderLoading && orderRows.length === 0" class="empty-state">
          <div class="empty-state__icon"><IconClipboard /></div>
          <p class="empty-state__text">暂无订单</p>
        </div>
      </div>

      <div v-else class="orders__table-wrap">
        <table v-if="orderRows.length > 0" class="table">
          <thead class="table__head">
            <tr>
              <th class="table__th">选择</th>
              <th class="table__th">订单ID</th>
              <th class="table__th">商品</th>
              <th class="table__th table__th--center">买家</th>
              <th class="table__th table__th--center">金额</th>
              <th class="table__th">收货信息</th>
              <th class="table__th">收货地址</th>
              <th class="table__th table__th--center">状态</th>
              <th class="table__th table__th--center">下单时间</th>
              <th class="table__th table__th--actions">操作</th>
            </tr>
          </thead>
          <tbody class="table__body">
            <tr v-for="order in orderRows" :key="order.id" class="table__tr">
              <td class="table__td">
                <input
                  type="checkbox"
                  :checked="!!order.orderId && selectedOrderIds.has(order.orderId)"
                  :disabled="!order.orderId"
                  @change="toggleOrderSelection(order.orderId)"
                />
              </td>
              <td class="table__td"><span class="order-id">{{ order.orderId || '-' }}</span></td>
              <td class="table__td table__td--title">{{ order.goodsTitle || '-' }}</td>
              <td class="table__td table__td--center">{{ order.buyerUserName || '-' }}</td>
              <td class="table__td table__td--center">{{ formatAmount(order.orderAmountText) }}</td>
              <td class="table__td">
                <div class="orders__receiver-cell">
                  <span>{{ order.receiverName || '-' }}</span>
                  <span>{{ order.receiverPhone || '' }}</span>
                </div>
              </td>
              <td class="table__td table__td--address">{{ order.receiverAddress || '-' }}</td>
              <td class="table__td table__td--center">
                <span class="status-tag">{{ getOrderStatusText(order.orderStatus, order.orderStatusText) }}</span>
              </td>
              <td class="table__td table__td--center">{{ formatOrderTime(order.createTime) }}</td>
              <td class="table__td table__td--actions">
                <button class="table__action" type="button" @click="openOrderDetail(order)">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!orderLoading && orderRows.length === 0" class="empty-state">
          <div class="empty-state__icon"><IconClipboard /></div>
          <p class="empty-state__text">暂无订单</p>
        </div>
      </div>

      <div v-if="orderTotalPages > 1" class="orders__pagination">
        <button
          class="orders__page-btn"
          :class="{ 'orders__page-btn--disabled': orderQuery.pageNum <= 1 }"
          @click="handleOrderPageChange(orderQuery.pageNum - 1)"
        >
          <IconChevronLeft />
        </button>
        <span class="orders__page-info">{{ orderQuery.pageNum }} / {{ orderTotalPages }}</span>
        <button
          class="orders__page-btn"
          :class="{ 'orders__page-btn--disabled': orderQuery.pageNum >= orderTotalPages }"
          @click="handleOrderPageChange(orderQuery.pageNum + 1)"
        >
          <IconChevronRight />
        </button>
      </div>
    </div>

    <Transition name="overlay-fade">
      <div v-if="showFilterSheet" class="orders__filter-overlay" @click="showFilterSheet = false">
        <div
          class="orders__filter-sheet"
          :class="{ 'orders__filter-sheet--open': showFilterSheet }"
          @click.stop
        >
          <div class="orders__filter-sheet-handle"></div>
          <h3 class="orders__filter-sheet-title">筛选条件</h3>

          <div class="orders__filter-group">
            <label class="orders__filter-label">商品ID</label>
            <input
              v-model="filterGoodsId"
              class="orders__filter-input"
              placeholder="请输入商品ID"
            />
          </div>

          <div class="orders__filter-group">
            <label class="orders__filter-label">发货状态</label>
            <select v-model="filterState" class="orders__filter-input">
              <option :value="undefined">全部状态</option>
              <option :value="0">待发货</option>
              <option :value="1">已发货</option>
              <option :value="-1">失败</option>
            </select>
          </div>

          <div class="orders__filter-actions">
            <button class="btn btn--secondary" @click="resetFilter">重置</button>
            <button class="btn btn--primary" @click="applyFilter">查询</button>
          </div>
        </div>
      </div>
    </Transition>

    <OrderDetailDrawer
      v-model:visible="detailVisible"
      :order="detailOrder"
      @refresh="loadBusinessOrders"
    />

    <Transition name="overlay-fade">
      <div v-if="showConfirmDialog" class="orders__dialog-overlay" @click.self="showConfirmDialog = false">
        <div class="orders__dialog">
          <div class="orders__dialog-header">
            <h3 class="orders__dialog-title">确认发货</h3>
          </div>
          <div class="orders__dialog-body">
            <p class="orders__dialog-text">
              确认订单「{{ confirmTargetOrder?.orderId }}」已发货吗？
            </p>
          </div>
          <div class="orders__dialog-footer">
            <button
              class="orders__dialog-btn orders__dialog-btn--cancel"
              @click="showConfirmDialog = false"
            >
              取消
            </button>
            <button
              class="orders__dialog-btn orders__dialog-btn--confirm"
              @click="executeConfirmShipment"
            >
              确认
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

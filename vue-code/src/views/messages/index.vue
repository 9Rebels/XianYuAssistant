<script setup lang="ts">
import { onMounted, onBeforeUnmount, inject, defineComponent, h } from 'vue'
import { useMessageManager } from './useMessageManager'
import './messages.css'
import './messages-mobile.css'
import '@/styles/header-selectors.css'

import IconMessage from '@/components/icons/IconMessage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import IconSend from '@/components/icons/IconSend.vue'

import MessageList from './components/MessageList.vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'
import { getAccountDisplayName } from '@/utils/accountDisplay'

const {
  loading,
  accounts,
  selectedAccountId,
  messageList,
  currentPage,
  total,
  totalPages,
  filterCurrentAccount,
  quickReplyVisible,
  quickReplyMessage,
  quickReplySending,
  currentReplyMessage,
  quickReplyImage,
  isMobile,
  autoRefreshPresetOptions,
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
  cleanupAutoRefresh
} = useMessageManager()

// 分页按钮
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

const handleManualRefresh = () => {
  loadMessages()
}

const handleFilterToggle = () => {
  filterCurrentAccount.value = !filterCurrentAccount.value
  currentPage.value = 1
  loadMessages()
}

const toggleAutoRefresh = () => {
  setAutoRefreshEnabled(!autoRefreshEnabled.value)
}

const handleCustomRefreshSecondsChange = () => {
  updateAutoRefreshInterval()
}

// 注入导航栏内容
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          onChange: (e: Event) => {
            const val = (e.target as HTMLSelectElement).value
            selectedAccountId.value = val ? parseInt(val) : null
            handleAccountChange()
          }
        }, [
          h('option', { value: '', disabled: true, selected: !selectedAccountId.value }, '账号'),
          ...accounts.value.map(acc =>
            h('option', {
              value: acc.id.toString(),
              selected: selectedAccountId.value === acc.id
            }, getAccountDisplayName(acc))
          )
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('button', {
        class: ['header-toggle-btn', { 'header-toggle-btn--on': filterCurrentAccount.value }],
        title: '隐藏我发送的',
        onClick: handleFilterToggle
      }, [
        h('span', { class: 'header-toggle-track' }, [
          h('span', { class: 'header-toggle-thumb' })
        ])
      ]),
      h('button', {
        class: ['header-refresh-btn', { 'header-refresh-btn--loading': loading.value }],
        disabled: loading.value,
        title: '刷新',
        onClick: handleManualRefresh
      }, [
        h(IconRefresh, { class: 'header-refresh-icon' })
      ]),
      h('button', {
        class: ['header-auto-refresh-btn', { 'header-auto-refresh-btn--on': autoRefreshEnabled.value }],
        title: autoRefreshEnabled.value ? `自动刷新：每 ${selectedAutoRefreshSeconds.value} 秒` : '开启自动刷新',
        onClick: toggleAutoRefresh
      }, '自动')
    ])
  }
})

onMounted(async () => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  if (setHeaderContent) {
    setHeaderContent(HeaderSelectors)
  }
  await loadAccounts()
  // 账号加载完后重新注入，确保选项渲染
  if (setHeaderContent) {
    setHeaderContent(HeaderSelectors)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkScreenSize)
  cleanupAutoRefresh()
})

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}
</script>

<template>
  <div class="messages">
    <!-- ========== Desktop Layout ========== -->
    <template v-if="!isMobile">
      <!-- Header -->
      <div class="messages__header">
        <div class="messages__title-row">
          <div class="messages__title-icon">
            <IconMessage />
          </div>
          <h1 class="messages__title">消息管理</h1>
        </div>
        <div class="messages__actions">
          <div class="messages__select-wrap">
            <select
              v-model="selectedAccountId"
              class="messages__select"
              @change="handleAccountChange"
            >
              <option :value="null" disabled>选择账号</option>
              <option v-for="acc in accounts" :key="acc.id" :value="acc.id">
                {{ getAccountDisplayName(acc) }}
              </option>
            </select>
            <span class="messages__select-icon">
              <IconChevronDown />
            </span>
          </div>

          <button
            class="btn btn--secondary"
            :class="{ 'btn--loading': loading }"
            :disabled="loading"
            @click="handleManualRefresh"
          >
            <IconRefresh />
            <span>刷新</span>
          </button>

          <div class="messages__auto-refresh">
            <span class="messages__toggle-label">自动刷新</span>
            <button
              class="messages__toggle"
              :class="{ 'messages__toggle--on': autoRefreshEnabled }"
              :title="autoRefreshEnabled ? `每 ${selectedAutoRefreshSeconds} 秒自动刷新` : '开启自动刷新'"
              @click="toggleAutoRefresh"
            >
              <span class="messages__toggle-track">
                <span class="messages__toggle-thumb"></span>
              </span>
            </button>
            <select
              v-if="autoRefreshEnabled"
              v-model="refreshIntervalMode"
              class="messages__interval-select"
              @change="updateAutoRefreshInterval"
            >
              <option
                v-for="seconds in autoRefreshPresetOptions"
                :key="seconds"
                :value="seconds.toString()"
              >
                {{ seconds }}秒
              </option>
              <option value="custom">自定义</option>
            </select>
            <input
              v-if="autoRefreshEnabled && refreshIntervalMode === 'custom'"
              v-model.number="customRefreshSeconds"
              class="messages__custom-interval"
              type="number"
              min="3"
              max="300"
              step="1"
              title="自定义刷新秒数，范围 3-300"
              @change="handleCustomRefreshSecondsChange"
            />
            <span v-if="autoRefreshEnabled" class="messages__auto-status">
              每 {{ selectedAutoRefreshSeconds }} 秒
            </span>
          </div>

          <div class="messages__toggle-wrap">
            <span class="messages__toggle-label">隐藏我发送的</span>
            <button
              class="messages__toggle"
              :class="{ 'messages__toggle--on': filterCurrentAccount }"
              @click="handleFilterToggle"
            >
              <span class="messages__toggle-track">
                <span class="messages__toggle-thumb"></span>
              </span>
            </button>
          </div>
        </div>
      </div>

      <!-- Content -->
      <div class="messages__content">
        <div class="messages__main messages__main--full">
          <div class="messages__main-header">
            <span class="messages__main-title">消息列表</span>
            <span v-if="total > 0" class="messages__main-count">
              共 {{ total }} 条
            </span>
          </div>

          <div class="messages__table-wrap">
            <MessageList
              :message-list="messageList"
              :loading="loading"
              :xianyu-account-id="selectedAccountId || undefined"
              :current-account-unb="getCurrentAccountUnb"
              @reply="openQuickReply"
            />
          </div>

          <!-- Pagination -->
          <div v-if="totalPages > 1" class="messages__pagination">
            <button
              class="messages__page-btn"
              :class="{ 'messages__page-btn--disabled': currentPage <= 1 }"
              @click="handlePageChange(currentPage - 1)"
            >
              <IconChevronLeft />
            </button>
            <template v-for="page in getPageButtons()" :key="page">
              <button
                class="messages__page-btn"
                :class="{ 'messages__page-btn--active': page === currentPage }"
                @click="handlePageChange(page)"
              >
                {{ page }}
              </button>
            </template>
            <button
              class="messages__page-btn"
              :class="{ 'messages__page-btn--disabled': currentPage >= totalPages }"
              @click="handlePageChange(currentPage + 1)"
            >
              <IconChevronRight />
            </button>
            <span class="messages__page-info">{{ currentPage }} / {{ totalPages }}</span>
          </div>
        </div>
      </div>
    </template>

    <!-- ========== Mobile Layout ========== -->
    <template v-else>
      <div class="mobile-messages">
        <div class="mobile-messages__toolbar">
          <div class="mobile-messages__summary">
            <span class="mobile-messages__title">消息列表</span>
            <span v-if="total > 0" class="mobile-messages__count">共 {{ total }} 条</span>
          </div>
          <div class="mobile-messages__actions">
            <button
              class="mobile-messages__refresh"
              :class="{ 'mobile-messages__refresh--loading': loading }"
              :disabled="loading"
              @click="handleManualRefresh"
            >
              <IconRefresh />
            </button>
            <button
              class="mobile-messages__auto"
              :class="{ 'mobile-messages__auto--on': autoRefreshEnabled }"
              @click="toggleAutoRefresh"
            >
              自动
            </button>
          </div>
        </div>

        <div v-if="autoRefreshEnabled" class="mobile-messages__interval-row">
          <select
            v-model="refreshIntervalMode"
            class="messages__interval-select"
            @change="updateAutoRefreshInterval"
          >
            <option
              v-for="seconds in autoRefreshPresetOptions"
              :key="seconds"
              :value="seconds.toString()"
            >
              {{ seconds }}秒
            </option>
            <option value="custom">自定义</option>
          </select>
          <input
            v-if="refreshIntervalMode === 'custom'"
            v-model.number="customRefreshSeconds"
            class="messages__custom-interval"
            type="number"
            min="3"
            max="300"
            step="1"
            @change="handleCustomRefreshSecondsChange"
          />
          <span class="messages__auto-status">每 {{ selectedAutoRefreshSeconds }} 秒刷新</span>
        </div>

        <div class="mobile-messages__body">
          <MessageList
            :message-list="messageList"
            :loading="loading"
            :xianyu-account-id="selectedAccountId || undefined"
            :current-account-unb="getCurrentAccountUnb"
            @reply="openQuickReply"
          />
        </div>

        <!-- Mobile Pagination -->
        <div v-if="totalPages > 1" class="messages__pagination" style="flex-shrink: 0;">
          <button
            class="messages__page-btn"
            :class="{ 'messages__page-btn--disabled': currentPage <= 1 }"
            @click="handlePageChange(currentPage - 1)"
          >
            <IconChevronLeft />
          </button>
          <template v-for="page in getPageButtons()" :key="page">
            <button
              class="messages__page-btn"
              :class="{ 'messages__page-btn--active': page === currentPage }"
              @click="handlePageChange(page)"
            >
              {{ page }}
            </button>
          </template>
          <button
            class="messages__page-btn"
            :class="{ 'messages__page-btn--disabled': currentPage >= totalPages }"
            @click="handlePageChange(currentPage + 1)"
          >
            <IconChevronRight />
          </button>
          <span class="messages__page-info">{{ currentPage }} / {{ totalPages }}</span>
        </div>
      </div>
    </template>

    <!-- ========== Quick Reply Dialog ========== -->
    <Transition name="overlay-fade">
      <div v-if="quickReplyVisible" class="messages__dialog-overlay" @click.self="quickReplyVisible = false">
        <div class="messages__dialog">
          <div class="messages__dialog-header">
            <h3 class="messages__dialog-title">快速回复</h3>
          </div>
          <div class="messages__dialog-body">
            <div class="messages__reply-info" v-if="currentReplyMessage">
              <div class="messages__reply-row">
                <span class="messages__reply-label">回复给：</span>
                <span class="messages__reply-value">{{ currentReplyMessage.senderUserName }}</span>
              </div>
              <div class="messages__reply-row">
                <span class="messages__reply-label">原消息：</span>
                <span class="messages__reply-value">{{ currentReplyMessage.msgContent }}</span>
              </div>
            </div>
            <textarea
              v-model="quickReplyMessage"
              class="messages__reply-textarea"
              placeholder="请输入回复内容..."
              maxlength="500"
            ></textarea>
            <div class="messages__reply-image">
              <MultiImageUploader
                :account-id="selectedAccountId || 0"
                v-model="quickReplyImage"
              />
            </div>
          </div>
          <div class="messages__dialog-footer">
            <button class="btn btn--secondary" @click="quickReplyVisible = false">取消</button>
            <button
              class="btn btn--primary"
              :class="{ 'btn--loading': quickReplySending }"
              :disabled="quickReplySending"
              @click="handleQuickReply"
            >
              <IconSend />
              <span>发送</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

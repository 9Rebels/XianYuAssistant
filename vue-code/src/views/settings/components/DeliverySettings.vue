<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAccountList } from '@/api/account'
import { getSetting, saveSetting } from '@/api/setting'
import { uploadImage } from '@/api/image'
import { deleteMedia, getMediaList, type MediaItem } from '@/api/media'
import type { Account } from '@/types'
import { getAccountDisplayName } from '@/utils/accountDisplay'
import './DeliverySettings.css'

type MessageType = 'text' | 'image'
type MultiQuantitySendMode = 'merge' | 'separate'
type StatusKey = 'shipped' | 'received' | 'refunding'

interface StatusConfig {
  key: StatusKey
  title: string
  enabled: boolean
  messageType: MessageType
  content: string
}

const KAMI_SINGLE_KEY = 'delivery_kami_single_send_enabled'
const MULTI_QUANTITY_SEND_MODE_KEY = 'delivery_multi_quantity_send_mode'
const MAX_TEXT_LENGTH = 200
const MEDIA_PAGE_SIZE = 24

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const mediaItems = ref<MediaItem[]>([])
const mediaKeyword = ref('')
const mediaLoading = ref(false)
const mediaUploading = ref(false)
const mediaPageNum = ref(1)
const mediaTotal = ref(0)
const saving = ref(false)
const kamiSingleSendEnabled = ref(false)
const multiQuantitySendMode = ref<MultiQuantitySendMode>('merge')

const statusConfigs = ref<StatusConfig[]>([
  { key: 'shipped', title: '订单发货后发给买家', enabled: false, messageType: 'text', content: '' },
  { key: 'received', title: '订单收货后发给买家', enabled: false, messageType: 'text', content: '' },
  { key: 'refunding', title: '订单退款中发给买家', enabled: false, messageType: 'text', content: '' }
])

const selectedImagesByKey = computed(() => {
  const map = new Map<StatusKey, string[]>()
  statusConfigs.value.forEach(config => {
    const images = config.content.split(',').map(item => item.trim()).filter(Boolean)
    map.set(config.key, images)
  })
  return map
})

const selectedImageSet = computed(() => {
  const urls = new Set<string>()
  statusConfigs.value.forEach(config => {
    config.content.split(',').map(item => item.trim()).filter(Boolean).forEach(url => urls.add(url))
  })
  return urls
})

onMounted(async () => {
  await Promise.all([loadAccounts(), loadDeliveryConfig()])
})

watch(selectedAccountId, async accountId => {
  if (accountId) {
    mediaPageNum.value = 1
    await loadMediaList()
  } else {
    mediaItems.value = []
    mediaTotal.value = 0
  }
})

async function loadAccounts() {
  try {
    const res = await getAccountList()
    if (res.code === 200 && res.data?.accounts) {
      accounts.value = res.data.accounts
      selectedAccountId.value = res.data.accounts[0]?.id || null
    }
  } catch (error) {
    console.error('加载账号失败:', error)
  }
}

async function loadDeliveryConfig() {
  const keys: string[] = [
    KAMI_SINGLE_KEY,
    MULTI_QUANTITY_SEND_MODE_KEY,
    ...statusConfigs.value.flatMap(config => [
      enabledKey(config.key),
      typeKey(config.key),
      contentKey(config.key)
    ])
  ]

  try {
    const results = await Promise.all(keys.map(settingKey => getSetting({ settingKey })))
    const valueMap = new Map<string, string>()
    results.forEach((res, index) => {
      const settingKey = keys[index]
      if (settingKey && res.code === 200 && res.data) {
        valueMap.set(settingKey, res.data.settingValue || '')
      }
    })

    kamiSingleSendEnabled.value = isEnabled(valueMap.get(KAMI_SINGLE_KEY))
    multiQuantitySendMode.value = normalizeMultiQuantitySendMode(valueMap.get(MULTI_QUANTITY_SEND_MODE_KEY))
    statusConfigs.value = statusConfigs.value.map(config => ({
      ...config,
      enabled: isEnabled(valueMap.get(enabledKey(config.key))),
      messageType: normalizeType(valueMap.get(typeKey(config.key))),
      content: valueMap.get(contentKey(config.key)) || ''
    }))
  } catch (error) {
    console.error('加载发货配置失败:', error)
    ElMessage.error('加载发货配置失败')
  }
}

async function loadMediaList() {
  if (!selectedAccountId.value) return
  mediaLoading.value = true
  try {
    const res = await getMediaList({
      xianyuAccountId: selectedAccountId.value,
      keyword: mediaKeyword.value.trim(),
      pageNum: mediaPageNum.value,
      pageSize: MEDIA_PAGE_SIZE
    })
    if (res.code === 200 && res.data) {
      mediaItems.value = res.data.list || []
      mediaTotal.value = res.data.total || 0
    }
  } catch (error) {
    console.error('加载媒体库失败:', error)
    ElMessage.error('加载媒体库失败')
  } finally {
    mediaLoading.value = false
  }
}

async function handleUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !selectedAccountId.value) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    input.value = ''
    return
  }

  mediaUploading.value = true
  try {
    const res = await uploadImage(selectedAccountId.value, file)
    if (res.code === 200) {
      ElMessage.success('图片上传成功')
      mediaPageNum.value = 1
      await loadMediaList()
    } else {
      ElMessage.error(res.msg || '图片上传失败')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '图片上传失败')
  } finally {
    mediaUploading.value = false
    input.value = ''
  }
}

async function handleDeleteMedia(item: MediaItem) {
  try {
    const res = await deleteMedia({ id: item.id })
    if (res.code === 200) {
      removeImageFromAllConfigs(item.mediaUrl)
      ElMessage.success('素材已删除')
      await loadMediaList()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '删除素材失败')
  }
}

function toggleImage(config: StatusConfig, imageUrl: string) {
  const list = getImages(config)
  const exists = list.includes(imageUrl)
  config.content = exists
    ? list.filter(url => url !== imageUrl).join(',')
    : [...list, imageUrl].join(',')
}

function removeImage(config: StatusConfig, imageUrl: string) {
  config.content = getImages(config).filter(url => url !== imageUrl).join(',')
}

function removeImageFromAllConfigs(imageUrl: string) {
  statusConfigs.value.forEach(config => removeImage(config, imageUrl))
}

async function handleSaveDeliveryConfig() {
  if (!validateConfig()) return
  saving.value = true
  try {
    const tasks = [
      saveSetting({
        settingKey: KAMI_SINGLE_KEY,
        settingValue: kamiSingleSendEnabled.value ? '1' : '0',
        settingDesc: '卡密发货后是否单独发送原始卡密'
      }),
      saveSetting({
        settingKey: MULTI_QUANTITY_SEND_MODE_KEY,
        settingValue: multiQuantitySendMode.value,
        settingDesc: '多件订单自动发货发送方式：merge=合并一条，separate=逐条发送'
      }),
      ...statusConfigs.value.flatMap(config => [
        saveSetting({
          settingKey: enabledKey(config.key),
          settingValue: config.enabled ? '1' : '0',
          settingDesc: `${config.title}开关`
        }),
        saveSetting({
          settingKey: typeKey(config.key),
          settingValue: config.messageType,
          settingDesc: `${config.title}消息类型`
        }),
        saveSetting({
          settingKey: contentKey(config.key),
          settingValue: config.content.trim(),
          settingDesc: `${config.title}消息内容`
        })
      ])
    ]
    await Promise.all(tasks)
    ElMessage.success('发货配置已保存')
  } catch (error: any) {
    ElMessage.error(error.message || '保存发货配置失败')
  } finally {
    saving.value = false
  }
}

function validateConfig() {
  for (const config of statusConfigs.value) {
    if (config.messageType === 'text' && config.content.length > MAX_TEXT_LENGTH) {
      ElMessage.warning(`${config.title}文本不能超过${MAX_TEXT_LENGTH}字`)
      return false
    }
    if (config.enabled && !config.content.trim()) {
      ElMessage.warning(`请配置${config.title}的消息内容`)
      return false
    }
  }
  return true
}

function getImages(config: StatusConfig) {
  return config.content.split(',').map(item => item.trim()).filter(Boolean)
}

function isImageSelected(config: StatusConfig, imageUrl: string) {
  return getImages(config).includes(imageUrl)
}

function isEnabled(value?: string) {
  return value === '1' || value === 'true'
}

function normalizeType(value?: string): MessageType {
  return value === 'image' ? 'image' : 'text'
}

function normalizeMultiQuantitySendMode(value?: string): MultiQuantitySendMode {
  return value === 'separate' ? 'separate' : 'merge'
}

function enabledKey(key: StatusKey) {
  return `delivery_order_${key}_enabled`
}

function typeKey(key: StatusKey) {
  return `delivery_order_${key}_message_type`
}

function contentKey(key: StatusKey) {
  return `delivery_order_${key}_content`
}
</script>

<template>
  <div class="delivery-settings">
    <div class="delivery-settings__header">
      <div>
        <div class="delivery-settings__title">发货配置</div>
        <p class="delivery-settings__desc">配置多件订单发货、卡密单发和订单状态后续消息，保存后立即生效。</p>
      </div>
      <button class="delivery-settings__btn delivery-settings__btn--primary" :disabled="saving" @click="handleSaveDeliveryConfig">
        {{ saving ? '保存中...' : '保存配置' }}
      </button>
    </div>

    <section class="delivery-settings__section">
      <div class="delivery-settings__section-title">多件订单发货方式</div>
      <p class="delivery-settings__desc">买家一次拍下多件时，文本、卡密、API 发货都会按购买数量生成对应份数。</p>
      <div class="delivery-settings__option-grid">
        <button
          type="button"
          class="delivery-settings__option-card"
          :class="{ 'delivery-settings__option-card--active': multiQuantitySendMode === 'merge' }"
          @click="multiQuantitySendMode = 'merge'"
        >
          <strong>合并一条发送</strong>
          <span>买 3 件时发送 1 条消息，内容包含 3 份发货内容。</span>
        </button>
        <button
          type="button"
          class="delivery-settings__option-card"
          :class="{ 'delivery-settings__option-card--active': multiQuantitySendMode === 'separate' }"
          @click="multiQuantitySendMode = 'separate'"
        >
          <strong>逐条发送</strong>
          <span>买 3 件时发送 3 条消息，每条对应 1 份发货内容。</span>
        </button>
      </div>
    </section>

    <section class="delivery-settings__section">
      <label class="delivery-settings__switch-row">
        <span>
          <strong>卡卷单发</strong>
          <small>卡密发货模板消息发送后，再单独发送原始卡密一次。</small>
        </span>
        <span class="delivery-settings__switch">
          <input v-model="kamiSingleSendEnabled" type="checkbox" />
          <i></i>
        </span>
      </label>
    </section>

    <section class="delivery-settings__section">
      <div class="delivery-settings__section-head">
        <div>
          <div class="delivery-settings__section-title">媒体库</div>
          <p class="delivery-settings__desc">图片消息从媒体库选择；上传后会先进入闲鱼 CDN，再保存到媒体库。</p>
        </div>
        <div class="delivery-settings__media-tools">
          <select v-model.number="selectedAccountId" class="delivery-settings__select">
            <option v-for="account in accounts" :key="account.id" :value="account.id">
              {{ getAccountDisplayName(account) }}
            </option>
          </select>
          <input
            v-model="mediaKeyword"
            class="delivery-settings__input"
            placeholder="搜索素材"
            @keyup.enter="loadMediaList"
          />
          <button class="delivery-settings__btn" :disabled="mediaLoading" @click="loadMediaList">刷新</button>
          <label class="delivery-settings__btn delivery-settings__btn--primary" :class="{ 'delivery-settings__btn--disabled': !selectedAccountId || mediaUploading }">
            {{ mediaUploading ? '上传中...' : '上传图片' }}
            <input type="file" accept="image/*" :disabled="!selectedAccountId || mediaUploading" @change="handleUpload" />
          </label>
        </div>
      </div>

      <div v-if="mediaItems.length" class="delivery-settings__media-grid">
        <div
          v-for="item in mediaItems"
          :key="item.id"
          class="delivery-settings__media-card"
          :class="{ 'delivery-settings__media-card--used': selectedImageSet.has(item.mediaUrl) }"
        >
          <img :src="item.mediaUrl" alt="素材图片" />
          <div class="delivery-settings__media-meta">
            <span>{{ item.fileName || '图片素材' }}</span>
            <button type="button" @click="handleDeleteMedia(item)">删除</button>
          </div>
        </div>
      </div>
      <div v-else class="delivery-settings__empty">
        {{ mediaLoading ? '加载中...' : '暂无素材，先上传图片' }}
      </div>
    </section>

    <section class="delivery-settings__section">
      <div class="delivery-settings__section-title">订单状态消息</div>
      <div class="delivery-settings__status-list">
        <div v-for="config in statusConfigs" :key="config.key" class="delivery-settings__status-card">
          <div class="delivery-settings__status-head">
            <label class="delivery-settings__switch-row delivery-settings__switch-row--compact">
              <span>
                <strong>{{ config.title }}</strong>
                <small>{{ config.enabled ? '启用' : '关闭' }}</small>
              </span>
              <span class="delivery-settings__switch">
                <input v-model="config.enabled" type="checkbox" />
                <i></i>
              </span>
            </label>
          </div>

          <div class="delivery-settings__field">
            <label>消息类型</label>
            <div class="delivery-settings__segmented">
              <button
                type="button"
                :class="{ 'delivery-settings__segmented-btn--active': config.messageType === 'text' }"
                class="delivery-settings__segmented-btn"
                @click="config.messageType = 'text'"
              >
                文本消息
              </button>
              <button
                type="button"
                :class="{ 'delivery-settings__segmented-btn--active': config.messageType === 'image' }"
                class="delivery-settings__segmented-btn"
                @click="config.messageType = 'image'"
              >
                图片消息
              </button>
            </div>
          </div>

          <div v-if="config.messageType === 'text'" class="delivery-settings__field">
            <label>消息内容</label>
            <textarea
              v-model="config.content"
              class="delivery-settings__textarea"
              maxlength="200"
              rows="4"
              placeholder="输入消息内容"
            ></textarea>
            <div class="delivery-settings__counter">{{ config.content.length }}/200</div>
          </div>

          <div v-else class="delivery-settings__field">
            <label>选择图片</label>
            <div v-if="selectedImagesByKey.get(config.key)?.length" class="delivery-settings__selected-images">
              <div v-for="url in selectedImagesByKey.get(config.key)" :key="url" class="delivery-settings__selected-image">
                <img :src="url" alt="已选图片" />
                <button type="button" @click="removeImage(config, url)">移除</button>
              </div>
            </div>
            <div class="delivery-settings__media-picker">
              <button
                v-for="item in mediaItems"
                :key="item.id"
                type="button"
                class="delivery-settings__pick-card"
                :class="{ 'delivery-settings__pick-card--active': isImageSelected(config, item.mediaUrl) }"
                @click="toggleImage(config, item.mediaUrl)"
              >
                <img :src="item.mediaUrl" alt="媒体库图片" />
              </button>
            </div>
            <div v-if="!mediaItems.length" class="delivery-settings__hint">先在上方媒体库上传图片，再选择。</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

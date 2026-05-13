<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAllSettings, saveSetting } from '@/api/setting'
import { getNotificationLogs, testNotification, type NotificationLog } from '@/api/notification'
import { formatTime as formatDateTime, showError, showSuccess } from '@/utils'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconSend from '@/components/icons/IconSend.vue'
import './notifications.css'

type NotifyChannel =
  | 'generic'
  | 'feishu'
  | 'dingtalk'
  | 'dingtalk_signed'
  | 'wecom'
  | 'wecom_app'
  | 'bark'
  | 'pushplus'
  | 'wxpusher'
  | 'pushdeer'
  | 'serverchan'
  | 'telegram'
  | 'gotify'
  | 'email'

interface ChannelField {
  key: string
  label: string
  placeholder?: string
  type?: 'text' | 'password' | 'number' | 'textarea'
  hint?: string
}

interface ChannelOption {
  key: NotifyChannel
  name: string
  desc: string
  fields: ChannelField[]
}

const channels: ChannelOption[] = [
  {
    key: 'generic',
    name: '通用 Webhook',
    desc: '向自定义接口发送 JSON',
    fields: [{ key: 'notify_generic_url', label: 'Webhook URL', placeholder: 'https://...' }]
  },
  {
    key: 'feishu',
    name: '飞书机器人',
    desc: '飞书群机器人 Webhook',
    fields: [{ key: 'notify_feishu_url', label: 'Webhook URL', placeholder: 'https://open.feishu.cn/...' }]
  },
  {
    key: 'dingtalk',
    name: '钉钉机器人',
    desc: '填写 access_token 后面的 Token',
    fields: [{ key: 'notify_dingtalk_token', label: 'Access Token', type: 'password', placeholder: 'access_token= 后面的 XXX' }]
  },
  {
    key: 'dingtalk_signed',
    name: '钉钉加签',
    desc: '填写 Token 和 Secret 后端自动加签',
    fields: [
      { key: 'notify_dingtalk_token', label: 'Access Token', type: 'password', placeholder: 'access_token= 后面的 XXX' },
      { key: 'notify_dingtalk_secret', label: '加签 Secret', type: 'password', placeholder: 'SEC...' }
    ]
  },
  {
    key: 'wecom',
    name: '企业微信群机器人',
    desc: '企业微信群机器人官方 Webhook',
    fields: [
      { key: 'notify_wecom_key', label: '机器人 Key', type: 'password', placeholder: '企业微信机器人 key' }
    ]
  },
  {
    key: 'wecom_app',
    name: '企业微信应用消息',
    desc: '通过企业微信应用发送给成员，可配置接口代理',
    fields: [
      { key: 'notify_wecom_app_corpid', label: 'Corpid' },
      { key: 'notify_wecom_app_secret', label: 'CorpSecret', type: 'password' },
      { key: 'notify_wecom_app_touser', label: '接收成员', placeholder: '@all 或 user1|user2' },
      { key: 'notify_wecom_app_agentid', label: 'AgentId' },
      {
        key: 'notify_wecom_app_media_id',
        label: 'media_id',
        placeholder: '可选，填写后按 mpnews 消息发送',
        hint: '留空发送文本消息；填写素材 media_id 后作为 mpnews 的 thumb_media_id。'
      },
      {
        key: 'notify_wecom_app_custom_url',
        label: '自定义接口地址',
        placeholder: '留空使用官方接口，例如 http://127.0.0.1:1100',
        hint: '填写接口代理域名或 Base URL，后端会自动拼接 /cgi-bin/gettoken 和 /cgi-bin/message/send。'
      }
    ]
  },
  {
    key: 'bark',
    name: 'Bark',
    desc: 'iOS Bark 推送',
    fields: [
      { key: 'notify_bark_url', label: 'Bark 地址', placeholder: 'https://api.day.app/你的key' },
      { key: 'notify_bark_group', label: '分组', placeholder: 'XianYuAssistant' },
      { key: 'notify_bark_sound', label: '铃声', placeholder: '可选' }
    ]
  },
  {
    key: 'pushplus',
    name: 'PushPlus',
    desc: '微信 PushPlus 推送',
    fields: [
      { key: 'notify_pushplus_token', label: 'Token', type: 'password' },
      { key: 'notify_pushplus_topic', label: '群组编码', placeholder: '可选' }
    ]
  },
  {
    key: 'wxpusher',
    name: 'WxPusher',
    desc: 'WxPusher UID 或 Topic 推送',
    fields: [
      { key: 'notify_wxpusher_app_token', label: 'AppToken', type: 'password' },
      { key: 'notify_wxpusher_uids', label: 'UIDs', placeholder: '多个用 ; 或换行分隔', type: 'textarea' },
      { key: 'notify_wxpusher_topic_ids', label: 'Topic IDs', placeholder: '多个用 ; 或换行分隔', type: 'textarea' },
      { key: 'notify_wxpusher_url', label: '跳转链接', placeholder: '可选' }
    ]
  },
  {
    key: 'pushdeer',
    name: 'PushDeer',
    desc: 'PushDeer Key 推送',
    fields: [
      { key: 'notify_pushdeer_token', label: 'PushKey', type: 'password' },
      { key: 'notify_pushdeer_custom_url', label: '自定义 API', placeholder: '留空使用官方接口' }
    ]
  },
  {
    key: 'serverchan',
    name: 'Server酱',
    desc: 'Server酱 SendKey',
    fields: [{ key: 'notify_serverchan_token', label: 'SendKey', type: 'password' }]
  },
  {
    key: 'telegram',
    name: 'Telegram',
    desc: 'Telegram Bot 推送',
    fields: [
      { key: 'notify_telegram_bot_token', label: 'Bot Token', type: 'password' },
      { key: 'notify_telegram_chat_id', label: 'Chat ID' },
      { key: 'notify_telegram_api_base', label: 'API Base', placeholder: '留空使用 https://api.telegram.org' }
    ]
  },
  {
    key: 'gotify',
    name: 'Gotify',
    desc: '自建 Gotify 服务',
    fields: [
      { key: 'notify_gotify_url', label: 'Gotify 地址', placeholder: 'https://push.example.com' },
      { key: 'notify_gotify_token', label: '应用 Token', type: 'password' },
      { key: 'notify_gotify_priority', label: '优先级', type: 'number', placeholder: '0' }
    ]
  },
  {
    key: 'email',
    name: '邮箱 SMTP',
    desc: '通过 SMTP 邮箱发送通知',
    fields: [
      { key: 'email_smtp_host', label: 'SMTP 服务器', placeholder: '如 smtp.qq.com' },
      { key: 'email_smtp_port', label: 'SMTP 端口', placeholder: '465' },
      { key: 'email_smtp_username', label: '用户名', placeholder: '邮箱账号' },
      { key: 'email_smtp_password', label: '密码/授权码', type: 'password', placeholder: '邮箱密码或 SMTP 授权码' },
      { key: 'email_smtp_from', label: '收件人邮箱', placeholder: '接收通知的邮箱地址' },
      { key: 'email_smtp_ssl', label: '启用 SSL', placeholder: '1 启用，0 关闭', hint: '默认 1，QQ 邮箱通常使用 SSL 和授权码。' }
    ]
  }
]

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const logs = ref<NotificationLog[]>([])
const selectedChannel = ref<NotifyChannel>('generic')
const dispatchMode = ref<'single' | 'all'>('single')
const settings = ref<Record<string, string>>({})
const activePanel = ref<'settings' | 'logs'>('settings')
const expandedLogIds = ref<Set<number>>(new Set())

const form = ref({
  enabled: false,
  success: false,
  fail: true,
  stock: true,
  hourlyReport: false,
  wsDisconnect: false,
  cookieExpire: false,
  captchaRequired: true,
  captchaSuccess: false,
  inAppToast: true,
  inAppOnlineMessage: true
})

const currentChannel = computed(() => {
  return channels.find(item => item.key === selectedChannel.value) || channels[0]!
})

const enabledCount = computed(() => {
  return [
    form.value.enabled,
    form.value.success,
    form.value.fail,
    form.value.stock,
    form.value.hourlyReport,
    form.value.wsDisconnect,
    form.value.cookieExpire,
    form.value.captchaRequired,
    form.value.captchaSuccess,
    form.value.inAppToast,
    form.value.inAppOnlineMessage
  ].filter(Boolean).length
})

const latestLogs = computed(() => logs.value.slice(0, 20))

const setValue = (key: string, value: string) => {
  settings.value[key] = value
}

const getValue = (key: string) => settings.value[key] || ''

const statusText = (status: number) => {
  if (status === 1) return '成功'
  if (status === 0) return '跳过'
  return '失败'
}

const statusClass = (status: number) => {
  if (status === 1) return 'success'
  if (status === 0) return 'skip'
  return 'fail'
}

const channelText = (channel?: string) => {
  if (!channel) return '未知渠道'
  if (channel === 'local') return '本地跳过'
  return channels.find(item => item.key === channel)?.name || channel
}

const formatTime = (value?: string) => {
  return value ? formatDateTime(value) : '-'
}

const logContent = (log: NotificationLog) => {
  return log.status === 1 ? (log.content || '-') : (log.errorMessage || log.content || '-')
}

const isLogExpanded = (id: number) => expandedLogIds.value.has(id)

const toggleLogExpanded = (id: number) => {
  const next = new Set(expandedLogIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedLogIds.value = next
}

const loadNotificationPage = async () => {
  loading.value = true
  try {
    const [settingsRes, logsRes] = await Promise.all([
      getAllSettings(),
      getNotificationLogs()
    ])

    if (settingsRes.code === 0 || settingsRes.code === 200) {
      const map = new Map((settingsRes.data || []).map(item => [item.settingKey, item.settingValue]))
      settings.value = Object.fromEntries(map.entries())
      form.value.enabled = map.get('notify_webhook_enabled') === '1'
      form.value.success = map.get('notify_auto_delivery_success') === '1'
      form.value.fail = (map.get('notify_auto_delivery_fail') || '1') === '1'
      form.value.stock = (map.get('notify_stock_warning') || '1') === '1'
      form.value.hourlyReport = map.get('notify_hourly_report_enabled') === '1'
      form.value.wsDisconnect = map.get('email_notify_ws_disconnect_enabled') === '1' || map.get('email_notify_ws_disconnect_enabled') === 'true'
      form.value.cookieExpire = map.get('email_notify_cookie_expire_enabled') === '1' || map.get('email_notify_cookie_expire_enabled') === 'true'
      form.value.captchaRequired = (map.get('notify_captcha_required_enabled') || '1') === '1'
      form.value.captchaSuccess = map.get('notify_captcha_success_enabled') === '1'
      form.value.inAppToast = (map.get('notify_in_app_toast_enabled') || '1') === '1'
      form.value.inAppOnlineMessage = (map.get('notify_in_app_online_message_enabled') || '1') === '1'
      if (!settings.value.email_smtp_ssl) {
        settings.value.email_smtp_ssl = '1'
      }
      selectedChannel.value = (map.get('notify_channel') || map.get('notify_webhook_type') || 'generic') as NotifyChannel
      dispatchMode.value = map.get('notify_dispatch_mode') === 'all' ? 'all' : 'single'

      const legacyUrl = map.get('notify_webhook_url') || ''
      if (legacyUrl) {
        const legacyKey = getLegacyFieldKey(selectedChannel.value)
        if (legacyKey && !settings.value[legacyKey]) {
          settings.value[legacyKey] = legacyKey === 'notify_dingtalk_token' ? extractDingTalkToken(legacyUrl) : legacyUrl
        }
      }
    }

    if (logsRes.code === 0 || logsRes.code === 200) {
      logs.value = logsRes.data || []
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '加载通知设置失败')
    }
  } finally {
    loading.value = false
  }
}

const saveNotifications = async () => {
  saving.value = true
  try {
    await persistNotificationSettings()
    showSuccess('通知设置已保存')
    await loadNotificationPage()
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '保存通知设置失败')
    }
  } finally {
    saving.value = false
  }
}

const persistNotificationSettings = async () => {
  const channelKeys = new Set(channels.flatMap(item => item.fields.map(field => field.key)))
  const requests = [
    saveSetting({ settingKey: 'notify_webhook_enabled', settingValue: form.value.enabled ? '1' : '0', settingDesc: '通知总开关' }),
    saveSetting({ settingKey: 'notify_channel', settingValue: selectedChannel.value, settingDesc: '当前通知方式' }),
    saveSetting({ settingKey: 'notify_dispatch_mode', settingValue: dispatchMode.value, settingDesc: '通知发送模式' }),
    saveSetting({ settingKey: 'notify_webhook_type', settingValue: selectedChannel.value, settingDesc: '兼容旧通知类型' }),
    saveSetting({ settingKey: 'notify_webhook_url', settingValue: getLegacyWebhookUrl(), settingDesc: '兼容旧通知地址' }),
    saveSetting({ settingKey: 'notify_auto_delivery_success', settingValue: form.value.success ? '1' : '0', settingDesc: '自动发货成功通知开关' }),
    saveSetting({ settingKey: 'notify_auto_delivery_fail', settingValue: form.value.fail ? '1' : '0', settingDesc: '自动发货失败通知开关' }),
    saveSetting({ settingKey: 'notify_stock_warning', settingValue: form.value.stock ? '1' : '0', settingDesc: '库存预警通知开关' }),
    saveSetting({ settingKey: 'notify_hourly_report_enabled', settingValue: form.value.hourlyReport ? '1' : '0', settingDesc: '自动发货整点报表通知开关' }),
    saveSetting({ settingKey: 'email_notify_ws_disconnect_enabled', settingValue: form.value.wsDisconnect ? '1' : '0', settingDesc: '账号监听掉线通知开关' }),
    saveSetting({ settingKey: 'email_notify_cookie_expire_enabled', settingValue: form.value.cookieExpire ? '1' : '0', settingDesc: 'Cookie过期通知开关' }),
    saveSetting({ settingKey: 'notify_captcha_required_enabled', settingValue: form.value.captchaRequired ? '1' : '0', settingDesc: '人机验证通知开关' }),
    saveSetting({ settingKey: 'notify_captcha_success_enabled', settingValue: form.value.captchaSuccess ? '1' : '0', settingDesc: '人机验证恢复成功通知开关' }),
    saveSetting({ settingKey: 'notify_in_app_toast_enabled', settingValue: form.value.inAppToast ? '1' : '0', settingDesc: '右下角应用内通知开关' }),
    saveSetting({ settingKey: 'notify_in_app_online_message_enabled', settingValue: form.value.inAppOnlineMessage ? '1' : '0', settingDesc: '在线消息右下角通知开关' }),
    ...Array.from(channelKeys).map(key =>
      saveSetting({ settingKey: key, settingValue: getValue(key).trim(), settingDesc: '通知配置' })
    )
  ]
  await Promise.all(requests)
}

const getLegacyWebhookUrl = () => {
  const channel = selectedChannel.value
  if (channel === 'generic') return getValue('notify_generic_url')
  if (channel === 'feishu') return getValue('notify_feishu_url')
  if (channel === 'dingtalk' || channel === 'dingtalk_signed') return getValue('notify_dingtalk_token')
  if (channel === 'wecom') return getValue('notify_wecom_key')
  return ''
}

const getLegacyFieldKey = (channel: NotifyChannel) => {
  if (channel === 'generic') return 'notify_generic_url'
  if (channel === 'feishu') return 'notify_feishu_url'
  if (channel === 'dingtalk' || channel === 'dingtalk_signed') return 'notify_dingtalk_token'
  if (channel === 'wecom') return 'notify_wecom_key'
  return ''
}

const extractDingTalkToken = (value: string) => {
  const token = value.match(/[?&]access_token=([^&]+)/)?.[1]
  return token ? decodeURIComponent(token) : value
}

const sendTestNotification = async () => {
  testing.value = true
  try {
    await persistNotificationSettings()
    const res = await testNotification()
    if (res.code === 0 || res.code === 200) {
      showSuccess(res.data || '测试通知已发送')
      await loadNotificationPage()
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '测试通知失败')
    }
  } finally {
    testing.value = false
  }
}

onMounted(loadNotificationPage)
</script>

<template>
  <div class="notify-page">
    <header class="notify-page__header">
      <div class="notify-page__title-row">
        <div class="notify-page__title-icon">
          <IconAlert />
        </div>
        <div>
          <h1 class="notify-page__title">通知设置</h1>
          <p class="notify-page__subtitle">选择推送方式，右侧填写对应配置</p>
        </div>
      </div>
      <button class="notify-btn notify-btn--secondary" :disabled="loading" @click="loadNotificationPage">
        <IconRefresh />
        <span>{{ loading ? '刷新中' : '刷新' }}</span>
      </button>
    </header>

    <main class="notify-page__body">
      <div class="notify-tabs">
        <button
          class="notify-tab"
          :class="{ 'notify-tab--active': activePanel === 'settings' }"
          type="button"
          @click="activePanel = 'settings'"
        >
          推送配置
        </button>
        <button
          class="notify-tab"
          :class="{ 'notify-tab--active': activePanel === 'logs' }"
          type="button"
          @click="activePanel = 'logs'"
        >
          通知日志
        </button>
      </div>

      <section class="notify-panel notify-panel--settings" :class="{ 'notify-panel--active': activePanel === 'settings' }">
        <div class="notify-panel__header">
          <div>
            <h2 class="notify-panel__title">推送配置</h2>
            <p class="notify-panel__desc">已启用 {{ enabledCount }} 项</p>
          </div>
          <label class="notify-switch">
            <input v-model="form.enabled" type="checkbox" />
            <span class="notify-switch__track"></span>
            <span class="notify-switch__thumb"></span>
          </label>
        </div>

        <div class="notify-dispatch" aria-label="通知发送模式">
          <button
            class="notify-dispatch__option"
            :class="{ 'notify-dispatch__option--active': dispatchMode === 'single' }"
            type="button"
            @click="dispatchMode = 'single'"
          >
            <span class="notify-dispatch__title">只推送当前方式</span>
            <span class="notify-dispatch__desc">{{ currentChannel.name }}</span>
          </button>
          <button
            class="notify-dispatch__option"
            :class="{ 'notify-dispatch__option--active': dispatchMode === 'all' }"
            type="button"
            @click="dispatchMode = 'all'"
          >
            <span class="notify-dispatch__title">推送全部已配置方式</span>
            <span class="notify-dispatch__desc">企业微信、钉钉等会分别通知</span>
          </button>
        </div>

        <div class="notify-config-layout">
          <nav class="notify-channel-list">
            <button
              v-for="channel in channels"
              :key="channel.key"
              class="notify-channel"
              :class="{ 'notify-channel--active': selectedChannel === channel.key }"
              type="button"
              @click="selectedChannel = channel.key"
            >
              <span class="notify-channel__name">{{ channel.name }}</span>
              <span class="notify-channel__desc">{{ channel.desc }}</span>
            </button>
          </nav>

          <div class="notify-channel-config">
            <div class="notify-channel-config__header">
              <h3>{{ currentChannel.name }}</h3>
              <p>{{ currentChannel.desc }}</p>
            </div>

            <div class="notify-form">
              <label
                v-for="field in currentChannel.fields"
                :key="field.key"
                class="notify-field"
                :class="{ 'notify-field--textarea': field.type === 'textarea' }"
              >
                <span class="notify-field__label">{{ field.label }}</span>
                <textarea
                  v-if="field.type === 'textarea'"
                  class="notify-input notify-input--textarea"
                  :value="getValue(field.key)"
                  :placeholder="field.placeholder"
                  @input="setValue(field.key, ($event.target as HTMLTextAreaElement).value)"
                />
                <input
                  v-else
                  class="notify-input"
                  :type="field.type || 'text'"
                  :value="getValue(field.key)"
                  :placeholder="field.placeholder"
                  @input="setValue(field.key, ($event.target as HTMLInputElement).value)"
                />
                <span v-if="field.hint" class="notify-field__hint">{{ field.hint }}</span>
              </label>
            </div>
          </div>
        </div>

        <div class="notify-actions">
          <button class="notify-btn notify-btn--secondary" :disabled="testing" @click="sendTestNotification">
            <IconSend />
            <span>{{ testing ? '发送中' : '测试通知' }}</span>
          </button>
          <button class="notify-btn notify-btn--primary" :disabled="saving" @click="saveNotifications">
            <span>{{ saving ? '保存中' : '保存设置' }}</span>
          </button>
        </div>

        <div class="notify-events">
          <label class="notify-check">
            <input v-model="form.success" type="checkbox" />
            <span>自动发货成功</span>
          </label>
          <label class="notify-check">
            <input v-model="form.fail" type="checkbox" />
            <span>自动发货失败</span>
          </label>
          <label class="notify-check">
            <input v-model="form.stock" type="checkbox" />
            <span>库存预警</span>
          </label>
          <label class="notify-check">
            <input v-model="form.hourlyReport" type="checkbox" />
            <span>整点报表</span>
          </label>
          <label class="notify-check">
            <input v-model="form.wsDisconnect" type="checkbox" />
            <span>账号监听掉线</span>
          </label>
          <label class="notify-check">
            <input v-model="form.cookieExpire" type="checkbox" />
            <span>Cookie 过期</span>
          </label>
          <label class="notify-check">
            <input v-model="form.captchaRequired" type="checkbox" />
            <span>人机验证</span>
          </label>
          <label class="notify-check">
            <input v-model="form.captchaSuccess" type="checkbox" />
            <span>人机验证成功</span>
          </label>
          <label class="notify-check">
            <input v-model="form.inAppToast" type="checkbox" />
            <span>右下角系统通知</span>
          </label>
          <label class="notify-check">
            <input v-model="form.inAppOnlineMessage" type="checkbox" />
            <span>在线消息右下角通知</span>
          </label>
        </div>
      </section>

      <section class="notify-panel notify-panel--logs" :class="{ 'notify-panel--active': activePanel === 'logs' }">
        <div class="notify-panel__header">
          <div>
            <h2 class="notify-panel__title">通知日志</h2>
            <p class="notify-panel__desc">最新记录 · 显示 {{ latestLogs.length }} 条</p>
          </div>
        </div>

        <div v-if="loading && logs.length === 0" class="notify-empty">加载中...</div>
        <div v-else-if="latestLogs.length === 0" class="notify-empty">暂无通知日志</div>
        <div v-else class="notify-log-list">
          <article
            v-for="log in latestLogs"
            :key="log.id"
            class="notify-log"
            :class="[
              `notify-log--${statusClass(log.status)}`,
              { 'notify-log--expanded': isLogExpanded(log.id) }
            ]"
            role="button"
            tabindex="0"
            @click="toggleLogExpanded(log.id)"
            @keydown.enter.prevent="toggleLogExpanded(log.id)"
            @keydown.space.prevent="toggleLogExpanded(log.id)"
          >
            <div class="notify-log__state" aria-hidden="true">
              <IconCheck v-if="log.status === 1" />
              <IconClock v-else-if="log.status === 0" />
              <IconAlert v-else />
            </div>
            <div class="notify-log__body">
              <div class="notify-log__head">
                <div class="notify-log__title">{{ log.title }}</div>
                <span class="notify-log__status" :class="`notify-log__status--${log.status}`">
                  {{ statusText(log.status) }}
                </span>
              </div>
              <div class="notify-log__content">{{ logContent(log) }}</div>
              <div class="notify-log__meta">
                <span class="notify-log__channel">
                  <span class="notify-log__channel-label">渠道：</span>{{ channelText(log.channel) }}
                </span>
                <span class="notify-log__time">{{ formatTime(log.createTime) }}</span>
              </div>
            </div>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

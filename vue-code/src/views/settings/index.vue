<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentUser, changePassword, kickLoginDevice, listLoginDevices } from '@/api/system'
import type { LoginDevice } from '@/api/system'
import { logout } from '@/api/auth'
import { getSetting, saveSetting } from '@/api/setting'
import { getAIStatus } from '@/api/ai'
import { getAccountList } from '@/api/account'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearAuthToken } from '@/utils/request'
import { getAccountDisplayName } from '@/utils/accountDisplay'
import { GOOFISH_IM_EMBED_ENABLED_KEY, GOOFISH_IM_URL } from '@/constants/goofishIm'
import DeliverySettings from './components/DeliverySettings.vue'
import type { Account } from '@/types'

const router = useRouter()

// 当前选中的菜单
const activeMenu = ref('account')

// 账号信息
const username = ref('')
const lastLoginTime = ref('')
const loading = ref(false)

// 修改密码
const showPasswordForm = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const changingPassword = ref(false)

const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

// 退出登录
const loggingOut = ref(false)
const loginDevices = ref<LoginDevice[]>([])
const loginDevicesLoading = ref(false)
const kickingDeviceId = ref<number | null>(null)

// 系统提示词
const SYS_PROMPT_KEY = 'sys_prompt'
const DEFAULT_SYS_PROMPT = '你是一个闲鱼卖家，你叫肥极喵，不要回复的像AI，简短回答\n参考相关信息回答,不要乱回答,不知道就换不同语气回复提示用户详细点询问'
const sysPromptValue = ref('')
const sysPromptSaving = ref(false)
const sysPromptLoaded = ref(false)

// 账号级 AI 回复配置
const aiReplyAccounts = ref<Account[]>([])
const selectedAiReplyAccountId = ref<number | null>(null)
const globalAiReplyEnabled = ref(false)
const globalAiReplyTemplate = ref('')
const globalAiReplyConfigLoading = ref(false)
const globalAiReplyConfigSaving = ref(false)
const globalAiReplyEnabledInput = ref<HTMLInputElement | null>(null)
const GLOBAL_AI_REPLY_ENABLED_PREFIX = 'global_ai_reply_enabled_'
const GLOBAL_AI_REPLY_TEMPLATE_PREFIX = 'global_ai_reply_template_'

// 相似度阈值
const SIMILARITY_THRESHOLD_KEY = 'similarity_threshold'
const DEFAULT_SIMILARITY_THRESHOLD = 0.1
const similarityThreshold = ref(DEFAULT_SIMILARITY_THRESHOLD)
const similarityThresholdSaving = ref(false)

// AI 提供商配置（动态 N 个）
import { listAiProviders, saveAiProvider, deleteAiProvider, activateAiProvider, testAiProvider, getAiProviderModels } from '@/api/aiProvider'
import type { AiProvider, AiProviderSaveReq } from '@/api/aiProvider'

const aiProviders = ref<AiProvider[]>([])
const aiProviderLoading = ref(false)
const aiProviderDialogVisible = ref(false)
const aiProviderForm = ref<AiProviderSaveReq>({ name: '', apiKey: '', baseUrl: '', model: '' })
const aiProviderFormId = ref<number | undefined>(undefined)
const aiProviderSaving = ref(false)
const aiProviderTesting = ref<number | null>(null)
const aiProviderModelLoading = ref<number | null>(null)
const aiProviderModels = ref<string[]>([])
const showProviderApiKey = ref(false)

// Embedding 模型配置（可选，默认共用 AI 配置）
const EMBEDDING_API_KEY_SETTING = 'ai_embedding_api_key'
const EMBEDDING_BASE_URL_SETTING = 'ai_embedding_base_url'
const EMBEDDING_MODEL_SETTING = 'ai_embedding_model'
const DEFAULT_EMBEDDING_MODEL = 'text-embedding-v3'

const embeddingApiKey = ref('')
const embeddingBaseUrl = ref('')
const embeddingModel = ref(DEFAULT_EMBEDDING_MODEL)
const embeddingSaving = ref(false)
const showEmbeddingApiKey = ref(false)
const showEmbeddingConfig = ref(false)

// AI 状态
const aiStatus = ref({
  enabled: false,
  available: false,
  apiKeyConfigured: false,
  message: '',
  baseUrl: '',
  model: '',
  provider: '',
  providerName: '',
  configuredCount: 0
})

// 菜单配置
const menuItems = [
  { key: 'account', label: '系统账号', icon: '👤' },
  { key: 'ai', label: 'AI 服务配置', icon: '🤖' },
  { key: 'prompt', label: 'AI客服配置', icon: '💬' },
  { key: 'delivery', label: '发货配置', icon: '🚚' },
  { key: 'message', label: '消息配置', icon: '💬' },
  { key: 'goods', label: '商品操作', icon: '🛒' },
  { key: 'about', label: '关于', icon: 'ℹ️' }
]

const goofishImEmbedEnabled = ref(false)
const goofishImEmbedSaving = ref(false)
const GOODS_OFF_SHELF_ENABLED_KEY = 'goods_off_shelf_enabled'
const GOODS_DELETE_ENABLED_KEY = 'goods_delete_enabled'
const goodsOffShelfEnabled = ref(true)
const goodsDeleteEnabled = ref(true)
const goodsOperationSaving = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const res = await getCurrentUser()
    if (res.code === 200 && res.data) {
      username.value = res.data.username || ''
      lastLoginTime.value = res.data.lastLoginTime || ''
    }
  } catch (e) {
    console.error('获取用户信息失败:', e)
  } finally {
    loading.value = false
  }

  // 加载系统提示词配置
  try {
    const res = await getSetting({ settingKey: SYS_PROMPT_KEY })
    if (res.code === 200 && res.data) {
      sysPromptValue.value = res.data.settingValue || ''
      sysPromptLoaded.value = true
    }
  } catch (e) {
    console.error('获取系统提示词配置失败:', e)
  }

  // 加载相似度阈值配置
  try {
    const res = await getSetting({ settingKey: SIMILARITY_THRESHOLD_KEY })
    if (res.code === 200 && res.data && res.data.settingValue) {
      similarityThreshold.value = parseFloat(res.data.settingValue) || DEFAULT_SIMILARITY_THRESHOLD
    }
  } catch (e) {
    console.error('获取相似度阈值配置失败:', e)
  }

  // 加载 AI 配置
  await loadAiProviders()
  // 加载 Embedding 配置
  await loadEmbeddingConfig()
  // 加载 AI 状态
  await loadAIStatus()
  // 加载账号级 AI 回复配置
  await loadAiReplyAccounts()
  // 加载消息配置
  await loadMessageConfig()
  // 加载商品操作配置
  await loadGoodsOperationConfig()
  await loadLoginDevices()
})

function settingEnabled(value: string | null | undefined, defaultValue: boolean) {
  if (value === null || value === undefined || value === '') return defaultValue
  return value === '1' || value.toLowerCase() === 'true'
}

async function loadMessageConfig() {
  try {
    const res = await getSetting({ settingKey: GOOFISH_IM_EMBED_ENABLED_KEY })
    goofishImEmbedEnabled.value = settingEnabled(res.data?.settingValue, false)
  } catch (e) {
    console.error('加载消息配置失败:', e)
    goofishImEmbedEnabled.value = false
  }
}

async function handleSaveMessageConfig() {
  goofishImEmbedSaving.value = true
  try {
    await saveSetting({
      settingKey: GOOFISH_IM_EMBED_ENABLED_KEY,
      settingValue: goofishImEmbedEnabled.value ? '1' : '0',
      settingDesc: '官方闲鱼IM辅助入口开关'
    })
    ElMessage.success('消息配置已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存消息配置失败')
  } finally {
    goofishImEmbedSaving.value = false
  }
}

async function loadGoodsOperationConfig() {
  try {
    const [offShelfRes, deleteRes] = await Promise.all([
      getSetting({ settingKey: GOODS_OFF_SHELF_ENABLED_KEY }),
      getSetting({ settingKey: GOODS_DELETE_ENABLED_KEY })
    ])
    goodsOffShelfEnabled.value = settingEnabled(offShelfRes.data?.settingValue, true)
    goodsDeleteEnabled.value = settingEnabled(deleteRes.data?.settingValue, true)
  } catch (e) {
    console.error('加载商品操作配置失败:', e)
    goodsOffShelfEnabled.value = true
    goodsDeleteEnabled.value = true
  }
}

async function handleSaveGoodsOperationConfig() {
  goodsOperationSaving.value = true
  try {
    await Promise.all([
      saveSetting({
        settingKey: GOODS_OFF_SHELF_ENABLED_KEY,
        settingValue: goodsOffShelfEnabled.value ? '1' : '0',
        settingDesc: '商品下架功能开关'
      }),
      saveSetting({
        settingKey: GOODS_DELETE_ENABLED_KEY,
        settingValue: goodsDeleteEnabled.value ? '1' : '0',
        settingDesc: '商品删除功能开关'
      })
    ])
    ElMessage.success('商品操作配置已保存')
  } catch (e: any) {
    ElMessage.error(e.message || '保存商品操作配置失败')
  } finally {
    goodsOperationSaving.value = false
  }
}

async function loadAiReplyAccounts() {
  try {
    const res = await getAccountList()
    if (res.code === 200 && res.data?.accounts) {
      aiReplyAccounts.value = res.data.accounts
      selectedAiReplyAccountId.value = res.data.accounts[0]?.id || null
      await loadGlobalAiReplyConfig()
    }
  } catch (e) {
    console.error('获取闲鱼账号失败:', e)
  }
}

async function loadGlobalAiReplyConfig() {
  if (!selectedAiReplyAccountId.value) {
    globalAiReplyEnabled.value = false
    globalAiReplyTemplate.value = ''
    return
  }

  globalAiReplyConfigLoading.value = true
  try {
    const accountId = selectedAiReplyAccountId.value
    const [enabledRes, templateRes] = await Promise.all([
      getSetting({ settingKey: buildGlobalAiReplyEnabledKey(accountId) }),
      getSetting({ settingKey: buildGlobalAiReplyTemplateKey(accountId) })
    ])
    globalAiReplyEnabled.value = enabledRes.code === 200 && enabledRes.data?.settingValue === '1'
    globalAiReplyTemplate.value = templateRes.code === 200 ? (templateRes.data?.settingValue || '') : ''
  } catch (e) {
    console.error('获取账号级AI回复配置失败:', e)
    ElMessage.error('获取账号级AI回复配置失败')
  } finally {
    globalAiReplyConfigLoading.value = false
  }
}

async function handleSaveGlobalAiReplyConfig() {
  if (!selectedAiReplyAccountId.value) {
    ElMessage.warning('请先选择闲鱼账号')
    return
  }

  globalAiReplyConfigSaving.value = true
  try {
    const accountId = selectedAiReplyAccountId.value
    const expectedEnabled = globalAiReplyEnabledInput.value?.checked ?? globalAiReplyEnabled.value
    globalAiReplyEnabled.value = expectedEnabled
    const expectedTemplate = globalAiReplyTemplate.value.trim()
    const responses = await Promise.all([
      saveSetting({
        settingKey: buildGlobalAiReplyEnabledKey(accountId),
        settingValue: expectedEnabled ? '1' : '0',
        settingDesc: '账号级所有商品AI回复总开关'
      }),
      saveSetting({
        settingKey: buildGlobalAiReplyTemplateKey(accountId),
        settingValue: expectedTemplate,
        settingDesc: '账号级全局AI回复模板'
      })
    ])

    if (responses.every((res) => res.code === 200)) {
      await loadGlobalAiReplyConfig()
      if (globalAiReplyEnabled.value !== expectedEnabled || globalAiReplyTemplate.value !== expectedTemplate) {
        ElMessage.error('保存后读回配置不一致，请刷新页面重试')
        return
      }
      ElMessage.success('账号级AI回复配置已保存')
    }
  } catch (e) {
    console.error('保存账号级AI回复配置失败:', e)
    ElMessage.error('保存账号级AI回复配置失败')
  } finally {
    globalAiReplyConfigSaving.value = false
  }
}

function buildGlobalAiReplyEnabledKey(accountId: number) {
  return `${GLOBAL_AI_REPLY_ENABLED_PREFIX}${accountId}`
}

function buildGlobalAiReplyTemplateKey(accountId: number) {
  return `${GLOBAL_AI_REPLY_TEMPLATE_PREFIX}${accountId}`
}

async function loadAiProviders() {
  aiProviderLoading.value = true
  try {
    aiProviders.value = await listAiProviders()
  } catch (e) {
    console.error('获取AI提供商列表失败:', e)
  } finally {
    aiProviderLoading.value = false
  }
}

async function loadEmbeddingConfig() {
  try {
    const [apiKeyRes, baseUrlRes, modelRes] = await Promise.all([
      getSetting({ settingKey: EMBEDDING_API_KEY_SETTING }),
      getSetting({ settingKey: EMBEDDING_BASE_URL_SETTING }),
      getSetting({ settingKey: EMBEDDING_MODEL_SETTING })
    ])

    if (apiKeyRes.code === 200 && apiKeyRes.data) {
      embeddingApiKey.value = apiKeyRes.data.settingValue || ''
    }
    if (baseUrlRes.code === 200 && baseUrlRes.data && baseUrlRes.data.settingValue) {
      embeddingBaseUrl.value = baseUrlRes.data.settingValue
    }
    if (modelRes.code === 200 && modelRes.data && modelRes.data.settingValue) {
      embeddingModel.value = modelRes.data.settingValue
    }
  } catch (e) {
    console.error('获取Embedding配置失败:', e)
  }
}

async function loadAIStatus() {
  try {
    const res = await getAIStatus()
    const data = await res.json()
    if (data.code === 200 && data.data) {
      aiStatus.value = data.data
    }
  } catch (e) {
    console.error('获取AI状态失败:', e)
  }
}

async function handleChangePassword() {
  if (!oldPassword.value) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!newPassword.value || newPassword.value.length < 6) {
    ElMessage.warning('新密码长度需在6-50之间')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次密码不一致')
    return
  }
  changingPassword.value = true
  try {
    const res = await changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功')
      showPasswordForm.value = false
      oldPassword.value = ''
      newPassword.value = ''
      confirmPassword.value = ''
    }
  } finally {
    changingPassword.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm(
      '确定要退出登录吗？',
      '退出确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    loggingOut.value = true
    try {
      await logout()
      clearAuthToken()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) {
      console.error('退出登录失败:', e)
      // 即使接口失败，也清除本地token并跳转
      clearAuthToken()
      router.push('/login')
    } finally {
      loggingOut.value = false
    }
  } catch {
    // 用户取消
  }
}

async function loadLoginDevices() {
  loginDevicesLoading.value = true
  try {
    const res = await listLoginDevices()
    if (res.code === 200) {
      loginDevices.value = res.data || []
    }
  } catch (e) {
    console.error('获取登录设备失败:', e)
  } finally {
    loginDevicesLoading.value = false
  }
}

function getLoginDeviceStatusText(status: number) {
  if (status === 1) return '在线'
  if (status === 0) return '已退出'
  if (status === -1) return '已踢出'
  return '未知'
}

function getLoginDeviceStatusClass(device: LoginDevice) {
  if (device.current) return 'settings__status-badge--success'
  if (device.status === 1) return 'settings__status-badge--success'
  if (device.status === -1) return 'settings__status-badge--danger'
  return ''
}

async function handleKickLoginDevice(device: LoginDevice) {
  if (device.current) {
    ElMessage.warning('不能踢出当前设备，请使用退出登录')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定踢出 ${device.deviceName || '该设备'} 吗？`,
      '踢出设备',
      {
        confirmButtonText: '踢出',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    kickingDeviceId.value = device.id
    const res = await kickLoginDevice(device.id)
    if (res.code === 200) {
      ElMessage.success('已踢出该设备')
      await loadLoginDevices()
    }
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e.message || '踢出设备失败')
    }
  } finally {
    kickingDeviceId.value = null
  }
}

async function handleSaveSysPrompt() {
  if (!sysPromptValue.value.trim()) {
    ElMessage.warning('系统提示词不能为空')
    return
  }
  sysPromptSaving.value = true
  try {
    const res = await saveSetting({
      settingKey: SYS_PROMPT_KEY,
      settingValue: sysPromptValue.value,
      settingDesc: 'AI智能回复的系统提示词'
    })
    if (res.code === 200) {
      ElMessage.success('系统提示词保存成功')
      sysPromptLoaded.value = true
    }
  } finally {
    sysPromptSaving.value = false
  }
}

function handleResetSysPrompt() {
  sysPromptValue.value = DEFAULT_SYS_PROMPT
}

async function handleSaveSimilarityThreshold() {
  if (similarityThreshold.value < 0 || similarityThreshold.value > 1) {
    ElMessage.warning('相似度阈值必须在 0 到 1 之间')
    return
  }
  similarityThresholdSaving.value = true
  try {
    const res = await saveSetting({
      settingKey: SIMILARITY_THRESHOLD_KEY,
      settingValue: similarityThreshold.value.toString(),
      settingDesc: 'RAG向量搜索的相似度阈值（0-1之间，值越小匹配越宽松）'
    })
    if (res.code === 200) {
      ElMessage.success('相似度阈值保存成功')
    }
  } catch (e) {
    console.error('保存相似度阈值失败:', e)
    ElMessage.error('保存相似度阈值失败')
  } finally {
    similarityThresholdSaving.value = false
  }
}

function handleResetSimilarityThreshold() {
  similarityThreshold.value = DEFAULT_SIMILARITY_THRESHOLD
}

function openAddProviderDialog() {
  aiProviderFormId.value = undefined
  aiProviderForm.value = { name: '', apiKey: '', baseUrl: '', model: '' }
  aiProviderModels.value = []
  showProviderApiKey.value = false
  aiProviderDialogVisible.value = true
}

function openEditProviderDialog(provider: AiProvider) {
  aiProviderFormId.value = provider.id
  aiProviderForm.value = {
    name: provider.name,
    apiKey: '',
    baseUrl: provider.baseUrl,
    model: provider.model
  }
  aiProviderModels.value = []
  showProviderApiKey.value = false
  aiProviderDialogVisible.value = true
}

async function handleSaveProvider() {
  if (!aiProviderForm.value.name.trim()) {
    ElMessage.warning('请输入提供商名称')
    return
  }
  if (!aiProviderFormId.value && !aiProviderForm.value.apiKey.trim()) {
    ElMessage.warning('请输入 API Key')
    return
  }
  if (!aiProviderForm.value.baseUrl.trim()) {
    ElMessage.warning('请输入 API Base URL')
    return
  }
  if (!aiProviderForm.value.model.trim()) {
    ElMessage.warning('请输入模型名称')
    return
  }

  aiProviderSaving.value = true
  try {
    const payload: AiProviderSaveReq = {
      ...aiProviderForm.value,
      name: aiProviderForm.value.name.trim(),
      apiKey: aiProviderForm.value.apiKey.trim(),
      baseUrl: aiProviderForm.value.baseUrl.trim(),
      model: aiProviderForm.value.model.trim()
    }
    if (aiProviderFormId.value) {
      (payload as any).id = aiProviderFormId.value
      if (!payload.apiKey) delete (payload as any).apiKey
    }
    await saveAiProvider(payload)
    ElMessage.success(aiProviderFormId.value ? '提供商已更新' : '提供商已添加')
    aiProviderDialogVisible.value = false
    await loadAiProviders()
    await loadAIStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    aiProviderSaving.value = false
  }
}

async function handleDeleteProvider(id: number) {
  try {
    await deleteAiProvider(id)
    ElMessage.success('已删除')
    await loadAiProviders()
    await loadAIStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function handleActivateProvider(id: number) {
  try {
    await activateAiProvider(id)
    ElMessage.success('已切换激活提供商')
    await loadAiProviders()
    await loadAIStatus()
  } catch (e: any) {
    ElMessage.error(e.message || '切换失败')
  }
}

async function handleTestProvider(id: number) {
  aiProviderTesting.value = id
  try {
    const resp = await testAiProvider({ id })
    ElMessage.success(resp.message || '连接成功')
  } catch (e: any) {
    ElMessage.error(e.message || '测试连接失败')
  } finally {
    aiProviderTesting.value = null
  }
}

async function handleFetchProviderModels() {
  const form = aiProviderForm.value
  if (!form.apiKey && !aiProviderFormId.value) {
    ElMessage.warning('请先填写 API Key')
    return
  }
  if (!form.baseUrl) {
    ElMessage.warning('请先填写 API Base URL')
    return
  }

  aiProviderModelLoading.value = aiProviderFormId.value || -1
  try {
    const payload = aiProviderFormId.value
      ? { id: aiProviderFormId.value }
      : { apiKey: form.apiKey, baseUrl: form.baseUrl }
    const resp = await getAiProviderModels(payload)
    aiProviderModels.value = resp.models || []
    if (aiProviderModels.value.length > 0 && !form.model) {
      const firstModel = aiProviderModels.value[0]
      if (firstModel) {
        aiProviderForm.value.model = firstModel
      }
    }
    ElMessage.success(aiProviderModels.value.length ? `已获取 ${aiProviderModels.value.length} 个模型` : '连接成功，但未返回模型列表')
  } catch (e: any) {
    ElMessage.error(e.message || '获取模型失败')
  } finally {
    aiProviderModelLoading.value = null
  }
}

async function handleSaveEmbeddingConfig() {
  embeddingSaving.value = true
  try {
    // 保存三个配置（可以为空，空值表示使用 AI 对话配置）
    const [keyRes, urlRes, modelRes] = await Promise.all([
      saveSetting({
        settingKey: EMBEDDING_API_KEY_SETTING,
        settingValue: embeddingApiKey.value.trim(),
        settingDesc: 'Embedding模型API Key（留空则使用AI对话的API Key）'
      }),
      saveSetting({
        settingKey: EMBEDDING_BASE_URL_SETTING,
        settingValue: embeddingBaseUrl.value.trim(),
        settingDesc: 'Embedding模型API Base URL（留空则使用AI对话的Base URL）'
      }),
      saveSetting({
        settingKey: EMBEDDING_MODEL_SETTING,
        settingValue: embeddingModel.value.trim(),
        settingDesc: 'Embedding模型名称'
      })
    ])

    if (keyRes.code === 200 && urlRes.code === 200 && modelRes.code === 200) {
      ElMessage.success('Embedding 配置保存成功，重启服务后生效')
    }
  } catch (e) {
    console.error('保存Embedding配置失败:', e)
    ElMessage.error('保存Embedding配置失败')
  } finally {
    embeddingSaving.value = false
  }
}

function handleResetEmbeddingConfig() {
  embeddingApiKey.value = ''
  embeddingBaseUrl.value = ''
  embeddingModel.value = DEFAULT_EMBEDDING_MODEL
}

</script>

<template>
  <div class="settings">
    <!-- 左侧菜单 -->
    <div class="settings__sidebar">
      <div class="settings__sidebar-title">设置</div>
      <div class="settings__menu">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="settings__menu-item"
          :class="{ 'settings__menu-item--active': activeMenu === item.key }"
          @click="activeMenu = item.key"
        >
          <span class="settings__menu-icon">{{ item.icon }}</span>
          <span class="settings__menu-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 右侧内容 -->
    <div class="settings__content">
      <!-- 系统账号（包含修改密码和退出登录） -->
      <div v-if="activeMenu === 'account'" class="settings__panel">
        <div class="settings__panel-title">系统账号</div>
        <div v-if="loading" class="settings__loading">
          <div class="settings__spinner"></div>
          <span>加载中...</span>
        </div>
        <div v-else class="settings__info">
          <div class="settings__info-row">
            <span class="settings__info-label">账号</span>
            <span class="settings__info-value">{{ username }}</span>
          </div>
          <div class="settings__info-row">
            <span class="settings__info-label">最后登录时间</span>
            <span class="settings__info-value">{{ lastLoginTime || '-' }}</span>
          </div>
        </div>

        <!-- 修改密码 -->
        <div class="settings__section">
          <div class="settings__section-header">
            <div class="settings__section-title">修改密码</div>
            <button
              v-if="!showPasswordForm"
              class="settings__toggle-btn"
              @click="showPasswordForm = true"
            >
              修改
            </button>
          </div>
          <div v-if="showPasswordForm" class="settings__form">
            <div class="settings__field">
              <label class="settings__label">原密码</label>
              <div class="settings__input-wrap">
                <input
                  v-model="oldPassword"
                  :type="showOldPassword ? 'text' : 'password'"
                  class="settings__input"
                  placeholder="请输入原密码"
                  :disabled="changingPassword"
                />
                <button class="settings__eye-btn" @click="showOldPassword = !showOldPassword" tabindex="-1">
                  {{ showOldPassword ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <div class="settings__field">
              <label class="settings__label">新密码</label>
              <div class="settings__input-wrap">
                <input
                  v-model="newPassword"
                  :type="showNewPassword ? 'text' : 'password'"
                  class="settings__input"
                  placeholder="请输入新密码（6-50位）"
                  :disabled="changingPassword"
                />
                <button class="settings__eye-btn" @click="showNewPassword = !showNewPassword" tabindex="-1">
                  {{ showNewPassword ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <div class="settings__field">
              <label class="settings__label">确认新密码</label>
              <div class="settings__input-wrap">
                <input
                  v-model="confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  class="settings__input"
                  placeholder="请再次输入新密码"
                  :disabled="changingPassword"
                  @keydown.enter="handleChangePassword"
                />
                <button class="settings__eye-btn" @click="showConfirmPassword = !showConfirmPassword" tabindex="-1">
                  {{ showConfirmPassword ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <div class="settings__actions">
              <button class="settings__btn settings__btn--secondary" :disabled="changingPassword" @click="showPasswordForm = false">
                取消
              </button>
              <button class="settings__btn settings__btn--primary" :disabled="changingPassword" @click="handleChangePassword">
                {{ changingPassword ? '请稍候...' : '确认修改' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 登录设备 -->
        <div class="settings__section">
          <div class="settings__section-header">
            <div class="settings__section-title">登录设备</div>
            <button
              class="settings__toggle-btn"
              :disabled="loginDevicesLoading"
              @click="loadLoginDevices"
            >
              {{ loginDevicesLoading ? '刷新中...' : '刷新' }}
            </button>
          </div>
          <p class="settings__desc">支持多端同时在线，可手动踢出指定设备。</p>
          <div v-if="loginDevicesLoading" class="settings__loading">
            <div class="settings__spinner"></div>
            <span>加载登录设备...</span>
          </div>
          <div v-else-if="loginDevices.length === 0" class="settings__empty">
            暂无登录设备
          </div>
          <div v-else class="settings__device-list">
            <div
              v-for="device in loginDevices"
              :key="device.id"
              class="settings__device-card"
              :class="{ 'settings__device-card--current': device.current }"
            >
              <div class="settings__device-main">
                <div class="settings__device-title-row">
                  <span class="settings__device-title">{{ device.deviceName || '未知设备' }}</span>
                  <span
                    class="settings__status-badge"
                    :class="getLoginDeviceStatusClass(device)"
                  >
                    {{ device.current ? '当前设备' : getLoginDeviceStatusText(device.status) }}
                  </span>
                </div>
                <div class="settings__device-meta">
                  <span>{{ device.loginIp || '-' }}</span>
                  <span>{{ device.browserName || '-' }}</span>
                  <span>{{ device.osName || '-' }}</span>
                </div>
                <div class="settings__device-time">
                  <span>登录：{{ device.loginTime || '-' }}</span>
                  <span>活跃：{{ device.lastActiveTime || '-' }}</span>
                </div>
              </div>
              <button
                class="settings__btn settings__btn--danger settings__btn--small"
                :disabled="device.current || device.status !== 1 || kickingDeviceId === device.id"
                @click="handleKickLoginDevice(device)"
              >
                {{ kickingDeviceId === device.id ? '踢出中...' : '踢出' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 退出登录 -->
        <div class="settings__section">
          <div class="settings__section-title">退出登录</div>
          <p class="settings__logout-text">退出当前系统账号，退出后需要重新登录</p>
          <button
            class="settings__btn settings__btn--danger"
            :disabled="loggingOut"
            @click="handleLogout"
          >
            {{ loggingOut ? '退出中...' : '退出登录' }}
          </button>
        </div>
      </div>

      <!-- AI 服务配置（包含 Embedding 配置和系统提示词） -->
      <div v-if="activeMenu === 'ai'" class="settings__panel">
        <div class="settings__panel-title">AI 服务配置</div>

        <!-- AI 状态指示 -->
        <div class="settings__ai-status">
          <div class="settings__ai-status-row">
            <span class="settings__info-label">服务状态</span>
            <span class="settings__ai-status-badge" :class="aiStatus.available ? 'settings__ai-status-badge--ok' : 'settings__ai-status-badge--off'">
              {{ aiStatus.available ? '可用' : '不可用' }}
            </span>
          </div>
          <div v-if="aiStatus.message" class="settings__ai-status-row">
            <span class="settings__info-label">状态说明</span>
            <span class="settings__info-value settings__ai-status-msg">{{ aiStatus.message }}</span>
          </div>
          <div v-if="aiStatus.baseUrl" class="settings__ai-status-row">
            <span class="settings__info-label">Base URL</span>
            <span class="settings__info-value">{{ aiStatus.baseUrl }}</span>
          </div>
          <div v-if="aiStatus.model" class="settings__ai-status-row">
            <span class="settings__info-label">模型</span>
            <span class="settings__info-value">{{ aiStatus.model }}</span>
          </div>
        </div>

        <!-- AI 对话配置：动态提供商列表 -->
        <div class="settings__section">
          <div class="settings__section-header">
            <div class="settings__section-title">对话模型配置</div>
            <button class="settings__btn settings__btn--primary" @click="openAddProviderDialog">+ 添加提供商</button>
          </div>
          <p class="settings__desc">支持添加任意数量的 OpenAI 兼容提供商（DeepSeek、Moonshot、Ollama 等），配置后立即生效</p>

          <div v-if="aiProviderLoading" class="settings__loading">加载中...</div>
          <div v-else-if="aiProviders.length === 0" class="settings__empty">
            暂无提供商配置，点击上方按钮添加
          </div>
          <div v-else class="settings__provider-list">
            <div
              v-for="provider in aiProviders"
              :key="provider.id"
              class="settings__provider-card"
              :class="{ 'settings__provider-card--active': provider.isActive === 1 }"
            >
              <div class="settings__provider-head">
                <div>
                  <div class="settings__provider-title">{{ provider.name }}</div>
                  <div class="settings__provider-desc">{{ provider.baseUrl }} · {{ provider.model }}</div>
                </div>
                <span v-if="provider.isActive === 1" class="settings__provider-badge settings__provider-badge--ok">激活中</span>
                <span v-else class="settings__provider-badge">未激活</span>
              </div>
              <div class="settings__provider-actions">
                <button class="settings__btn settings__btn--secondary" @click="openEditProviderDialog(provider)">编辑</button>
                <button
                  class="settings__btn settings__btn--secondary"
                  :disabled="aiProviderTesting === provider.id"
                  @click="handleTestProvider(provider.id)"
                >{{ aiProviderTesting === provider.id ? '测试中...' : '测试连接' }}</button>
                <button
                  v-if="provider.isActive !== 1"
                  class="settings__btn settings__btn--primary"
                  @click="handleActivateProvider(provider.id)"
                >设为激活</button>
                <button
                  v-if="provider.isActive !== 1"
                  class="settings__btn settings__btn--danger"
                  @click="handleDeleteProvider(provider.id)"
                >删除</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 添加/编辑提供商弹窗 -->
        <div v-if="aiProviderDialogVisible" class="settings__dialog-overlay" @click.self="aiProviderDialogVisible = false">
          <div class="settings__dialog">
            <div class="settings__dialog-header">
              <span>{{ aiProviderFormId ? '编辑提供商' : '添加提供商' }}</span>
              <button class="settings__dialog-close" @click="aiProviderDialogVisible = false">&times;</button>
            </div>
            <div class="settings__dialog-body">
              <div class="settings__field">
                <label class="settings__label">名称</label>
                <input v-model="aiProviderForm.name" type="text" class="settings__input" placeholder="如：阿里百炼、DeepSeek 直连" />
              </div>
              <div class="settings__field">
                <label class="settings__label">API Key{{ aiProviderFormId ? '（留空则不修改）' : '' }}</label>
                <div class="settings__input-wrap">
                  <input v-model="aiProviderForm.apiKey" :type="showProviderApiKey ? 'text' : 'password'" class="settings__input" placeholder="sk-..." />
                  <button class="settings__eye-btn" @click="showProviderApiKey = !showProviderApiKey" tabindex="-1">{{ showProviderApiKey ? '隐藏' : '显示' }}</button>
                </div>
              </div>
              <div class="settings__field">
                <label class="settings__label">API Base URL</label>
                <input v-model="aiProviderForm.baseUrl" type="text" class="settings__input" placeholder="https://api.openai.com/v1" />
                <p class="settings__hint">可填写域名或带 /v1 的地址，系统会自动处理路径</p>
              </div>
              <div class="settings__field">
                <label class="settings__label">模型名称</label>
                <div class="settings__model-row">
                  <select v-if="aiProviderModels.length > 0" v-model="aiProviderForm.model" class="settings__input settings__select">
                    <option v-for="m in aiProviderModels" :key="m" :value="m">{{ m }}</option>
                  </select>
                  <input v-else v-model="aiProviderForm.model" type="text" class="settings__input" placeholder="deepseek-chat" />
                  <button class="settings__btn settings__btn--secondary" :disabled="aiProviderModelLoading !== null" @click="handleFetchProviderModels">
                    {{ aiProviderModelLoading !== null ? '获取中...' : '获取模型' }}
                  </button>
                </div>
              </div>
            </div>
            <div class="settings__dialog-footer">
              <button class="settings__btn settings__btn--secondary" @click="aiProviderDialogVisible = false">取消</button>
              <button class="settings__btn settings__btn--primary" :disabled="aiProviderSaving" @click="handleSaveProvider">
                {{ aiProviderSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Embedding 配置 -->
        <div class="settings__section">
          <div class="settings__section-header">
            <div class="settings__section-title">Embedding 模型配置</div>
            <button
              class="settings__toggle-btn"
              @click="showEmbeddingConfig = !showEmbeddingConfig"
            >
              {{ showEmbeddingConfig ? '收起' : '高级配置' }}
            </button>
          </div>
          <p class="settings__desc">
            配置向量嵌入模型（用于 RAG 知识库）。默认共用对话模型的配置。
            <strong>注意：修改后需要重启服务才能生效。</strong>
          </p>
          <div class="settings__form">
            <!-- 折叠内容 -->
            <div v-if="showEmbeddingConfig" class="settings__collapse-content">
              <div class="settings__field">
                <label class="settings__label">API Key <span class="settings__label-hint">(留空则使用对话模型的 API Key)</span></label>
                <div class="settings__input-wrap">
                  <input
                    v-model="embeddingApiKey"
                    :type="showEmbeddingApiKey ? 'text' : 'password'"
                    class="settings__input"
                    placeholder="留空则使用对话模型的 API Key"
                    :disabled="embeddingSaving"
                  />
                  <button class="settings__eye-btn" @click="showEmbeddingApiKey = !showEmbeddingApiKey" tabindex="-1">
                    {{ showEmbeddingApiKey ? '隐藏' : '显示' }}
                  </button>
                </div>
              </div>

              <div class="settings__field">
                <label class="settings__label">API Base URL <span class="settings__label-hint">(留空则使用对话模型的 Base URL)</span></label>
                <input
                  v-model="embeddingBaseUrl"
                  type="text"
                  class="settings__input"
                  placeholder="留空则使用对话模型的 Base URL"
                  :disabled="embeddingSaving"
                />
              </div>
            </div>

            <div class="settings__field">
              <label class="settings__label">模型名称</label>
              <input
                v-model="embeddingModel"
                type="text"
                class="settings__input"
                placeholder="Embedding 模型名称，如 text-embedding-v3"
                :disabled="embeddingSaving"
              />
            </div>

            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="embeddingSaving"
                @click="handleResetEmbeddingConfig"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="embeddingSaving"
                @click="handleSaveEmbeddingConfig"
              >
                {{ embeddingSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- AI客服配置 -->
      <div v-if="activeMenu === 'prompt'" class="settings__panel">
        <div class="settings__panel-title">AI客服配置</div>

        <!-- 账号级 AI 回复 -->
        <div class="settings__section">
          <div class="settings__section-title">所有商品 AI 回复</div>
          <p class="settings__desc">当前账号所有商品共用这个开关和模板；商品固定资料只补充单个商品的差异内容。</p>
          <div class="settings__form">
            <div class="settings__field">
              <label class="settings__label">闲鱼账号</label>
              <select
                v-model.number="selectedAiReplyAccountId"
                class="settings__input settings__select"
                :disabled="globalAiReplyConfigLoading || globalAiReplyConfigSaving"
                @change="loadGlobalAiReplyConfig"
              >
                <option v-if="aiReplyAccounts.length === 0" :value="null">暂无账号</option>
                <option v-for="account in aiReplyAccounts" :key="account.id" :value="account.id">
                  {{ getAccountDisplayName(account) }}
                </option>
              </select>
            </div>

            <label class="settings__switch-row">
              <span>
                <strong>开启所有商品 AI 回复</strong>
                <small>{{ globalAiReplyEnabled ? '当前账号所有商品会触发 AI 回复' : '当前账号所有商品不会触发 AI 回复' }}</small>
              </span>
              <input
                ref="globalAiReplyEnabledInput"
                v-model="globalAiReplyEnabled"
                type="checkbox"
                :disabled="globalAiReplyConfigLoading || globalAiReplyConfigSaving || !selectedAiReplyAccountId"
              />
            </label>

            <div class="settings__field">
              <label class="settings__label">全局 AI 回复模板</label>
              <textarea
                v-model="globalAiReplyTemplate"
                class="settings__textarea settings__textarea--compact"
                placeholder="填写通用回复口径，如称呼、售后政策、发货说明、议价规则、禁止承诺内容等"
                :disabled="globalAiReplyConfigLoading || globalAiReplyConfigSaving || !selectedAiReplyAccountId"
                maxlength="5000"
                rows="7"
              ></textarea>
              <p class="settings__hint">{{ globalAiReplyTemplate.length }} / 5000</p>
            </div>

            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--primary"
                :disabled="globalAiReplyConfigLoading || globalAiReplyConfigSaving || !selectedAiReplyAccountId"
                @click="handleSaveGlobalAiReplyConfig"
              >
                {{ globalAiReplyConfigSaving ? '保存中...' : '保存账号配置' }}
              </button>
            </div>
          </div>
        </div>

        <!-- AI回复服务选择 -->
        <div class="settings__section">
          <div class="settings__section-title">AI回复使用服务</div>
          <p class="settings__desc">配置了多个提供商时，自动回复和AI测试会使用激活的提供商。可在"AI 服务配置"页面切换。</p>
          <div class="settings__provider-choice">
            <label
              v-for="provider in aiProviders"
              :key="provider.id"
              class="settings__choice-card"
              :class="{ 'settings__choice-card--active': provider.isActive === 1 }"
              @click="handleActivateProvider(provider.id)"
            >
              <input type="radio" :checked="provider.isActive === 1" />
              <span>
                <strong>{{ provider.name }}</strong>
                <small>{{ provider.model }}</small>
              </span>
            </label>
          </div>
          <p v-if="aiProviders.length === 0" class="settings__hint">暂无提供商，请先在"AI 服务配置"中添加</p>
        </div>

        <!-- 系统提示词 -->
        <div class="settings__section">
          <div class="settings__section-title">系统提示词</div>
          <p class="settings__desc">配置 AI 智能回复的系统提示词，用于设定 AI 的角色和行为规则</p>
          <div class="settings__form">
            <textarea
              v-model="sysPromptValue"
              class="settings__textarea"
              placeholder="请输入系统提示词"
              :disabled="sysPromptSaving"
              rows="8"
            ></textarea>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="sysPromptSaving"
                @click="handleResetSysPrompt"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="sysPromptSaving"
                @click="handleSaveSysPrompt"
              >
                {{ sysPromptSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 相似度阈值 -->
        <div class="settings__section">
          <div class="settings__section-title">相似度阈值</div>
          <p class="settings__desc">
            配置 RAG 向量搜索的相似度阈值。值越小，匹配越宽松，会返回更多相关度较低的结果；值越大，匹配越严格，只返回高度相关的结果。
          </p>
          <div class="settings__form">
            <div class="settings__field">
              <label class="settings__label">相似度阈值 (0-1)</label>
              <input
                v-model.number="similarityThreshold"
                type="number"
                class="settings__input"
                placeholder="0.1"
                :disabled="similarityThresholdSaving"
                min="0"
                max="1"
                step="0.01"
              />
              <p class="settings__hint">推荐值：0.1（宽松）到 0.5（严格）之间</p>
            </div>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="similarityThresholdSaving"
                @click="handleResetSimilarityThreshold"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="similarityThresholdSaving"
                @click="handleSaveSimilarityThreshold"
              >
                {{ similarityThresholdSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 发货配置 -->
      <div v-if="activeMenu === 'delivery'" class="settings__panel">
        <DeliverySettings />
      </div>

      <!-- 消息配置 -->
      <div v-if="activeMenu === 'message'" class="settings__panel">
        <div class="settings__panel-title">消息配置</div>
        <div class="settings__section">
          <div class="settings__section-title">官方闲鱼 IM</div>
          <p class="settings__desc">开启后，在线消息页面会显示官方 IM 辅助入口，可直接新窗口打开官方页面。</p>
          <div class="settings__form">
            <label class="settings__operation-row">
              <span>
                <strong>显示官方 IM 辅助入口</strong>
                <small>目标地址：{{ GOOFISH_IM_URL }}，使用浏览器当前登录态</small>
              </span>
              <span class="settings__switch">
                <input v-model="goofishImEmbedEnabled" type="checkbox" />
                <span class="settings__switch-track"></span>
                <span class="settings__switch-thumb"></span>
              </span>
            </label>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--primary"
                :disabled="goofishImEmbedSaving"
                @click="handleSaveMessageConfig"
              >
                {{ goofishImEmbedSaving ? '保存中...' : '保存配置' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 商品操作 -->
      <div v-if="activeMenu === 'goods'" class="settings__panel">
        <div class="settings__panel-title">商品操作</div>
        <div class="settings__section">
          <div class="settings__section-title">远程商品操作开关</div>
          <p class="settings__desc">下架和删除会调用闲鱼接口并同步更新本地商品记录；改价只对鱼小铺账号开放，不提供总开关。</p>
          <div class="settings__form">
            <label class="settings__operation-row">
              <span>
                <strong>允许下架商品</strong>
                <small>普通账号走商品详情页接口，鱼小铺账号走工作台接口</small>
              </span>
              <span class="settings__switch">
                <input v-model="goodsOffShelfEnabled" type="checkbox" />
                <span class="settings__switch-track"></span>
                <span class="settings__switch-thumb"></span>
              </span>
            </label>
            <label class="settings__operation-row">
              <span>
                <strong>允许删除商品</strong>
                <small>只删除项目内已同步且属于当前账号的商品</small>
              </span>
              <span class="settings__switch">
                <input v-model="goodsDeleteEnabled" type="checkbox" />
                <span class="settings__switch-track"></span>
                <span class="settings__switch-thumb"></span>
              </span>
            </label>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--primary"
                :disabled="goodsOperationSaving"
                @click="handleSaveGoodsOperationConfig"
              >
                {{ goodsOperationSaving ? '保存中...' : '保存配置' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 关于 -->
      <div v-if="activeMenu === 'about'" class="settings__panel">
        <div class="settings__panel-title">关于</div>

        <!-- 更新教程 -->
        <div class="settings__section">
          <div class="settings__section-title">更新教程</div>
          <p class="settings__desc">按照以下步骤更新到最新版本：</p>

          <div class="settings__tutorial">
            <div class="settings__tutorial-step">
              <div class="settings__step-number">1</div>
              <div class="settings__step-content">
                <div class="settings__step-title">停止当前容器</div>
                <div class="settings__code-block">
                  <code>docker stop xianyu-assistant</code>
                </div>
              </div>
            </div>

            <div class="settings__tutorial-step">
              <div class="settings__step-number">2</div>
              <div class="settings__step-content">
                <div class="settings__step-title">删除旧容器</div>
                <div class="settings__code-block">
                  <code>docker rm xianyu-assistant</code>
                </div>
              </div>
            </div>

            <div class="settings__tutorial-step">
              <div class="settings__step-number">3</div>
              <div class="settings__step-content">
                <div class="settings__step-title">拉取最新镜像</div>
                <div class="settings__code-block">
                  <pre><code>docker pull ghcr.io/9rebels/xianyuassistant-pro:latest</code></pre>
                </div>
              </div>
            </div>

            <div class="settings__tutorial-step">
              <div class="settings__step-number">4</div>
              <div class="settings__step-content">
                <div class="settings__step-title">启动新容器（使用之前的数据目录）</div>
                <div class="settings__code-block">
                  <pre><code>docker run -d \
  --name xianyu-assistant \
  -p 12400:12400 \
  -v $(pwd)/data/dbdata:/app/dbdata \
  -v $(pwd)/data/logs:/app/logs \
  --restart unless-stopped \
  ghcr.io/9rebels/xianyuassistant-pro:latest</code></pre>
                </div>
                <p class="settings__step-tip">💡 提示：数据目录路径保持不变，数据会自动迁移</p>
                <p class="settings__step-tip">滑块失败和二维码验证截图会保存到 <code>data/dbdata/captcha-debug</code></p>
              </div>
            </div>
          </div>

          <div class="settings__warning-box">
            <div class="settings__warning-icon">⚠️</div>
            <div class="settings__warning-content">
              <strong>重要提示：</strong>
              <ul>
                <li>更新前请确保数据目录路径与之前一致，否则数据会丢失</li>
                <li>建议定期备份 <code>data/dbdata</code> 目录</li>
                <li>滑块调试截图位于 <code>data/dbdata/captcha-debug</code> 目录</li>
                <li>Windows 用户请将 <code>$(pwd)</code> 替换为实际路径，如 <code>D:\data</code></li>
              </ul>
            </div>
          </div>
        </div>

        <!-- 开源地址 -->
        <div class="settings__section">
          <div class="settings__section-title">开源地址</div>
          <p class="settings__desc">本项目已开源，欢迎 Star 支持</p>
          <div class="settings__github-link">
            <a href="https://github.com/MainClassxxx/XianYuAssistant" target="_blank" class="settings__link">
              https://github.com/MainClassxxx/XianYuAssistant
            </a>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.settings {
  --s-surface: var(--app-surface-strong);
  --s-surface-muted: var(--app-bg-muted);
  --s-border: var(--app-border);
  --s-border-strong: var(--app-border-strong);
  --s-text-1: var(--app-text);
  --s-text-2: var(--app-text-muted);
  --s-text-3: var(--app-text-soft);
  --s-primary: var(--app-primary);
  --s-primary-hover: var(--app-primary-hover);
  --s-accent: var(--app-accent);
  --s-success: var(--app-success);
  --s-warning: var(--app-warning);
  --s-danger: var(--app-danger);
  --s-neutral-soft: color-mix(in srgb, var(--app-text-soft) 14%, transparent);
  --s-success-soft: color-mix(in srgb, var(--app-success) 16%, transparent);
  --s-danger-soft: color-mix(in srgb, var(--app-danger) 16%, transparent);
  --s-field-bg: color-mix(in srgb, var(--app-text) 4%, transparent);
  --s-field-bg-strong: color-mix(in srgb, var(--app-text) 6%, transparent);
  --s-field-bg-soft: color-mix(in srgb, var(--app-text) 2%, transparent);
  --s-field-border: color-mix(in srgb, var(--app-text) 10%, transparent);
  --s-field-border-strong: color-mix(in srgb, var(--app-text) 15%, transparent);
  --s-card-bg: color-mix(in srgb, var(--app-text) 1.5%, transparent);
  --s-card-border: color-mix(in srgb, var(--app-text) 8%, transparent);
  --s-info-bg: color-mix(in srgb, var(--app-accent) 10%, transparent);
  --s-info-border: color-mix(in srgb, var(--app-accent) 36%, transparent);
  --s-warning-bg: color-mix(in srgb, var(--app-warning) 12%, transparent);
  --s-warning-border: color-mix(in srgb, var(--app-warning) 24%, transparent);
  --s-warning-strong: color-mix(in srgb, var(--app-warning) 72%, var(--app-primary));
  --s-thumb-shadow: 0 1px 3px color-mix(in srgb, var(--app-text) 15%, transparent);
  display: flex;
  gap: 24px;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  box-sizing: border-box;
}

.settings *,
.settings *::before,
.settings *::after {
  box-sizing: border-box;
}

/* 左侧菜单 */
.settings__sidebar {
  width: 200px;
  flex-shrink: 0;
  background: var(--s-surface);
  border-radius: 12px;
  border: 1px solid var(--s-border);
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.settings__sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--s-text-1);
  padding: 0 12px;
  margin-bottom: 16px;
}

.settings__menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings__menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--s-text-2);
}

.settings__menu-item:hover {
  background: var(--s-field-bg);
}

.settings__menu-item--active {
  background: var(--s-field-bg-strong);
  color: var(--s-text-1);
  font-weight: 500;
}

.settings__menu-icon {
  font-size: 16px;
}

.settings__menu-label {
  font-size: 14px;
}

/* 右侧内容 */
.settings__content {
  flex: 1;
  min-width: 0;
  background: var(--s-surface);
  border-radius: 12px;
  border: 1px solid var(--s-border);
  padding: 24px;
  overflow-y: auto;
}

.settings__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__panel-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--s-text-1);
}

.settings__section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--s-border);
}

.settings__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.settings__section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--s-text-1);
  margin-bottom: 0;
}

.settings__toggle-btn {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--s-text-1);
  background: var(--s-field-bg);
  border: 1px solid var(--s-field-border);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.settings__toggle-btn:hover {
  background: var(--s-field-bg-strong);
  border-color: var(--s-field-border-strong);
}

.settings__collapse-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.settings__desc {
  font-size: 13px;
  color: var(--s-text-2);
  margin: 0;
}

/* Loading */
.settings__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: var(--s-text-3);
  font-size: 13px;
}

.settings__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--s-field-border);
  border-top-color: var(--s-text-1);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Info */
.settings__info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings__info-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.settings__info-label {
  font-size: 13px;
  color: var(--s-text-3);
  min-width: 100px;
  flex-shrink: 0;
}

.settings__info-value {
  font-size: 14px;
  color: var(--s-text-1);
  font-weight: 500;
}

/* Form */
.settings__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__provider-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.settings__provider-card {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--s-card-border);
  border-radius: 10px;
  background: var(--s-card-bg);
}

.settings__provider-card--active {
  border-color: var(--s-primary);
  box-shadow: 0 0 0 1px var(--s-primary);
}

.settings__provider-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 12px;
}

.settings__loading,
.settings__empty {
  padding: 24px;
  text-align: center;
  color: var(--s-text-3);
  font-size: 14px;
}

.settings__provider-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.settings__provider-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--s-text-1);
}

.settings__provider-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--s-text-2);
}

.settings__provider-badge {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--s-text-3);
  background: var(--s-neutral-soft);
}

.settings__provider-badge--ok {
  color: var(--s-success);
  background: var(--s-success-soft);
}

.settings__model-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  gap: 8px;
}

.settings__provider-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.settings__dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.settings__dialog {
  width: 520px;
  max-width: 90vw;
  max-height: 85vh;
  overflow-y: auto;
  background: var(--s-card-bg, #fff);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}

.settings__dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--s-card-border);
  font-size: 16px;
  font-weight: 600;
}

.settings__dialog-close {
  background: none;
  border: none;
  font-size: 22px;
  cursor: pointer;
  color: var(--s-text-3);
  line-height: 1;
}

.settings__dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.settings__dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  border-top: 1px solid var(--s-card-border);
}

.settings__provider-choice {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.settings__choice-card {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--s-field-border);
  border-radius: 10px;
  cursor: pointer;
  background: var(--s-card-bg);
}

.settings__choice-card--active {
  border-color: var(--s-text-1);
  background: var(--s-surface);
}

.settings__choice-card input {
  margin-top: 2px;
  flex-shrink: 0;
}

.settings__choice-card span {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.settings__choice-card strong {
  font-size: 14px;
  color: var(--s-text-1);
}

.settings__choice-card small {
  min-width: 0;
  color: var(--s-text-2);
  font-size: 12px;
  overflow-wrap: anywhere;
}

.settings__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings__label {
  font-size: 13px;
  font-weight: 500;
  color: var(--s-text-1);
}

.settings__label-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--s-text-3);
}

.settings__link {
  color: var(--s-accent);
  text-decoration: none;
}

.settings__link:hover {
  text-decoration: underline;
}

.settings__github-link {
  padding: 12px 0;
  font-size: 14px;
}

.settings__github-link .settings__link {
  font-weight: 500;
  word-break: break-all;
}

.settings__input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.settings__input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  font-size: 14px;
  color: var(--s-text-1);
  background: var(--s-field-bg-soft);
  border: 1px solid var(--s-field-border);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.settings__hint {
  margin: 2px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--s-text-3);
}

.settings__input:focus {
  border-color: var(--s-text-1);
  background: var(--s-surface);
}

.settings__input::placeholder {
  color: var(--s-text-3);
}

.settings__input:disabled {
  opacity: 0.5;
}

.settings__select {
  appearance: auto;
  cursor: pointer;
}

.settings__eye-btn {
  position: absolute;
  right: 10px;
  background: none;
  border: none;
  font-size: 12px;
  color: var(--s-text-3);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: color 0.2s;
}

.settings__eye-btn:hover {
  color: var(--s-text-1);
}

.settings__textarea {
  width: 100%;
  min-height: 200px;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--s-text-1);
  background: var(--s-field-bg-soft);
  border: 1px solid var(--s-field-border);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
  resize: vertical;
  font-family: inherit;
}

.settings__textarea:focus {
  border-color: var(--s-text-1);
  background: var(--s-surface);
}

.settings__textarea::placeholder {
  color: var(--s-text-3);
}

.settings__textarea:disabled {
  opacity: 0.5;
}

.settings__textarea--compact {
  min-height: 150px;
}

.settings__switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid var(--s-field-border);
  border-radius: 8px;
  background: var(--s-field-bg-soft);
}

.settings__switch-row span {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings__switch-row strong {
  font-size: 14px;
  color: var(--s-text-1);
}

.settings__switch-row small {
  font-size: 12px;
  color: var(--s-text-2);
}

.settings__switch-row input {
  width: 42px;
  height: 24px;
  flex-shrink: 0;
  accent-color: var(--s-primary);
}

/* Actions */
.settings__actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
}

.settings__btn {
  height: 36px;
  padding: 0 20px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.settings__btn--primary {
  background: var(--s-primary);
  color: var(--vt-c-white);
}

.settings__btn--primary:hover {
  background: var(--s-primary-hover);
}

.settings__btn--primary:active {
  transform: scale(0.97);
}

.settings__btn--secondary {
  background: var(--s-field-bg);
  color: var(--s-text-1);
  border: 1px solid var(--s-field-border);
}

.settings__btn--secondary:hover {
  background: var(--s-field-bg-strong);
}

.settings__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.settings__btn--danger {
  background: var(--s-danger);
  color: var(--vt-c-white);
}

.settings__btn--danger:hover {
  background: color-mix(in srgb, var(--s-danger) 78%, var(--vt-c-white));
}

.settings__btn--danger:active {
  transform: scale(0.97);
}

.settings__btn--small {
  height: 30px;
  padding: 0 12px;
  font-size: 12px;
}

/* AI Status */
.settings__ai-status {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: var(--s-field-bg-soft);
  border-radius: 8px;
}

.settings__ai-status-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.settings__ai-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.settings__ai-status-badge--ok {
  background: var(--s-success-soft);
  color: var(--s-success);
}

.settings__ai-status-badge--off {
  background: var(--s-danger-soft);
  color: var(--s-danger);
}

.settings__ai-status-msg {
  font-weight: 400;
  color: var(--s-text-2);
}

/* Logout */
.settings__logout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__logout-text {
  font-size: 14px;
  color: var(--s-text-2);
  margin: 0 0 12px 0;
}

.settings__device-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.settings__device-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--s-border);
  border-radius: 10px;
  background: var(--s-card-bg);
}

.settings__device-card--current {
  border-color: color-mix(in srgb, var(--s-success) 38%, transparent);
  background: var(--s-success-soft);
}

.settings__device-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings__device-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.settings__device-title {
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--s-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.settings__device-meta,
.settings__device-time {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  font-size: 12px;
  color: var(--s-text-2);
}

/* QR Code */
.settings__qrcode-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.settings__qrcode {
  max-width: 300px;
  width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: var(--s-thumb-shadow);
}

/* Responsive */
@media (max-width: 768px) {
  .settings {
    flex-direction: column;
    gap: 16px;
    width: 100%;
    max-width: 100%;
    overflow-x: hidden;
  }

  .settings__sidebar {
    width: 100%;
    max-width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
    flex-shrink: 0;
  }

  .settings__sidebar-title {
    width: 100%;
    margin-bottom: 8px;
  }

  .settings__menu {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }

  .settings__menu-item {
    padding: 8px 12px;
  }

  .settings__content {
    padding: 16px;
    width: 100%;
    max-width: 100%;
    min-height: 0;
  }

  .settings__provider-grid,
  .settings__provider-choice {
    grid-template-columns: 1fr;
  }

  .settings__provider-card {
    padding: 14px;
  }

  .settings__qrcode {
    max-width: 250px;
  }
}

@media (max-width: 480px) {
  .settings__info-row,
  .settings__ai-status-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .settings__actions {
    flex-direction: column;
  }

  .settings__btn {
    width: 100%;
  }

  .settings__provider-head {
    flex-direction: column;
  }

  .settings__switch-row {
    align-items: flex-start;
  }

  .settings__model-row {
    grid-template-columns: 1fr;
  }

  .settings__device-card {
    align-items: stretch;
    flex-direction: column;
  }

  .settings__qrcode {
    max-width: 200px;
  }

  .settings__menu-item {
    flex: 1 1 calc(50% - 8px);
    min-width: 0;
  }

  .settings__menu-label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* 更新教程样式 */
.settings__tutorial {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.settings__tutorial-step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.settings__step-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--s-accent) 82%, var(--vt-c-white)) 0%,
    color-mix(in srgb, var(--s-accent) 56%, #6f42c1) 100%
  );
  color: var(--vt-c-white);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.settings__step-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings__step-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--s-text-1);
}

.settings__code-block {
  background: var(--s-surface-muted);
  border: 1px solid var(--s-card-border);
  border-radius: 8px;
  padding: 12px 16px;
  overflow-x: auto;
  max-width: 100%;
}

.settings__code-block pre {
  margin: 0;
  padding: 0;
  background: transparent;
  font-family: inherit;
}

.settings__code-block code {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 13px;
  color: var(--s-text-1);
  display: block;
}

.settings__step-tip {
  font-size: 12px;
  color: var(--s-text-2);
  margin: 0;
  padding: 8px 12px;
  background: var(--s-info-bg);
  border-left: 3px solid var(--s-info-border);
  border-radius: 4px;
}

.settings__warning-box {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding: 16px;
  background: var(--s-warning-bg);
  border: 1px solid var(--s-warning-border);
  border-radius: 8px;
}

.settings__warning-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.settings__warning-content {
  flex: 1;
  font-size: 13px;
  color: var(--s-text-1);
}

.settings__warning-content strong {
  display: block;
  margin-bottom: 8px;
  color: var(--s-warning-strong);
}

.settings__warning-content ul {
  margin: 0;
  padding-left: 20px;
}

.settings__warning-content li {
  margin-bottom: 6px;
  color: var(--s-text-2);
}

.settings__warning-content li:last-child {
  margin-bottom: 0;
}

.settings__warning-content code {
  background: var(--s-field-bg-strong);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
}

.settings__section-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settings__operation-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--s-border);
  border-radius: 8px;
  background: var(--s-surface);
}

.settings__operation-row > span:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.settings__operation-row strong {
  font-size: 14px;
  color: var(--s-text-1);
}

.settings__operation-row small {
  font-size: 12px;
  color: var(--s-text-2);
}

.settings__status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
  background: var(--s-neutral-soft);
  color: var(--s-text-2);
}

.settings__status-badge--success {
  background: var(--s-success-soft);
  color: var(--s-success);
}

.settings__status-badge--danger {
  background: var(--s-danger-soft);
  color: var(--s-danger);
}

.settings__switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.settings__switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.settings__switch-track {
  width: 40px;
  height: 22px;
  background: color-mix(in srgb, var(--s-text-3) 32%, transparent);
  border-radius: 11px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.settings__switch-thumb {
  position: absolute;
  left: 2px;
  top: 50%;
  transform: translateY(-50%);
  width: 18px;
  height: 18px;
  background: var(--s-surface);
  border-radius: 50%;
  box-shadow: var(--s-thumb-shadow);
  transition: left 0.2s;
}

.settings__switch input:checked + .settings__switch-track {
  background: var(--s-accent);
}

.settings__switch input:checked + .settings__switch-track + .settings__switch-thumb {
  left: 20px;
}

.settings__switch input:disabled + .settings__switch-track {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 通知开关表格 */
.settings__notify-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid var(--s-border);
  border-radius: 8px;
  overflow: hidden;
  margin-top: 12px;
}

.settings__notify-th {
  background: var(--s-field-bg-soft);
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--s-text-1);
  text-align: left;
  border-bottom: 1px solid var(--s-border);
}

.settings__notify-th:last-child {
  text-align: center;
  width: 80px;
}

.settings__notify-tr {
  transition: background 0.2s;
}

.settings__notify-tr:hover {
  background: var(--s-field-bg-soft);
}

.settings__notify-td {
  padding: 14px 16px;
  font-size: 13px;
  color: var(--s-text-1);
  border-bottom: 1px solid color-mix(in srgb, var(--s-border) 82%, transparent);
}

.settings__notify-tr:last-child .settings__notify-td {
  border-bottom: none;
}

.settings__notify-td:last-child {
  text-align: center;
}

.settings__notify-td--desc {
  color: var(--s-text-2);
  font-size: 12px;
}
</style>

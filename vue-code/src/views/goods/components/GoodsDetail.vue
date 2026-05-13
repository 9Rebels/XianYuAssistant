<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import {
  getGoodsDetail,
  deleteItem,
  offShelfItem,
  republishItem,
  remoteDeleteItem,
  updateItemPrice,
  updateItemStock,
  updateAutoDeliveryStatus,
  updateAutoReplyStatus,
  getAutoReplyConfig,
  updateAutoReplyConfig
} from '@/api/goods'
import {
  getAutoDeliveryConfig,
  saveOrUpdateAutoDeliveryConfig,
  type SaveAutoDeliveryConfigReq
} from '@/api/auto-delivery-config'
import { getFixedMaterial, saveFixedMaterial } from '@/api/ai'
import { listAutoReplyRules, saveAutoReplyRule, deleteAutoReplyRule, type AutoReplyRule, type AutoReplyRuleReq } from '@/api/auto-reply-rule'
import { getKamiConfigsByAccountId, type KamiConfig } from '@/api/kami-config'
import { showSuccess, showError, showInfo, showConfirm } from '@/utils'
import { getGoodsStatusText, formatPrice, formatTime } from '@/utils'
import type { GoodsItemWithConfig } from '@/api/goods'

import IconImage from '@/components/icons/IconImage.vue'
import IconSend from '@/components/icons/IconSend.vue'
import IconRobot from '@/components/icons/IconRobot.vue'
import IconSparkle from '@/components/icons/IconSparkle.vue'
import IconPackage from '@/components/icons/IconPackage.vue'
import IconTrash from '@/components/icons/IconTrash.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import IconEdit from '@/components/icons/IconEdit.vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'
import { ElMessageBox } from 'element-plus'

const DEFAULT_API_ALLOCATE_URL = 'http://127.0.0.1:3000/api/delivery/v1/allocate'
const DEFAULT_API_CONFIRM_URL = 'http://127.0.0.1:3000/api/delivery/v1/confirm'
const DEFAULT_API_RETURN_URL = 'http://127.0.0.1:3000/api/delivery/v1/return'

interface Props {
  modelValue: boolean
  goodsId: string
  accountId: number | null
  isFishShopAccount?: boolean
  offShelfEnabled?: boolean
  deleteEnabled?: boolean
  initialConfigAction?: 'delivery' | 'reply' | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}

const props = withDefaults(defineProps<Props>(), {
  isFishShopAccount: false,
  offShelfEnabled: true,
  deleteEnabled: true
})
const emit = defineEmits<Emits>()

const loading = ref(false)
const goodsDetail = ref<GoodsItemWithConfig | null>(null)
const currentImageIndex = ref(0)
const images = ref<string[]>([])
const deliveryDialogVisible = ref(false)
const replyDialogVisible = ref(false)
const deliverySaving = ref(false)
const replySaving = ref(false)
const actionLoading = ref(false)
const kamiConfigOptions = ref<KamiConfig[]>([])
const ruleSaving = ref(false)
const replyRules = ref<AutoReplyRule[]>([])

const deliveryForm = ref({
  enabled: false,
  deliveryMode: 1,
  ruleName: '',
  matchKeyword: '',
  matchType: 1,
  priority: 100,
  stock: -1,
  stockWarnThreshold: 0,
  autoDeliveryContent: '',
  kamiConfigIds: '',
  kamiDeliveryTemplate: '',
  autoDeliveryImageUrl: '',
  postDeliveryText: '',
  autoConfirmShipment: false,
  deliveryDelaySeconds: 0,
  triggerPaymentEnabled: true,
  triggerBargainEnabled: false,
  apiAllocateUrl: DEFAULT_API_ALLOCATE_URL,
  apiConfirmUrl: DEFAULT_API_CONFIRM_URL,
  apiReturnUrl: DEFAULT_API_RETURN_URL,
  apiHeaderValue: '',
  apiRequestExtras: '',
  apiDeliveryTemplate: ''
})

const replyForm = ref({
  enabled: false,
  contextOn: true,
  delaySeconds: 15,
  fixedMaterial: ''
})

const replyRuleForm = ref<AutoReplyRuleReq>({
  xianyuAccountId: 0,
  xyGoodsId: null,
  ruleName: '',
  keywords: '',
  matchType: 1,
  replyType: 1,
  replyContent: '',
  imageUrls: '',
  priority: 100,
  enabled: 1,
  isDefault: 0
})

const isMobile = ref(false)
const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

// 状态颜色
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

const displayGoodsTime = (time: string) => {
  return formatTime(time)
}

// 加载商品详情
const loadDetail = async () => {
  if (!props.goodsId) return
  loading.value = true
  try {
    const response = await getGoodsDetail(props.goodsId)
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value = response.data?.itemWithConfig || null

      if (goodsDetail.value?.item.infoPic) {
        try {
          const infoPicArray = JSON.parse(goodsDetail.value.item.infoPic)
          images.value = infoPicArray.map((pic: any) => pic.url)
        } catch (e) {
          images.value = []
        }
      }
      if (images.value.length === 0 && goodsDetail.value?.item.coverPic) {
        images.value = [goodsDetail.value.item.coverPic]
      }
      currentImageIndex.value = 0
    } else {
      throw new Error(response.msg || '获取商品详情失败')
    }
  } catch (error: any) {
    console.error('加载商品详情失败:', error)
  } finally {
    loading.value = false
  }
}

const loadKamiConfigOptions = async () => {
  if (!props.accountId) return
  try {
    const response = await getKamiConfigsByAccountId(props.accountId)
    if (response.code === 0 || response.code === 200) {
      kamiConfigOptions.value = response.data || []
    }
  } catch (error) {
    console.error('加载卡密配置失败:', error)
  }
}

// 配置自动发货
const handleConfigAutoDelivery = async () => {
  if (!goodsDetail.value || !props.accountId) {
    showInfo('请先选择账号和商品')
    return
  }

  deliveryForm.value = {
    enabled: goodsDetail.value.xianyuAutoDeliveryOn === 1,
    deliveryMode: goodsDetail.value.autoDeliveryType || 1,
    ruleName: '',
    matchKeyword: '',
    matchType: 1,
    priority: 100,
    stock: -1,
    stockWarnThreshold: 0,
    autoDeliveryContent: goodsDetail.value.autoDeliveryContent || '',
    kamiConfigIds: '',
    kamiDeliveryTemplate: '',
    autoDeliveryImageUrl: '',
    postDeliveryText: '',
    autoConfirmShipment: false,
    deliveryDelaySeconds: 0,
    triggerPaymentEnabled: true,
    triggerBargainEnabled: false,
    apiAllocateUrl: DEFAULT_API_ALLOCATE_URL,
    apiConfirmUrl: DEFAULT_API_CONFIRM_URL,
    apiReturnUrl: DEFAULT_API_RETURN_URL,
    apiHeaderValue: '',
    apiRequestExtras: '',
    apiDeliveryTemplate: ''
  }
  deliveryDialogVisible.value = true
  await loadKamiConfigOptions()

  try {
    const response = await getAutoDeliveryConfig({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId
    })
    if ((response.code === 0 || response.code === 200) && response.data) {
      deliveryForm.value.deliveryMode = response.data.deliveryMode || 1
      deliveryForm.value.ruleName = response.data.ruleName || ''
      deliveryForm.value.matchKeyword = response.data.matchKeyword || ''
      deliveryForm.value.matchType = response.data.matchType || 1
      deliveryForm.value.priority = response.data.priority ?? 100
      deliveryForm.value.stock = response.data.stock ?? -1
      deliveryForm.value.stockWarnThreshold = response.data.stockWarnThreshold ?? 0
      deliveryForm.value.autoDeliveryContent = response.data.autoDeliveryContent || ''
      deliveryForm.value.kamiConfigIds = response.data.kamiConfigIds || ''
      deliveryForm.value.kamiDeliveryTemplate = response.data.kamiDeliveryTemplate || ''
      deliveryForm.value.autoDeliveryImageUrl = response.data.autoDeliveryImageUrl || ''
      deliveryForm.value.postDeliveryText = response.data.postDeliveryText || ''
      deliveryForm.value.autoConfirmShipment = response.data.autoConfirmShipment === 1
      deliveryForm.value.deliveryDelaySeconds = response.data.deliveryDelaySeconds ?? 0
      deliveryForm.value.triggerPaymentEnabled = (response.data.triggerPaymentEnabled ?? 1) === 1
      deliveryForm.value.triggerBargainEnabled = (response.data.triggerBargainEnabled ?? 0) === 1
      deliveryForm.value.apiAllocateUrl = response.data.apiAllocateUrl || DEFAULT_API_ALLOCATE_URL
      deliveryForm.value.apiConfirmUrl = response.data.apiConfirmUrl || DEFAULT_API_CONFIRM_URL
      deliveryForm.value.apiReturnUrl = response.data.apiReturnUrl || DEFAULT_API_RETURN_URL
      deliveryForm.value.apiHeaderValue = response.data.apiHeaderValue || ''
      deliveryForm.value.apiRequestExtras = response.data.apiRequestExtras || ''
      deliveryForm.value.apiDeliveryTemplate = response.data.apiDeliveryTemplate || ''
    }
  } catch (error) {
    console.error('加载自动发货配置失败:', error)
  }
}

// 配置自动回复
const handleConfigAutoReply = async () => {
  if (!goodsDetail.value || !props.accountId) {
    showInfo('请先选择账号和商品')
    return
  }

  replyForm.value = {
    enabled: goodsDetail.value.xianyuAutoReplyOn === 1,
    contextOn: goodsDetail.value.xianyuAutoReplyContextOn !== 0,
    delaySeconds: 15,
    fixedMaterial: ''
  }
  replyDialogVisible.value = true

  try {
    await Promise.all([loadAutoReplyDelay(), loadFixedMaterial(), loadReplyRules()])
  } catch (error) {
    console.error('加载自动回复配置失败:', error)
  }
}

const loadAutoReplyDelay = async () => {
  if (!goodsDetail.value || !props.accountId) return
  const response = await getAutoReplyConfig({
    xianyuAccountId: props.accountId,
    xyGoodsId: goodsDetail.value.item.xyGoodId
  })
  if (response.code === 0 || response.code === 200) {
    replyForm.value.delaySeconds = response.data?.ragDelaySeconds ?? 15
  }
}

const loadFixedMaterial = async () => {
  if (!goodsDetail.value || !props.accountId) return
  const response = await getFixedMaterial({
    accountId: props.accountId,
    goodsId: goodsDetail.value.item.xyGoodId
  })
  const data = await response.json()
  if (data.code === 0 || data.code === 200) {
    replyForm.value.fixedMaterial = data.data?.fixedMaterial || ''
  }
}

const loadReplyRules = async () => {
  if (!goodsDetail.value || !props.accountId) return
  const response = await listAutoReplyRules({
    xianyuAccountId: props.accountId,
    xyGoodsId: goodsDetail.value.item.xyGoodId,
    includeGlobal: true
  })
  if (response.code === 0 || response.code === 200) {
    replyRules.value = response.data || []
    if (!replyRules.value.length) {
      resetReplyRuleForm()
      return
    }
    fillReplyRuleForm(replyRules.value[0]!)
  }
}

const fillReplyRuleForm = (rule: AutoReplyRule) => {
  replyRuleForm.value = {
    id: rule.id,
    xianyuAccountId: rule.xianyuAccountId,
    xyGoodsId: rule.xyGoodsId || null,
    ruleName: rule.ruleName || '',
    keywords: rule.keywords || '',
    matchType: rule.matchType || 1,
    replyType: rule.replyType || 1,
    replyContent: rule.replyContent || '',
    imageUrls: rule.imageUrls || '',
    priority: rule.priority ?? 100,
    enabled: rule.enabled ?? 1,
    isDefault: rule.isDefault ?? 0
  }
}

const resetReplyRuleForm = () => {
  replyRuleForm.value = {
    xianyuAccountId: props.accountId || 0,
    xyGoodsId: goodsDetail.value?.item.xyGoodId || null,
    ruleName: '',
    keywords: '',
    matchType: 1,
    replyType: 1,
    replyContent: '',
    imageUrls: '',
    priority: 100,
    enabled: 1,
    isDefault: 0
  }
}

const saveDeliverySettings = async () => {
  if (!goodsDetail.value || !props.accountId || deliverySaving.value) return

  if (
    deliveryForm.value.enabled &&
    deliveryForm.value.deliveryMode === 1 &&
    !deliveryForm.value.autoDeliveryContent.trim()
  ) {
    showInfo('请输入自动发货内容')
    return
  }
  if (deliveryForm.value.enabled && deliveryForm.value.deliveryMode === 2 && !deliveryForm.value.kamiConfigIds) {
    showInfo('请选择卡密配置')
    return
  }
  if (deliveryForm.value.enabled && deliveryForm.value.deliveryMode === 4 && !deliveryForm.value.apiAllocateUrl.trim()) {
    showInfo('请输入 API 分配接口')
    return
  }

  deliverySaving.value = true
  try {
    const req: SaveAutoDeliveryConfigReq = {
      xianyuAccountId: props.accountId,
      xianyuGoodsId: goodsDetail.value.item.id,
      xyGoodsId: goodsDetail.value.item.xyGoodId,
      deliveryMode: deliveryForm.value.deliveryMode,
      ruleName: deliveryForm.value.ruleName.trim(),
      matchKeyword: deliveryForm.value.matchKeyword.trim(),
      matchType: deliveryForm.value.matchType,
      priority: deliveryForm.value.priority,
      stock: deliveryForm.value.stock,
      stockWarnThreshold: deliveryForm.value.stockWarnThreshold,
      autoDeliveryContent: deliveryForm.value.autoDeliveryContent.trim(),
      kamiConfigIds: deliveryForm.value.kamiConfigIds,
      kamiDeliveryTemplate: deliveryForm.value.kamiDeliveryTemplate.trim(),
      autoDeliveryImageUrl: deliveryForm.value.autoDeliveryImageUrl.trim(),
      postDeliveryText: deliveryForm.value.postDeliveryText.trim(),
      autoConfirmShipment: deliveryForm.value.autoConfirmShipment ? 1 : 0,
      deliveryDelaySeconds: deliveryForm.value.deliveryDelaySeconds,
      triggerPaymentEnabled: deliveryForm.value.triggerPaymentEnabled ? 1 : 0,
      triggerBargainEnabled: deliveryForm.value.triggerBargainEnabled ? 1 : 0,
      apiAllocateUrl: deliveryForm.value.apiAllocateUrl.trim(),
      apiConfirmUrl: deliveryForm.value.apiConfirmUrl.trim(),
      apiReturnUrl: deliveryForm.value.apiReturnUrl.trim(),
      apiHeaderValue: deliveryForm.value.apiHeaderValue.trim(),
      apiRequestExtras: deliveryForm.value.apiRequestExtras.trim(),
      apiDeliveryTemplate: deliveryForm.value.apiDeliveryTemplate.trim()
    }
    const configResponse = await saveOrUpdateAutoDeliveryConfig(req)
    if (configResponse.code !== 0 && configResponse.code !== 200) {
      throw new Error(configResponse.msg || '保存自动发货配置失败')
    }

    const statusResponse = await updateAutoDeliveryStatus({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId,
      xianyuAutoDeliveryOn: deliveryForm.value.enabled ? 1 : 0
    })
    if (statusResponse.code !== 0 && statusResponse.code !== 200) {
      throw new Error(statusResponse.msg || '更新自动发货状态失败')
    }

    goodsDetail.value.xianyuAutoDeliveryOn = deliveryForm.value.enabled ? 1 : 0
    goodsDetail.value.autoDeliveryType = deliveryForm.value.deliveryMode
    goodsDetail.value.autoDeliveryContent = deliveryForm.value.autoDeliveryContent.trim()
    deliveryDialogVisible.value = false
    emit('refresh')
    showSuccess('自动发货设置已保存')
  } catch (error: any) {
    showError(error.message || '保存自动发货设置失败')
  } finally {
    deliverySaving.value = false
  }
}

const saveReplySettings = async () => {
  if (!goodsDetail.value || !props.accountId || replySaving.value) return

  let seconds = Number(replyForm.value.delaySeconds)
  if (!Number.isFinite(seconds)) seconds = 15
  seconds = Math.min(120, Math.max(5, Math.round(seconds)))
  replyForm.value.delaySeconds = seconds

  replySaving.value = true
  try {
    const [configResponse, fixedMaterialResponse] = await Promise.all([
      updateAutoReplyConfig({
        xianyuAccountId: props.accountId,
        xyGoodsId: goodsDetail.value.item.xyGoodId,
        ragDelaySeconds: seconds
      }),
      saveFixedMaterial({
        accountId: props.accountId,
        goodsId: goodsDetail.value.item.xyGoodId,
        fixedMaterial: replyForm.value.fixedMaterial
      })
    ])
    if (configResponse.code !== 0 && configResponse.code !== 200) {
      throw new Error(configResponse.msg || '保存自动回复延时失败')
    }
    const fixedMaterialData = await fixedMaterialResponse.json()
    if (fixedMaterialData.code !== 0 && fixedMaterialData.code !== 200) {
      throw new Error(fixedMaterialData.msg || '保存固定资料失败')
    }

    const statusResponse = await updateAutoReplyStatus({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId,
      xianyuAutoReplyOn: replyForm.value.enabled ? 1 : 0,
      xianyuAutoReplyContextOn: replyForm.value.contextOn ? 1 : 0
    })
    if (statusResponse.code !== 0 && statusResponse.code !== 200) {
      throw new Error(statusResponse.msg || '更新自动回复状态失败')
    }

    goodsDetail.value.xianyuAutoReplyOn = replyForm.value.enabled ? 1 : 0
    goodsDetail.value.xianyuAutoReplyContextOn = replyForm.value.contextOn ? 1 : 0
    replyDialogVisible.value = false
    emit('refresh')
    showSuccess('自动回复设置已保存')
  } catch (error: any) {
    showError(error.message || '保存自动回复设置失败')
  } finally {
    replySaving.value = false
  }
}

const saveReplyRule = async () => {
  if (!props.accountId || !goodsDetail.value || ruleSaving.value) return
  ruleSaving.value = true
  try {
    const payload = {
      ...replyRuleForm.value,
      xianyuAccountId: props.accountId,
      xyGoodsId: replyRuleForm.value.xyGoodsId === null ? null : goodsDetail.value.item.xyGoodId
    }
    const response = await saveAutoReplyRule(payload)
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '保存规则失败')
    }
    await loadReplyRules()
    showSuccess('关键词规则已保存')
  } catch (error: any) {
    showError(error.message || '保存规则失败')
  } finally {
    ruleSaving.value = false
  }
}

const removeReplyRule = async (rule: AutoReplyRule) => {
  try {
    await showConfirm(`确定删除规则 "${rule.ruleName || '未命名规则'}" 吗？`, '删除规则')
    const response = await deleteAutoReplyRule(rule.id)
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '删除规则失败')
    }
    await loadReplyRules()
    showSuccess('关键词规则已删除')
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError(error.message || '删除规则失败')
  }
}

const handleRemoveSelectedReplyRule = async () => {
  const id = replyRuleForm.value.id
  if (!id) return
  const rule = replyRules.value.find(item => item.id === id)
  if (!rule) return
  await removeReplyRule(rule)
}

const handleOffShelf = async () => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  if (!props.offShelfEnabled) {
    showError('商品下架功能已关闭')
    return
  }
  try {
    await showConfirm(`确定要下架商品 "${goodsDetail.value.item.title}" 吗？`, '下架确认')
    actionLoading.value = true
    const response = await offShelfItem({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId
    })
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value.item.status = 1
      emit('refresh')
      showSuccess('商品下架成功')
    } else {
      throw new Error(response.msg || '下架失败')
    }
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError('下架失败: ' + (error.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const handleRemoteOffShelfToggle = async (value: boolean) => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  if (!props.offShelfEnabled) {
    showError(value ? '商品上架功能已关闭' : '商品下架功能已关闭')
    return
  }
  if (value) {
    try {
      await showConfirm(
        `确定要恢复上架商品 "${goodsDetail.value.item.title}" 吗？会保留原商品ID。`,
        '上架确认'
      )
      actionLoading.value = true
      const response = await republishItem({
        xianyuAccountId: props.accountId,
        xyGoodsId: goodsDetail.value.item.xyGoodId
      })
      if (response.code === 0 || response.code === 200) {
        goodsDetail.value.item.status = 0
        emit('refresh')
        showSuccess('商品已恢复在售')
      } else {
        throw new Error(response.msg || '上架失败')
      }
    } catch (error: any) {
      if (error === 'cancel' || error?.message === 'cancel') return
      showError('上架失败: ' + (error.message || '未知错误'))
    } finally {
      actionLoading.value = false
    }
    return
  }
  await handleOffShelf()
}

const handleRemoteDeleteToggle = async (value: boolean) => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  if (!value) {
    showInfo('闲鱼删除不可直接恢复')
    return
  }
  if (!props.deleteEnabled) {
    showError('商品删除功能已关闭')
    return
  }
  try {
    await showConfirm(
      `确定要在闲鱼删除商品 "${goodsDetail.value.item.title}" 吗？本地商品会保留。`,
      '闲鱼删除确认'
    )
    actionLoading.value = true
    const response = await remoteDeleteItem({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId
    })
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value.item.status = 3
      emit('refresh')
      showSuccess('闲鱼商品删除成功，本地商品已保留')
    } else {
      throw new Error(response.msg || '删除失败')
    }
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError('闲鱼删除失败: ' + (error.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const handleDelete = async () => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  try {
    await showConfirm(
      `确定只删除本地同步商品 "${goodsDetail.value.item.title}" 吗？不会删除闲鱼商品。`,
      '本地删除确认'
    )
    actionLoading.value = true
    const response = await deleteItem({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId
    })
    if (response.code === 0 || response.code === 200) {
      showSuccess('本地商品删除成功')
      handleClose()
      emit('refresh')
    } else {
      throw new Error(response.msg || '删除失败')
    }
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError('删除失败: ' + (error.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const handleUpdatePrice = async () => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  if (!props.isFishShopAccount) {
    showError('当前账号不是鱼小铺，无法改价')
    return
  }
  try {
    const result = await ElMessageBox.prompt(`修改商品 "${goodsDetail.value.item.title}" 的价格`, '改价', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: goodsDetail.value.item.soldPrice || '',
      inputPlaceholder: '请输入新价格',
      inputPattern: /^(?!0+(?:\.0{1,2})?$)\d+(?:\.\d{1,2})?$/,
      inputErrorMessage: '请输入大于0且最多2位小数的价格'
    })
    actionLoading.value = true
    const price = String(result.value || '').trim()
    const response = await updateItemPrice({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId,
      price
    })
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value.item.soldPrice = price
      emit('refresh')
      showSuccess('商品改价成功')
    } else {
      throw new Error(response.msg || '改价失败')
    }
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError('改价失败: ' + (error.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

const handleUpdateStock = async () => {
  if (!props.accountId || !goodsDetail.value || actionLoading.value) return
  try {
    const result = await ElMessageBox.prompt(`调整商品 "${goodsDetail.value.item.title}" 的库存`, '库存', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: String(goodsDetail.value.item.quantity || 1),
      inputPlaceholder: '请输入库存',
      inputPattern: /^[1-9]\d*$/,
      inputErrorMessage: '库存必须为大于0的整数'
    })
    const quantity = Number(result.value)
    if (!Number.isInteger(quantity) || quantity <= 0) {
      showError('库存必须为大于0的整数')
      return
    }
    actionLoading.value = true
    const response = await updateItemStock({
      xianyuAccountId: props.accountId,
      xyGoodsId: goodsDetail.value.item.xyGoodId,
      quantity
    })
    if (response.code === 0 || response.code === 200) {
      goodsDetail.value.item.quantity = quantity
      emit('refresh')
      showSuccess('商品库存修改成功')
    } else {
      throw new Error(response.msg || '改库存失败')
    }
  } catch (error: any) {
    if (error === 'cancel' || error?.message === 'cancel') return
    showError('改库存失败: ' + (error.message || '未知错误'))
  } finally {
    actionLoading.value = false
  }
}

// 图片切换
const prevImage = () => {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--
  }
}

const nextImage = () => {
  if (currentImageIndex.value < images.value.length - 1) {
    currentImageIndex.value++
  }
}

const selectImage = (index: number) => {
  currentImageIndex.value = index
}

// 关闭
const handleClose = () => {
  emit('update:modelValue', false)
  goodsDetail.value = null
  images.value = []
  deliveryDialogVisible.value = false
  replyDialogVisible.value = false
}

// 点击遮罩关闭（仅桌面端）
const handleOverlayClick = (e: MouseEvent) => {
  if ((e.target as HTMLElement).classList.contains('detail-overlay')) {
    handleClose()
  }
}

const applyInitialConfigAction = async () => {
  if (props.initialConfigAction === 'delivery') {
    await handleConfigAutoDelivery()
  } else if (props.initialConfigAction === 'reply') {
    await handleConfigAutoReply()
  }
}

watch(() => props.modelValue, async (val) => {
  if (val) {
    await loadDetail()
    await applyInitialConfigAction()
  }
})

watch(() => props.goodsId, async () => {
  if (props.modelValue) {
    await loadDetail()
    await applyInitialConfigAction()
  }
})

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', checkScreenSize)
})
</script>

<template>
  <!-- Overlay -->
  <Transition name="overlay-fade">
    <div v-if="modelValue" class="detail-overlay" @click="handleOverlayClick">
      <!-- Mobile: Full-screen -->
      <div v-if="isMobile" class="detail-mobile" @click.stop>
        <!-- Mobile Header -->
        <div class="detail-mobile__header">
          <button class="detail-mobile__back" @click="handleClose">
            <IconChevronLeft />
            <span>返回</span>
          </button>
          <span class="detail-mobile__title">商品详情</span>
          <div style="width: 60px"></div>
        </div>

        <!-- Mobile Body -->
        <div class="detail-mobile__body" v-if="!loading && goodsDetail">
          <!-- Image Gallery -->
          <div class="detail-gallery" v-if="images.length > 0">
            <div class="detail-gallery__main">
              <img :src="images[currentImageIndex]" class="detail-gallery__img" />
              <button
                v-if="images.length > 1 && currentImageIndex > 0"
                class="detail-gallery__nav detail-gallery__nav--prev"
                @click="prevImage"
              >
                <IconChevronLeft />
              </button>
              <button
                v-if="images.length > 1 && currentImageIndex < images.length - 1"
                class="detail-gallery__nav detail-gallery__nav--next"
                @click="nextImage"
              >
                <IconChevronRight />
              </button>
              <span v-if="images.length > 1" class="detail-gallery__counter">
                {{ currentImageIndex + 1 }} / {{ images.length }}
              </span>
            </div>
            <div v-if="images.length > 1" class="detail-gallery__thumbs">
              <div
                v-for="(img, idx) in images"
                :key="idx"
                class="detail-gallery__thumb"
                :class="{ 'detail-gallery__thumb--active': idx === currentImageIndex }"
                @click="selectImage(idx)"
              >
                <img :src="img" />
              </div>
            </div>
          </div>

          <!-- Info Section -->
          <div class="detail-info">
            <div class="detail-info__title">{{ goodsDetail.item.title }}</div>
            <div class="detail-info__id">ID: {{ goodsDetail.item.xyGoodId }}</div>

            <div class="detail-info__row">
              <span class="detail-info__price">{{ formatPrice(goodsDetail.item.soldPrice) }}</span>
              <span
                class="detail-info__status"
                :style="{
                  color: getStatusColor(goodsDetail.item.status),
                  background: getStatusBg(goodsDetail.item.status)
                }"
              >
                {{ getGoodsStatusText(goodsDetail.item.status).text }}
              </span>
            </div>

            <!-- Description -->
            <div v-if="goodsDetail.item.detailInfo" class="detail-info__desc-section">
              <div class="detail-info__section-title">商品描述</div>
              <div class="detail-info__desc">{{ goodsDetail.item.detailInfo }}</div>
            </div>

            <!-- Config -->
            <div class="detail-info__config">
              <div class="detail-info__config-item">
                <div class="detail-info__config-left">
                  <IconSend />
                  <span>自动发货</span>
                </div>
                <div class="detail-info__config-right">
                  <span
                    class="detail-info__config-value"
                    :class="{ 'detail-info__config-value--on': goodsDetail.xianyuAutoDeliveryOn === 1 }"
                  >
                    {{ goodsDetail.xianyuAutoDeliveryOn === 1 ? '已开启' : '已关闭' }}
                  </span>
                  <button
                    class="detail-info__config-btn"
                    @click="handleConfigAutoDelivery"
                  >
                    <IconSparkle />
                    <span>配置</span>
                  </button>
                </div>
              </div>
              <div class="detail-info__config-item">
                <div class="detail-info__config-left">
                  <IconRobot />
                  <span>自动回复</span>
                </div>
                <div class="detail-info__config-right">
                  <span
                    class="detail-info__config-value"
                    :class="{ 'detail-info__config-value--on': goodsDetail.xianyuAutoReplyOn === 1 }"
                  >
                    {{ goodsDetail.xianyuAutoReplyOn === 1 ? '已开启' : '已关闭' }}
                  </span>
                  <button
                    class="detail-info__config-btn detail-info__config-btn--reply"
                    @click="handleConfigAutoReply"
                  >
                    <IconSparkle />
                    <span>配置</span>
                  </button>
                </div>
              </div>
              <div class="detail-info__config-item">
                <div class="detail-info__config-left">
                  <IconPackage />
                  <span>在售</span>
                </div>
                <div class="detail-info__config-right">
                  <button
                    v-if="offShelfEnabled"
                    class="toggle-btn"
                    :class="{ 'toggle-btn--on': goodsDetail.item.status === 0 }"
                    @click="handleRemoteOffShelfToggle(goodsDetail.item.status !== 0)"
                  >
                    <span class="toggle-btn__track"><span class="toggle-btn__thumb"></span></span>
                  </button>
                  <span v-else class="detail-info__config-value">已关闭</span>
                </div>
              </div>
              <div class="detail-info__config-item">
                <div class="detail-info__config-left">
                  <IconTrash />
                  <span>闲鱼删除</span>
                </div>
                <div class="detail-info__config-right">
                  <button
                    v-if="deleteEnabled"
                    class="toggle-btn"
                    :class="{ 'toggle-btn--on': goodsDetail.item.status === 3 }"
                    @click="handleRemoteDeleteToggle(goodsDetail.item.status !== 3)"
                  >
                    <span class="toggle-btn__track"><span class="toggle-btn__thumb"></span></span>
                  </button>
                  <span v-else class="detail-info__config-value">已关闭</span>
                </div>
              </div>
            </div>

            <!-- Time -->
            <div class="detail-info__time">
              <div v-if="goodsDetail.item.createdTime" class="detail-info__time-item">
                <IconClock />
                <span>创建: {{ displayGoodsTime(goodsDetail.item.createdTime) }}</span>
              </div>
              <div v-if="goodsDetail.item.updatedTime" class="detail-info__time-item">
                <IconClock />
                <span>更新: {{ displayGoodsTime(goodsDetail.item.updatedTime) }}</span>
              </div>
            </div>

            <!-- Actions -->
            <div class="detail-info__actions">
              <button
                class="detail-info__action detail-info__action--stock"
                @click="handleUpdateStock"
              >
                <IconEdit />
                <span>库存 {{ goodsDetail.item.quantity && goodsDetail.item.quantity > 0 ? goodsDetail.item.quantity : '-' }}</span>
              </button>
              <button
                v-if="isFishShopAccount"
                class="detail-info__action detail-info__action--price"
                @click="handleUpdatePrice"
              >
                <IconEdit />
                <span>修改价格</span>
              </button>
              <button class="detail-info__action detail-info__action--delete" @click="handleDelete">
                <IconTrash />
                <span>本地删除</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="detail-loading">
          <div class="detail-loading__spinner"></div>
          <span>加载中...</span>
        </div>
      </div>

      <!-- Desktop: Side Panel -->
      <div v-else class="detail-panel" @click.stop>
        <div class="detail-panel__header">
          <span class="detail-panel__title">商品详情</span>
          <button class="detail-panel__close" @click="handleClose">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M1 1L13 13M13 1L1 13" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </button>
        </div>

        <div class="detail-panel__body" v-if="!loading && goodsDetail">
          <div class="detail-content">
            <!-- Left: Image Gallery -->
            <div class="detail-left">
              <div class="detail-gallery" v-if="images.length > 0">
                <div class="detail-gallery__main">
                  <img :src="images[currentImageIndex]" class="detail-gallery__img" />
                  <button
                    v-if="images.length > 1 && currentImageIndex > 0"
                    class="detail-gallery__nav detail-gallery__nav--prev"
                    @click="prevImage"
                  >
                    <IconChevronLeft />
                  </button>
                  <button
                    v-if="images.length > 1 && currentImageIndex < images.length - 1"
                    class="detail-gallery__nav detail-gallery__nav--next"
                    @click="nextImage"
                  >
                    <IconChevronRight />
                  </button>
                  <span v-if="images.length > 1" class="detail-gallery__counter">
                    {{ currentImageIndex + 1 }} / {{ images.length }}
                  </span>
                </div>
                <div v-if="images.length > 1" class="detail-gallery__thumbs">
                  <div
                    v-for="(img, idx) in images"
                    :key="idx"
                    class="detail-gallery__thumb"
                    :class="{ 'detail-gallery__thumb--active': idx === currentImageIndex }"
                    @click="selectImage(idx)"
                  >
                    <img :src="img" />
                  </div>
                </div>
              </div>
              <div v-else class="detail-gallery detail-gallery--empty">
                <div class="detail-gallery__placeholder">
                  <IconImage />
                  <span>暂无图片</span>
                </div>
              </div>
            </div>

            <!-- Right: Info Section -->
            <div class="detail-right">
              <div class="detail-info">
                <div class="detail-info__title">{{ goodsDetail.item.title }}</div>
                <div class="detail-info__id">ID: {{ goodsDetail.item.xyGoodId }}</div>

                <div class="detail-info__row">
                  <span class="detail-info__price">{{ formatPrice(goodsDetail.item.soldPrice) }}</span>
                  <span
                    class="detail-info__status"
                    :style="{
                      color: getStatusColor(goodsDetail.item.status),
                      background: getStatusBg(goodsDetail.item.status)
                    }"
                  >
                    {{ getGoodsStatusText(goodsDetail.item.status).text }}
                  </span>
                </div>

                <div v-if="goodsDetail.item.detailInfo" class="detail-info__desc-section">
                  <div class="detail-info__section-title">商品描述</div>
                  <div class="detail-info__desc">{{ goodsDetail.item.detailInfo }}</div>
                </div>

                <div class="detail-info__config">
                  <div class="detail-info__config-item">
                    <div class="detail-info__config-left">
                      <IconSend />
                      <span>自动发货</span>
                    </div>
                    <div class="detail-info__config-right">
                      <span
                        class="detail-info__config-value"
                        :class="{ 'detail-info__config-value--on': goodsDetail.xianyuAutoDeliveryOn === 1 }"
                      >
                        {{ goodsDetail.xianyuAutoDeliveryOn === 1 ? '已开启' : '已关闭' }}
                      </span>
                      <button
                        class="detail-info__config-btn"
                        @click="handleConfigAutoDelivery"
                      >
                        <IconSparkle />
                        <span>配置</span>
                      </button>
                    </div>
                  </div>
                  <div class="detail-info__config-item">
                    <div class="detail-info__config-left">
                      <IconRobot />
                      <span>自动回复</span>
                    </div>
                    <div class="detail-info__config-right">
                      <span
                        class="detail-info__config-value"
                        :class="{ 'detail-info__config-value--on': goodsDetail.xianyuAutoReplyOn === 1 }"
                      >
                        {{ goodsDetail.xianyuAutoReplyOn === 1 ? '已开启' : '已关闭' }}
                      </span>
                      <button
                        class="detail-info__config-btn detail-info__config-btn--reply"
                        @click="handleConfigAutoReply"
                      >
                        <IconSparkle />
                        <span>配置</span>
                      </button>
                    </div>
                  </div>
                  <div class="detail-info__config-item">
                    <div class="detail-info__config-left">
                      <IconPackage />
                      <span>在售</span>
                    </div>
                    <div class="detail-info__config-right">
                      <button
                        v-if="offShelfEnabled"
                        class="toggle-btn"
                        :class="{ 'toggle-btn--on': goodsDetail.item.status === 0 }"
                        @click="handleRemoteOffShelfToggle(goodsDetail.item.status !== 0)"
                      >
                        <span class="toggle-btn__track"><span class="toggle-btn__thumb"></span></span>
                      </button>
                      <span v-else class="detail-info__config-value">已关闭</span>
                    </div>
                  </div>
                  <div class="detail-info__config-item">
                    <div class="detail-info__config-left">
                      <IconTrash />
                      <span>闲鱼删除</span>
                    </div>
                    <div class="detail-info__config-right">
                      <button
                        v-if="deleteEnabled"
                        class="toggle-btn"
                        :class="{ 'toggle-btn--on': goodsDetail.item.status === 3 }"
                        @click="handleRemoteDeleteToggle(goodsDetail.item.status !== 3)"
                      >
                        <span class="toggle-btn__track"><span class="toggle-btn__thumb"></span></span>
                      </button>
                      <span v-else class="detail-info__config-value">已关闭</span>
                    </div>
                  </div>
                </div>

                <div class="detail-info__time">
                  <div v-if="goodsDetail.item.createdTime" class="detail-info__time-item">
                    <IconClock />
                    <span>创建: {{ displayGoodsTime(goodsDetail.item.createdTime) }}</span>
                  </div>
                  <div v-if="goodsDetail.item.updatedTime" class="detail-info__time-item">
                    <IconClock />
                    <span>更新: {{ displayGoodsTime(goodsDetail.item.updatedTime) }}</span>
                  </div>
                </div>

                <div class="detail-info__actions">
                  <button
                    class="detail-info__action detail-info__action--stock"
                    @click="handleUpdateStock"
                  >
                    <IconEdit />
                    <span>库存 {{ goodsDetail.item.quantity && goodsDetail.item.quantity > 0 ? goodsDetail.item.quantity : '-' }}</span>
                  </button>
                  <button
                    v-if="isFishShopAccount"
                    class="detail-info__action detail-info__action--price"
                    @click="handleUpdatePrice"
                  >
                    <IconEdit />
                    <span>修改价格</span>
                  </button>
                  <button class="detail-info__action detail-info__action--delete" @click="handleDelete">
                    <IconTrash />
                    <span>本地删除</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="detail-loading">
          <div class="detail-loading__spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    </div>
  </Transition>

  <Transition name="overlay-fade">
    <div v-if="deliveryDialogVisible" class="settings-overlay" @click.self="deliveryDialogVisible = false">
      <div class="settings-dialog" @click.stop>
        <div class="settings-dialog__header">
          <div>
            <h3 class="settings-dialog__title">自动发货设置</h3>
            <p class="settings-dialog__subtitle">{{ goodsDetail?.item.title }}</p>
          </div>
          <button class="settings-dialog__close" type="button" @click="deliveryDialogVisible = false">×</button>
        </div>

        <div class="settings-dialog__body">
          <label class="settings-row settings-row--switch">
            <span>
              <strong>自动发货</strong>
              <small>买家下单后自动发送配置内容</small>
            </span>
            <input v-model="deliveryForm.enabled" type="checkbox" />
          </label>

          <div class="settings-field">
            <label>规则基础</label>
            <div class="settings-grid">
              <input v-model="deliveryForm.ruleName" class="settings-input" placeholder="规则名称" />
              <input v-model.number="deliveryForm.priority" class="settings-input" type="number" min="1" placeholder="优先级" />
              <input v-model="deliveryForm.matchKeyword" class="settings-input settings-grid__wide" placeholder="匹配关键词，多个用逗号分隔" />
              <select v-model.number="deliveryForm.matchType" class="settings-select">
                <option :value="1">任意关键词</option>
                <option :value="2">全部关键词</option>
              </select>
              <input v-model.number="deliveryForm.stock" class="settings-input" type="number" min="-1" placeholder="库存 -1不限" />
              <input v-model.number="deliveryForm.stockWarnThreshold" class="settings-input" type="number" min="0" placeholder="预警阈值" />
              <input v-model.number="deliveryForm.deliveryDelaySeconds" class="settings-input" type="number" min="0" max="86400" placeholder="延时秒数" />
            </div>
          </div>

          <div class="settings-field">
            <label>发货模式</label>
            <div class="settings-segmented">
              <button
                type="button"
                :class="{ 'settings-segmented__btn--active': deliveryForm.deliveryMode === 1 }"
                class="settings-segmented__btn"
                @click="deliveryForm.deliveryMode = 1"
              >
                文本
              </button>
              <button
                type="button"
                :class="{ 'settings-segmented__btn--active': deliveryForm.deliveryMode === 2 }"
                class="settings-segmented__btn"
                @click="deliveryForm.deliveryMode = 2"
              >
                卡密
              </button>
              <button
                type="button"
                :class="{ 'settings-segmented__btn--active': deliveryForm.deliveryMode === 3 }"
                class="settings-segmented__btn"
                @click="deliveryForm.deliveryMode = 3"
              >
                自定义
              </button>
              <button
                type="button"
                :class="{ 'settings-segmented__btn--active': deliveryForm.deliveryMode === 4 }"
                class="settings-segmented__btn"
                @click="deliveryForm.deliveryMode = 4"
              >
                API
              </button>
            </div>
          </div>

          <div class="settings-field">
            <label>触发条件</label>
            <div class="settings-toggle-grid">
              <label class="settings-check">
                <input v-model="deliveryForm.triggerPaymentEnabled" type="checkbox" />
                <span>付款消息</span>
              </label>
              <label class="settings-check">
                <input v-model="deliveryForm.triggerBargainEnabled" type="checkbox" />
                <span>小刀流程卡片</span>
              </label>
            </div>
          </div>

          <div v-if="deliveryForm.deliveryMode === 2" class="settings-field">
            <label>卡密配置</label>
            <select v-model="deliveryForm.kamiConfigIds" class="settings-select">
              <option value="">请选择卡密配置</option>
              <option v-for="opt in kamiConfigOptions" :key="opt.id" :value="String(opt.id)">
                {{ opt.aliasName || `配置#${opt.id}` }}（可用{{ opt.availableCount }} / 总{{ opt.totalCount }}）
              </option>
            </select>
          </div>

          <div v-if="deliveryForm.deliveryMode === 1" class="settings-field">
            <label>发货内容</label>
            <textarea
              v-model="deliveryForm.autoDeliveryContent"
              class="settings-textarea"
              maxlength="1000"
              placeholder="请输入自动发货内容"
            ></textarea>
          </div>

          <div v-if="deliveryForm.deliveryMode === 2" class="settings-field">
            <label>卡密模板</label>
            <textarea
              v-model="deliveryForm.kamiDeliveryTemplate"
              class="settings-textarea settings-textarea--small"
              placeholder="可选，例如：您的卡密为：{kmKey}"
            ></textarea>
          </div>

          <div v-if="deliveryForm.deliveryMode === 3" class="settings-field">
            <label>自定义发货</label>
            <div class="settings-field__hint">
              自定义模式保留给外部程序轮询订单并确认发货，接口说明请在自动发货页面查看。
            </div>
          </div>

          <div v-if="deliveryForm.deliveryMode === 4" class="settings-field">
            <label>API 发货接口</label>
            <input v-model="deliveryForm.apiAllocateUrl" class="settings-input" placeholder="分配接口 POST URL" />
            <input v-model="deliveryForm.apiConfirmUrl" class="settings-input" placeholder="确认交付接口 POST URL（可选）" />
            <input v-model="deliveryForm.apiReturnUrl" class="settings-input" placeholder="回库接口 POST URL（可选）" />
            <div class="settings-grid">
              <input v-model="deliveryForm.apiHeaderValue" class="settings-input" type="password" placeholder="Header 密钥" />
            </div>
            <textarea
              v-model="deliveryForm.apiRequestExtras"
              class="settings-textarea"
              placeholder='附加请求 JSON，例如：{"ruleCode":"croissant_free9"}'
            ></textarea>
            <textarea
              v-model="deliveryForm.apiDeliveryTemplate"
              class="settings-textarea settings-textarea--small"
              placeholder="发送文案模板，可用 {apiContent} 引用接口返回内容"
            ></textarea>
            <div class="settings-field__hint">
              固定使用标准协议：Header 为 X-Delivery-Key，响应字段为 data.content / data.allocationId。附加请求 JSON 用于对接外部选号规则。
            </div>
          </div>

          <div class="settings-field">
            <label>发货图片</label>
            <div class="settings-field__hint">
              可选，买家下单后先发送图片再发送发货内容
            </div>
            <MultiImageUploader
              v-if="accountId"
              v-model="deliveryForm.autoDeliveryImageUrl"
              :account-id="accountId"
            />
          </div>

          <div class="settings-field">
            <label>发货后文本</label>
            <div class="settings-field__hint">
              可选，发货内容发送成功后追加发送此文本
            </div>
            <textarea
              v-model="deliveryForm.postDeliveryText"
              class="settings-textarea settings-textarea--small"
              maxlength="1000"
              placeholder="例如：请及时保存以上内容，如有问题请直接回复。"
            ></textarea>
          </div>

          <label class="settings-row settings-row--switch">
            <span>
              <strong>自动确认发货</strong>
              <small>发货成功后自动确认已发货</small>
            </span>
            <input v-model="deliveryForm.autoConfirmShipment" type="checkbox" />
          </label>
        </div>

        <div class="settings-dialog__footer">
          <button class="settings-btn settings-btn--ghost" type="button" @click="deliveryDialogVisible = false">
            取消
          </button>
          <button
            class="settings-btn settings-btn--primary"
            type="button"
            :disabled="deliverySaving"
            @click="saveDeliverySettings"
          >
            {{ deliverySaving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </div>
  </Transition>

  <Transition name="overlay-fade">
    <div v-if="replyDialogVisible" class="settings-overlay" @click.self="replyDialogVisible = false">
      <div class="settings-dialog settings-dialog--narrow" @click.stop>
        <div class="settings-dialog__header">
          <div>
            <h3 class="settings-dialog__title">自动回复设置</h3>
            <p class="settings-dialog__subtitle">{{ goodsDetail?.item.title }}</p>
          </div>
          <button class="settings-dialog__close" type="button" @click="replyDialogVisible = false">×</button>
        </div>

        <div class="settings-dialog__body">
          <label class="settings-row settings-row--switch">
            <span>
              <strong>自动回复</strong>
              <small>买家咨询时自动生成回复</small>
            </span>
            <input v-model="replyForm.enabled" type="checkbox" />
          </label>

          <label class="settings-row settings-row--switch">
            <span>
              <strong>携带上下文</strong>
              <small>结合当前商品资料生成回复</small>
            </span>
            <input v-model="replyForm.contextOn" type="checkbox" />
          </label>

          <div class="settings-field">
            <label>回复延时（秒）</label>
            <input
              v-model.number="replyForm.delaySeconds"
              class="settings-input"
              type="number"
              min="5"
              max="120"
              step="1"
            />
          </div>

          <div class="settings-field">
            <label>固定资料</label>
            <textarea
              v-model="replyForm.fixedMaterial"
              class="settings-textarea"
              maxlength="5000"
              placeholder="当前商品专属补充资料，会与全局模板一起发送给AI"
            ></textarea>
          </div>

          <div class="settings-field">
            <label>关键词规则</label>
            <div v-if="replyRules.length" class="settings-rule-list">
              <button
                v-for="rule in replyRules"
                :key="rule.id"
                class="settings-rule-pill"
                type="button"
                @click="fillReplyRuleForm(rule)"
              >
                {{ rule.ruleName || `规则#${rule.id}` }} · {{ rule.xyGoodsId ? '当前商品' : '通用' }}
              </button>
            </div>
            <div class="settings-grid">
              <input v-model="replyRuleForm.ruleName" class="settings-input" placeholder="规则名称" />
              <input v-model.number="replyRuleForm.priority" class="settings-input" type="number" min="1" placeholder="优先级" />
              <input v-model="replyRuleForm.keywords" class="settings-input settings-grid__wide" placeholder="关键词，多个用逗号分隔" />
              <select v-model.number="replyRuleForm.matchType" class="settings-select">
                <option :value="1">任意关键词</option>
                <option :value="2">全部关键词</option>
              </select>
              <select v-model.number="replyRuleForm.replyType" class="settings-select">
                <option :value="1">文字</option>
                <option :value="2">图片</option>
                <option :value="3">文字+图片</option>
              </select>
            </div>
            <textarea
              v-model="replyRuleForm.replyContent"
              class="settings-textarea settings-textarea--small"
              placeholder="命中关键词时优先回复的文字内容"
            ></textarea>
            <input v-model="replyRuleForm.imageUrls" class="settings-input" placeholder="图片URL，多个用逗号分隔" />
            <div class="settings-rule-actions">
              <button class="settings-btn settings-btn--ghost" type="button" @click="resetReplyRuleForm">
                新建规则
              </button>
              <button
                class="settings-btn settings-btn--ghost"
                type="button"
                :disabled="!replyRuleForm.id"
                @click="handleRemoveSelectedReplyRule"
              >
                删除规则
              </button>
              <button class="settings-btn settings-btn--primary" type="button" :disabled="ruleSaving" @click="saveReplyRule">
                {{ ruleSaving ? '保存中...' : '保存规则' }}
              </button>
            </div>
          </div>
        </div>

        <div class="settings-dialog__footer">
          <button class="settings-btn settings-btn--ghost" type="button" @click="replyDialogVisible = false">
            取消
          </button>
          <button
            class="settings-btn settings-btn--primary"
            type="button"
            :disabled="replySaving"
            @click="saveReplySettings"
          >
            {{ replySaving ? '保存中...' : '保存设置' }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* ============================================================
   Shared Tokens
   ============================================================ */
.detail-overlay,
.detail-panel,
.detail-mobile,
.settings-overlay {
  --d-bg: transparent;
  --d-surface: rgba(255, 255, 255, 0.82);
  --d-surface-hover: rgba(255, 255, 255, 0.95);
  --d-border: rgba(0, 0, 0, 0.06);
  --d-border-strong: rgba(0, 0, 0, 0.12);
  --d-text-1: #1d1d1f;
  --d-text-2: #6e6e73;
  --d-text-3: #86868b;
  --d-accent: #007aff;
  --d-danger: #ff3b30;
  --d-success: #34c759;
  --d-price: #ff3b30;
  --d-r-sm: 8px;
  --d-r-md: 12px;
  --d-r-lg: 16px;
  --d-ease: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

/* ============================================================
   Overlay
   ============================================================ */
.detail-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 900;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ============================================================
   Desktop Panel
   ============================================================ */
.detail-panel {
  width: 720px;
  max-width: 90vw;
  max-height: 90vh;
  background: #fff;
  border-radius: var(--d-r-lg);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.18), 0 4px 12px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: panel-in 0.25s cubic-bezier(0.25, 0.1, 0.25, 1);
}

@keyframes panel-in {
  from { opacity: 0; transform: scale(0.96) translateY(8px); }
  to { opacity: 1; transform: scale(1) translateY(0); }
}

.detail-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--d-border);
  flex-shrink: 0;
}

.detail-panel__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--d-text-1);
}

.detail-panel__close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.05);
  color: var(--d-text-2);
  cursor: pointer;
  transition: all var(--d-ease);
}

@media (hover: hover) {
  .detail-panel__close:hover {
    background: rgba(0, 0, 0, 0.1);
    color: var(--d-text-1);
  }
}

.detail-panel__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.12) transparent;
}

.detail-panel__body::-webkit-scrollbar {
  width: 5px;
}

.detail-panel__body::-webkit-scrollbar-track {
  background: transparent;
}

.detail-panel__body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.12);
  border-radius: 3px;
}

.detail-content {
  display: flex;
  gap: 16px;
  padding: 12px;
}

.detail-left {
  flex: 0 0 280px;
  display: flex;
  flex-direction: column;
}

.detail-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* ============================================================
   Mobile Full-screen
   ============================================================ */
.detail-mobile {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #fff;
  z-index: 901;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: mobile-slide-in 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
}

@keyframes mobile-slide-in {
  from { transform: translateX(100%); }
  to { transform: translateX(0); }
}

.detail-mobile__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  padding-top: max(8px, env(safe-area-inset-top, 8px));
  border-bottom: 1px solid var(--d-border);
  flex-shrink: 0;
  height: 48px;
}

.detail-mobile__back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 36px;
  padding: 0 8px;
  font-size: 16px;
  font-weight: 500;
  color: var(--d-accent);
  background: none;
  border: none;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.detail-mobile__back svg {
  width: 20px;
  height: 20px;
}

.detail-mobile__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--d-text-1);
}

.detail-mobile__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.12) transparent;
}

.detail-mobile__body::-webkit-scrollbar {
  width: 4px;
}

.detail-mobile__body::-webkit-scrollbar-track {
  background: transparent;
}

.detail-mobile__body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 2px;
}

/* ============================================================
   Gallery (Shared)
   ============================================================ */
.detail-gallery {
  padding: 0;
}

.detail-gallery__main {
  position: relative;
  width: 100%;
  height: 260px;
  border-radius: var(--d-r-md);
  overflow: hidden;
  background: rgba(0, 0, 0, 0.03);
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-gallery__img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.detail-gallery__nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  border: none;
  background: rgba(255, 255, 255, 0.85);
  color: var(--d-text-1);
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  transition: all var(--d-ease);
  -webkit-tap-highlight-color: transparent;
}

.detail-gallery__nav svg {
  width: 16px;
  height: 16px;
}

.detail-gallery__nav--prev { left: 8px; }
.detail-gallery__nav--next { right: 8px; }

@media (hover: hover) {
  .detail-gallery__nav:hover {
    background: rgba(255, 255, 255, 0.95);
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.16);
  }
}

.detail-gallery__counter {
  position: absolute;
  bottom: 8px;
  right: 8px;
  font-size: 11px;
  font-weight: 500;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 3px 8px;
  border-radius: 10px;
}

.detail-gallery__thumbs {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
}

.detail-gallery__thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
  border: 2px solid transparent;
  cursor: pointer;
  transition: border-color var(--d-ease);
  background: rgba(0, 0, 0, 0.03);
}

.detail-gallery__thumb--active {
  border-color: var(--d-accent);
}

.detail-gallery__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-gallery--empty {
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-gallery__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--d-text-3);
  opacity: 0.4;
}

.detail-gallery__placeholder svg {
  width: 32px;
  height: 32px;
}

.detail-gallery__placeholder span {
  font-size: 13px;
}

/* ============================================================
   Info Section (Shared)
   ============================================================ */
.detail-info {
  padding: 0;
}

.detail-info__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--d-text-1);
  line-height: 1.4;
  margin-bottom: 2px;
}

.detail-info__id {
  font-size: 11px;
  color: var(--d-text-3);
  font-family: 'SF Mono', 'Menlo', monospace;
  margin-bottom: 8px;
}

.detail-info__row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.detail-info__price {
  font-size: 20px;
  font-weight: 700;
  color: var(--d-price);
  font-variant-numeric: tabular-nums;
}

.detail-info__status {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
  line-height: 1;
}

.detail-info__desc-section {
  margin-bottom: 12px;
  padding: 10px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: var(--d-r-sm);
}

.detail-info__section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--d-text-2);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.detail-info__desc {
  font-size: 12px;
  color: var(--d-text-2);
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 100px;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.08) transparent;
}

.detail-info__desc::-webkit-scrollbar {
  width: 4px;
}

.detail-info__desc::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.08);
  border-radius: 2px;
}

.detail-info__config {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 1px solid var(--d-border);
  border-radius: var(--d-r-sm);
  overflow: hidden;
  margin-bottom: 12px;
}

.detail-info__config-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 10px;
}

.detail-info__config-item:not(:last-child) {
  border-bottom: 1px solid var(--d-border);
}

.detail-info__config-left {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--d-text-1);
}

.detail-info__config-left svg {
  width: 14px;
  height: 14px;
  color: var(--d-text-3);
}

.detail-info__config-value {
  font-size: 12px;
  font-weight: 500;
  color: var(--d-text-3);
}

.detail-info__config-value--on {
  color: var(--d-success);
}

.detail-info__config-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.detail-info__config-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 12px;
  border: none;
  background: rgba(0, 122, 255, 0.1);
  color: var(--d-accent);
  cursor: pointer;
  transition: all var(--d-ease);
  -webkit-tap-highlight-color: transparent;
}

.detail-info__config-btn svg {
  width: 12px;
  height: 12px;
}

@media (hover: hover) {
  .detail-info__config-btn:hover {
    background: rgba(0, 122, 255, 0.18);
  }
}

.detail-info__config-btn:active {
  transform: scale(0.95);
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  -webkit-tap-highlight-color: transparent;
}

.toggle-btn__track {
  width: 44px;
  height: 26px;
  border-radius: 13px;
  background: rgba(0, 0, 0, 0.12);
  position: relative;
  transition: background var(--d-ease);
  display: block;
}

.toggle-btn--on .toggle-btn__track {
  background: var(--d-success);
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
  transition: transform var(--d-ease);
}

.toggle-btn--on .toggle-btn__thumb {
  transform: translateX(18px);
}

.detail-info__time {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}

.detail-info__time-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--d-text-3);
}

.detail-info__time-item svg {
  width: 12px;
  height: 12px;
}

.detail-info__actions {
  display: flex;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--d-border);
}

.detail-info__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  height: 36px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--d-r-sm);
  border: 1px solid;
  cursor: pointer;
  transition: all var(--d-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.detail-info__action svg {
  width: 15px;
  height: 15px;
}

.detail-info__action--off-shelf {
  color: #ff9500;
  border-color: rgba(255, 149, 0, 0.2);
}

.detail-info__action--price {
  color: #5856d6;
  border-color: rgba(88, 86, 214, 0.2);
}

.detail-info__action--stock {
  color: #007aff;
  border-color: rgba(0, 122, 255, 0.2);
}

@media (hover: hover) {
  .detail-info__action--price:hover {
    background: rgba(88, 86, 214, 0.08);
  }

  .detail-info__action--stock:hover {
    background: rgba(0, 122, 255, 0.08);
  }
}

@media (hover: hover) {
  .detail-info__action--off-shelf:hover {
    background: rgba(255, 149, 0, 0.06);
  }
}

.detail-info__action--delete {
  color: var(--d-danger);
  border-color: rgba(255, 59, 48, 0.2);
}

@media (hover: hover) {
  .detail-info__action--delete:hover {
    background: rgba(255, 59, 48, 0.06);
  }
}

.detail-info__action:active {
  transform: scale(0.97);
}

/* ============================================================
   Loading
   ============================================================ */
.detail-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 60px;
  color: var(--d-text-3);
  font-size: 13px;
}

.detail-loading__spinner {
  width: 24px;
  height: 24px;
  border: 2px solid rgba(0, 0, 0, 0.08);
  border-top-color: var(--d-accent);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ============================================================
   Transition
   ============================================================ */
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.2s ease;
}

.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}

/* ============================================================
   Inline Settings Dialog
   ============================================================ */
.settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 920;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
}

.settings-dialog {
  width: min(760px, calc(100vw - 48px));
  max-height: min(820px, calc(100vh - 48px));
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: var(--d-r-lg);
  box-shadow: 0 20px 70px rgba(0, 0, 0, 0.22);
  overflow: hidden;
}

.settings-dialog--narrow {
  width: min(520px, calc(100vw - 48px));
}

.settings-dialog,
.settings-dialog * {
  box-sizing: border-box;
}

.settings-dialog__header,
.settings-dialog__footer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--d-border);
}

.settings-dialog__footer {
  justify-content: flex-end;
  border-top: 1px solid var(--d-border);
  border-bottom: none;
}

.settings-dialog__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--d-text-1);
}

.settings-dialog__subtitle {
  margin: 4px 0 0;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--d-text-3);
}

.settings-dialog__close {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.06);
  color: var(--d-text-2);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.settings-dialog__body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px 16px;
}

.settings-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
}

.settings-row:not(:last-child) {
  border-bottom: 1px solid var(--d-border);
}

.settings-row strong,
.settings-field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--d-text-1);
}

.settings-row small {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--d-text-3);
}

.settings-field__hint {
  margin-top: -3px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--d-text-3);
}

.settings-row--switch input {
  width: 18px;
  height: 18px;
  accent-color: var(--d-accent);
}

.settings-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.settings-grid__wide {
  grid-column: 1 / -1;
}

.settings-segmented {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.settings-toggle-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.settings-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid var(--d-border-strong);
  border-radius: var(--d-r-sm);
}

.settings-rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.settings-rule-pill {
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--d-border-strong);
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.03);
  color: var(--d-text-2);
  font-size: 12px;
  cursor: pointer;
}

.settings-rule-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.settings-segmented__btn,
.settings-btn {
  height: 34px;
  border-radius: var(--d-r-sm);
  border: 1px solid var(--d-border-strong);
  background: #fff;
  color: var(--d-text-2);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.settings-segmented__btn--active {
  border-color: rgba(0, 122, 255, 0.35);
  background: rgba(0, 122, 255, 0.1);
  color: var(--d-accent);
}

.settings-input,
.settings-select,
.settings-textarea {
  width: 100%;
  border: 1px solid var(--d-border-strong);
  border-radius: var(--d-r-sm);
  color: var(--d-text-1);
  background: #fff;
  outline: none;
}

.settings-input,
.settings-select {
  height: 36px;
  padding: 0 10px;
}

.settings-textarea {
  min-height: 108px;
  padding: 9px 10px;
  resize: vertical;
  line-height: 1.5;
}

.settings-textarea--small {
  min-height: 76px;
}

.settings-input:focus,
.settings-select:focus,
.settings-textarea:focus {
  border-color: var(--d-accent);
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.1);
}

.settings-btn {
  min-width: 86px;
  padding: 0 14px;
}

.settings-btn--ghost {
  background: rgba(0, 0, 0, 0.03);
}

.settings-btn--primary {
  border-color: var(--d-accent);
  background: var(--d-accent);
  color: #fff;
}

.settings-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* ============================================================
   Responsive
   ============================================================ */
@media screen and (max-width: 768px) {
  .settings-overlay {
    align-items: flex-end;
    padding: 0;
  }

  .settings-dialog,
  .settings-dialog--narrow {
    width: 100%;
    max-height: min(88vh, 720px);
    border-radius: var(--d-r-lg) var(--d-r-lg) 0 0;
  }

  .settings-dialog__header,
  .settings-dialog__footer {
    padding: 12px 16px;
  }

  .settings-dialog__subtitle {
    max-width: calc(100vw - 88px);
  }

  .settings-dialog__body {
    padding: 12px 16px;
  }

  .settings-segmented {
    gap: 8px;
  }

  .settings-grid,
  .settings-toggle-grid {
    grid-template-columns: 1fr;
  }

  .detail-content {
    flex-direction: column;
    padding: 0;
  }

  .detail-left {
    flex: none;
    width: 100%;
  }

  .detail-right {
    flex: none;
  }

  .detail-gallery {
    padding: 12px;
  }

  .detail-gallery__main {
    height: 280px;
  }

  .detail-info {
    padding: 12px 16px 24px;
  }

  .detail-info__price {
    font-size: 20px;
  }

  .detail-gallery__thumb {
    width: 40px;
    height: 40px;
  }
}

@media screen and (max-width: 480px) {
  .detail-gallery__main {
    height: 240px;
  }

  .detail-gallery {
    padding: 12px;
  }

  .detail-info__title {
    font-size: 15px;
  }

  .detail-info__price {
    font-size: 18px;
  }

  .detail-info__action {
    height: 36px;
    font-size: 12px;
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'
import { fetchPoiCandidates, getDefaultPoiCache, getPoiCache, publishItem, savePoiCache } from '@/api/goods'
import './PublishGoodsDialog.css'
import type { PoiCacheItem, PoiCandidate, PublishItemAddress, PublishItemCategory, PublishItemLabel, PublishItemRequest } from '@/api/goods'
import type { Account } from '@/types'
import { showError, showSuccess } from '@/utils'
import { getAccountDisplayName } from '@/utils/accountDisplay'

interface TreeNode {
  id: number
  value: number
  v?: number
  label: string
  title?: string
  children?: TreeNode[]
}

interface PresetData {
  cation: TreeNode[]
  positioning: TreeNode[]
  dianpu: string[]
}

interface SpecGroup {
  id: number
  propertyName: string
  values: string[]
}

interface SkuRow {
  key: string
  propertyKey?: string
  propertyValue?: string
  secondPropertyKey?: string
  secondPropertyValue?: string
  price: string
  quantity: string
}

const DRAFT_KEY = 'xianyu_publish_goods_draft'

const props = defineProps<{ modelValue: boolean; accounts: Account[]; accountId: number | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'published'): void }>()

const presetData = ref<PresetData>({ cation: [], positioning: [], dianpu: [] })
const submitting = ref(false)
const categoryOpen = ref(false)
const cityOpen = ref(false)
const categoryPath = ref<TreeNode[]>([])
const cityPath = ref<TreeNode[]>([])
const categoryBrowsePath = ref<TreeNode[]>([])
const cityBrowsePath = ref<TreeNode[]>([])
const isMobile = ref(false)
const poiCache = ref<PoiCacheItem | null>(null)
const poiCandidates = ref<PoiCandidate[]>([])
const poiPanelOpen = ref(false)
const poiLoading = ref(false)
const specGroups = ref<SpecGroup[]>([])
const skuRows = ref<SkuRow[]>([])
let specIdSeed = 1

const form = reactive({
  accountId: props.accountId,
  title: '',
  desc: '',
  imageUrls: '',
  price: '0.00',
  origPrice: '',
  quantity: '1',
  freeShipping: true,
  shippingType: 'FREE_SHIPPING' as 'FREE_SHIPPING' | 'CHARGE_SHIPPING' | 'FIXED_PRICE' | 'NO_SHIPPING',
  supportSelfPick: false,
  postFee: '',
  specMode: 'multi',
  autoOnShelf: false,
  scheduled: false,
  scheduledTime: ''
})

const draftLoaded = ref(false)

const imageList = computed(() => form.imageUrls.split(',').map(item => item.trim()).filter(Boolean))
const titleCount = computed(() => form.title.trim().length)
const descCount = computed(() => form.desc.trim().length)
const selectedCategory = computed(() => categoryPath.value[categoryPath.value.length - 1])
const selectedCity = computed(() => cityPath.value[cityPath.value.length - 1])
const selectedAccountName = computed(() => {
  const account = props.accounts.find(item => item.id === form.accountId)
  return getAccountDisplayName(account, '')
})

const categoryColumns = computed(() => buildColumns(presetData.value.cation, categoryPath.value, 4))
const cityColumns = computed(() => buildColumns(presetData.value.positioning, cityPath.value, 3))
const mobileCategoryNodes = computed(() => currentMobileNodes(presetData.value.cation, categoryBrowsePath.value))
const mobileCityNodes = computed(() => currentMobileNodes(presetData.value.positioning, cityBrowsePath.value))
const mobileCategoryTrail = computed(() => categoryBrowsePath.value.map(item => item.label).join(' / '))
const mobileCityTrail = computed(() => cityBrowsePath.value.map(item => item.label).join(' / '))
const categoryText = computed(() => selectedCategory.value?.label || '其他闲置')
const cityText = computed(() => cityPath.value.length ? cityPath.value.map(item => item.label).join(' / ') : '')
const minScheduledTime = computed(() => toDatetimeLocalValue(new Date(Date.now() + 60000)))
const selectedAddressBase = computed(() => buildAddressBase())
const poiText = computed(() => {
  if (!poiCache.value) return '未设置地图点位'
  return `${poiCache.value.poiName}${poiCache.value.area ? ` · ${poiCache.value.area}` : ''}`
})

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  await loadPresets()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateMobile)
})

watch(() => props.modelValue, value => {
  if (!value) return
  form.accountId = props.accountId
  restoreDraft()
  loadDefaultPoiCache()
})

watch(() => props.accountId, value => {
  if (!props.modelValue) form.accountId = value
})

watch(() => [form.accountId, selectedAddressBase.value.divisionId], async ([accountId, divisionId]) => {
  if (!props.modelValue || !accountId || !divisionId) return
  await loadPoiCache()
})

async function loadPresets() {
  const response = await fetch('/publish-presets.json')
  const data = await response.json() as PresetData
  presetData.value = data
  if (!categoryPath.value.length) {
    categoryPath.value = findPathByLabel(data.cation, '其他闲置')
  }
  restoreDraft()
}

function updateMobile() {
  isMobile.value = window.innerWidth <= 920
}

function closeDialog() {
  if (submitting.value) return
  categoryOpen.value = false
  cityOpen.value = false
  poiPanelOpen.value = false
  emit('update:modelValue', false)
}

function closePickers() {
  categoryOpen.value = false
  cityOpen.value = false
  poiPanelOpen.value = false
}

function closePoiPanel() {
  poiPanelOpen.value = false
}

function restoreDraft() {
  if (draftLoaded.value || !presetData.value.cation.length) return
  const rawDraft = localStorage.getItem(DRAFT_KEY)
  if (!rawDraft) {
    draftLoaded.value = true
    loadDefaultPoiCache()
    return
  }
  try {
    const draft = JSON.parse(rawDraft)
    Object.assign(form, {
      title: draft.title || '',
      desc: draft.desc || '',
      imageUrls: draft.imageUrls || '',
      price: draft.price || '0.00',
      origPrice: draft.origPrice || '',
      quantity: draft.quantity || '1',
      shippingType: draft.shippingType || (draft.freeShipping === true ? 'FREE_SHIPPING' : 'FIXED_PRICE'),
      supportSelfPick: draft.supportSelfPick === true,
      freeShipping: draft.shippingType ? draft.shippingType === 'FREE_SHIPPING' : draft.freeShipping === true,
      postFee: draft.postFee || '',
      scheduled: draft.scheduled === true,
      scheduledTime: draft.scheduledTime || ''
    })
    restoreSpecs(draft)
    categoryPath.value = findPathByIds(presetData.value.cation, draft.categoryIds || [])
      || findPathByLabel(presetData.value.cation, '其他闲置')
    cityPath.value = findPathByIds(presetData.value.positioning, draft.cityIds || []) || []
    if (draft.poiCache) {
      applyPoiCache(draft.poiCache)
    } else {
      loadDefaultPoiCache()
    }
  } catch {
    localStorage.removeItem(DRAFT_KEY)
  } finally {
    draftLoaded.value = true
  }
}

function saveDraft() {
  localStorage.setItem(DRAFT_KEY, JSON.stringify({
    title: form.title,
    desc: form.desc,
    imageUrls: form.imageUrls,
    price: form.price,
    origPrice: form.origPrice,
    quantity: form.quantity,
    freeShipping: form.shippingType === 'FREE_SHIPPING',
    shippingType: form.shippingType,
    supportSelfPick: form.supportSelfPick,
    postFee: form.postFee,
    scheduled: form.scheduled,
    scheduledTime: form.scheduledTime,
    specGroups: specGroups.value,
    skuRows: skuRows.value,
    categoryIds: categoryPath.value.map(item => item.id),
    cityIds: cityPath.value.map(item => item.id),
    poiCache: poiCache.value
  }))
}

function clearDraft() {
  localStorage.removeItem(DRAFT_KEY)
  draftLoaded.value = false
}

function togglePicker(type: 'category' | 'city') {
  const opening = type === 'category' ? !categoryOpen.value : !cityOpen.value
  categoryOpen.value = type === 'category' ? opening : false
  cityOpen.value = type === 'city' ? opening : false
  if (type === 'category' && opening) {
    categoryBrowsePath.value = initialBrowsePath(categoryPath.value)
  }
  if (type === 'city' && opening) {
    cityBrowsePath.value = initialBrowsePath(cityPath.value)
  }
}

function buildColumns(root: TreeNode[], path: TreeNode[], maxColumns: number) {
  const columns: TreeNode[][] = [root]
  let children = path[0]?.children
  while (children?.length) {
    columns.push(children)
    const selected = path.find(item => children?.some(child => child.id === item.id))
    children = selected?.children
  }
  return columns.slice(0, maxColumns)
}

function selectNode(type: 'category' | 'city', node: TreeNode, level: number) {
  const path = type === 'category' ? categoryPath : cityPath
  const nextPath = [...path.value.slice(0, level), ...expandDuplicateLeaf(node)]
  path.value = nextPath
  if (type === 'city') poiCache.value = null
  if (!nextPath[nextPath.length - 1]?.children?.length) {
    if (type === 'category') categoryOpen.value = false
    if (type === 'city') cityOpen.value = false
  }
}

function selectMobileNode(type: 'category' | 'city', node: TreeNode) {
  const browsePath = type === 'category' ? categoryBrowsePath : cityBrowsePath
  const selectedPath = type === 'category' ? categoryPath : cityPath
  const expandedNodes = expandDuplicateLeaf(node)
  const nextPath = [...browsePath.value, ...expandedNodes]
  const lastNode = nextPath[nextPath.length - 1]
  if (lastNode?.children?.length) {
    browsePath.value = nextPath
    return
  }
  selectedPath.value = nextPath
  if (type === 'city') poiCache.value = null
  if (type === 'category') categoryOpen.value = false
  if (type === 'city') cityOpen.value = false
}

function backMobilePicker(type: 'category' | 'city') {
  const browsePath = type === 'category' ? categoryBrowsePath : cityBrowsePath
  browsePath.value = browsePath.value.slice(0, -1)
}

function currentMobileNodes(root: TreeNode[], path: TreeNode[]) {
  if (!path.length) return root
  return path[path.length - 1]?.children || root
}

function initialBrowsePath(path: TreeNode[]) {
  const lastNode = path[path.length - 1]
  if (!lastNode || lastNode.children?.length) return [...path]
  return path.slice(0, -1)
}

function expandDuplicateLeaf(node: TreeNode): TreeNode[] {
  const result = [node]
  let current = node
  while (true) {
    if (current.children?.length !== 1) break
    const child = current.children[0]
    if (!child || child.label !== current.label) break
    current = child
    result.push(current)
  }
  return result
}

function isSelected(path: TreeNode[], node: TreeNode) {
  return path.some(item => item.id === node.id)
}

function findPathByLabel(nodes: TreeNode[], label: string, path: TreeNode[] = []): TreeNode[] {
  for (const node of nodes) {
    const nextPath = [...path, node]
    if (node.label === label) return nextPath
    const found = node.children ? findPathByLabel(node.children, label, nextPath) : []
    if (found.length) return found
  }
  return []
}

function findPathByIds(nodes: TreeNode[], ids: number[]) {
  if (!ids.length) return null
  let currentNodes = nodes
  const result: TreeNode[] = []
  for (const id of ids) {
    const node = currentNodes.find(item => item.id === id)
    if (!node) return null
    result.push(node)
    currentNodes = node.children || []
  }
  return result
}

function findPathByDivisionId(nodes: TreeNode[], divisionId: number, path: TreeNode[] = []): TreeNode[] {
  for (const node of nodes) {
    const nextPath = [...path, node]
    const nodeDivisionId = node.v || node.value || node.id
    if (nodeDivisionId === divisionId) return nextPath
    const found = node.children ? findPathByDivisionId(node.children, divisionId, nextPath) : []
    if (found.length) return found
  }
  return []
}

function applyPoiCache(cache: PoiCacheItem) {
  poiCache.value = cache
  if (!cache.divisionId || cityPath.value.length) return
  const path = findPathByDivisionId(presetData.value.positioning, cache.divisionId)
  if (path.length) cityPath.value = path
}

function restoreSpecs(draft: any) {
  specGroups.value = Array.isArray(draft.specGroups)
    ? draft.specGroups.slice(0, 2).map((group: any) => ({
      id: Number(group.id) || specIdSeed++,
      propertyName: String(group.propertyName || '').slice(0, 4),
      values: Array.isArray(group.values) ? group.values.map((value: any) => String(value || '').slice(0, 12)) : []
    }))
    : []
  skuRows.value = Array.isArray(draft.skuRows)
    ? draft.skuRows.map((row: any) => ({
      key: String(row.key || ''),
      propertyKey: row.propertyKey,
      propertyValue: row.propertyValue,
      secondPropertyKey: row.secondPropertyKey,
      secondPropertyValue: row.secondPropertyValue,
      price: String(row.price || form.price || '0.00'),
      quantity: String(row.quantity || '1')
    }))
    : []
  rebuildSkuRows()
}

function addSpecGroup() {
  if (specGroups.value.length >= 2) return
  specGroups.value.push({ id: specIdSeed++, propertyName: '', values: [''] })
  rebuildSkuRows()
}

function removeSpecGroup(index: number) {
  specGroups.value.splice(index, 1)
  rebuildSkuRows()
}

function addSpecValue(group: SpecGroup) {
  group.values.push('')
  rebuildSkuRows()
}

function removeSpecValue(group: SpecGroup, index: number) {
  group.values.splice(index, 1)
  if (!group.values.length) group.values.push('')
  rebuildSkuRows()
}

function trimSpecName(group: SpecGroup) {
  group.propertyName = group.propertyName.trim().slice(0, 4)
  rebuildSkuRows()
}

function trimSpecValue(group: SpecGroup, index: number) {
  group.values[index] = (group.values[index] || '').trim().slice(0, 12)
  rebuildSkuRows()
}

function activeSpecGroups() {
  return specGroups.value
    .map(group => ({
      ...group,
      propertyName: group.propertyName.trim(),
      values: group.values.map(value => value.trim()).filter(Boolean)
    }))
    .filter(group => group.propertyName && group.values.length)
}

function rebuildSkuRows() {
  const groups = activeSpecGroups()
  if (!groups.length) {
    skuRows.value = []
    return
  }
  const previous = new Map(skuRows.value.map(row => [row.key, row]))
  const rows: SkuRow[] = []
  const first = groups[0] as SpecGroup
  const second = groups[1]
  for (const value of first.values) {
    if (second) {
      for (const secondValue of second.values) {
        rows.push(buildSkuRow(previous, first.propertyName, value, second.propertyName, secondValue))
      }
    } else {
      rows.push(buildSkuRow(previous, first.propertyName, value))
    }
  }
  skuRows.value = rows.slice(0, 1500)
}

function buildSkuRow(previous: Map<string, SkuRow>, propertyKey: string, propertyValue: string, secondPropertyKey?: string, secondPropertyValue?: string) {
  const key = [propertyKey, propertyValue, secondPropertyKey, secondPropertyValue].filter(Boolean).join('::')
  const old = previous.get(key)
  return {
    key,
    propertyKey,
    propertyValue,
    secondPropertyKey,
    secondPropertyValue,
    price: old?.price || form.price || '0.00',
    quantity: old?.quantity || form.quantity || '1'
  }
}

function validateSpecs() {
  const rawGroups = specGroups.value.filter(group => group.propertyName.trim() || group.values.some(value => value.trim()))
  if (!rawGroups.length) return ''
  const groups = activeSpecGroups()
  if (groups.length !== rawGroups.length) return '请填写完整规格类型和规格值'
  if (groups.length > 2) return '规格类型最多2个'
  const combinationCount = groups.reduce((total, group) => total * group.values.length, 1)
  if (combinationCount > 1500) return '规格组合数量超过上限，请精简规格值'
  if (!skuRows.value.length || skuRows.value.length !== combinationCount) return '规格明细不完整'
  let hasPositiveStock = false
  for (const row of skuRows.value) {
    if (!Number(row.price) || Number(row.price) <= 0) return '请填写有效规格价格'
    const quantity = Number(row.quantity)
    if (!Number.isInteger(quantity) || quantity < 0 || quantity > 9999) return '规格库存必须在0到9999之间'
    if (quantity > 0) hasPositiveStock = true
  }
  return hasPositiveStock ? '' : '至少需要一个规格库存大于0'
}

function validateForm() {
  if (!form.accountId) return '请选择发布账号'
  if (!form.title.trim()) return '请填写宝贝标题'
  if (titleCount.value > 30) return '宝贝标题不能超过30个字符'
  if (!form.desc.trim()) return '请填写宝贝描述'
  if (!imageList.value.length) return '请至少上传一张主图'
  if (!poiCache.value?.poiId || !poiCache.value?.gps) return '请先设置地图点位'
  if (!Number(form.price) || Number(form.price) <= 0) return '请填写有效售价'
  if (form.origPrice.trim() && (!Number(form.origPrice) || Number(form.origPrice) < Number(form.price))) return '原价不能低于售价'
  const specError = validateSpecs()
  if (specError) return specError
  const quantity = Number(form.quantity)
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 9999) return '库存必须在1到9999之间'
  if (form.shippingType === 'CHARGE_SHIPPING') return '按距离计费暂未开放，请选择包邮、一口价或无需邮寄'
  if (form.shippingType === 'FIXED_PRICE' && !isValidPostFee(form.postFee)) return '请填写有效固定邮费'
  if (form.scheduled && !isFutureTime(form.scheduledTime)) return '定时发布时间必须晚于当前时间'
  return ''
}

function isValidPostFee(value: string) {
  if (!value.trim()) return false
  const fee = Number(value)
  return Number.isFinite(fee) && fee >= 0
}

function isFutureTime(value: string) {
  if (!value) return false
  return new Date(value).getTime() > Date.now()
}

function toDatetimeLocalValue(date: Date) {
  const offsetMs = date.getTimezoneOffset() * 60000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

function buildCategory(): PublishItemCategory {
  const category = selectedCategory.value
  return {
    catId: String(category?.id || 42),
    catName: category?.label || '其他闲置',
    channelCatId: String(category?.value || category?.id || 42),
    tbCatId: String(category?.value || category?.id || 42)
  }
}

function buildLabels(category: PublishItemCategory): PublishItemLabel[] {
  return [{
    propertyName: '分类',
    text: category.catName,
    properties: `-10000##分类:${category.channelCatId}##${category.catName}`,
    channelCateId: category.channelCatId,
    tbCatId: category.tbCatId,
    propertyId: '-10000',
    channelCateName: category.catName
  }]
}

function buildAddressBase(): PublishItemAddress {
  const province = cityPath.value[0]
  const city = cityPath.value[1] || province
  const area = cityPath.value[2] || city
  return {
    prov: province?.label || '',
    city: city?.label || '',
    area: area?.label || '',
    divisionId: area?.v || area?.value || area?.id || 0,
    gps: '',
    poiId: '',
    poiName: area?.label || city?.label || ''
  }
}

function buildAddress(): PublishItemAddress {
  const base = buildAddressBase()
  if (!poiCache.value) return base
  return {
    prov: poiCache.value.prov || base.prov,
    city: poiCache.value.city || base.city,
    area: poiCache.value.area || base.area,
    divisionId: poiCache.value.divisionId || base.divisionId,
    gps: poiCache.value.gps,
    poiId: poiCache.value.poiId,
    poiName: poiCache.value.poiName
  }
}

async function loadPoiCache() {
  const address = buildAddressBase()
  if (!form.accountId || !address.divisionId) return
  try {
    const response = await getPoiCache({
      xianyuAccountId: form.accountId,
      divisionId: address.divisionId
    })
    poiCache.value = response.data || null
  } catch {
    poiCache.value = null
  }
}

async function openPoiPanel() {
  if (!form.accountId) return showError('请选择发布账号')
  poiPanelOpen.value = true
  poiCandidates.value = []
}

function validatePoiRequest() {
  if (!form.accountId) return '请选择发布账号'
  if (cityPath.value.length < 2) return '请先选择定位城市'
  return ''
}

async function loadPoiCandidates() {
  const error = validatePoiRequest()
  if (error) return showError(error)
  const address = buildAddressBase()
  poiLoading.value = true
  try {
    const response = await fetchPoiCandidates({
      xianyuAccountId: form.accountId as number,
      divisionId: address.divisionId,
      prov: address.prov,
      city: address.city,
      area: address.area
    })
    poiCandidates.value = response.data || []
    if (!poiCandidates.value.length) showError('未获取到可用地图点位')
  } catch (error: any) {
    showError(error.message || '获取地图点位失败')
  } finally {
    poiLoading.value = false
  }
}

async function selectPoiCandidate(candidate: PoiCandidate) {
  if (!form.accountId) return
  try {
    const response = await savePoiCache({
      ...candidate,
      xianyuAccountId: form.accountId,
      defaultPoi: true
    })
    applyPoiCache(response.data || candidate)
    poiPanelOpen.value = false
    saveDraft()
    showSuccess('地图点位已保存')
  } catch (error: any) {
    showError(error.message || '保存地图点位失败')
  }
}

async function loadDefaultPoiCache() {
  if (!form.accountId || cityPath.value.length || poiCache.value?.poiId) return
  try {
    const response = await getDefaultPoiCache(form.accountId)
    if (response.data) applyPoiCache(response.data)
  } catch {
    // 默认点位只是减少重复选择，失败不影响手动重新选择。
  }
}

function buildPayload(): PublishItemRequest {
  const itemCat = buildCategory()
  const groups = activeSpecGroups()
  const hasSku = groups.length > 0
  return {
    xianyuAccountId: form.accountId as number,
    title: form.title.trim(),
    desc: form.desc.trim(),
    price: form.price.trim(),
    origPrice: form.origPrice.trim() || undefined,
    quantity: form.quantity.trim() || '1',
    scheduled: form.scheduled,
    scheduledTime: form.scheduled ? form.scheduledTime : undefined,
    freeShipping: form.shippingType === 'FREE_SHIPPING',
    shippingType: form.shippingType,
    supportSelfPick: form.supportSelfPick,
    postFee: form.shippingType === 'FIXED_PRICE' ? form.postFee.trim() : undefined,
    imageUrls: imageList.value,
    itemCat,
    itemAddr: buildAddress(),
    itemLabels: buildLabels(itemCat),
    itemProperties: hasSku ? groups.map((group, index) => ({
      propertyName: group.propertyName,
      supportImage: index === 0,
      propertyValues: group.values.map(value => ({ propertyValue: value }))
    })) : undefined,
    itemSkuList: hasSku ? skuRows.value.map(row => ({
      propertyKey: row.propertyKey,
      propertyValue: row.propertyValue,
      secondPropertyKey: row.secondPropertyKey,
      secondPropertyValue: row.secondPropertyValue,
      price: row.price,
      quantity: row.quantity
    })) : undefined
  }
}

async function submitPublish() {
  const error = validateForm()
  if (error) return showError(error)
  saveDraft()
  submitting.value = true
  try {
    const response = await publishItem(buildPayload())
    if ((response.code === 0 || response.code === 200) && response.data?.success) {
      showSuccess(response.data.message || '商品发布成功')
      clearDraft()
      emit('published')
      emit('update:modelValue', false)
    } else {
      saveDraft()
      const data = response.data
      if (data?.needCaptcha || data?.needManual) {
        showError(data.message || '自动滑块失败，请人工更新 Cookie')
      } else if (data?.recoveryAttempted) {
        showError(data.message || '已尝试自动刷新和验证，仍失败，请人工更新Cookie')
      } else {
        showError(response.msg || data?.message || '商品发布失败')
      }
    }
  } catch (error: any) {
    saveDraft()
    showError(error.message || '商品发布失败')
  } finally {
    submitting.value = false
  }
}

</script>

<template>
  <Transition name="overlay-fade">
    <div v-if="modelValue" class="publish-dialog__overlay" @click.self="closeDialog">
      <section class="publish-dialog" role="dialog" aria-modal="true" @click="closePickers">
        <header class="publish-dialog__header">
          <div>
            <h2 class="publish-dialog__title">发布商品</h2>
            <p class="publish-dialog__subtitle">{{ selectedAccountName || '请选择账号' }}</p>
          </div>
          <button class="publish-dialog__close" type="button" @click="closeDialog">×</button>
        </header>

        <div class="publish-form">
          <label class="publish-row publish-row--required">
            <span class="publish-label">宝贝标题:</span>
            <span class="publish-control publish-control--count">
              <input v-model="form.title" class="publish-input" maxlength="30" placeholder="请输入宝贝标题，最多30个字" />
              <em>{{ titleCount }}/30</em>
            </span>
          </label>

          <label class="publish-row publish-row--required publish-row--top">
            <span class="publish-label">宝贝描述:</span>
            <span class="publish-control publish-control--textarea">
              <textarea v-model="form.desc" class="publish-textarea" maxlength="5000" placeholder="请输入宝贝描述"></textarea>
              <em>{{ descCount }}/5000</em>
            </span>
          </label>

          <div class="publish-row publish-row--required publish-row--top">
            <span class="publish-label">宝贝图:</span>
            <div class="publish-control">
              <MultiImageUploader :account-id="form.accountId || 0" v-model="form.imageUrls" :max="10" />
              <p class="publish-help">可拖拽改变图片顺序，默认首张图为主图，最多上传10张</p>
            </div>
          </div>

          <div class="publish-row">
            <span class="publish-label">商品分类:</span>
            <div class="publish-control publish-picker" @click.stop>
              <button class="publish-picker__input" type="button" @click="togglePicker('category')">
                <span>{{ categoryText }}</span><span>⌄</span>
              </button>
              <div v-if="categoryOpen" class="publish-cascader" :class="{ 'publish-cascader--mobile': isMobile }">
                <template v-if="isMobile">
                  <div class="publish-cascader__mobile-head">
                    <button type="button" :disabled="!categoryBrowsePath.length" @click="backMobilePicker('category')">返回上级</button>
                    <span>{{ mobileCategoryTrail || '选择分类' }}</span>
                  </div>
                  <div class="publish-cascader__col publish-cascader__col--mobile">
                    <button v-for="node in mobileCategoryNodes" :key="node.id" type="button" :class="{ active: isSelected(categoryPath, node) }" @click="selectMobileNode('category', node)">
                      <span>{{ node.label }}</span><span v-if="node.children?.length">›</span>
                    </button>
                  </div>
                </template>
                <div v-for="(column, level) in categoryColumns" v-else :key="level" class="publish-cascader__col">
                  <button v-for="node in column" :key="node.id" type="button" :class="{ active: isSelected(categoryPath, node) }" @click="selectNode('category', node, level)">
                    <span>{{ node.label }}</span><span v-if="node.children?.length">›</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="publish-row">
            <span class="publish-label">定位城市:</span>
            <div class="publish-control publish-picker" @click.stop>
              <button class="publish-picker__input" type="button" @click="togglePicker('city')">
                <span>{{ cityText || '请选择' }}</span><span>{{ cityOpen ? '⌃' : '⌄' }}</span>
              </button>
              <div v-if="cityOpen" class="publish-cascader publish-cascader--wide" :class="{ 'publish-cascader--mobile': isMobile }">
                <template v-if="isMobile">
                  <div class="publish-cascader__mobile-head">
                    <button type="button" :disabled="!cityBrowsePath.length" @click="backMobilePicker('city')">返回上级</button>
                    <span>{{ mobileCityTrail || '选择地区' }}</span>
                  </div>
                  <div class="publish-cascader__col publish-cascader__col--mobile">
                    <button v-for="node in mobileCityNodes" :key="node.id" type="button" :class="{ active: isSelected(cityPath, node) }" @click="selectMobileNode('city', node)">
                      <span>{{ node.label }}</span><span v-if="node.children?.length">›</span>
                    </button>
                  </div>
                </template>
                <div v-for="(column, level) in cityColumns" v-else :key="level" class="publish-cascader__col">
                  <button v-for="node in column" :key="node.id" type="button" :class="{ active: isSelected(cityPath, node) }" @click="selectNode('city', node, level)">
                    <span>{{ node.label }}</span><span v-if="node.children?.length">›</span>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div class="publish-row publish-row--required">
            <span class="publish-label">地图点位:</span>
            <div class="publish-control publish-poi" @click.stop>
              <div class="publish-poi__line">
                <button class="publish-picker__input publish-poi__display" type="button" @click="openPoiPanel">
                  <span>{{ poiText }}</span><span>{{ poiCache ? '已缓存' : '设置' }}</span>
                </button>
                <button class="publish-poi__button" type="button" @click="openPoiPanel">选择点位</button>
              </div>
              <p class="publish-help">同账号同地区只需设置一次，发布时直接复用缓存点位</p>
              <div v-if="poiPanelOpen" class="publish-poi-panel" :class="{ 'publish-poi-panel--mobile': isMobile }">
                <div class="publish-poi-panel__head">
                  <strong>选择地图点位</strong>
                  <button type="button" @click="closePoiPanel">×</button>
                </div>
                <div class="publish-poi-panel__actions">
                  <span>{{ cityText }}</span>
                  <button type="button" :disabled="poiLoading" @click="loadPoiCandidates">
                    {{ poiLoading ? '获取中...' : '获取官方点位' }}
                  </button>
                </div>
                <div class="publish-poi-list">
                  <button v-for="candidate in poiCandidates" :key="candidate.poiId" type="button" @click="selectPoiCandidate(candidate)">
                    <strong>{{ candidate.poiName }}</strong>
                    <span>{{ candidate.address || [candidate.prov, candidate.city, candidate.area].filter(Boolean).join(' / ') }}</span>
                  </button>
                  <p v-if="!poiCandidates.length" class="publish-poi-empty">点击“获取官方点位”后选择一个真实地图点位</p>
                </div>
              </div>
            </div>
          </div>

          <div class="publish-section">
            <div class="publish-section__head">
              <strong>商品规格</strong>
              <span>上传主图/填写内容后将为你智能识别属性</span>
            </div>
            <div class="publish-spec-list">
              <div v-for="(group, groupIndex) in specGroups" :key="group.id" class="publish-spec-group">
                <div class="publish-spec-group__head">
                  <input v-model="group.propertyName" class="publish-input publish-spec-name" maxlength="4" placeholder="规格类型" @blur="trimSpecName(group)" />
                  <button class="publish-icon-btn" type="button" @click="removeSpecGroup(groupIndex)">删除</button>
                </div>
                <div class="publish-spec-values">
                  <span v-for="(_, valueIndex) in group.values" :key="valueIndex" class="publish-spec-value">
                    <input v-model="group.values[valueIndex]" class="publish-input" maxlength="12" placeholder="规格值" @blur="trimSpecValue(group, valueIndex)" />
                    <button type="button" @click="removeSpecValue(group, valueIndex)">×</button>
                  </span>
                  <button class="publish-add-value" type="button" @click="addSpecValue(group)">+ 规格值</button>
                </div>
              </div>
            </div>
            <button class="publish-add-spec" :disabled="specGroups.length >= 2" type="button" @click="addSpecGroup">+ 添加规格类型（{{ specGroups.length }}/2）</button>
            <div v-if="skuRows.length" class="publish-sku-table">
              <div class="publish-sku-table__head" :class="{ 'publish-sku-table__head--two': activeSpecGroups().length > 1 }">
                <span>{{ activeSpecGroups()[0]?.propertyName }}</span>
                <span v-if="activeSpecGroups().length > 1">{{ activeSpecGroups()[1]?.propertyName }}</span>
                <span>价格</span>
                <span>库存</span>
              </div>
              <div v-for="row in skuRows" :key="row.key" class="publish-sku-row" :class="{ 'publish-sku-row--two': activeSpecGroups().length > 1 }">
                <span>{{ row.propertyValue }}</span>
                <span v-if="activeSpecGroups().length > 1">{{ row.secondPropertyValue }}</span>
                <input v-model="row.price" class="publish-input" inputmode="decimal" placeholder="0.00" />
                <input v-model="row.quantity" class="publish-input" inputmode="numeric" placeholder="0" />
              </div>
            </div>
          </div>

          <div class="publish-section">
            <div class="publish-section__head">
              <strong>价格</strong>
            </div>
            <div class="publish-price-grid">
              <label class="publish-price-field publish-price-field--required">
                <span>价格</span>
                <div class="publish-money-input">
                  <em>￥</em>
                  <input v-model="form.price" class="publish-input" inputmode="decimal" placeholder="0.00" />
                </div>
              </label>
              <label class="publish-price-field">
                <span>原价</span>
                <div class="publish-money-input">
                  <em>￥</em>
                  <input v-model="form.origPrice" class="publish-input" inputmode="decimal" placeholder="0.00" />
                </div>
              </label>
            </div>
            <p class="publish-help">鱼小铺软件服务费 = 成交额（含运费）*1.6%</p>
          </div>

          <label class="publish-row publish-row--required">
            <span class="publish-label">库存:</span>
            <span class="publish-price-wrap"><input v-model="form.quantity" class="publish-input publish-input--price" inputmode="numeric" /> 件</span>
          </label>

          <div class="publish-section">
            <div class="publish-section__head">
              <strong>发货设置</strong>
            </div>
            <div class="publish-shipping-options">
              <label><input v-model="form.shippingType" value="FREE_SHIPPING" type="radio" /> 包邮</label>
              <label class="publish-disabled"><input value="CHARGE_SHIPPING" type="radio" disabled /> 按距离计费</label>
              <label><input v-model="form.shippingType" value="FIXED_PRICE" type="radio" /> 一口价</label>
              <label><input v-model="form.shippingType" value="NO_SHIPPING" type="radio" /> 无需邮寄</label>
              <label class="publish-shipping-switch">
                <span>支持自提</span>
                <span class="publish-switch"><input v-model="form.supportSelfPick" type="checkbox" /><span></span></span>
              </label>
            </div>
            <span v-if="form.shippingType === 'FIXED_PRICE'" class="publish-postage-wrap">
              <span class="publish-postage-label">邮费</span>
              <span class="publish-money-input publish-money-input--postage">
                <em>￥</em>
                <input v-model="form.postFee" class="publish-input publish-input--postage" inputmode="decimal" placeholder="请输入邮费" />
              </span>
            </span>
          </div>

          <label class="publish-row publish-row--disabled">
            <span class="publish-label"></span>
            <span class="publish-check publish-disabled"><input v-model="form.autoOnShelf" type="checkbox" disabled /> 开启售罄自动上架</span>
          </label>

          <div class="publish-row">
            <span class="publish-label">启用定时发布:</span>
            <label class="publish-switch"><input v-model="form.scheduled" type="checkbox" /><span></span></label>
          </div>

          <label v-if="form.scheduled" class="publish-row">
            <span class="publish-label">发布时间:</span>
            <input v-model="form.scheduledTime" class="publish-input publish-input--short" type="datetime-local" :min="minScheduledTime" />
          </label>
        </div>

        <footer class="publish-dialog__footer">
          <button class="publish-btn publish-btn--primary" :disabled="submitting || !form.accountId" type="button" @click="submitPublish">
            {{ submitting ? '提交中...' : (form.scheduled ? '定时发布' : '发布') }}
          </button>
        </footer>
      </section>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import GoodsDetail from './GoodsDetail.vue'

interface Props {
  modelValue: boolean
  goodsId: string
  accountId: number | null
  initialConfigAction?: 'delivery' | 'reply' | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>

<template>
  <GoodsDetail
    :model-value="modelValue"
    :goods-id="goodsId"
    :account-id="accountId"
    :initial-config-action="initialConfigAction"
    @update:model-value="$emit('update:modelValue', $event)"
    @refresh="$emit('refresh')"
  />
</template>

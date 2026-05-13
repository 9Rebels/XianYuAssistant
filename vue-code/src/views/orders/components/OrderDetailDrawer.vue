<!-- 订单详情抽屉组件 -->
<script setup lang="ts">
import { ref, computed } from 'vue'
import type { OrderVO } from '@/api/order'
import { formatTime as formatDateTime } from '@/utils'

interface Props {
  visible: boolean
  order: OrderVO | null
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'refresh'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const handleClose = () => {
  emit('update:visible', false)
}

const formatTime = (timestamp?: number) => {
  return timestamp ? formatDateTime(timestamp) : '-'
}

const getStatusTag = (status?: number) => {
  const statusMap: Record<number, { text: string; type: string }> = {
    1: { text: '待付款', type: 'warning' },
    2: { text: '待发货', type: 'primary' },
    3: { text: '已发货', type: 'success' },
    4: { text: '已完成', type: 'info' },
    5: { text: '已取消', type: 'danger' }
  }
  return statusMap[status || 0] || { text: '未知', type: 'info' }
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="订单详情"
    direction="rtl"
    size="500px"
    @close="handleClose"
  >
    <div v-if="order" class="order-detail">
      <!-- 订单基本信息 -->
      <el-descriptions title="订单信息" :column="1" border>
        <el-descriptions-item label="订单ID">
          {{ order.orderId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="订单状态">
          <el-tag :type="getStatusTag(order.orderStatus).type">
            {{ order.orderStatusText || getStatusTag(order.orderStatus).text }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品标题">
          {{ order.goodsTitle || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="买家">
          {{ order.buyerUserName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="订单金额">
          {{ order.orderAmountText ? `¥${order.orderAmountText}` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="下单时间">
          {{ formatTime(order.createTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="付款时间">
          {{ formatTime(order.payTime) }}
        </el-descriptions-item>
        <el-descriptions-item label="发货时间">
          {{ formatTime(order.deliveryTime) }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 收货人信息 -->
      <el-descriptions
        v-if="order.receiverName || order.receiverPhone || order.receiverAddress"
        title="收货人信息"
        :column="1"
        border
        style="margin-top: 20px"
      >
        <el-descriptions-item label="收货人">
          {{ order.receiverName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="联系电话">
          {{ order.receiverPhone || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="收货地址">
          {{ order.receiverAddress || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="城市">
          {{ order.receiverCity || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-else style="margin-top: 20px; padding: 20px; background: #f5f5f5; border-radius: 4px; text-align: center; color: #999;">
        暂无收货人信息
      </div>
    </div>

    <div v-else style="padding: 20px; text-align: center; color: #999;">
      暂无订单数据
    </div>
  </el-drawer>
</template>

<style scoped>
.order-detail {
  padding: 0 20px;
}

:deep(.el-descriptions__title) {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
</style>

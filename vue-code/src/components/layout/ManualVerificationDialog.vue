<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppNotificationStore } from '@/stores/appNotification'

import IconAlert from '@/components/icons/IconAlert.vue'
import IconQrCode from '@/components/icons/IconQrCode.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconWifi from '@/components/icons/IconWifi.vue'

const router = useRouter()
const store = useAppNotificationStore()

const state = computed(() => store.manualVerification)

const dialogVisible = computed({
  get: () => state.value.visible,
  set: (value: boolean) => {
    if (!value) store.closeManualVerification()
  }
})

const accountLabel = computed(() => state.value.accountId ? `账号 ${state.value.accountId}` : '闲鱼账号')
const imageUrl = computed(() => state.value.screenshotUrl)
const confirmText = computed(() => state.value.confirming ? '读取中...' : '我已处理')

const handleImageError = () => {
  store.manualVerification.screenshotUrl = ''
}

const openConnection = async () => {
  const accountId = state.value.accountId
  if (!accountId) return
  store.closeManualVerification()
  await router.push(`/connection/${accountId}`)
}

const confirmDone = async () => {
  await store.confirmManualVerificationDone()
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    class="manual-verification-dialog"
    width="760px"
    append-to-body
    :close-on-click-modal="false"
    :destroy-on-close="false"
  >
    <template #header>
      <div class="manual-verification__header">
        <span class="manual-verification__icon"><IconAlert /></span>
        <div>
          <div class="manual-verification__title">需要人工验证</div>
          <div class="manual-verification__subtitle">{{ accountLabel }} · {{ state.verificationType || '扫码/人脸验证' }}</div>
        </div>
      </div>
    </template>

    <div class="manual-verification">
      <section class="manual-verification__main">
        <div class="manual-verification__notice">
          <IconQrCode />
          <div>
            <strong>{{ state.message || '账号登录不成功，需要人工处理' }}</strong>
            <p>{{ state.detail || '请使用闲鱼 App 扫码，或按截图中的页面提示完成人脸、短信等验证。' }}</p>
            <p v-if="state.lastError" class="manual-verification__error">{{ state.lastError }}</p>
          </div>
        </div>

        <div class="manual-verification__preview">
          <el-image
            v-if="imageUrl"
            :key="imageUrl"
            :src="imageUrl"
            class="manual-verification__image"
            fit="contain"
            :preview-src-list="[imageUrl]"
            :initial-index="0"
            preview-teleported
            hide-on-click-modal
            @error="handleImageError"
          />
          <div v-else class="manual-verification__empty">
            <IconQrCode />
            <span>暂未收到验证截图</span>
          </div>
        </div>
      </section>

      <aside class="manual-verification__side">
        <div class="manual-verification__step">
          <span>1</span>
          <p>如果截图里有二维码，用闲鱼 App 扫码确认。</p>
        </div>
        <div class="manual-verification__step">
          <span>2</span>
          <p>如果是人脸、短信或安全验证，按截图页面提示完成。</p>
        </div>
        <div class="manual-verification__step">
          <span>3</span>
          <p>完成后等待系统自动检测成功，或进入连接详情重新拉取状态。</p>
        </div>
      </aside>
    </div>

    <template #footer>
      <div class="manual-verification__actions">
        <el-button @click="store.refreshManualVerificationImage">
          <IconRefresh />
          <span>刷新截图</span>
        </el-button>
        <el-button @click="openConnection" :disabled="!state.accountId">
          <IconWifi />
          <span>连接详情</span>
        </el-button>
        <el-button type="primary" :loading="state.confirming" @click="confirmDone">
          <IconCheck />
          <span>{{ confirmText }}</span>
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.manual-verification__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.manual-verification__icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--app-warning) 14%, transparent);
  color: var(--app-warning);
  flex-shrink: 0;
}

.manual-verification__icon svg {
  width: 20px;
  height: 20px;
}

.manual-verification__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--app-text);
}

.manual-verification__subtitle {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 13px;
}

.manual-verification {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 18px;
}

.manual-verification__main,
.manual-verification__side {
  min-width: 0;
}

.manual-verification__notice {
  display: flex;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--app-warning) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--app-warning) 35%, var(--app-border));
  color: var(--app-text);
}

.manual-verification__notice svg {
  width: 22px;
  height: 22px;
  color: var(--app-warning);
  flex-shrink: 0;
}

.manual-verification__notice strong {
  display: block;
  font-size: 14px;
  line-height: 1.45;
}

.manual-verification__notice p {
  margin: 6px 0 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.55;
}

.manual-verification__notice .manual-verification__error {
  color: #f56c6c;
}

.manual-verification__preview {
  margin-top: 14px;
  min-height: 320px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-bg-muted);
  overflow: auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.manual-verification__image {
  display: flex;
  max-width: 100%;
  max-height: 70vh;
  width: auto;
  height: auto;
  cursor: zoom-in;
}

.manual-verification__image :deep(img) {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.manual-verification__empty {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  color: var(--app-text-muted);
  font-size: 13px;
}

.manual-verification__empty svg {
  width: 42px;
  height: 42px;
  color: var(--app-text-soft);
}

.manual-verification__side {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.manual-verification__step {
  display: flex;
  gap: 10px;
  padding: 12px;
  border-radius: 8px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
}

.manual-verification__step span {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--app-primary);
  color: var(--app-surface-strong);
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.manual-verification__step p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.5;
}

.manual-verification__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.manual-verification__actions :deep(.el-button span) {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.manual-verification__actions svg {
  width: 16px;
  height: 16px;
}

@media (max-width: 767px) {
  .manual-verification {
    grid-template-columns: 1fr;
  }

  .manual-verification__preview {
    min-height: 240px;
  }

  .manual-verification__actions {
    justify-content: stretch;
  }

  .manual-verification__actions :deep(.el-button) {
    flex: 1 1 100%;
  }
}
</style>

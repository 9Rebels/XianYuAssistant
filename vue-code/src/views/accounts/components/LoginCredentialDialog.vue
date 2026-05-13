<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { updateAccount } from '@/api/account'
import { showSuccess, showError } from '@/utils'
import { getAccountDisplayName } from '@/utils/accountDisplay'
import type { Account } from '@/types'

interface Props {
  modelValue: boolean
  account?: Account | null
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const formData = ref({
  loginUsername: '',
  loginPassword: '',
  clearPassword: false
})

const accountName = computed(() => props.account ? getAccountDisplayName(props.account) : '')

watch(() => [props.modelValue, props.account] as const, ([visible, account]) => {
  if (!visible || !account) return
  formData.value = {
    loginUsername: account.loginUsername || '',
    loginPassword: '',
    clearPassword: false
  }
}, { immediate: true })

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!props.account) return
  try {
    const data: Record<string, unknown> = {
      accountId: props.account.id,
      loginUsername: formData.value.loginUsername.trim()
    }
    if (formData.value.clearPassword) {
      data.clearLoginPassword = true
    } else if (formData.value.loginPassword) {
      data.loginPassword = formData.value.loginPassword
    }

    const response = await updateAccount(data)
    if (response.code === 0 || response.code === 200) {
      showSuccess('登录凭据已保存')
      handleClose()
      emit('success')
    } else {
      throw new Error(response.msg || '保存失败')
    }
  } catch (error: any) {
    if (!error.messageShown) {
      showError('保存失败: ' + error.message)
    }
  }
}
</script>

<template>
  <teleport to="body">
    <div v-if="modelValue" class="modal-overlay" @click="handleClose">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h2 class="modal-title">登录凭据</h2>
          <p v-if="accountName" class="modal-subtitle">{{ accountName }}</p>
        </div>

        <div class="modal-body">
          <div class="credential-field">
            <label>登录账号</label>
            <input
              v-model="formData.loginUsername"
              type="text"
              class="modal-input"
              autocomplete="username"
              placeholder="手机号 / 邮箱 / 用户名"
            />
          </div>
          <div class="credential-field">
            <label>登录密码</label>
            <input
              v-model="formData.loginPassword"
              type="password"
              class="modal-input"
              autocomplete="current-password"
              :disabled="formData.clearPassword"
              placeholder="留空则不修改已保存密码"
            />
          </div>
          <label class="credential-check">
            <input v-model="formData.clearPassword" type="checkbox" />
            <span>清空已保存密码</span>
          </label>
        </div>

        <div class="modal-footer">
          <button class="modal-btn modal-btn-cancel" @click="handleClose">取消</button>
          <div class="modal-divider"></div>
          <button class="modal-btn modal-btn-primary" @click="handleSubmit">保存</button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}
.modal {
  width: 360px;
  max-width: calc(100vw - 32px);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  box-shadow: 0 16px 44px rgba(0, 0, 0, 0.18);
  overflow: hidden;
  animation: scaleIn 0.2s ease;
}
.modal-header {
  padding: 16px 18px 12px;
  text-align: center;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.1);
}
.modal-title {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  color: #111;
}
.modal-subtitle {
  margin: 4px 0 0;
  font-size: 12px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.modal-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.credential-field {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.credential-field label {
  font-size: 13px;
  color: #666;
}
.modal-input {
  width: 100%;
  height: 40px;
  border-radius: 10px;
  border: none;
  padding: 0 12px;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.06);
  color: #111;
  outline: none;
  box-sizing: border-box;
}
.modal-input:focus {
  background: rgba(0, 0, 0, 0.09);
}
.modal-input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.credential-check {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #555;
  user-select: none;
}
.credential-check input {
  width: 16px;
  height: 16px;
}
.modal-footer {
  display: flex;
  height: 48px;
  border-top: 0.5px solid rgba(0, 0, 0, 0.1);
}
.modal-btn {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  color: #333;
}
.modal-btn:active { opacity: 0.55; }
.modal-btn-primary { color: #007aff; }
.modal-divider {
  width: 0.5px;
  background: rgba(0, 0, 0, 0.1);
}
@media (prefers-color-scheme: dark) {
  .modal {
    background: rgba(30, 30, 32, 0.92);
    box-shadow: 0 18px 48px rgba(0, 0, 0, 0.55);
  }
  .modal-header,
  .modal-footer {
    border-color: rgba(255, 255, 255, 0.12);
  }
  .modal-title,
  .modal-input {
    color: #f5f5f7;
  }
  .modal-subtitle,
  .credential-field label,
  .credential-check,
  .modal-btn-cancel {
    color: #b6b6bd;
  }
  .modal-input {
    background: rgba(255, 255, 255, 0.1);
  }
  .modal-input:focus {
    background: rgba(255, 255, 255, 0.14);
  }
  .modal-divider {
    background: rgba(255, 255, 255, 0.12);
  }
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes scaleIn { from { transform: scale(0.94); opacity: 0; } to { transform: scale(1); opacity: 1; } }
</style>

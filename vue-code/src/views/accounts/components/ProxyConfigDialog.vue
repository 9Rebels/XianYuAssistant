<script setup lang="ts">
import { ref, watch } from 'vue'
import { updateAccount } from '@/api/account'
import { showSuccess, showError } from '@/utils'
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
  enabled: false,
  proxyType: 'http',
  proxyHost: '',
  proxyPort: '',
  proxyUsername: '',
  proxyPassword: ''
})

watch(() => props.account, (account) => {
  if (account) {
    const hasProxy = !!account.proxyType && !!account.proxyHost
    formData.value = {
      enabled: hasProxy,
      proxyType: account.proxyType || 'http',
      proxyHost: account.proxyHost || '',
      proxyPort: account.proxyPort ? String(account.proxyPort) : '',
      proxyUsername: account.proxyUsername || '',
      proxyPassword: account.proxyPassword === '***' ? '' : (account.proxyPassword || '')
    }
  }
}, { immediate: true })

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!props.account) return
  try {
    const data: any = { accountId: props.account.id, updateProxy: true }
    if (formData.value.enabled && formData.value.proxyHost && formData.value.proxyPort) {
      data.proxyType = formData.value.proxyType
      data.proxyHost = formData.value.proxyHost.trim()
      data.proxyPort = parseInt(formData.value.proxyPort)
      data.proxyUsername = formData.value.proxyUsername.trim() || null
      data.proxyPassword = formData.value.proxyPassword || null
    } else {
      data.proxyType = null
      data.proxyHost = null
      data.proxyPort = null
      data.proxyUsername = null
      data.proxyPassword = null
    }
    const response = await updateAccount(data)
    if (response.code === 0 || response.code === 200) {
      showSuccess('代理设置已保存')
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
          <h2 class="modal-title">代理设置</h2>
        </div>

        <div class="modal-body">
          <label class="proxy-switch">
            <span>启用代理</span>
            <input v-model="formData.enabled" type="checkbox" />
          </label>

          <template v-if="formData.enabled">
            <div class="proxy-field">
              <label>类型</label>
              <select v-model="formData.proxyType" class="modal-input">
                <option value="http">HTTP</option>
                <option value="https">HTTPS</option>
                <option value="socks5">SOCKS5</option>
              </select>
            </div>
            <div class="proxy-field">
              <label>主机地址</label>
              <input v-model="formData.proxyHost" type="text" class="modal-input" placeholder="例如: 127.0.0.1" />
            </div>
            <div class="proxy-field">
              <label>端口</label>
              <input v-model="formData.proxyPort" type="number" class="modal-input" placeholder="例如: 7890" />
            </div>
            <div class="proxy-field">
              <label>用户名（可选）</label>
              <input v-model="formData.proxyUsername" type="text" class="modal-input" placeholder="无认证可留空" />
            </div>
            <div class="proxy-field">
              <label>密码（可选）</label>
              <input v-model="formData.proxyPassword" type="password" class="modal-input" placeholder="无认证可留空" />
            </div>
          </template>
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
  background: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}
.modal {
  width: 340px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  animation: scaleIn 0.2s ease;
}
.modal-header {
  padding: 16px;
  text-align: center;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.1);
}
.modal-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #000;
}
.modal-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.proxy-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  color: #333;
}
.proxy-switch input[type="checkbox"] {
  width: 44px;
  height: 24px;
  appearance: none;
  -webkit-appearance: none;
  background: #e0e0e0;
  border-radius: 12px;
  position: relative;
  cursor: pointer;
  transition: background 0.2s;
}
.proxy-switch input[type="checkbox"]:checked {
  background: #007aff;
}
.proxy-switch input[type="checkbox"]::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}
.proxy-switch input[type="checkbox"]:checked::after {
  transform: translateX(20px);
}
.proxy-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.proxy-field label {
  font-size: 13px;
  color: #666;
}
.modal-input {
  width: 100%;
  height: 38px;
  border-radius: 10px;
  border: none;
  padding: 0 12px;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.05);
  color: #000;
  outline: none;
  box-sizing: border-box;
}
.modal-input:focus {
  background: rgba(0, 0, 0, 0.08);
}
select.modal-input {
  cursor: pointer;
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
}
.modal-btn:active { opacity: 0.5; }
.modal-btn-cancel { color: #666; }
.modal-btn-primary { color: #007aff; }
.modal-divider {
  width: 0.5px;
  background: rgba(0, 0, 0, 0.1);
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes scaleIn { from { transform: scale(0.9); opacity: 0; } to { transform: scale(1); opacity: 1; } }
</style>

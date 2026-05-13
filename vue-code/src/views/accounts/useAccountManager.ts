import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import {
  getAccountList,
  deleteAccount as deleteAccountApi,
  refreshAccountProfile as refreshAccountProfileApi,
  runItemPolish as runItemPolishApi,
  getItemPolishTask,
  saveItemPolishTask,
  type ItemPolishTask
} from '@/api/account'
import { showSuccess, showError, showInfo } from '@/utils'
import type { Account } from '@/types'

export function useAccountManager() {
  const router = useRouter()
  const loading = ref(false)
  const refreshingProfileId = ref<number | null>(null)
  const polishingAccountId = ref<number | null>(null)
  const savingPolishTask = ref(false)
  const accounts = ref<Account[]>([])
  
  const dialogs = reactive({
    add: false,
    manualAdd: false,
    qrLogin: false,
    deleteConfirm: false,
    polishTask: false,
    proxy: false,
    credential: false
  })
  
  const currentAccount = ref<Account | null>(null)
  const deleteAccountId = ref<number | null>(null)
  const proxyAccount = ref<Account | null>(null)
  const credentialAccount = ref<Account | null>(null)
  const polishTaskAccount = ref<Account | null>(null)
  const polishTask = ref<ItemPolishTask | null>(null)
  const polishTaskForm = reactive({
    enabled: true,
    runHour: 8,
    randomDelayMaxMinutes: 10
  })

  // 加载账号列表
  const loadAccounts = async () => {
    loading.value = true;
    try {
      const response = await getAccountList();
      if (response.code === 0 || response.code === 200) {
        // 后端返回格式: { code: 200, data: { accounts: [...] } }
        accounts.value = response.data?.accounts || [];
      } else {
        throw new Error(response.msg || '获取账号列表失败');
      }
    } catch (error: any) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('加载账号列表失败: ' + error.message);
      }
      accounts.value = [];
    } finally {
      loading.value = false;
    }
  };

  // 显示添加对话框
  const showAddDialog = () => {
    currentAccount.value = null;
    dialogs.add = true;
  };

  // 显示手动添加对话框
  const showManualAddDialog = () => {
    dialogs.manualAdd = true;
  };

  // 显示扫码登录对话框
  const showQRLoginDialog = () => {
    dialogs.qrLogin = true;
  };

  // 编辑账号
  const editAccount = (account: Account) => {
    currentAccount.value = account;
    dialogs.add = true;
  };

  const openProxyDialog = (account: Account) => {
    proxyAccount.value = account;
    dialogs.proxy = true;
  };

  const openCredentialDialog = (account: Account) => {
    credentialAccount.value = account
    dialogs.credential = true
  }

  const refreshAccountProfile = async (account: Account) => {
    refreshingProfileId.value = account.id
    try {
      const response = await refreshAccountProfileApi(account.id)
      if (response.code === 0 || response.code === 200) {
        showSuccess(response.data?.message || '账号资料刷新成功')
        await loadAccounts()
      } else {
        throw new Error(response.msg || '刷新资料失败')
      }
    } catch (error: any) {
      if (!error.messageShown) {
        showError('刷新资料失败: ' + error.message)
      }
    } finally {
      refreshingProfileId.value = null
    }
  }

  const runItemPolish = async (account: Account) => {
    polishingAccountId.value = account.id
    try {
      const response = await runItemPolishApi(account.id)
      if (response.code === 0 || response.code === 200) {
        const data = response.data
        if (data?.needCaptcha || data?.needManual) {
          showError(data.message || '自动滑块失败，请人工更新 Cookie')
        } else {
          showSuccess(data?.message || '擦亮完成')
        }
      } else {
        throw new Error(response.msg || '擦亮失败')
      }
    } catch (error: any) {
      if (!error.messageShown) {
        showError('擦亮失败: ' + error.message)
      }
    } finally {
      polishingAccountId.value = null
    }
  }

  const openPolishTaskDialog = async (account: Account) => {
    polishTaskAccount.value = account
    dialogs.polishTask = true
    try {
      const response = await getItemPolishTask(account.id)
      if (response.code === 0 || response.code === 200) {
        polishTask.value = response.data?.task || null
        polishTaskForm.enabled = (polishTask.value?.enabled ?? 1) === 1
        polishTaskForm.runHour = polishTask.value?.runHour ?? 8
        polishTaskForm.randomDelayMaxMinutes = polishTask.value?.randomDelayMaxMinutes ?? 10
      } else {
        throw new Error(response.msg || '获取定时设置失败')
      }
    } catch (error: any) {
      if (!error.messageShown) {
        showError('获取定时设置失败: ' + error.message)
      }
    }
  }

  const savePolishTaskConfig = async () => {
    if (!polishTaskAccount.value) return
    savingPolishTask.value = true
    try {
      const response = await saveItemPolishTask({
        xianyuAccountId: polishTaskAccount.value.id,
        enabled: polishTaskForm.enabled ? 1 : 0,
        runHour: Number(polishTaskForm.runHour),
        randomDelayMaxMinutes: Number(polishTaskForm.randomDelayMaxMinutes)
      })
      if (response.code === 0 || response.code === 200) {
        polishTask.value = response.data?.task || null
        dialogs.polishTask = false
        showSuccess(response.msg || '定时擦亮设置已保存')
      } else {
        throw new Error(response.msg || '保存定时设置失败')
      }
    } catch (error: any) {
      if (!error.messageShown) {
        showError('保存定时设置失败: ' + error.message)
      }
    } finally {
      savingPolishTask.value = false
    }
  }

  const copyUnb = async (account: Account) => {
    if (!account.unb) {
      showInfo('当前账号没有UNB')
      return
    }
    try {
      await navigator.clipboard.writeText(account.unb)
      showSuccess('UNB已复制')
    } catch {
      const input = document.createElement('input')
      input.value = account.unb
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
      showSuccess('UNB已复制')
    }
  }

  const openConnection = (account: Account) => {
    router.push(`/connection/${account.id}`)
  }

  // 删除账号
  const deleteAccount = (id: number) => {
    deleteAccountId.value = id;
    dialogs.deleteConfirm = true;
  };

  // 确认删除账号
  const confirmDelete = async () => {
    if (!deleteAccountId.value) return;
    
    try {
      const response = await deleteAccountApi({ id: deleteAccountId.value });
      if (response.code === 0 || response.code === 200) {
        showSuccess('账号删除成功');
        dialogs.deleteConfirm = false;
        await loadAccounts();
      } else {
        throw new Error(response.msg || '删除失败');
      }
    } catch (error: any) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('删除失败: ' + error.message);
      }
    }
  };

  return {
    loading,
    refreshingProfileId,
    polishingAccountId,
    savingPolishTask,
    accounts,
    dialogs,
    currentAccount,
    deleteAccountId,
    proxyAccount,
    credentialAccount,
    polishTaskAccount,
    polishTask,
    polishTaskForm,
    loadAccounts,
    showAddDialog,
    showManualAddDialog,
    showQRLoginDialog,
    editAccount,
    openProxyDialog,
    openCredentialDialog,
    refreshAccountProfile,
    runItemPolish,
    openPolishTaskDialog,
    savePolishTaskConfig,
    copyUnb,
    openConnection,
    deleteAccount,
    confirmDelete
  }
}

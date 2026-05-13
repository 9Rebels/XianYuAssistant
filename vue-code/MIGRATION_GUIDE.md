# 从静态页面迁移到 Vue3 指南

## 迁移步骤

### 1. 安装额外依赖

```bash
cd vue-code
npm install element-plus axios
npm install @element-plus/icons-vue
```

### 2. 项目结构对应关系

| 原静态文件 | Vue3 对应 |
|-----------|----------|
| `static/js/api.js` | `src/api/` 目录下的模块 |
| `static/js/pageManager.js` | `src/router/index.ts` |
| `static/js/pages/*/` | `src/views/` 目录 |
| `static/css/` | `src/assets/styles/` |

### 3. API 迁移

原来的 `API` 对象需要拆分成多个模块：

- `src/api/account.ts` - 账号相关 API
- `src/api/items.ts` - 商品相关 API
- `src/api/websocket.ts` - WebSocket 相关 API
- `src/api/qrlogin.ts` - 二维码登录 API
- `src/api/message.ts` - 消息相关 API

### 4. 页面迁移

每个页面需要转换为 Vue 组件：

- `dashboard.js` → `src/views/Dashboard.vue`
- `accounts.js` → `src/views/Accounts.vue`
- `goods.js` → `src/views/Goods.vue`
- 等等...

### 5. 状态管理

使用 Pinia 管理全局状态：

- `src/stores/account.ts` - 账号状态
- `src/stores/websocket.ts` - WebSocket 连接状态
- `src/stores/goods.ts` - 商品状态

### 6. 类型定义

在 `src/types/` 目录下定义 TypeScript 类型：

```typescript
// src/types/account.ts
export interface Account {
  id: number
  accountNote: string
  unb: string
  status: number
  createdTime: string
  updatedTime: string
}

// src/types/goods.ts
export interface Goods {
  id: number
  xyGoodId: string
  title: string
  coverPic: string
  soldPrice: string
  status: number
}
```

## 迁移示例

### 原 JavaScript 代码

```javascript
// static/js/pages/accounts/accounts.js
const AccountsPage = {
  render(container) {
    container.innerHTML = `<div>账号列表</div>`;
    this.loadAccounts();
  },
  
  async loadAccounts() {
    const result = await API.account.list();
    // 处理数据...
  }
};
```

### 转换后的 Vue 组件

```vue
<!-- src/views/Accounts.vue -->
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAccountList } from '@/api/account'
import type { Account } from '@/types/account'

const accounts = ref<Account[]>([])
const loading = ref(false)

const loadAccounts = async () => {
  loading.value = true
  try {
    const result = await getAccountList()
    if (result.code === 0) {
      accounts.value = result.data
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadAccounts()
})
</script>

<template>
  <div class="accounts-page">
    <el-table :data="accounts" v-loading="loading">
      <el-table-column prop="accountNote" label="账号备注" />
      <el-table-column prop="unb" label="UNB" />
      <el-table-column prop="status" label="状态" />
    </el-table>
  </div>
</template>
```

## 开发建议

1. **先转换核心功能**：从最常用的页面开始（如账号管理、商品管理）
2. **复用组件**：将重复的 UI 抽取为公共组件
3. **类型安全**：充分利用 TypeScript 的类型检查
4. **响应式设计**：使用 Element Plus 的响应式布局
5. **错误处理**：统一的错误处理和提示机制

## 后续步骤

1. 我已经创建了基础项目结构
2. 需要安装 Element Plus 和 Axios
3. 然后我可以帮你转换具体的页面

你想先转换哪个页面？我建议从以下页面开始：
- 账号管理（Accounts）
- 商品管理（Goods）
- 仪表板（Dashboard）

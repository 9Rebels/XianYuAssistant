# Vue3 项目结构说明

## 已完成的工作

### ✅ 基础架构
- Vite 配置（代理、构建）
- TypeScript 类型定义
- API 模块封装
- 工具函数
- 路由配置
- 主布局（App.vue）

### ✅ 完整示例页面
1. **Dashboard（仪表板）**
   - `src/views/dashboard/index.vue` - 页面组件
   - `src/views/dashboard/dashboard.css` - 样式文件
   - `src/views/dashboard/useDashboard.ts` - 业务逻辑（Composable）

2. **Accounts（账号管理）** - 部分完成
   - `src/views/accounts/index.vue` - 主页面
   - `src/views/accounts/accounts.css` - 样式
   - `src/views/accounts/useAccountManager.ts` - 业务逻辑

## 需要完成的工作

### 1. 完成 Accounts 页面的子组件

创建以下组件文件：

```
src/views/accounts/components/
├── AccountTable.vue          # 账号列表表格
├── AddAccountDialog.vue      # 添加/编辑账号对话框
├── ManualAddDialog.vue       # 手动添加账号对话框
├── QRLoginDialog.vue         # 扫码登录对话框
└── DeleteConfirmDialog.vue   # 删除确认对话框
```

### 2. 创建其他页面

按照相同的模式创建以下页面：

#### Connection（连接管理）
```
src/views/connection/
├── index.vue
├── connection.css
├── useConnectionManager.ts
└── components/
    ├── ConnectionTable.vue
    └── ConnectionControl.vue
```

#### Goods（商品管理）
```
src/views/goods/
├── index.vue
├── goods.css
├── useGoodsManager.ts
└── components/
    ├── GoodsTable.vue
    ├── GoodsFilter.vue
    └── GoodsConfigDialog.vue
```

#### Messages（消息管理）
```
src/views/messages/
├── index.vue
├── messages.css
├── useMessageManager.ts
└── components/
    ├── MessageList.vue
    └── MessageDetail.vue
```

#### AutoDelivery（自动发货）
```
src/views/auto-delivery/
├── index.vue
├── auto-delivery.css
├── useAutoDeliveryManager.ts
└── components/
    ├── DeliveryConfigList.vue
    ├── DeliveryConfigDialog.vue
    └── DeliveryRecordList.vue
```

#### AutoReply（自动回复）
```
src/views/auto-reply/
├── index.vue
├── auto-reply.css
├── useAutoReplyManager.ts
└── components/
    ├── ReplyConfigList.vue
    ├── ReplyConfigDialog.vue
    └── ReplyRecordList.vue
```

#### Records（操作记录）
```
src/views/records/
├── index.vue
├── records.css
├── useRecordManager.ts
└── components/
    └── RecordTable.vue
```

#### QRLogin（扫码登录）
```
src/views/qrlogin/
├── index.vue
├── qrlogin.css
└── useQRLogin.ts
```

## 开发模式

### 页面组件模式

每个页面遵循以下结构：

1. **index.vue** - 页面主组件
   - 使用 Composable 管理状态和逻辑
   - 组合子组件
   - 处理页面级事件

2. **[page].css** - 页面样式
   - 页面级样式
   - 布局样式
   - 响应式样式

3. **use[Page]Manager.ts** - 业务逻辑
   - 状态管理
   - API 调用
   - 业务逻辑处理

4. **components/** - 子组件
   - 可复用的 UI 组件
   - 特定功能组件
   - 对话框组件

### 组件示例模板

```vue
<!-- src/views/[page]/components/Example.vue -->
<script setup lang="ts">
import { ref } from 'vue'

interface Props {
  data: any[]
  loading?: boolean
}

interface Emits {
  (e: 'action', id: number): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false
})

const emit = defineEmits<Emits>()

const handleAction = (id: number) => {
  emit('action', id)
}
</script>

<template>
  <div class="example-component">
    <!-- 组件内容 -->
  </div>
</template>

<style scoped>
.example-component {
  /* 组件样式 */
}
</style>
```

## 开发步骤

1. **启动开发服务器**
   ```bash
   cd vue-code
   npm run dev
   ```

2. **参考原有 JS 代码**
   - 查看 `src/main/resources/static/js/pages/[page]/` 目录
   - 理解业务逻辑
   - 转换为 Vue3 + TypeScript

3. **创建页面**
   - 创建页面目录和文件
   - 实现 Composable
   - 创建子组件
   - 编写样式

4. **测试功能**
   - 确保后端 API 正常
   - 测试页面功能
   - 检查样式和交互

5. **构建生产版本**
   ```bash
   npm run build
   ```

6. **部署到 Spring Boot**
   ```bash
   # 复制构建产物到 Spring Boot 静态资源目录
   xcopy /E /I /Y dist\* ..\src\main\resources\static\
   ```

## 注意事项

1. **API 响应格式**
   - 后端返回 `code: 200` 或 `code: 0` 表示成功
   - 统一在 `request.ts` 中处理

2. **类型定义**
   - 所有类型定义在 `src/types/index.ts`
   - 保持类型安全

3. **样式规范**
   - 使用 scoped 样式
   - 遵循 BEM 命名规范
   - 复用 Element Plus 组件样式

4. **状态管理**
   - 页面级状态使用 Composable
   - 全局状态可以使用 Pinia（如需要）

5. **错误处理**
   - 使用统一的错误提示
   - 在 Composable 中处理异常

## 快速命令

```bash
# 安装依赖
npm install

# 启动开发
npm run dev

# 构建生产
npm run build

# 类型检查
npm run type-check

# 代码检查
npm run lint
```

## 下一步

1. 完成 Accounts 页面的所有子组件
2. 参考 Accounts 页面，创建其他页面
3. 测试所有功能
4. 优化样式和交互
5. 构建并部署

需要帮助时，参考已完成的 Dashboard 和 Accounts 页面作为模板。

# 快速开始

## 1. 安装依赖

```bash
cd vue-code
npm install element-plus axios @element-plus/icons-vue
```

## 2. 配置代理（开发环境）

编辑 `vite.config.ts`，添加代理配置：

```typescript
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
```

## 3. 启动开发服务器

```bash
npm run dev
```

访问 `http://localhost:5173`

## 4. 构建生产版本

```bash
npm run build
```

构建产物在 `dist/` 目录

## 5. 集成到 Spring Boot

将构建后的文件复制到 Spring Boot 项目：

```bash
# Windows
xcopy /E /I /Y dist\* ..\src\main\resources\static\

# Linux/Mac
cp -r dist/* ../src/main/resources/static/
```

## 当前状态

✅ Vue3 + TypeScript 项目已创建
✅ 路由配置完成
✅ Pinia 状态管理已配置
⏳ 需要安装 Element Plus
⏳ 需要创建 API 模块
⏳ 需要转换页面组件

## 下一步

请告诉我你想先转换哪个页面，我会帮你完成：

1. **账号管理** - 账号列表、添加、编辑、删除
2. **商品管理** - 商品列表、刷新、配置自动发货/回复
3. **连接管理** - WebSocket 连接状态、启动/停止
4. **仪表板** - 系统概览、统计信息

或者我可以一次性创建所有基础文件（API、类型定义、工具函数等），然后你可以根据需要逐步完善页面。

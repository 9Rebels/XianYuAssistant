<script setup lang="ts">
import { ref, shallowRef, onMounted, onUnmounted, computed, provide, markRaw } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import NavMenu from './NavMenu.vue'
import AppNotificationCenter from './AppNotificationCenter.vue'
import ManualVerificationDialog from './ManualVerificationDialog.vue'
import ThemeToggle from './ThemeToggle.vue'

// 导入所有页面图标
import IconChart from '@/components/icons/IconChart.vue'
import IconAccount from '@/components/icons/IconAccount.vue'
import IconWifi from '@/components/icons/IconWifi.vue'
import IconShoppingBag from '@/components/icons/IconShoppingBag.vue'
import IconTruck from '@/components/icons/IconTruck.vue'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconRobot from '@/components/icons/IconRobot.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconLog from '@/components/icons/IconLog.vue'
import IconShield from '@/components/icons/IconShield.vue'
import IconKey from '@/components/icons/IconKey.vue'
import IconAlert from '@/components/icons/IconAlert.vue'

const route = useRoute()

// 响应式设备类型
const isMobile = ref(false)  // < 768px
const isTablet = ref(false)  // 768px - 1024px
const isDesktop = ref(false) // > 1024px

// 移动端和平板端共用的抽屉状态
const drawerVisible = ref(false)

// 页面特定的导航栏内容
const headerContent = shallowRef<any>(null)

// 提供给页面组件的方法来设置导航栏内容
const setHeaderContent = (content: any) => {
  headerContent.value = content
}

// 提供给页面组件
provide('setHeaderContent', setHeaderContent)

const pageTitleMap: Record<string, string> = {
  '/dashboard': '仪表板',
  '/accounts': '闲鱼账号',
  '/connection': '连接管理',
  '/goods': '商品管理',
  '/orders': '发货记录',
  '/messages': '消息管理',
  '/online-messages': '在线消息',
  '/auto-delivery': '自动发货',
  '/auto-reply': '自动回复',
  '/operation-log': '操作日志',
  '/kami-config': '卡密配置',
  '/notifications': '通知设置',
  '/settings': '系统设置'
}

const pageIconMap: Record<string, any> = {
  '/dashboard': markRaw(IconChart),
  '/accounts': markRaw(IconAccount),
  '/connection': markRaw(IconWifi),
  '/goods': markRaw(IconShoppingBag),
  '/orders': markRaw(IconTruck),
  '/messages': markRaw(IconMessage),
  '/online-messages': markRaw(IconChat),
  '/auto-delivery': markRaw(IconRobot),
  '/auto-reply': markRaw(IconChat),
  '/operation-log': markRaw(IconLog),
  '/kami-config': markRaw(IconKey),
  '/notifications': markRaw(IconAlert),
  '/settings': markRaw(IconShield)
}

const currentPageTitle = computed(() => pageTitleMap[route.path] || '闲鱼自动化')
const currentPageIcon = computed(() => pageIconMap[route.path] || null)
const mainClass = computed(() => ({
  'el-main--online-messages': route.path === '/online-messages'
}))

// 检测屏幕尺寸并自动设置设备类型
const checkScreenSize = () => {
  const width = window.innerWidth

  // 判断设备类型
  if (width < 768) {
    isMobile.value = true
    isTablet.value = false
    isDesktop.value = false
    // 切换到手机模式时，关闭抽屉
    drawerVisible.value = false
  } else if (width < 1024) {
    isMobile.value = false
    isTablet.value = true
    isDesktop.value = false
    // 切换到平板模式时，关闭抽屉
    drawerVisible.value = false
  } else {
    isMobile.value = false
    isTablet.value = false
    isDesktop.value = true
    // 切换到桌面模式时，关闭抽屉
    drawerVisible.value = false
  }
}

// 切换抽屉（手机端和平板端共用）
const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value
}

const closeDrawer = () => {
  drawerVisible.value = false
}

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
})
</script>

<template>
  <div class="app-layout">
    <!-- 手机端: 顶部导航栏 -->
    <div v-if="isMobile" class="mobile-header">
      <el-button class="menu-toggle-btn" @click="toggleDrawer" circle>
        <span class="menu-icon">☰</span>
      </el-button>
      <div class="header-title-section">
        <component v-if="currentPageIcon" :is="currentPageIcon" class="header-page-icon" />
        <div class="mobile-page-title">{{ currentPageTitle }}</div>
      </div>
      <div v-if="headerContent && (route.path === '/goods' || route.path === '/messages' || route.path === '/online-messages' || route.path === '/auto-delivery' || route.path === '/kami-config' || route.path === '/orders' || route.path === '/auto-reply' || route.path === '/operation-log')" class="header-content-slot">
        <component :is="headerContent" />
      </div>
      <ThemeToggle />
    </div>

    <!-- 平板端: 顶部导航栏（带抽屉按钮） -->
    <div v-if="isTablet" class="tablet-header">
      <el-button class="menu-toggle-btn" @click="toggleDrawer" circle>
        <span class="menu-icon">☰</span>
      </el-button>
      <div class="header-title-section">
        <component v-if="currentPageIcon" :is="currentPageIcon" class="header-page-icon" />
        <div class="tablet-page-title">{{ currentPageTitle }}</div>
      </div>
      <div v-if="headerContent && (route.path === '/goods' || route.path === '/messages' || route.path === '/online-messages' || route.path === '/auto-delivery' || route.path === '/kami-config' || route.path === '/orders' || route.path === '/auto-reply' || route.path === '/operation-log')" class="header-content-slot">
        <component :is="headerContent" />
      </div>
      <ThemeToggle />
    </div>

    <!-- 手机端和平板端: 左侧抽屉菜单 -->
    <transition name="drawer">
      <div v-if="(isMobile || isTablet) && drawerVisible" class="drawer-overlay" @click="closeDrawer">
        <div class="drawer-menu" @click.stop>
          <div class="drawer-header">
            <div class="logo">
              <div class="logo-icon">X</div>
              <div class="logo-text">XianYuAssistant</div>
            </div>
            <el-button class="drawer-close-btn" @click="closeDrawer" circle size="small">
              <span class="close-icon">✕</span>
            </el-button>
          </div>
          <div class="drawer-content">
            <NavMenu @select="closeDrawer" />
          </div>
        </div>
      </div>
    </transition>

    <!-- 桌面端: 固定侧边栏 -->
    <el-container v-if="isDesktop" class="layout-container">
      <el-aside width="240px" class="sidebar">
        <div class="sidebar-top">
          <div class="logo">
            <div class="logo-icon">X</div>
            <div class="logo-text">XianYuAssistant</div>
          </div>
          <ThemeToggle />
        </div>
        <NavMenu />
      </el-aside>

      <el-container>
        <el-main :class="mainClass">
          <RouterView />
        </el-main>
      </el-container>
    </el-container>

    <!-- 平板端: 主内容区 -->
    <el-container v-if="isTablet" class="layout-container">
      <el-main :class="mainClass">
        <RouterView />
      </el-main>
    </el-container>

    <!-- 手机端: 主内容区 -->
    <el-container v-if="isMobile" class="layout-container">
      <el-main :class="mainClass">
        <RouterView />
      </el-main>
    </el-container>

    <AppNotificationCenter />
    <ManualVerificationDialog />
  </div>
</template>

<style scoped>
.app-layout {
  height: 100vh;
  background: var(--app-bg);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: var(--app-text);
}

.layout-container {
  flex: 1;
  overflow: hidden;
}

/* ========== 桌面端: 固定侧边栏 ========== */
.sidebar {
  background: var(--app-bg-elevated);
  border-right: 1px solid var(--app-border);
  box-shadow: none;
  transition: width 0.3s ease;
  overflow-y: auto;
  overflow-x: hidden;
  /* 隐藏滚动条 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
}

.sidebar::-webkit-scrollbar {
  display: none; /* Chrome, Safari, Opera */
}

.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 10px 24px;
  gap: 12px;
}

.logo {
  display: flex;
  align-items: center;
  padding: 0;
  border-bottom: none;
  gap: 12px;
  min-width: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: var(--app-primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--app-surface-strong);
  font-size: 18px;
  font-weight: bold;
  flex-shrink: 0;
}

.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
  min-width: 0;
}

:deep(.el-menu-item) {
  margin: 2px 16px;
  border-radius: 8px;
  color: var(--app-text-muted);
  transition: all 0.2s;
}

:deep(.el-menu-item:hover) {
  background: color-mix(in srgb, var(--app-surface-hover) 88%, transparent) !important;
  color: var(--app-text);
}

:deep(.el-menu-item.is-active) {
  background: var(--app-primary) !important;
  color: var(--app-surface-strong);
}

.el-main {
  padding: 32px 40px;
  overflow: auto;
  background: var(--app-bg);
  height: 100%;
  /* 隐藏滚动条 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
}

.el-main::-webkit-scrollbar {
  display: none; /* Chrome, Safari, Opera */
}

.el-main--online-messages {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

:deep(.el-divider__text) {
  font-size: 12px;
  color: var(--app-text-soft);
  text-transform: uppercase;
  font-weight: 600;
}

:deep(.el-divider) {
  margin: 24px 0 8px;
  border-color: transparent;
}

/* ========== 平板端: 顶部导航栏 ========== */
.tablet-header {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 14px 20px;
  background: var(--app-bg-elevated);
  border-bottom: 1px solid var(--app-border);
  z-index: 100;
  gap: 16px;
  height: 64px;
  box-sizing: border-box;
  flex-shrink: 0;
}

.tablet-page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--app-text);
  flex: 1;
}

.header-title-section {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1 1 auto;
  min-width: 0;
}

.header-page-icon {
  width: 24px;
  height: 24px;
  color: var(--app-text);
  flex-shrink: 0;
}

.header-content-slot {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
}

/* ========== 手机端: 顶部导航栏 ========== */
.mobile-header {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  padding: 12px 16px;
  background: var(--app-bg-elevated);
  border-bottom: 1px solid var(--app-border);
  z-index: 100;
  gap: 12px;
  height: 56px;
  box-sizing: border-box;
  flex-shrink: 0;
}

.mobile-page-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--app-text);
  flex: 0 0 auto;
  white-space: nowrap;
  line-height: 1.2;
}

.header-content-slot {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1 1 auto;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.header-content-slot::-webkit-scrollbar {
  display: none;
}

.menu-toggle-btn {
  width: 44px;
  height: 44px;
  background: var(--app-primary) !important;
  border-color: var(--app-primary) !important;
  color: var(--app-surface-strong) !important;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
}

.menu-toggle-btn:hover,
.menu-toggle-btn:active,
.menu-toggle-btn:focus {
  background: var(--app-primary-hover) !important;
  border-color: var(--app-primary-hover) !important;
}

.menu-icon {
  font-size: 22px;
  line-height: 1;
  display: block;
}

/* ========== 左侧抽屉菜单（手机端和平板端共用） ========== */
.drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--app-overlay);
  z-index: 1000;
  display: flex;
  align-items: stretch;
}

.drawer-menu {
  width: 280px;
  max-width: 80vw;
  background: var(--app-bg-elevated);
  box-shadow: var(--app-shadow-strong);
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--app-border);
  flex-shrink: 0;
  background: var(--app-bg-elevated);
}

.drawer-header .logo {
  padding: 0;
  flex: 1;
}

.drawer-close-btn {
  width: 32px;
  height: 32px;
  background: transparent !important;
  border: 1px solid var(--app-border) !important;
  color: var(--app-text-muted) !important;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0;
  margin-left: 12px;
}

.drawer-close-btn:hover,
.drawer-close-btn:active {
  background: color-mix(in srgb, var(--app-surface-hover) 88%, transparent) !important;
  border-color: var(--app-border-strong) !important;
  color: var(--app-text) !important;
}

.close-icon {
  font-size: 18px;
  line-height: 1;
  display: block;
}

.drawer-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 0;
  -webkit-overflow-scrolling: touch;
  /* 隐藏滚动条 */
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE and Edge */
}

.drawer-content::-webkit-scrollbar {
  display: none; /* Chrome, Safari, Opera */
}

/* 抽屉动画 */
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.3s ease;
}

.drawer-enter-active .drawer-menu,
.drawer-leave-active .drawer-menu {
  transition: transform 0.3s ease;
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}

.drawer-enter-from .drawer-menu {
  transform: translateX(-100%);
}

.drawer-leave-to .drawer-menu {
  transform: translateX(-100%);
}

/* ========== 响应式适配 ========== */
/* 平板模式 (768px - 1024px) */
@media screen and (min-width: 768px) and (max-width: 1024px) {
  .tablet-header {
    padding: 12px 18px;
    height: 60px;
  }

  .tablet-page-title {
    font-size: 17px;
  }

  .menu-toggle-btn {
    width: 42px;
    height: 42px;
    font-size: 20px;
  }

  .menu-icon {
    font-size: 20px;
  }

  .el-main {
    padding: 24px 28px;
  }

  .drawer-menu {
    width: 260px;
  }

  .drawer-header {
    padding: 14px 18px;
  }

  :deep(.el-menu-item) {
    margin: 2px 14px;
    font-size: 15px;
  }
}

/* 手机模式 (< 768px) */
@media (max-width: 767px) {
  .mobile-header {
    padding: 10px 14px;
    height: 52px;
    gap: 10px;
  }

  .mobile-page-title {
    font-size: 16px;
  }

  .mobile-header .header-title-section {
    flex: 0 0 auto;
    min-width: auto;
  }

  .mobile-header .header-content-slot {
    flex: 1 1 auto;
    min-width: 0;
  }

  .menu-toggle-btn {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .menu-icon {
    font-size: 20px;
  }

  .el-main {
    padding: 16px 20px;
    overflow: hidden;
  }

  .el-main--online-messages {
    padding: 10px 12px;
  }

  .drawer-menu {
    width: 260px;
  }

  .drawer-header {
    padding: 12px 16px;
  }

  .drawer-close-btn {
    width: 30px;
    height: 30px;
    font-size: 16px;
  }

  .close-icon {
    font-size: 16px;
  }

  :deep(.el-menu-item) {
    margin: 2px 12px;
    font-size: 15px;
    padding: 12px 16px;
  }
}

/* 小屏手机模式 (< 480px) */
@media (max-width: 480px) {
  .mobile-header {
    padding: 8px 12px;
    height: 48px;
    gap: 8px;
  }

  .mobile-page-title {
    font-size: 15px;
  }

  .mobile-header .header-title-section {
    gap: 8px;
  }

  .menu-toggle-btn {
    width: 36px;
    height: 36px;
    font-size: 18px;
  }

  .menu-icon {
    font-size: 18px;
  }

  .el-main {
    padding: 12px 16px;
    overflow: hidden;
  }

  .el-main--online-messages {
    padding: 8px 10px;
  }

  .drawer-menu {
    width: 240px;
  }

  .drawer-header {
    padding: 10px 14px;
  }

  .drawer-header .logo-icon {
    width: 28px;
    height: 28px;
    font-size: 16px;
  }

  .drawer-header .logo-text {
    font-size: 15px;
  }

  .drawer-close-btn {
    width: 28px;
    height: 28px;
    font-size: 14px;
  }

  .close-icon {
    font-size: 14px;
  }

  :deep(.el-menu-item) {
    margin: 2px 10px;
    font-size: 14px;
    padding: 10px 14px;
  }
}
</style>

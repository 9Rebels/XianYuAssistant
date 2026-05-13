<script setup lang="ts">
import { RouterView } from 'vue-router'
import ThemeToggle from './ThemeToggle.vue'
</script>

<template>
  <div class="login-layout">
    <div class="login-layout__theme-toggle">
      <ThemeToggle />
    </div>
    <RouterView />
  </div>
</template>

<style scoped>
.login-layout {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: var(--app-bg);
}

.login-layout__theme-toggle {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1001;
}

@media (max-width: 767px) {
  .login-layout__theme-toggle {
    top: 14px;
    right: 14px;
  }
}
</style>


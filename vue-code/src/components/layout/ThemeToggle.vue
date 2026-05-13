<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const label = computed(() => (themeStore.isDark ? '切换浅色模式' : '切换黑夜模式'))
const icon = computed(() => (themeStore.isDark ? '☀' : '☾'))
</script>

<template>
  <button
    class="theme-toggle"
    :aria-label="label"
    :title="label"
    type="button"
    @click="themeStore.toggleTheme()"
  >
    <span class="theme-toggle__icon" aria-hidden="true">{{ icon }}</span>
  </button>
</template>

<style scoped>
.theme-toggle {
  width: 40px;
  height: 40px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: var(--app-surface);
  color: var(--app-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: var(--app-shadow-soft);
  flex-shrink: 0;
}

.theme-toggle:hover {
  border-color: var(--app-border-strong);
  background: var(--app-surface-hover);
}

.theme-toggle:active {
  transform: scale(0.97);
}

.theme-toggle__icon {
  font-size: 18px;
  line-height: 1;
}

@media (max-width: 767px) {
  .theme-toggle {
    width: 36px;
    height: 36px;
    border-radius: 9px;
  }

  .theme-toggle__icon {
    font-size: 16px;
  }
}
</style>

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark'

const STORAGE_KEY = 'xianyuassistant-theme'

function readStoredTheme(): ThemeMode | null {
  if (typeof window === 'undefined') return null
  const value = window.localStorage.getItem(STORAGE_KEY)
  return value === 'dark' || value === 'light' ? value : null
}

function resolveInitialTheme(): ThemeMode {
  const stored = readStoredTheme()
  if (stored) return stored
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(mode: ThemeMode) {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.classList.toggle('dark', mode === 'dark')
  root.setAttribute('data-theme', mode)
  root.style.colorScheme = mode
}

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>(resolveInitialTheme())

  const isDark = computed(() => mode.value === 'dark')

  function persist(nextMode: ThemeMode) {
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, nextMode)
    }
  }

  function setTheme(nextMode: ThemeMode) {
    mode.value = nextMode
    persist(nextMode)
    applyTheme(nextMode)
  }

  function toggleTheme() {
    setTheme(isDark.value ? 'light' : 'dark')
  }

  function initTheme() {
    applyTheme(mode.value)
  }

  return {
    mode,
    isDark,
    setTheme,
    toggleTheme,
    initTheme
  }
})

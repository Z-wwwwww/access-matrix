import { ref, watch } from 'vue'

const STORAGE_KEY = 'theme'

function getInitialTheme() {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark' || stored === 'light') return stored
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

const theme = ref(getInitialTheme())

function applyTheme(value) {
  const root = document.documentElement
  if (value === 'dark') {
    root.classList.add('dark')
  } else {
    root.classList.remove('dark')
  }
  localStorage.setItem(STORAGE_KEY, value)
}

// Apply on init
applyTheme(theme.value)

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  watch(theme, applyTheme)

  return {
    theme,
    toggleTheme,
    isDark: /** @type {import('vue').ComputedRef<boolean>} */ {
      get value() { return theme.value === 'dark' }
    }
  }
}

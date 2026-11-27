import { ref } from 'vue'

export type ThemePreference = 'light' | 'dark'

const STORAGE_KEY = 'argus-theme'
const theme = ref<ThemePreference>('light')
let initialized = false

export function useTheme() {
  if (!initialized) {
    initialized = true
    theme.value = readInitialTheme()
    applyTheme(theme.value)
  }

  return { theme, toggleTheme }
}

function toggleTheme() {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  applyTheme(theme.value)
  try {
    window.localStorage.setItem(STORAGE_KEY, theme.value)
  } catch {
    return
  }
}

function readInitialTheme(): ThemePreference {
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY)
    if (saved === 'light' || saved === 'dark') {
      return saved
    }
  } catch {
    return getSystemTheme()
  }
  return getSystemTheme()
}

function getSystemTheme(): ThemePreference {
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function applyTheme(value: ThemePreference) {
  document.documentElement.dataset.theme = value
  document.documentElement.classList.toggle('dark', value === 'dark')
}

import { ref } from 'vue'

// The user picks a theme. 'system' follows the setting of the device.
export type Theme = 'system' | 'light' | 'dark'

// index.html reads the same key before the app starts, so the page does not flash.
export const THEME_KEY = 'status-tacos-theme'

const ORDER: Theme[] = ['system', 'light', 'dark']

function isTheme(value: unknown): value is Theme {
  return typeof value === 'string' && (ORDER as string[]).includes(value)
}

function loadTheme(): Theme {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    return isTheme(stored) ? stored : 'system'
  } catch {
    return 'system'
  }
}

export const theme = ref<Theme>(loadTheme())

export function applyTheme(value: Theme): void {
  const root = document.documentElement
  if (value === 'system') delete root.dataset.theme
  else root.dataset.theme = value
}

export function setTheme(value: Theme): void {
  theme.value = value
  applyTheme(value)
  try {
    if (value === 'system') localStorage.removeItem(THEME_KEY)
    else localStorage.setItem(THEME_KEY, value)
  } catch {
    // No storage (private mode): the choice lasts until the page reloads.
  }
}

// System, then light, then dark, then system again.
export function nextTheme(value: Theme): Theme {
  return ORDER[(ORDER.indexOf(value) + 1) % ORDER.length]!
}

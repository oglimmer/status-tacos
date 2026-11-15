import { ref } from 'vue'

// The user picks a theme. 'system', 'light' and 'dark' are the original Taquería theme;
// 'system' follows the setting of the device. The other themes have a fixed light or dark mode.
export type Theme = 'system' | 'light' | 'dark' | 'talavera' | 'rosa' | 'mole' | 'lucha'

export interface ThemeOption {
  id: Theme
  label: string
  // What the theme looks like, in a few words. For the title of the menu item.
  hint: string
}

export interface ThemeGroup {
  label: string
  options: ThemeOption[]
}

export const THEME_GROUPS: ThemeGroup[] = [
  {
    label: 'Taquería, the original',
    options: [
      { id: 'system', label: 'System', hint: 'Light or dark, like your device' },
      { id: 'light', label: 'Light', hint: 'Agua fresca and the teal of the logo' },
      { id: 'dark', label: 'Dark', hint: 'A night taqueria' },
    ],
  },
  {
    label: 'Light',
    options: [
      { id: 'talavera', label: 'Talavera', hint: 'White tiles with cobalt blue' },
      { id: 'rosa', label: 'Rosa', hint: 'Bright paper with Mexican pink' },
    ],
  },
  {
    label: 'Dark',
    options: [
      { id: 'mole', label: 'Mole', hint: 'Dark chocolate with gold' },
      { id: 'lucha', label: 'Lucha', hint: 'Wrestling night in indigo and lilac' },
    ],
  },
]

// index.html reads the same key before the app starts, so the page does not flash.
// It has its own copy of the list of themes; keep the two the same.
export const THEME_KEY = 'status-tacos-theme'

const THEMES: Theme[] = THEME_GROUPS.flatMap((group) => group.options.map((option) => option.id))

export function isTheme(value: unknown): value is Theme {
  return typeof value === 'string' && (THEMES as string[]).includes(value)
}

export function themeLabel(value: Theme): string {
  for (const group of THEME_GROUPS) {
    const option = group.options.find((o) => o.id === value)
    if (option)
      return group === THEME_GROUPS[0] ? `Taquería ${option.label.toLowerCase()}` : option.label
  }
  return value
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

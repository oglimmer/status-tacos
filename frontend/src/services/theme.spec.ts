import { beforeEach, describe, expect, it } from 'vitest'
import { THEME_GROUPS, THEME_KEY, isTheme, setTheme, theme, themeLabel } from './theme'

describe('theme', () => {
  beforeEach(() => {
    localStorage.clear()
    delete document.documentElement.dataset.theme
  })

  it('has the original theme and two light and two dark themes', () => {
    const ids = THEME_GROUPS.flatMap((g) => g.options.map((o) => o.id))
    expect(ids).toEqual(['system', 'light', 'dark', 'talavera', 'rosa', 'mole', 'lucha'])
  })

  it('knows the themes and nothing else', () => {
    expect(isTheme('mole')).toBe(true)
    expect(isTheme('neon')).toBe(false)
    expect(isTheme(null)).toBe(false)
  })

  it('names the original modes with the name of the theme', () => {
    expect(themeLabel('dark')).toBe('Taquería dark')
    expect(themeLabel('talavera')).toBe('Talavera')
  })

  it('sets the attribute on <html> and keeps the choice', () => {
    setTheme('lucha')
    expect(theme.value).toBe('lucha')
    expect(document.documentElement.dataset.theme).toBe('lucha')
    expect(localStorage.getItem(THEME_KEY)).toBe('lucha')
  })

  it('removes the attribute and the stored choice for system', () => {
    setTheme('light')
    setTheme('system')
    expect(document.documentElement.dataset.theme).toBeUndefined()
    expect(localStorage.getItem(THEME_KEY)).toBeNull()
  })
})

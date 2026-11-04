<script setup lang="ts">
import { computed } from 'vue'
import { nextTheme, setTheme, theme } from '../services/theme'

const LABELS = { system: 'System', light: 'Light', dark: 'Dark' } as const
const ICONS = { system: '🖥️', light: '☀️', dark: '🌙' } as const

const label = computed(() => `Theme: ${LABELS[theme.value]}`)
const nextLabel = computed(() => LABELS[nextTheme(theme.value)])
</script>

<template>
  <button
    type="button"
    class="theme-toggle"
    :title="`${label}. Click for ${nextLabel}.`"
    :aria-label="`${label}. Switch to ${nextLabel}.`"
    @click="setTheme(nextTheme(theme))"
  >
    <span aria-hidden="true">{{ ICONS[theme] }}</span>
    <span class="theme-toggle-text">{{ LABELS[theme] }}</span>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  border: 1px solid currentColor;
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 0.9rem;
  cursor: pointer;
  opacity: 0.85;
}

.theme-toggle:hover {
  opacity: 1;
}
</style>

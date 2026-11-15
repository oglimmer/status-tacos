<script setup lang="ts">
import { computed } from 'vue'
import { nextTheme, setTheme, theme } from '../services/theme'

const LABELS = { system: 'System', light: 'Light', dark: 'Dark' } as const

const label = computed(() => `Theme: ${LABELS[theme.value]}`)
const nextLabel = computed(() => LABELS[nextTheme(theme.value)])
</script>

<template>
  <!-- The colors come from the parent (currentColor), so it fits the app bar and the public footer. -->
  <button
    type="button"
    class="theme-toggle"
    :title="`${label}. Click for ${nextLabel}.`"
    :aria-label="`${label}. Switch to ${nextLabel}.`"
    @click="setTheme(nextTheme(theme))"
  >
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      <template v-if="theme === 'light'">
        <circle cx="12" cy="12" r="4" />
        <path
          d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
        />
      </template>
      <path v-else-if="theme === 'dark'" d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
      <template v-else>
        <rect x="2" y="4" width="20" height="13" rx="2" />
        <path d="M8 21h8M12 17v4" />
      </template>
    </svg>
    <span class="theme-toggle-text">{{ LABELS[theme] }}</span>
  </button>
</template>

<style scoped>
.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4em;
  min-height: 1.875rem;
  padding: 0 0.75em;
  border: 1px solid color-mix(in srgb, currentColor 45%, transparent);
  border-radius: 999px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 0.9rem;
  font-weight: 700;
  line-height: 1;
  cursor: pointer;
}

.theme-toggle:hover {
  border-color: currentColor;
}

.theme-toggle svg {
  width: 1.1em;
  height: 1.1em;
  flex: none;
}
</style>

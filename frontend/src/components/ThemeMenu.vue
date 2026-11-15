<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { THEME_GROUPS, setTheme, theme, themeLabel, type Theme } from '../services/theme'

// A button that opens the list of themes. Each theme shows a small swatch in its own colors.
// The colors of the button come from the parent (currentColor), so it fits the app bar and the
// public footer. In the footer the list opens upward.
const props = defineProps<{ up?: boolean }>()

const open = ref(false)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const items = ref<HTMLButtonElement[]>([])

const currentLabel = computed(() => themeLabel(theme.value))

function onDocumentPointer(event: PointerEvent) {
  if (root.value && !root.value.contains(event.target as Node)) close(false)
}

async function show() {
  open.value = true
  document.addEventListener('pointerdown', onDocumentPointer)
  await nextTick()
  const current = items.value.find((el) => el.dataset.value === theme.value)
  ;(current ?? items.value[0])?.focus()
}

function close(returnFocus = true) {
  if (!open.value) return
  open.value = false
  document.removeEventListener('pointerdown', onDocumentPointer)
  if (returnFocus) trigger.value?.focus()
}

function toggle() {
  if (open.value) close()
  else show()
}

function pick(value: Theme) {
  setTheme(value)
  close()
}

function onMenuKey(event: KeyboardEvent) {
  const list = items.value
  const index = list.indexOf(document.activeElement as HTMLButtonElement)
  let next = -1
  if (event.key === 'ArrowDown') next = (index + 1) % list.length
  else if (event.key === 'ArrowUp') next = (index - 1 + list.length) % list.length
  else if (event.key === 'Home') next = 0
  else if (event.key === 'End') next = list.length - 1
  else if (event.key === 'Escape') {
    event.preventDefault()
    close()
    return
  } else if (event.key === 'Tab') {
    close(false)
    return
  }
  if (next >= 0) {
    event.preventDefault()
    list[next]?.focus()
  }
}

onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointer))
</script>

<template>
  <div ref="root" class="theme-menu" :class="{ up: props.up }">
    <button
      ref="trigger"
      type="button"
      class="theme-trigger"
      aria-haspopup="menu"
      :aria-expanded="open"
      :aria-label="`Theme: ${currentLabel}. Change theme.`"
      @click="toggle"
      @keydown.down.prevent="show"
    >
      <span class="swatch swatch-sm" aria-hidden="true">
        <template v-if="theme === 'system'">
          <span class="swatch-half" data-theme="light"></span>
          <span class="swatch-half" data-theme="dark"></span>
        </template>
        <span v-else class="swatch-fill" :data-theme="theme"></span>
      </span>
      <span class="theme-trigger-text">{{ currentLabel }}</span>
      <svg viewBox="0 0 24 24" class="caret" aria-hidden="true">
        <path d="M6 9l6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" />
      </svg>
    </button>

    <div v-if="open" class="theme-list" role="menu" aria-label="Theme" @keydown="onMenuKey">
      <div
        v-for="group in THEME_GROUPS"
        :key="group.label"
        class="theme-group"
        role="group"
        :aria-label="group.label"
      >
        <div class="theme-group-label" aria-hidden="true">{{ group.label }}</div>
        <button
          v-for="option in group.options"
          :key="option.id"
          ref="items"
          type="button"
          role="menuitemradio"
          class="theme-item"
          :aria-checked="theme === option.id"
          :data-value="option.id"
          :title="option.hint"
          tabindex="-1"
          @click="pick(option.id)"
        >
          <span class="swatch" aria-hidden="true">
            <template v-if="option.id === 'system'">
              <span class="swatch-half" data-theme="light"></span>
              <span class="swatch-half" data-theme="dark"></span>
            </template>
            <span v-else class="swatch-fill" :data-theme="option.id"></span>
          </span>
          <span class="theme-item-text">
            <span class="theme-item-name">{{ option.label }}</span>
            <span class="theme-item-hint">{{ option.hint }}</span>
          </span>
          <svg v-if="theme === option.id" viewBox="0 0 24 24" class="check" aria-hidden="true">
            <path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" stroke-width="2.5" />
          </svg>
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-menu {
  position: relative;
  display: inline-block;
}

.theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 0.45em;
  min-height: 1.875rem;
  padding: 0 0.6em 0 0.4em;
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

.theme-trigger:hover,
.theme-trigger[aria-expanded='true'] {
  border-color: currentColor;
}

.caret {
  width: 0.9em;
  height: 0.9em;
  flex: none;
}

/*
 * The swatch shows a theme in its own colors: the page, a card on it, and the accent.
 * The colors repeat those of theme.css: --page-1, --surface-3 and --accent.
 */
.swatch {
  display: inline-flex;
  flex: none;
  width: 2.25rem;
  height: 1.5rem;
  overflow: hidden;
  border-radius: var(--radius-sm);
  box-shadow: inset 0 0 0 1px color-mix(in srgb, currentColor 25%, transparent);
}

.swatch-sm {
  width: 1.25rem;
  height: 1.25rem;
  border-radius: 999px;
}

.swatch-fill,
.swatch-half {
  flex: 1;
  background:
    linear-gradient(var(--sw-accent), var(--sw-accent)) 78% 50% / 22% 36% no-repeat,
    linear-gradient(var(--sw-card), var(--sw-card)) 22% 50% / 40% 56% no-repeat,
    var(--sw-page);
}

.swatch-sm .swatch-fill,
.swatch-sm .swatch-half {
  background: linear-gradient(135deg, var(--sw-page) 50%, var(--sw-accent) 50%);
}

[data-theme='light'] {
  --sw-page: #f3f7f5;
  --sw-card: #e1ebe7;
  --sw-accent: #1a7a72;
}

[data-theme='dark'] {
  --sw-page: #0c1a18;
  --sw-card: #1c3631;
  --sw-accent: #5fd0c6;
}

[data-theme='talavera'] {
  --sw-page: #f1f4fa;
  --sw-card: #d9e1f1;
  --sw-accent: #1d4cb0;
}

[data-theme='rosa'] {
  --sw-page: #fdf3f7;
  --sw-card: #f4d4e2;
  --sw-accent: #c2006a;
}

[data-theme='mole'] {
  --sw-page: #19110d;
  --sw-card: #34241b;
  --sw-accent: #f0b84a;
}

[data-theme='lucha'] {
  --sw-page: #110f29;
  --sw-card: #262249;
  --sw-accent: #bba8ff;
}

.theme-list {
  position: absolute;
  right: 0;
  top: calc(100% + var(--space-2));
  z-index: 150;
  width: max-content;
  max-width: min(20rem, calc(100vw - 2rem));
  padding: var(--space-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  color: var(--text);
  font-family: var(--font-text);
  font-size: var(--text-sm);
  text-align: left;
}

.up .theme-list {
  top: auto;
  bottom: calc(100% + var(--space-2));
}

.theme-group + .theme-group {
  margin-top: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--border-subtle);
}

.theme-group-label {
  padding: var(--space-1) var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-xs);
  font-weight: 700;
}

.theme-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-2);
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.theme-item:hover,
.theme-item:focus-visible {
  background: var(--surface-2);
}

.theme-item:focus-visible {
  outline: 2px solid var(--focus);
  outline-offset: -2px;
}

.theme-item[aria-checked='true'] .theme-item-name {
  color: var(--link);
}

.theme-item-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
}

.theme-item-name {
  font-weight: 700;
}

.theme-item-hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.check {
  width: 1.1rem;
  height: 1.1rem;
  flex: none;
  color: var(--link);
}

@media (max-width: 40rem) {
  .theme-list {
    position: fixed;
    left: var(--space-4);
    right: var(--space-4);
    top: auto;
    bottom: var(--space-4);
    max-width: none;
  }
}
</style>

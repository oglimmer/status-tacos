<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { SilentFor } from '../stores/monitors'
import { SILENCE_OPTIONS, formatSilenceEnd, formatSilenceEndLong, silenceEnd } from '../utils/silence'

/**
 * The four silence lengths. Each row shows how long (the bar) and when the alerts start again.
 * With `selected` the rows act as radio buttons (forms), without it each row is an action (menus).
 */
const props = defineProps<{
  selected?: SilentFor | null
  label?: string
}>()

const emit = defineEmits<{ pick: [code: SilentFor] }>()

const now = ref(new Date())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => (now.value = new Date()), 30_000)
})
onBeforeUnmount(() => clearInterval(timer))

const isRadio = props.selected !== undefined
</script>

<template>
  <div class="silence-picker" :role="isRadio ? 'radiogroup' : 'group'" :aria-label="label ?? 'Silence for'">
    <button
      v-for="option in SILENCE_OPTIONS"
      :key="option.code"
      type="button"
      class="silence-option"
      :class="{ selected: selected === option.code }"
      :role="isRadio ? 'radio' : undefined"
      :aria-checked="isRadio ? selected === option.code : undefined"
      :title="`Alerts start again ${formatSilenceEndLong(silenceEnd(option.code, now))}`"
      @click="emit('pick', option.code)"
    >
      <span class="option-label">{{ option.label }}</span>
      <span class="option-track" aria-hidden="true">
        <span class="option-bar" :style="{ width: `${option.bar * 100}%` }"></span>
      </span>
      <span class="option-end">until {{ formatSilenceEnd(silenceEnd(option.code, now), now) }}</span>
    </button>
  </div>
</template>

<style scoped>
.silence-picker {
  display: grid;
  grid-template-columns: auto minmax(4rem, 1fr) auto;
  gap: 2px;
  min-width: 17rem;
}

.silence-option {
  display: grid;
  grid-column: 1 / -1;
  grid-template-columns: subgrid;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.5rem 0.625rem;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--text);
  font: inherit;
  font-size: 0.85rem;
  text-align: left;
  cursor: pointer;
}

.silence-option:hover {
  background: var(--warning-soft);
}

.silence-option:focus-visible {
  outline: 2px solid var(--warning-text);
  outline-offset: 1px;
}

.silence-option.selected {
  background: var(--warning-soft);
  border-color: var(--warning-soft-border);
}

.option-label {
  font-weight: 600;
  white-space: nowrap;
}

.option-track {
  height: 4px;
  border-radius: 2px;
  background: var(--border-subtle);
  overflow: hidden;
}

.option-bar {
  display: block;
  height: 100%;
  border-radius: 2px;
  background: var(--warning-text);
  opacity: 0.55;
}

.silence-option:hover .option-bar,
.silence-option.selected .option-bar {
  opacity: 1;
}

.option-end {
  text-align: right;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.silence-option.selected .option-end {
  text-align: right;
  color: var(--warning-soft-text);
}
</style>

<template>
  <div ref="root" class="monitor-state-button" @keydown.esc="closePicker">
    <button
      ref="trigger"
      type="button"
      @click="onMainClick"
      :class="stateClass"
      :disabled="disabled"
      class="state-btn"
      :title="stateTooltip"
      :aria-expanded="state === 'ACTIVE' ? pickerOpen : undefined"
      :aria-haspopup="state === 'ACTIVE' ? 'true' : undefined"
    >
      <i :class="stateIcon"></i>
      <span class="state-text">{{ stateText }}</span>
    </button>

    <button
      v-if="state === 'SILENT'"
      type="button"
      class="state-btn state-to-active"
      :disabled="disabled"
      title="Send alerts again now"
      @click="emit('stateChange', 'ACTIVE')"
    >
      <i class="fas fa-bell"></i>
      <span class="state-text">End silence</span>
    </button>

    <span v-if="state === 'SILENT' && endText" class="silent-until" :title="endTitle">
      <i class="fas fa-bell-slash" aria-hidden="true"></i>
      Silent until {{ endText }}
    </span>

    <div v-if="pickerOpen" class="picker-popover">
      <p class="picker-title">Make silent for</p>
      <SilencePicker ref="picker" @pick="pickSilence" />
      <p class="picker-note">Checks go on. Alerts start again at the end.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import type { MonitorState, SilentFor } from '../stores/monitors'
import { formatSilenceEnd, formatSilenceEndLong } from '../utils/silence'
import SilencePicker from './SilencePicker.vue'

interface Props {
  state: MonitorState
  // End of the silence, ISO with Z. Shown when the state is SILENT.
  silentUntil?: string | null
  disabled?: boolean
}

interface Emits {
  (e: 'stateChange', state: MonitorState, silentFor?: SilentFor): void
}

const props = withDefaults(defineProps<Props>(), {
  silentUntil: null,
  disabled: false
})

const emit = defineEmits<Emits>()

const stateConfig = {
  ACTIVE: {
    text: 'Make Silent',
    icon: 'fas fa-pause-circle',
    class: 'state-to-silent',
    tooltip: 'Make the monitor silent for 1 hour to 7 days - checks without alerts',
    next: 'SILENT' as MonitorState
  },
  SILENT: {
    text: 'Make Inactive',
    icon: 'fas fa-stop-circle',
    class: 'state-to-inactive',
    tooltip: 'Click to make monitor inactive - no monitoring',
    next: 'INACTIVE' as MonitorState
  },
  INACTIVE: {
    text: 'Make Active',
    icon: 'fas fa-play-circle',
    class: 'state-to-active',
    tooltip: 'Click to make monitor active - monitoring with alerts',
    next: 'ACTIVE' as MonitorState
  }
}

const currentConfig = computed(() => stateConfig[props.state])

const stateClass = computed(() => `state-btn ${currentConfig.value.class}`)
const stateIcon = computed(() => currentConfig.value.icon)
const stateText = computed(() => currentConfig.value.text)
const stateTooltip = computed(() => currentConfig.value.tooltip)

const endDate = computed(() => (props.silentUntil ? new Date(props.silentUntil) : null))
const endText = computed(() => (endDate.value ? formatSilenceEnd(endDate.value) : null))
const endTitle = computed(() =>
  endDate.value ? `Alerts start again ${formatSilenceEndLong(endDate.value)}` : undefined
)

const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const picker = ref<InstanceType<typeof SilencePicker> | null>(null)
const pickerOpen = ref(false)

const onOutsideClick = (event: MouseEvent) => {
  if (root.value && !root.value.contains(event.target as Node)) pickerOpen.value = false
}

watch(pickerOpen, async (open) => {
  if (open) {
    document.addEventListener('mousedown', onOutsideClick)
    await nextTick()
    ;(picker.value?.$el as HTMLElement | undefined)?.querySelector('button')?.focus()
  } else {
    document.removeEventListener('mousedown', onOutsideClick)
  }
})
onBeforeUnmount(() => document.removeEventListener('mousedown', onOutsideClick))

const closePicker = () => {
  if (!pickerOpen.value) return
  pickerOpen.value = false
  trigger.value?.focus()
}

const onMainClick = () => {
  if (props.disabled) return
  if (props.state === 'ACTIVE') {
    pickerOpen.value = !pickerOpen.value
  } else {
    emit('stateChange', currentConfig.value.next)
  }
}

const pickSilence = (code: SilentFor) => {
  pickerOpen.value = false
  emit('stateChange', 'SILENT', code)
}
</script>

<style scoped>
.monitor-state-button {
  position: relative;
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
}

.silent-until {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  color: var(--warning-text);
  font-size: 0.8rem;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.picker-popover {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  width: max-content;
  max-width: calc(100vw - 1.5rem);
  padding: 0.5rem;
  background: var(--surface);
  border: 1px solid var(--border);
  border-top: 3px solid var(--warning-text);
  border-radius: 8px;
  box-shadow: 0 10px 28px -12px rgba(0, 0, 0, 0.35);
  animation: picker-open 0.14s ease-out;
}

.picker-title {
  margin: 0.125rem 0.625rem 0.375rem;
  color: var(--text);
  font-size: 0.85rem;
  font-weight: 600;
}

.picker-note {
  margin: 0.5rem 0.625rem 0.125rem;
  color: var(--text-muted);
  font-size: 0.75rem;
}

@keyframes picker-open {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
}

@media (prefers-reduced-motion: reduce) {
  .picker-popover {
    animation: none;
  }

  .state-btn:hover:not(:disabled) {
    transform: none;
  }
}

.state-btn {
  display: flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.25rem 0.75rem;
  border: 1px solid;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease-in-out;
  background: var(--surface);
}

.state-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.state-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.state-to-silent {
  color: var(--warning-text);
  border-color: #d97706;
}

.state-to-silent:hover:not(:disabled) {
  background: var(--warning-soft);
  border-color: #b45309;
  color: var(--warning-text);
}

.state-to-inactive {
  color: var(--danger-text);
  border-color: #dc2626;
}

.state-to-inactive:hover:not(:disabled) {
  background: var(--danger-soft);
  border-color: #b91c1c;
  color: var(--danger-text);
}

.state-to-active {
  color: var(--success-text);
  border-color: #059669;
}

.state-to-active:hover:not(:disabled) {
  background: var(--success-soft);
  border-color: #047857;
  color: var(--success-text);
}

.state-text {
  font-weight: 500;
}

@media (max-width: 768px) {
  .picker-popover {
    position: fixed;
    top: auto;
    right: 0.75rem;
    bottom: 0.75rem;
    left: 0.75rem;
  }

  .state-btn {
    padding: 0.25rem 0.5rem;
    min-width: 0;
    flex: 1;
    text-align: center;
    justify-content: center;
    font-size: 0.7rem;
  }

  .state-text {
    font-size: 0.7rem;
  }
}
</style>

<template>
  <div ref="root" class="monitor-state-button" @keydown.esc="closePicker">
    <button
      ref="trigger"
      type="button"
      class="btn btn-sm btn-outline state-btn"
      :class="stateClass"
      :disabled="disabled"
      :title="stateTooltip"
      :aria-expanded="state === 'ACTIVE' ? pickerOpen : undefined"
      :aria-haspopup="state === 'ACTIVE' ? 'true' : undefined"
      @click="onMainClick"
    >
      <MonitorIcon :name="stateIcon" class="state-icon" />
      <span class="state-text">{{ stateText }}</span>
    </button>

    <button
      v-if="state === 'SILENT'"
      type="button"
      class="btn btn-sm btn-outline state-btn state-to-active"
      :disabled="disabled"
      title="Send alerts again now"
      @click="emit('stateChange', 'ACTIVE')"
    >
      <MonitorIcon name="bell" class="state-icon" />
      <span class="state-text">End silence</span>
    </button>

    <span v-if="state === 'SILENT' && endText" class="silent-until" :title="endTitle">
      <MonitorIcon name="bell-off" />
      Silent until <span class="num">{{ endText }}</span>
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
import MonitorIcon, { type MonitorIconName } from './MonitorIcon.vue'

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
    icon: 'bell-off' as MonitorIconName,
    class: 'state-to-silent',
    tooltip: 'Make the monitor silent for 1 hour to 7 days - checks without alerts',
    next: 'SILENT' as MonitorState
  },
  SILENT: {
    text: 'Make Inactive',
    icon: 'stop' as MonitorIconName,
    class: 'state-to-inactive',
    tooltip: 'Click to make monitor inactive - no monitoring',
    next: 'INACTIVE' as MonitorState
  },
  INACTIVE: {
    text: 'Make Active',
    icon: 'play' as MonitorIconName,
    class: 'state-to-active',
    tooltip: 'Click to make monitor active - monitoring with alerts',
    next: 'ACTIVE' as MonitorState
  }
}

const currentConfig = computed(() => stateConfig[props.state])

const stateClass = computed(() => currentConfig.value.class)
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
  gap: var(--space-2);
}

/* The icon tells what the button does: silent (yellow), stop (red), start (green). */
.state-to-silent .state-icon {
  color: var(--warn);
}

.state-to-inactive .state-icon {
  color: var(--down);
}

.state-to-active .state-icon {
  color: var(--up);
}

.silent-until {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--warn);
  font-size: var(--text-sm);
  white-space: nowrap;
}

.picker-popover {
  position: absolute;
  top: calc(100% + var(--space-1));
  left: 0;
  z-index: 30;
  width: max-content;
  max-width: calc(100vw - 1.5rem);
  padding: var(--space-2);
  border: 1px solid var(--border);
  border-top: 3px solid var(--masa);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  animation: picker-open 0.14s ease-out;
}

.picker-title {
  margin: 0.125rem var(--space-2) var(--space-1);
  color: var(--text);
  font-size: var(--text-sm);
  font-weight: 700;
}

.picker-note {
  margin: var(--space-2) var(--space-2) 0.125rem;
  color: var(--text-muted);
  font-size: var(--text-xs);
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
}

/* On a phone the menu sits at the bottom of the screen, where the thumb is. */
@media (max-width: 768px) {
  .picker-popover {
    position: fixed;
    top: auto;
    right: var(--space-3);
    bottom: var(--space-3);
    left: var(--space-3);
  }
}
</style>

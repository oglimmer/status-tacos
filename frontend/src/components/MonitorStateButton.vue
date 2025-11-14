<template>
  <div class="monitor-state-button">
    <button
      @click="cycleState"
      :class="stateClass"
      :disabled="disabled"
      class="state-btn"
      :title="stateTooltip"
    >
      <i :class="stateIcon"></i>
      <span class="state-text">{{ stateText }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MonitorState } from '../stores/monitors'

interface Props {
  state: MonitorState
  disabled?: boolean
}

interface Emits {
  (e: 'stateChange', state: MonitorState): void
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false
})

const emit = defineEmits<Emits>()

const stateConfig = {
  ACTIVE: {
    text: 'Make Silent',
    icon: 'fas fa-pause-circle',
    class: 'state-to-silent',
    tooltip: 'Click to make monitor silent - monitoring without alerts',
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

const cycleState = () => {
  if (!props.disabled) {
    emit('stateChange', currentConfig.value.next)
  }
}
</script>

<style scoped>
.monitor-state-button {
  display: inline-block;
}

.state-btn {
  display: flex;
  align-items: center;
  padding: 0.25rem 0.75rem;
  border: 1px solid;
  border-radius: 4px;
  font-size: 0.8rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease-in-out;
  background: white;
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
  color: #d97706;
  border-color: #d97706;
}

.state-to-silent:hover:not(:disabled) {
  background: #fffbeb;
  border-color: #b45309;
  color: #b45309;
}

.state-to-inactive {
  color: #dc2626;
  border-color: #dc2626;
}

.state-to-inactive:hover:not(:disabled) {
  background: #fef2f2;
  border-color: #b91c1c;
  color: #b91c1c;
}

.state-to-active {
  color: #059669;
  border-color: #059669;
}

.state-to-active:hover:not(:disabled) {
  background: #ecfdf5;
  border-color: #047857;
  color: #047857;
}

.state-text {
  font-weight: 500;
}

@media (max-width: 768px) {
  .state-btn {
    padding: 0.25rem 0.5rem;
    min-width: 0;
    flex: 1;
    text-align: center;
    justify-content: center;
    font-size: 0.7rem;
  }

  .state-text {
    margin-left: 0.25rem;
    font-size: 0.7rem;
  }
}
</style>

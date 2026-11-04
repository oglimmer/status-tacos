<script setup lang="ts">
export type TimeframeType = '24h' | '7d' | '90d'

interface TimeframeSwitcherProps {
  modelValue: TimeframeType
}

interface TimeframeSwitcherEmits {
  'update:modelValue': [value: TimeframeType]
}

defineProps<TimeframeSwitcherProps>()
defineEmits<TimeframeSwitcherEmits>()

const timeframes = [
  { value: '24h' as TimeframeType, label: '24 Hours' },
  { value: '7d' as TimeframeType, label: '7 Days' },
  { value: '90d' as TimeframeType, label: '90 Days' }
]
</script>

<template>
  <div class="timeframe-switcher">
    <div class="switcher-buttons">
      <button
        v-for="timeframe in timeframes"
        :key="timeframe.value"
        @click="$emit('update:modelValue', timeframe.value)"
        class="timeframe-btn"
        :class="{ active: modelValue === timeframe.value }"
      >
        {{ timeframe.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.timeframe-switcher {
  margin-bottom: 1rem;
  display: flex;
  justify-content: center;
}

.switcher-buttons {
  display: flex;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
  background: var(--surface);
}

.timeframe-btn {
  padding: 0.5rem 1rem;
  border: none;
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s ease;
  border-right: 1px solid var(--border);
}

.timeframe-btn:last-child {
  border-right: none;
}

.timeframe-btn:hover:not(.active) {
  background: var(--surface-2);
  color: var(--text-2);
}

.timeframe-btn.active {
  background: var(--accent);
  color: white;
}

.timeframe-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

/* Mobile styles */
@media (max-width: 768px) {
  .switcher-buttons {
    width: 100%;
  }

  .timeframe-btn {
    flex: 1;
    padding: 0.75rem 0.5rem;
    font-size: 0.8rem;
  }
}
</style>

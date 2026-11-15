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
  { value: '24h' as TimeframeType, label: '24h', title: 'Last 24 hours' },
  { value: '7d' as TimeframeType, label: '7d', title: 'Last 7 days' },
  { value: '90d' as TimeframeType, label: '90d', title: 'Last 90 days' }
]
</script>

<template>
  <div class="button-group" role="group" aria-label="Timeframe">
    <button
      v-for="timeframe in timeframes"
      :key="timeframe.value"
      type="button"
      class="btn num"
      :aria-pressed="modelValue === timeframe.value"
      :title="timeframe.title"
      @click="$emit('update:modelValue', timeframe.value)"
    >
      {{ timeframe.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
export type ViewModeType = 'minimal' | 'default' | 'full'

interface ViewModeSwitcherProps {
  modelValue: ViewModeType
}

interface ViewModeSwitcherEmits {
  'update:modelValue': [value: ViewModeType]
}

defineProps<ViewModeSwitcherProps>()
defineEmits<ViewModeSwitcherEmits>()

const viewModes = [
  { value: 'minimal' as ViewModeType, label: 'Minimal', title: 'One short line per monitor' },
  { value: 'default' as ViewModeType, label: 'Default', title: 'One row per monitor, details on click' },
  { value: 'full' as ViewModeType, label: 'Full', title: 'All details of all monitors' }
]
</script>

<template>
  <div class="button-group" role="group" aria-label="View">
    <button
      v-for="viewMode in viewModes"
      :key="viewMode.value"
      type="button"
      class="btn"
      :aria-pressed="modelValue === viewMode.value"
      :title="viewMode.title"
      @click="$emit('update:modelValue', viewMode.value)"
    >
      {{ viewMode.label }}
    </button>
  </div>
</template>

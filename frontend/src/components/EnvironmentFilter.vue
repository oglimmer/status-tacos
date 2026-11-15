<script setup lang="ts">
// Show only the monitors of one environment (PROD, STAGE, DEV), or the monitors without one.
import type { MonitorEnvironmentFilter } from '../services/userConfig'

defineProps<{
  modelValue: MonitorEnvironmentFilter | null
}>()

defineEmits<{
  'update:modelValue': [value: MonitorEnvironmentFilter | null]
}>()

const options: { value: MonitorEnvironmentFilter; label: string }[] = [
  { value: 'PROD', label: 'Production' },
  { value: 'STAGE', label: 'Stage' },
  { value: 'DEV', label: 'Development' },
  { value: 'NONE', label: 'No environment' }
]
</script>

<template>
  <div class="env-filter">
    <label for="environment-filter" class="sr-only">Filter by environment</label>
    <select
      id="environment-filter"
      class="input"
      :value="modelValue ?? ''"
      @change="
        $emit(
          'update:modelValue',
          (($event.target as HTMLSelectElement).value || null) as MonitorEnvironmentFilter | null
        )
      "
    >
      <option value="">All environments</option>
      <option v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </div>
</template>

<style scoped>
.env-filter {
  display: flex;
  align-items: center;
  min-width: 0;
}

.env-filter select {
  width: auto;
  max-width: 100%;
}
</style>

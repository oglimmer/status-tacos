<script setup lang="ts">
import {
  MONITOR_SORT_OPTIONS,
  defaultDirection,
  type MonitorSort,
  type MonitorSortKey
} from '../utils/monitorSort'

const props = defineProps<{
  modelValue: MonitorSort
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MonitorSort]
}>()

// A new key starts in its usual direction, so "Last downtime" shows the latest downtime first
const selectKey = (key: MonitorSortKey) => {
  emit('update:modelValue', { key, direction: defaultDirection(key) })
}

const toggleDirection = () => {
  emit('update:modelValue', {
    ...props.modelValue,
    direction: props.modelValue.direction === 'asc' ? 'desc' : 'asc'
  })
}
</script>

<template>
  <div class="sort-select">
    <label for="monitor-sort" class="sort-label">Sort</label>
    <select
      id="monitor-sort"
      class="input"
      :value="modelValue.key"
      @change="selectKey(($event.target as HTMLSelectElement).value as MonitorSortKey)"
    >
      <option v-for="option in MONITOR_SORT_OPTIONS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <button
      type="button"
      class="btn btn-outline direction-btn"
      :title="
        modelValue.direction === 'asc'
          ? 'Ascending. Click for descending.'
          : 'Descending. Click for ascending.'
      "
      :aria-label="modelValue.direction === 'asc' ? 'Sort ascending' : 'Sort descending'"
      @click="toggleDirection"
    >
      {{ modelValue.direction === 'asc' ? '↑ Asc' : '↓ Desc' }}
    </button>
  </div>
</template>

<style scoped>
.sort-select {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
}

.sort-label {
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-2);
}

.sort-select select {
  width: auto;
  min-width: 0;
}

/* The same height as the select next to it. */
.direction-btn {
  padding-inline: var(--space-3);
  font-size: var(--text-sm);
}
</style>

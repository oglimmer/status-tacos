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
  <div class="filters">
    <label for="monitor-sort">Sort by:</label>
    <select
      id="monitor-sort"
      :value="modelValue.key"
      @change="selectKey(($event.target as HTMLSelectElement).value as MonitorSortKey)"
    >
      <option v-for="option in MONITOR_SORT_OPTIONS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
    <button
      type="button"
      class="direction-btn"
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
.filters {
  margin-bottom: 0;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  justify-content: flex-end;
}

.filters label {
  font-weight: 500;
  color: #495057;
  font-size: 0.9rem;
  margin-right: 0.5rem;
}

.filters select {
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.9rem;
  background: white;
  width: 170px;
}

.direction-btn {
  padding: 0.5rem 0.75rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.9rem;
  background: white;
  color: #495057;
  cursor: pointer;
  white-space: nowrap;
  min-width: 72px;
}

.direction-btn:hover {
  background: #f8f9fa;
}

.filters select:focus,
.direction-btn:focus-visible {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
</style>

<script setup lang="ts">
// Show all monitors, or only the ones that are down, up or paused. Each choice shows its count.
export type MonitorStatusFilterType = 'all' | 'down' | 'up' | 'paused'

const props = defineProps<{
  modelValue: MonitorStatusFilterType
  counts: Record<MonitorStatusFilterType, number>
}>()

defineEmits<{
  'update:modelValue': [value: MonitorStatusFilterType]
}>()

const options: { value: MonitorStatusFilterType; label: string; title: string }[] = [
  { value: 'all', label: 'All', title: 'All monitors' },
  { value: 'down', label: 'Down', title: 'Only the monitors that are down now' },
  { value: 'up', label: 'Up', title: 'Only the monitors that are up now' },
  { value: 'paused', label: 'Paused', title: 'Only the monitors that are silent or inactive' }
]

const isAlert = (value: MonitorStatusFilterType) => value === 'down' && props.counts.down > 0
</script>

<template>
  <div class="button-group status-filter" role="group" aria-label="Show monitors">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="btn"
      :class="{ 'is-alert': isAlert(option.value) }"
      :aria-pressed="modelValue === option.value"
      :title="option.title"
      @click="$emit('update:modelValue', option.value)"
    >
      {{ option.label }}
      <span class="status-filter-count num">{{ counts[option.value] }}</span>
    </button>
  </div>
</template>

<style scoped>
.status-filter > .btn {
  gap: var(--space-2);
}

.status-filter-count {
  font-weight: 400;
  color: var(--text-muted);
}

/* Down with monitors in it: red, so it is seen in the row of choices. */
.status-filter > .btn.is-alert {
  color: var(--down);
}

.status-filter > .btn.is-alert .status-filter-count {
  min-width: 1.5em;
  padding: 0 0.4em;
  border-radius: var(--radius-pill);
  background: var(--down);
  color: var(--on-down);
  font-weight: 700;
  text-align: center;
}
</style>

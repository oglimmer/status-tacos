<script setup lang="ts">
// The counts of the monitors. Each count is a status filter: click it to show only those monitors.

interface SummaryData {
  total: number
  up: number
  down: number
  active: number
}

type StatusFilter = 'all' | 'up' | 'down' | 'active'

defineProps<{
  summary: SummaryData
  selectedStatusFilter: StatusFilter
}>()

const emit = defineEmits<{
  'update:selectedStatusFilter': [value: StatusFilter]
}>()
</script>

<template>
  <div class="stats monitor-stats" role="group" aria-label="Filter by status">
    <button
      type="button"
      class="stat stat-down"
      :class="{ 'is-alert': summary.down > 0 }"
      :aria-pressed="selectedStatusFilter === 'down'"
      @click="emit('update:selectedStatusFilter', 'down')"
    >
      <span class="stat-value">{{ summary.down }}</span>
      <span class="stat-label">Down</span>
    </button>
    <button
      type="button"
      class="stat stat-up"
      :aria-pressed="selectedStatusFilter === 'up'"
      @click="emit('update:selectedStatusFilter', 'up')"
    >
      <span class="stat-value">{{ summary.up }}</span>
      <span class="stat-label">Up</span>
    </button>
    <button
      type="button"
      class="stat"
      :aria-pressed="selectedStatusFilter === 'active'"
      @click="emit('update:selectedStatusFilter', 'active')"
    >
      <span class="stat-value">{{ summary.active }}</span>
      <span class="stat-label">Active</span>
    </button>
    <button
      type="button"
      class="stat"
      :aria-pressed="selectedStatusFilter === 'all'"
      @click="emit('update:selectedStatusFilter', 'all')"
    >
      <span class="stat-value">{{ summary.total }}</span>
      <span class="stat-label">Total</span>
    </button>
  </div>
</template>

<style scoped>
/* Four equal tiles; on a phone two per row. */
.monitor-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.monitor-stats .stat {
  min-width: 0;
}

/* Zero down is good news: the number stays quiet. */
.stat-down:not(.is-alert) .stat-value {
  color: var(--text);
}

@media (max-width: 520px) {
  .monitor-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

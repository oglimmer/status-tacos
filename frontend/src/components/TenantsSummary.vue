<script setup lang="ts">
// The counts of the tenants in a stats row, then the "Active only" filter in the toolbar.

interface SummaryData {
  total: number
  active: number
  inactive: number
}

defineProps<{
  summary: SummaryData
  showActiveOnly: boolean
}>()

defineEmits<{
  'toggle-filter': []
}>()
</script>

<template>
  <div class="page-tools">
    <div class="stats" aria-label="Tenant counts">
      <div class="stat">
        <span class="stat-value">{{ summary.total }}</span>
        <span class="stat-label">Total</span>
      </div>
      <div class="stat stat-up">
        <span class="stat-value">{{ summary.active }}</span>
        <span class="stat-label">Active</span>
      </div>
      <div class="stat">
        <span class="stat-value">{{ summary.inactive }}</span>
        <span class="stat-label">Inactive</span>
      </div>
    </div>

    <div class="toolbar">
      <button
        type="button"
        class="chip"
        :aria-pressed="showActiveOnly"
        @click="$emit('toggle-filter')"
      >
        Active only
      </button>
    </div>
  </div>
</template>

<style scoped>
.page-tools {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}

/* On a phone the tiles share the width. */
@media (max-width: 520px) {
  .stats .stat {
    flex: 1 1 5.5rem;
    min-width: 0;
  }
}
</style>

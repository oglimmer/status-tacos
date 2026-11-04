<script setup lang="ts">
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
  <div class="dashboard-summary-container">
    <div class="dashboard-summary">
      <div class="summary-card">
        <div class="summary-number">{{ summary.total }}</div>
        <div class="summary-label">Total</div>
      </div>
      <div class="summary-card">
        <div class="summary-number active">{{ summary.active }}</div>
        <div class="summary-label">Active</div>
      </div>
      <div class="summary-card">
        <div class="summary-number inactive">{{ summary.inactive }}</div>
        <div class="summary-label">Inactive</div>
      </div>
    </div>

    <div class="tenant-filter">
      <button
        @click="$emit('toggle-filter')"
        class="btn btn-secondary"
        :class="{ active: showActiveOnly }"
      >
        {{ showActiveOnly ? 'Show All' : 'Active Only' }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.dashboard-summary-container {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1.5rem;
  gap: 2rem;
}

.dashboard-summary {
  display: flex;
  gap: 1rem;
}

.summary-card {
  background: var(--surface);
  padding: 0.75rem 1rem;
  border-radius: 6px;
  border: 1px solid var(--border-subtle);
  text-align: center;
  min-width: 80px;
}

.summary-number {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 0.25rem;
}

.summary-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  text-transform: uppercase;
  font-weight: 500;
}

.summary-number.active {
  color: var(--success-text);
}

.summary-number.inactive {
  color: var(--text-muted);
}

.tenant-filter {
  flex-shrink: 0;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--secondary);
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: var(--secondary-hover);
}

.btn-secondary.active {
  background: var(--success);
}

.btn-secondary.active:hover:not(:disabled) {
  background: var(--success-hover);
}

/* Mobile styles */
@media (max-width: 768px) {
  .dashboard-summary-container {
    flex-direction: column;
    gap: 1rem;
  }

  .dashboard-summary {
    flex-wrap: wrap;
    gap: 0.5rem;
  }

  .summary-card {
    flex: 1;
    min-width: 70px;
    padding: 0.5rem 0.75rem;
  }

  .summary-number {
    font-size: 1.1rem;
  }

  .summary-label {
    font-size: 0.7rem;
  }

  .tenant-filter {
    width: 100%;
  }
}
</style>

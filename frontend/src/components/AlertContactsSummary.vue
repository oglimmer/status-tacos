<script setup lang="ts">
import TenantFilter from './TenantFilter.vue'

interface Tenant {
  id: number
  name: string
}

interface SummaryData {
  total: number
  active: number
  inactive: number
  email: number
  webhook: number
}

defineProps<{
  summary: SummaryData
  tenants?: Tenant[]
  selectedTenantId: number | null
}>()

defineEmits<{
  'update:selectedTenantId': [value: number | null]
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
      <div class="summary-card">
        <div class="summary-number">{{ summary.email }}</div>
        <div class="summary-label">Email</div>
      </div>
      <div class="summary-card">
        <div class="summary-number">{{ summary.webhook }}</div>
        <div class="summary-label">Webhook</div>
      </div>
    </div>

    <TenantFilter
      v-if="tenants"
      :tenants="tenants"
      :model-value="selectedTenantId"
      @update:model-value="$emit('update:selectedTenantId', $event)"
      class="tenant-filter"
    />
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
  background: white;
  padding: 0.75rem 1rem;
  border-radius: 6px;
  border: 1px solid #e9ecef;
  text-align: center;
  min-width: 80px;
}

.summary-number {
  font-size: 1.25rem;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 0.25rem;
}

.summary-label {
  font-size: 0.75rem;
  color: #6c757d;
  text-transform: uppercase;
  font-weight: 500;
}

.summary-number.active {
  color: #28a745;
}

.summary-number.inactive {
  color: #6c757d;
}

.tenant-filter {
  flex-shrink: 0;
}

.tenant-filter :deep(.filters) {
  margin-bottom: 0;
}

.tenant-filter :deep(.filters label) {
  white-space: nowrap;
}

.tenant-filter :deep(.filters select) {
  min-width: 150px;
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

  .tenant-filter :deep(.filters select) {
    flex: 1;
    min-width: 0;
  }
}
</style>

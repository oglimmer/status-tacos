<script setup lang="ts">
import TenantFilter from './TenantFilter.vue'
import NameFilter from './NameFilter.vue'

interface Tenant {
  id: number
  name: string
}

interface SummaryData {
  total: number
  up: number
  down: number
  active: number
}

defineProps<{
  summary: SummaryData
  tenants?: Tenant[]
  selectedTenantId: number | null
  selectedStatusFilter: 'all' | 'up' | 'down' | 'active'
  selectedNameFilter: string
}>()

defineEmits<{
  'update:selectedTenantId': [value: number | null]
  'update:selectedStatusFilter': [value: 'all' | 'up' | 'down' | 'active']
  'update:selectedNameFilter': [value: string]
}>()
</script>

<template>
  <div class="dashboard-summary-container">
    <div class="dashboard-summary">
      <div
        class="summary-card clickable"
        :class="{ active: selectedStatusFilter === 'all' }"
        @click="$emit('update:selectedStatusFilter', 'all')"
      >
        <div class="summary-number">{{ summary.total }}</div>
        <div class="summary-label">Total</div>
      </div>
      <div
        class="summary-card clickable"
        :class="{ active: selectedStatusFilter === 'up' }"
        @click="$emit('update:selectedStatusFilter', 'up')"
      >
        <div class="summary-number up">{{ summary.up }}</div>
        <div class="summary-label">Up</div>
      </div>
      <div
        class="summary-card clickable"
        :class="{ active: selectedStatusFilter === 'down' }"
        @click="$emit('update:selectedStatusFilter', 'down')"
      >
        <div class="summary-number down">{{ summary.down }}</div>
        <div class="summary-label">Down</div>
      </div>
      <div
        class="summary-card clickable"
        :class="{ active: selectedStatusFilter === 'active' }"
        @click="$emit('update:selectedStatusFilter', 'active')"
      >
        <div class="summary-number">{{ summary.active }}</div>
        <div class="summary-label">Active</div>
      </div>
    </div>

    <div class="filters-container">
      <TenantFilter
        v-if="tenants"
        :tenants="tenants"
        :model-value="selectedTenantId"
        @update:model-value="$emit('update:selectedTenantId', $event)"
        class="tenant-filter"
      />

      <NameFilter
        :model-value="selectedNameFilter"
        @update:model-value="$emit('update:selectedNameFilter', $event)"
        class="name-filter"
      />
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

.filters-container {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  align-items: flex-end;
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

.summary-card.clickable {
  cursor: pointer;
  transition: all 0.2s ease;
}

.summary-card.clickable:hover {
  background: #f8f9fa;
  border-color: #dee2e6;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.summary-card.active {
  background: #007bff;
  border-color: #007bff;
  color: white;
}

.summary-card.active .summary-number {
  color: white;
}

.summary-card.active .summary-label {
  color: rgba(255, 255, 255, 0.8);
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

.summary-number.up {
  color: #28a745;
}

.summary-number.down {
  color: #dc3545;
}

.tenant-filter,
.name-filter {
  flex-shrink: 0;
}


.tenant-filter :deep(.filters),
.name-filter :deep(.filters) {
  margin-bottom: 0;
}

.tenant-filter :deep(.filters label) {
  white-space: nowrap;
}

.tenant-filter :deep(.filters select) {
  width: 150px;
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

  .filters-container {
    flex-direction: column;
    gap: 1rem;
    width: 100%;
  }

  .tenant-filter,
  .name-filter {
    width: 100%;
  }

  .tenant-filter :deep(.filters select),
  .name-filter :deep(.filters input) {
    flex: 1;
    min-width: 0;
    width: 100%;
  }
}
</style>

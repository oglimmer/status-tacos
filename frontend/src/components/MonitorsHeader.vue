<script setup lang="ts">
import TimeframeSwitcher, { type TimeframeType } from './TimeframeSwitcher.vue'
import ViewModeSwitcher, { type ViewModeType } from './ViewModeSwitcher.vue'

interface MonitorsHeaderProps {
  selectedTimeframe: TimeframeType
  selectedViewMode: ViewModeType
}

interface MonitorsHeaderEmits {
  'add-monitor': []
  'update:selectedTimeframe': [timeframe: TimeframeType]
  'update:selectedViewMode': [viewMode: ViewModeType]
}

defineProps<MonitorsHeaderProps>()
defineEmits<MonitorsHeaderEmits>()
</script>

<template>
  <div class="dashboard-header-section">
    <div class="header-left">
      <h2>Your Monitors</h2>
    </div>
    <div class="header-center">
      <div class="switchers-container">
        <TimeframeSwitcher
          :model-value="selectedTimeframe"
          @update:model-value="$emit('update:selectedTimeframe', $event)"
        />
        <ViewModeSwitcher
          :model-value="selectedViewMode"
          @update:model-value="$emit('update:selectedViewMode', $event)"
        />
      </div>
    </div>
    <div class="header-right">
      <button @click="$emit('add-monitor')" class="btn btn-primary">
        Add Monitor
      </button>
    </div>
  </div>
</template>

<style scoped>
.dashboard-header-section {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 1rem;
  margin-bottom: 2rem;
}

.header-left {
  display: flex;
  justify-content: flex-start;
}

.header-center {
  display: flex;
  justify-content: center;
}

.switchers-container {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.header-right {
  display: flex;
  justify-content: flex-end;
}

.dashboard-header-section h2 {
  margin: 0;
  color: #2c3e50;
}

@media (max-width: 768px) {
  .dashboard-header-section {
    grid-template-columns: 1fr;
    gap: 1rem;
    text-align: center;
  }

  .header-left,
  .header-center,
  .header-right {
    justify-content: center;
  }

  .switchers-container {
    flex-direction: column;
    gap: 0.5rem;
  }

  .dashboard-header-section h2 {
    font-size: 1.25rem;
  }
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

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}
</style>

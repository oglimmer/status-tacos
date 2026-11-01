<script setup lang="ts">
import TimeframeSwitcher, { type TimeframeType } from './TimeframeSwitcher.vue'
import ViewModeSwitcher, { type ViewModeType } from './ViewModeSwitcher.vue'

interface MonitorsHeaderProps {
  selectedTimeframe: TimeframeType
  selectedViewMode: ViewModeType
  /** The user has the maximum number of monitors. */
  limitReached?: boolean
  /** Multi edit: the user selects monitors. */
  selecting?: boolean
  /** There are monitors to select. */
  canSelect?: boolean
}

interface MonitorsHeaderEmits {
  'add-monitor': []
  'toggle-select-mode': []
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
      <button
        v-if="canSelect"
        @click="$emit('toggle-select-mode')"
        class="btn btn-outline"
        :class="{ active: selecting }"
        :aria-pressed="selecting"
        title="Select monitors to change settings of many monitors at once"
      >
        {{ selecting ? 'Done' : 'Select' }}
      </button>
      <button
        @click="$emit('add-monitor')"
        class="btn btn-primary"
        :disabled="limitReached"
        :title="limitReached ? 'You have the maximum number of monitors' : undefined"
      >
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
  gap: 0.5rem;
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

.btn-outline {
  background: white;
  color: #007bff;
  border: 1px solid #007bff;
}

.btn-outline:hover,
.btn-outline.active {
  background: #e7f1ff;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}
</style>

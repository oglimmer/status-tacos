<script setup lang="ts">
export type ViewModeType = 'minimal' | 'default' | 'full'

interface ViewModeSwitcherProps {
  modelValue: ViewModeType
}

interface ViewModeSwitcherEmits {
  'update:modelValue': [value: ViewModeType]
}

defineProps<ViewModeSwitcherProps>()
defineEmits<ViewModeSwitcherEmits>()

const viewModes = [
  { value: 'minimal' as ViewModeType, label: 'Minimal' },
  { value: 'default' as ViewModeType, label: 'Default' },
  { value: 'full' as ViewModeType, label: 'Full' }
]
</script>

<template>
  <div class="viewmode-switcher">
    <div class="switcher-buttons">
      <button
        v-for="viewMode in viewModes"
        :key="viewMode.value"
        @click="$emit('update:modelValue', viewMode.value)"
        class="viewmode-btn"
        :class="{ active: modelValue === viewMode.value }"
      >
        {{ viewMode.label }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.viewmode-switcher {
  margin-bottom: 1rem;
  display: flex;
  justify-content: center;
}

.switcher-buttons {
  display: flex;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  overflow: hidden;
  background: white;
}

.viewmode-btn {
  padding: 0.5rem 1rem;
  border: none;
  background: white;
  color: #6c757d;
  cursor: pointer;
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.2s ease;
  border-right: 1px solid #dee2e6;
}

.viewmode-btn:last-child {
  border-right: none;
}

.viewmode-btn:hover:not(.active) {
  background: #f8f9fa;
  color: #495057;
}

.viewmode-btn.active {
  background: #28a745;
  color: white;
}

.viewmode-btn:focus {
  outline: none;
  box-shadow: 0 0 0 2px rgba(40, 167, 69, 0.25);
}

/* Mobile styles */
@media (max-width: 768px) {
  .switcher-buttons {
    width: 100%;
  }

  .viewmode-btn {
    flex: 1;
    padding: 0.75rem 0.5rem;
    font-size: 0.8rem;
  }
}
</style>

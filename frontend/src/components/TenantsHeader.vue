<script setup lang="ts">
defineEmits<{
  'add-tenant': []
}>()
</script>

<template>
  <div class="dashboard-header-section">
    <h2>Tenants</h2>
    <button @click="$emit('add-tenant')" class="btn btn-primary">
      Add Tenant
    </button>
  </div>
</template>

<style scoped>
.dashboard-header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2rem;
}

.dashboard-header-section h2 {
  margin: 0;
  color: #2c3e50;
}

@media (max-width: 768px) {
  .dashboard-header-section {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    gap: 1rem;
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

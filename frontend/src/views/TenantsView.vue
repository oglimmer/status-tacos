<script setup lang="ts">
import TenantsManagement from '../components/TenantsManagement.vue'
import DashboardHeader from '../components/DashboardHeader.vue'
import PageNavigation from '../components/PageNavigation.vue'
</script>

<template>
  <div class="tenants-container">
    <DashboardHeader />

    <main class="dashboard-main">
      <PageNavigation />

      <div class="tenants-content">
        <TenantsManagement />
      </div>
    </main>
  </div>
</template>

<style scoped>
.tenants-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 50%, #dee2e6 100%);
  background-attachment: fixed;
}

.dashboard-main {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  margin-top: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.tenants-content {
  min-height: 400px;
}

/* Mobile styles */
@media (max-width: 768px) {
  .dashboard-main {
    padding: 1rem;
    margin: 0;
    border-radius: 0;
    border: none;
    background: transparent;
    backdrop-filter: none;
    box-shadow: none;
  }
}
</style>

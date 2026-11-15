<script setup lang="ts">
import TenantsManagement from '../components/TenantsManagement.vue'
import AppShell from '../components/AppShell.vue'
</script>

<template>
  <AppShell>
    <div class="tenants-content">
      <TenantsManagement />
    </div>
  </AppShell>
</template>

<style scoped>
.tenants-content {
  min-height: 400px;
}
</style>

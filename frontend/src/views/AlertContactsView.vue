<script setup lang="ts">
import AlertContactsManagement from '../components/AlertContactsManagement.vue'
import AppShell from '../components/AppShell.vue'
</script>

<template>
  <AppShell>
    <div class="alert-contacts-content">
      <AlertContactsManagement />
    </div>
  </AppShell>
</template>

<style scoped>
.alert-contacts-content {
  min-height: 400px;
}
</style>

<script setup lang="ts">
interface NavigationItem {
  path: string
  label: string
  isActive?: boolean
}

defineProps<{
  currentPath?: string
}>()

const navigationItems: NavigationItem[] = [
  { path: '/monitors', label: 'Monitors' },
  { path: '/alert-contacts', label: 'Alert Contacts' },
  { path: '/tenants', label: 'Tenants' }
]
</script>

<template>
  <nav class="page-navigation">
    <router-link
      v-for="item in navigationItems"
      :key="item.path"
      :to="item.path"
      class="nav-button"
      :class="{ active: $route.path === item.path }"
    >
      {{ item.label }}
    </router-link>
  </nav>
</template>

<style scoped>
.page-navigation {
  display: flex;
  border-bottom: 2px solid var(--border-subtle);
  margin-bottom: 2rem;
  gap: 0;
}

.nav-button {
  padding: 1rem 2rem;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 1rem;
  font-weight: 500;
  border-bottom: 3px solid transparent;
  transition: all 0.2s;
  text-decoration: none;
}

.nav-button:hover {
  color: var(--text-2);
  background: var(--surface-2);
}

.nav-button.active {
  color: var(--link);
  border-bottom-color: var(--accent);
  background: var(--surface);
}
</style>

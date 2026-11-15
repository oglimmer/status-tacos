<script setup lang="ts">
// The head of a page: the title, an optional line below it, the actions on the right.
// The default slot is for a toolbar (filters, search) under the title.
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header-top">
      <div class="page-header-text">
        <h1 class="page-title">{{ title }}</h1>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </div>
    <div v-if="$slots.default" class="page-header-below">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.page-header {
  margin-bottom: var(--space-5);
}

.page-header-top {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3) var(--space-4);
}

.page-header-text {
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: var(--text-xl);
  font-weight: 700;
  line-height: var(--leading-tight);
  color: var(--text);
}

.page-subtitle {
  margin: var(--space-1) 0 0;
  font-size: var(--text-md);
  color: var(--text-muted);
}

.page-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.page-header-below {
  margin-top: var(--space-4);
}
</style>

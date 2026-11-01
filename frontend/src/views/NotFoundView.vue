<script setup lang="ts">
import { useRoute } from 'vue-router'
import ErrorPage from '../components/ErrorPage.vue'

const route = useRoute()
</script>

<template>
  <ErrorPage
    code="404"
    title="No taco here"
    message="We looked everywhere. This page does not exist, or it moved."
  >
    <p class="error-path">
      You asked for <code>{{ route.fullPath }}</code>
    </p>
  </ErrorPage>
</template>

<style scoped>
.error-path {
  margin: 0 0 1.5rem;
  color: var(--ink-soft);
  overflow-wrap: anywhere;
}

.error-path code {
  font-family: var(--mono);
  font-size: 0.95em;
  padding: 0.1rem 0.35rem;
  background: var(--agua);
  border-radius: 0.3rem;
}
</style>

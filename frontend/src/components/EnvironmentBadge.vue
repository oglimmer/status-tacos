<script setup lang="ts">
import type { MonitorEnvironment } from '../stores/monitors'

defineProps<{
  environment?: MonitorEnvironment | null
}>()

const TITLES: Record<MonitorEnvironment, string> = {
  PROD: 'Production',
  STAGE: 'Stage',
  DEV: 'Development'
}
</script>

<template>
  <span
    v-if="environment && TITLES[environment]"
    class="badge env-badge"
    :class="`env-${environment.toLowerCase()}`"
    :title="`Environment: ${TITLES[environment]}`"
  >{{ environment }}</span>
</template>

<style scoped>
/* The badge base comes from app.css. Each environment has its own color, tinted as the fill. */
.env-badge {
  --env-color: var(--env-dev);
  background: color-mix(in srgb, var(--env-color) 14%, transparent);
  border-color: color-mix(in srgb, var(--env-color) 30%, transparent);
  color: var(--env-color);
  letter-spacing: 0.02em;
}

.env-prod {
  --env-color: var(--env-prod);
}

.env-stage {
  --env-color: var(--env-stage);
}
</style>

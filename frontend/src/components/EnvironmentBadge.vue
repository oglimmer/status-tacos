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
    class="env-badge"
    :class="`env-${environment.toLowerCase()}`"
    :title="`Environment: ${TITLES[environment]}`"
  >{{ environment }}</span>
</template>

<style scoped>
.env-badge {
  --env-color: var(--env-dev);
  margin-left: 0.4rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--env-color) 16%, transparent);
  color: var(--env-color);
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.03em;
  vertical-align: middle;
}

.env-prod {
  --env-color: var(--env-prod);
}

.env-stage {
  --env-color: var(--env-stage);
}
</style>

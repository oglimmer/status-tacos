<script setup lang="ts">
import { computed } from 'vue'

interface MonitorLimitInfoProps {
  monitorCount: number
  maxMonitors: number
}

const props = defineProps<MonitorLimitInfoProps>()

const limitReached = computed(() => props.monitorCount >= props.maxMonitors)
</script>

<template>
  <p class="limit-info" :class="{ 'limit-reached': limitReached }" role="note">
    <span class="num">{{ monitorCount }}/{{ maxMonitors }}</span>
    <span v-if="limitReached">monitors used: you cannot add more here.</span>
    <span v-else>monitors used.</span>
    <span class="limit-detail">
      For more, run your own Status Tacos backend without a limit:
      <a href="https://github.com/oglimmer/status-tacos" target="_blank" rel="noopener">see how on GitHub</a>.
    </span>
  </p>
</template>

<style scoped>
.limit-info {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  column-gap: var(--space-1);
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.limit-info .num {
  font-weight: 700;
  color: var(--text-2);
}

.limit-reached .num,
.limit-reached > span:nth-child(2) {
  color: var(--warn);
  font-weight: 700;
}

.limit-detail {
  margin-left: var(--space-1);
}

.limit-info a {
  color: var(--link);
}

.limit-info a:hover {
  color: var(--link-hover);
}
</style>

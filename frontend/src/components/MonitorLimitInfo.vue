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
  <div class="limit-info" :class="{ 'limit-reached': limitReached }" role="note">
    <p class="limit-count">
      <strong>{{ monitorCount }} of {{ maxMonitors }} monitors used.</strong>
      <span v-if="limitReached"> You cannot add more monitors here.</span>
    </p>
    <p class="limit-detail">
      Need more monitors? We recommend that you run your own Status Tacos backend. It has no limit.
      <a href="https://github.com/oglimmer/status-tacos" target="_blank" rel="noopener">
        See how on GitHub
      </a>
    </p>
  </div>
</template>

<style scoped>
.limit-info {
  margin-bottom: 1.5rem;
  padding: 0.75rem 1rem;
  border: 1px solid #b8daff;
  border-radius: 8px;
  background: #e8f4ff;
  color: #004085;
  font-size: 0.9rem;
}

.limit-reached {
  border-color: #ffd699;
  background: #fff6e5;
  color: #7a4d00;
}

.limit-info p {
  margin: 0;
}

.limit-detail {
  margin-top: 0.25rem !important;
}

.limit-info a {
  color: inherit;
  font-weight: 600;
}
</style>

<script setup lang="ts">
import type { TimeframeType } from './TimeframeSwitcher.vue'

/** The numbers this box shows. The 7d/90d stats and the 24h history both fit. */
export interface MetricsValues {
  uptimePercentage?: number
  minResponseTimeMs?: number | null
  avgResponseTimeMs?: number | null
  maxResponseTimeMs?: number | null
  p99ResponseTimeMs?: number | null
  totalChecks: number
}
import { formatUptime, getTimeframeLabel, uptimeLevel } from '../utils/uptime'

interface UptimeMetricsProps {
  stats: MetricsValues | null
  timeframe: TimeframeType
  isLoading?: boolean
}

defineProps<UptimeMetricsProps>()

const formatResponseTime = (ms: number | null | undefined): string => {
  if (ms === undefined || ms === null) return 'N/A'
  return `${ms} ms`
}

const getUptimeClass = (percentage: number | undefined): string => `level-${uptimeLevel(percentage)}`
</script>

<template>
  <section class="uptime-metrics" :aria-label="`Metrics of the ${getTimeframeLabel(timeframe)}`">
    <h4 class="metrics-title">Metrics, {{ getTimeframeLabel(timeframe) }}</h4>

    <p v-if="isLoading" class="metrics-note" role="status">
      <span class="metrics-spinner" aria-hidden="true"></span>
      Loading metrics…
    </p>

    <p v-else-if="!stats" class="metrics-note">No data for this timeframe yet.</p>

    <dl v-else class="metrics-grid">
      <div class="metric">
        <dt>Uptime</dt>
        <dd class="num" :class="getUptimeClass(stats.uptimePercentage)">{{ formatUptime(stats.uptimePercentage) }}</dd>
      </div>
      <div class="metric">
        <dt>Min</dt>
        <dd class="num">{{ formatResponseTime(stats.minResponseTimeMs) }}</dd>
      </div>
      <div class="metric">
        <dt>Avg</dt>
        <dd class="num">{{ formatResponseTime(stats.avgResponseTimeMs) }}</dd>
      </div>
      <div class="metric">
        <dt>Max</dt>
        <dd class="num">{{ formatResponseTime(stats.maxResponseTimeMs) }}</dd>
      </div>
      <div class="metric">
        <dt>p99</dt>
        <dd class="num">{{ formatResponseTime(stats.p99ResponseTimeMs) }}</dd>
      </div>
      <div class="metric">
        <dt>Checks</dt>
        <dd class="num">{{ stats.totalChecks.toLocaleString() }}</dd>
      </div>
    </dl>
  </section>
</template>

<style scoped>
.uptime-metrics {
  min-width: 0;
}

.metrics-title {
  margin: 0 0 var(--space-1);
  font-size: var(--text-xs);
  font-weight: 700;
  color: var(--text-2);
}

/* Six numbers in one band, separated by thin lines. Three per row on a phone. */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  margin: 0;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface);
  overflow: hidden;
}

.metric {
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  box-shadow: 1px 0 0 var(--border-subtle), 0 1px 0 var(--border-subtle);
}

.metric dt {
  font-size: var(--text-xs);
  color: var(--text-muted);
}

.metric dd {
  margin: 0;
  font-size: var(--text-md);
  color: var(--text);
  white-space: nowrap;
}

.metric dd.level-warning {
  color: var(--warn);
}

.metric dd.level-poor {
  color: var(--down);
  font-weight: 700;
}

.metric dd.level-no-data {
  color: var(--text-muted);
}

.metrics-note {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: var(--space-2) var(--space-3);
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.metrics-spinner {
  width: 0.875rem;
  height: 0.875rem;
  border: 2px solid var(--border);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: metrics-spin 0.9s linear infinite;
}

@keyframes metrics-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 640px) {
  .metrics-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>

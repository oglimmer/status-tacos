<script setup lang="ts">
import type { TimeframeType } from './TimeframeSwitcher.vue'

interface UptimeStats {
  id: number
  monitorId: number
  monitorName: string
  tenantId: number
  periodType: 'SEVEN_DAYS' | 'NINETY_DAYS' | 'THREE_SIXTY_FIVE_DAYS'
  periodStart: string
  periodEnd: string
  totalChecks: number
  successfulChecks: number
  uptimePercentage: number
  minResponseTimeMs?: number
  maxResponseTimeMs?: number
  avgResponseTimeMs?: number
  p99ResponseTimeMs?: number
  responseTimeData?: string
  statusChangeData?: string
  calculatedAt: string
}

interface UptimeMetricsProps {
  stats: UptimeStats | null
  timeframe: TimeframeType
  isLoading?: boolean
}

defineProps<UptimeMetricsProps>()

const formatResponseTime = (ms: number | undefined): string => {
  if (ms === undefined || ms === null) return 'N/A'
  return `${ms}ms`
}

const formatUptime = (percentage: number | undefined): string => {
  if (percentage === undefined || percentage === null) return 'N/A'
  return `${percentage.toFixed(2)}%`
}

const getUptimeClass = (percentage: number | undefined): string => {
  if (percentage === undefined || percentage === null) return 'metric-value no-data'

  if (percentage >= 99) return 'metric-value uptime-excellent'
  if (percentage >= 95) return 'metric-value uptime-good'
  if (percentage >= 90) return 'metric-value uptime-warning'
  return 'metric-value uptime-poor'
}

const getTimeframeLabel = (timeframe: TimeframeType): string => {
  switch (timeframe) {
    case '24h': return '24 Hours'
    case '7d': return '7 Days'
    case '90d': return '90 Days'
    case '365d': return '1 Year'
    default: return timeframe
  }
}
</script>

<template>
  <div class="uptime-metrics">
    <div class="metrics-header">
      <h4 class="metrics-title">Performance Metrics ({{ getTimeframeLabel(timeframe) }})</h4>
    </div>

    <div v-if="isLoading" class="metrics-loading">
      <div class="loading-spinner"></div>
      <span class="loading-text">Loading metrics...</span>
    </div>

    <div v-else-if="!stats" class="metrics-no-data">
      <span class="no-data-text">No data available for this timeframe</span>
    </div>

    <div v-else class="metrics-grid">
      <div class="metric-item">
        <span class="metric-label">Uptime</span>
        <span :class="getUptimeClass(stats.uptimePercentage)">
          {{ formatUptime(stats.uptimePercentage) }}
        </span>
      </div>

      <div class="metric-item">
        <span class="metric-label">Min Response</span>
        <span class="metric-value">
          {{ formatResponseTime(stats.minResponseTimeMs) }}
        </span>
      </div>

      <div class="metric-item">
        <span class="metric-label">Avg Response</span>
        <span class="metric-value">
          {{ formatResponseTime(stats.avgResponseTimeMs) }}
        </span>
      </div>

      <div class="metric-item">
        <span class="metric-label">Max Response</span>
        <span class="metric-value">
          {{ formatResponseTime(stats.maxResponseTimeMs) }}
        </span>
      </div>

      <div class="metric-item">
        <span class="metric-label">99th Percentile</span>
        <span class="metric-value">
          {{ formatResponseTime(stats.p99ResponseTimeMs) }}
        </span>
      </div>

      <div class="metric-item">
        <span class="metric-label">Total Checks</span>
        <span class="metric-value">
          {{ stats.totalChecks.toLocaleString() }}
        </span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.uptime-metrics {
  background: #f8f9fa;
  border-radius: 6px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.metrics-header {
  margin-bottom: 1rem;
}

.metrics-title {
  margin: 0;
  font-size: 0.9rem;
  color: #495057;
  font-weight: 600;
}

.metrics-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  gap: 0.5rem;
}

.loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #dee2e6;
  border-top: 2px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  color: #6c757d;
  font-size: 0.875rem;
}

.metrics-no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
}

.no-data-text {
  color: #6c757d;
  font-size: 0.875rem;
  font-style: italic;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 1rem;
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 0.75rem;
  background: white;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}

.metric-label {
  font-size: 0.75rem;
  color: #6c757d;
  font-weight: 500;
  margin-bottom: 0.25rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.metric-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: #2c3e50;
}

.metric-value.uptime-excellent {
  color: #28a745;
}

.metric-value.uptime-good {
  color: #6f7e08;
}

.metric-value.uptime-warning {
  color: #fd7e14;
}

.metric-value.uptime-poor {
  color: #dc3545;
}

.metric-value.no-data {
  color: #6c757d;
  font-style: italic;
  font-weight: 400;
}

/* Mobile styles */
@media (max-width: 768px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 0.75rem;
  }

  .metric-item {
    padding: 0.5rem;
  }

  .metric-label {
    font-size: 0.7rem;
  }

  .metric-value {
    font-size: 0.8rem;
  }
}
</style>

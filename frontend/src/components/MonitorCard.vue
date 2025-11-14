<script setup lang="ts">
import { } from 'vue'
import ResponseTimeChart from './ResponseTimeChart.vue'
import StatusChart from './StatusChart.vue'
import MonitorStateButton from './MonitorStateButton.vue'
import type { MonitorResponse, MonitorState } from '../stores/monitors'

interface MonitorStatus {
  currentStatus: 'up' | 'down'
  lastCheckedAt: string
  lastResponseTimeMs: number
  lastStatusCode: number
  consecutiveFailures: number
}

interface ResponseTimeHistory {
  uptimePercentage24h?: number
  totalChecks24h?: number
  dataPoints?: Array<{ maxResponseTimeMs: number }>
  statusChanges?: Array<{ timestamp: string; isUp: boolean }>
}

defineProps<{
  monitor: MonitorResponse
  status: MonitorStatus | null
  responseTimeHistory: ResponseTimeHistory | null
  isExpanded: boolean
}>()

defineEmits<{
  'toggle-expanded': [id: number]
  'edit': [monitor: MonitorResponse]
  'state-change': [id: number, state: MonitorState]
  'delete': [id: number]
}>()

const getStatusBadgeClass = (status: 'up' | 'down') => {
  return status === 'up' ? 'status-badge status-up' : 'status-badge status-down'
}

const formatDate = (dateString: string) => {
  // Backend returns UTC timestamps without 'Z' suffix, so we need to append it
  // to ensure proper UTC interpretation before converting to local timezone
  const utcTimestamp = dateString.endsWith('Z') ? dateString : dateString + 'Z'
  const date = new Date(utcTimestamp)
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZoneName: 'short'
  })
}

// Get uptime percentage from API data
const getUptimePercentage = (history: ResponseTimeHistory | null): number => {
  if (history && history.uptimePercentage24h !== undefined) {
    return Math.round(history.uptimePercentage24h * 100) / 100
  }
  return 0
}

// Get response time history data from API
const getResponseTimeHistoryData = (history: ResponseTimeHistory | null): number[] => {
  if (!history || !history.dataPoints || history.dataPoints.length === 0) {
    return []
  }
  return history.dataPoints.map((point: { maxResponseTimeMs: number }) => point.maxResponseTimeMs)
}


// Check if uptime data is available
const hasUptimeData = (history: ResponseTimeHistory | null): boolean => {
  return !!(history && history.totalChecks24h !== undefined && history.totalChecks24h > 0)
}

// Get status changes data from API
const getStatusChanges = (history: ResponseTimeHistory | null): Array<{ timestamp: string; isUp: boolean }> => {
  if (!history || !history.statusChanges || history.statusChanges.length === 0) {
    return []
  }
  return history.statusChanges
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const getUptimeClass = (history: ResponseTimeHistory | null): string => {
  if (!hasUptimeData(history)) {
    return 'stat-value no-data'
  }

  const uptime = getUptimePercentage(history)
  if (uptime >= 99) {
    return 'stat-value uptime-excellent'
  } else if (uptime >= 95) {
    return 'stat-value uptime-good'
  } else if (uptime >= 90) {
    return 'stat-value uptime-warning'
  } else {
    return 'stat-value uptime-poor'
  }
}
</script>

<template>
  <div class="monitor-card">
    <div class="monitor-header">
      <h3>{{ monitor.name }}</h3>
      <div class="monitor-status">
        <span
          :class="getStatusBadgeClass(status?.currentStatus || 'down')"
        >
          {{ status?.currentStatus || 'unknown' }}
        </span>
      </div>
    </div>

    <div class="monitor-details">
      <div class="detail-item">
        <strong>URL:</strong>
        <a :href="monitor.url" target="_blank" rel="noopener noreferrer">
          {{ monitor.url }}
        </a>
      </div>
      <div class="detail-item" v-if="isExpanded">
        <strong>Tenant:</strong>
        <span>{{ monitor.tenant?.name || 'Unknown' }}</span>
      </div>
      <div class="detail-item" v-if="isExpanded">
        <strong>State:</strong>
        <span class="state-display">{{ monitor.state }}</span>
      </div>
      <div class="detail-item" v-if="isExpanded && monitor.httpHeaders && Object.keys(monitor.httpHeaders).length > 0">
        <strong>Headers:</strong>
        <span class="headers-list">
          <span v-for="(value, key) in monitor.httpHeaders" :key="key" class="header-item" :title="`${key}: ${value}`">
            {{ key.length > 20 ? key.substring(0, 20) + '...' : key }}: {{ value.length > 30 ? value.substring(0, 30) + '...' : value }}
          </span>
        </span>
      </div>
      <div class="detail-item" v-if="isExpanded && monitor.statusCodeRegex">
        <strong>Status Code:</strong>
        <span class="criteria-code">{{ monitor.statusCodeRegex }}</span>
      </div>
      <div class="detail-item" v-if="isExpanded && monitor.responseBodyRegex">
        <strong>Body:</strong>
        <span class="criteria-code">{{ monitor.responseBodyRegex }}</span>
      </div>
      <div class="detail-item" v-if="isExpanded && monitor.prometheusKey">
        <strong>Prometheus:</strong>
        <span class="criteria-code">{{ monitor.prometheusKey }}</span>
        <span v-if="monitor.prometheusMinValue !== undefined || monitor.prometheusMaxValue !== undefined" class="prometheus-range">
          (<span v-if="monitor.prometheusMinValue !== undefined">{{ monitor.prometheusMinValue }}</span><span v-if="monitor.prometheusMinValue !== undefined && monitor.prometheusMaxValue !== undefined">-</span><span v-if="monitor.prometheusMaxValue !== undefined">{{ monitor.prometheusMaxValue }}</span>)
        </span>
      </div>
      <div class="detail-item" v-if="isExpanded">
        <strong>Alert Delay:</strong>
        <span class="criteria-value">{{ monitor.alertingThreshold }}s</span>
      </div>
    </div>

    <div v-if="status" class="monitor-stats">
      <div class="stat-item" v-if="isExpanded">
        <span class="stat-label">Last Checked:</span>
        <span class="stat-value">
          {{ formatDate(status.lastCheckedAt) }}
        </span>
      </div>
      <div class="stat-item" v-if="isExpanded">
        <span class="stat-label">Response Time:</span>
        <span class="stat-value">
          {{ status.lastResponseTimeMs }}ms
        </span>
      </div>
      <div class="stat-item" v-if="isExpanded">
        <span class="stat-label">Status Code:</span>
        <span class="stat-value">
          {{ status.lastStatusCode }}
        </span>
      </div>
      <div v-if="status.consecutiveFailures > 0 && isExpanded" class="stat-item">
        <span class="stat-label">Consecutive Failures:</span>
        <span class="stat-value failure-count">
          {{ status.consecutiveFailures }}
        </span>
      </div>
    </div>

    <ResponseTimeChart
      :data="getResponseTimeHistoryData(responseTimeHistory)"
    />

    <StatusChart
      :status-changes="getStatusChanges(responseTimeHistory)"
    />

    <div class="monitor-toggle">
      <button
        @click="$emit('toggle-expanded', monitor.id)"
        class="btn btn-icon"
        :class="isExpanded ? 'btn-collapse' : 'btn-expand'"
        :title="isExpanded ? 'Hide details' : 'Show details'"
      >
        <svg v-if="isExpanded" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="m18 15-6-6-6 6"/>
        </svg>
        <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="m6 9 6 6 6-6"/>
        </svg>
      </button>
    </div>

    <div class="monitor-actions" v-if="isExpanded">
      <button
        @click="$emit('edit', monitor)"
        class="btn btn-sm btn-primary"
      >
        Edit
      </button>
      <MonitorStateButton
        :state="monitor.state"
        @state-change="(newState) => $emit('state-change', monitor.id, newState)"
      />
      <button
        @click="$emit('delete', monitor.id)"
        class="btn btn-sm btn-danger"
      >
        Delete
      </button>
    </div>
  </div>
</template>

<style scoped>
.monitor-card {
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.monitor-header h3 {
  margin: 0;
  color: #2c3e50;
  font-size: 1.1rem;
}

.monitor-status .status-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
}

.status-up {
  background: #d4edda;
  color: #155724;
}

.status-down {
  background: #f8d7da;
  color: #721c24;
}

.monitor-details {
  margin-bottom: 1rem;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

.detail-item strong {
  color: #495057;
  min-width: 80px;
}

.detail-item a {
  color: #007bff;
  text-decoration: none;
  word-break: break-all;
}

.detail-item a:hover {
  text-decoration: underline;
}

.detail-item .active {
  color: #28a745;
  font-weight: 500;
}

.detail-item .inactive {
  color: #dc3545;
  font-weight: 500;
}

.headers-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.header-item {
  font-size: 0.8rem;
  background: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}

.criteria-code {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.8rem;
  color: #2c3e50;
}

.prometheus-range {
  font-size: 0.8rem;
  color: #6c757d;
  margin-left: 0.5rem;
}

.monitor-stats {
  background: #f8f9fa;
  padding: 1rem 0;
  border-radius: 6px;
  margin-bottom: 1rem;
  margin-left: -1.5rem;
  margin-right: -1.5rem;
  padding-left: 1.5rem;
  padding-right: 1.5rem;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.85rem;
}

.stat-item:last-child {
  margin-bottom: 0;
}

.stat-label {
  color: #6c757d;
}

.stat-value {
  font-weight: 500;
  color: #2c3e50;
}

.stat-value.failure-count {
  color: #dc3545;
  font-weight: 600;
}

.stat-value.uptime-excellent {
  color: #28a745;
  font-weight: 600;
}

.stat-value.uptime-good {
  color: #6f7e08;
  font-weight: 600;
}

.stat-value.uptime-warning {
  color: #fd7e14;
  font-weight: 600;
}

.stat-value.uptime-poor {
  color: #dc3545;
  font-weight: 600;
}

.stat-value.no-data {
  color: #6c757d;
  font-style: italic;
}

.monitor-toggle {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 0.5rem;
  padding-left: 0;
  padding-right: 0;
}

.btn-icon {
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  background: white;
  color: #6c757d;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  background: #f8f9fa;
  color: #495057;
  border-color: #adb5bd;
}

.btn-expand:hover {
  color: #007bff;
  border-color: #007bff;
}

.btn-collapse:hover {
  color: #6c757d;
  border-color: #6c757d;
}

.monitor-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 0.25rem 0.75rem;
  font-size: 0.8rem;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-success {
  background: #28a745;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: #1e7e34;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #c82333;
}

/* Mobile styles */
@media (max-width: 768px) {
  .monitor-card {
    padding: 1rem;
    overflow: hidden;
  }

  .monitor-header {
    margin-bottom: 0.75rem;
  }

  .monitor-header h3 {
    font-size: 1rem;
    margin-bottom: 0.5rem;
  }

  .monitor-status {
    margin-bottom: 0.5rem;
  }

  .detail-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.25rem;
    margin-bottom: 0.75rem;
  }

  .detail-item strong {
    min-width: 0;
  }

  .detail-item a {
    display: block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .monitor-stats {
    margin-left: -1rem;
    margin-right: -1rem;
    padding-left: 1rem;
    padding-right: 1rem;
  }

  .monitor-toggle {
    margin-top: 1rem;
    margin-bottom: 0.5rem;
  }

  .monitor-actions {
    flex-wrap: wrap;
    gap: 0.5rem;
    margin-top: 0.75rem;
  }

  .monitor-actions .btn-sm {
    flex: 1;
    min-width: 0;
    text-align: center;
  }
}
</style>

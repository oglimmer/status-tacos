<script setup lang="ts">
import { computed } from 'vue'
import { type TimeframeType } from './TimeframeSwitcher.vue'
import { type ViewModeType } from './ViewModeSwitcher.vue'
import UptimeMetrics from './UptimeMetrics.vue'
import EnhancedResponseTimeChart from './EnhancedResponseTimeChart.vue'
import EnhancedStatusChart from './EnhancedStatusChart.vue'
import ResponseTimeChart from './ResponseTimeChart.vue'
import type { MonitorResponse, UptimeStats, MonitorState, PeerResult, SilentFor } from '../stores/monitors'
import { formatSilenceEnd, formatSilenceEndLong } from '../utils/silence'
import MonitorStateButton from './MonitorStateButton.vue'
import { formatUptime, getTimeframeLabel } from '../utils/uptime'

interface MonitorStatus {
  currentStatus: 'up' | 'down'
  lastCheckedAt: string
  lastResponseTimeMs: number
  lastStatusCode: number
  lastErrorMessage?: string | null
  peerResult?: PeerResult | null
  consecutiveFailures: number
}

interface ResponseTimeHistory {
  uptimePercentage24h?: number
  totalChecks24h?: number
  dataPoints?: Array<{ maxResponseTimeMs: number }>
  statusDownPeriods?: Array<{ start: string; end: string }>
  firstCheckAt?: string
}

interface EnhancedMonitorCardProps {
  monitor: MonitorResponse
  status: MonitorStatus | null
  responseTimeHistory: ResponseTimeHistory | null
  uptimeStats7d: UptimeStats | null
  uptimeStats90d: UptimeStats | null
  selectedTimeframe: TimeframeType
  selectedViewMode: ViewModeType
  isExpanded: boolean
  isLoadingStats?: boolean
  // The monitor of the heartbeat of the other Status Tacos server
  isPeer?: boolean
  // Multi edit: the card shows a checkbox
  selectable?: boolean
  selected?: boolean
}

interface EnhancedMonitorCardEmits {
  'toggle-expanded': [id: number]
  'edit': [monitor: MonitorResponse]
  'state-change': [id: number, state: MonitorState, silentFor?: SilentFor]
  'delete': [id: number]
  'toggle-select': [id: number]
}

const props = defineProps<EnhancedMonitorCardProps>()
defineEmits<EnhancedMonitorCardEmits>()

const PEER_RESULT_TEXT: Record<PeerResult, string> = {
  CONFIRMED: 'Also DOWN for the other server',
  PEER_UP: 'Only this server sees DOWN: no alert sent',
  NO_ANSWER: 'The other server did not answer: alert sent'
}

// Silent monitors show when their alerts start again
const silentEnd = computed(() =>
  props.monitor.state === 'SILENT' && props.monitor.silentUntil ? new Date(props.monitor.silentUntil) : null
)
const silentChipText = computed(() => (silentEnd.value ? `Silent until ${formatSilenceEnd(silentEnd.value)}` : null))
const silentChipTitle = computed(() =>
  silentEnd.value ? `No alerts. Alerts start again ${formatSilenceEndLong(silentEnd.value)}.` : undefined
)

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

// Get current stats based on selected timeframe
const currentStats = computed(() => {
  switch (props.selectedTimeframe) {
    case '7d':
      return props.uptimeStats7d
    case '90d':
      return props.uptimeStats90d
    default:
      return null
  }
})

// Uptime of the selected timeframe, for the minimal view
const currentUptime = computed(() =>
  props.selectedTimeframe === '24h'
    ? props.responseTimeHistory?.uptimePercentage24h
    : currentStats.value?.uptimePercentage
)

const minimalBadgeText = computed(() =>
  currentUptime.value === undefined || currentUptime.value === null
    ? props.status?.currentStatus || 'unknown'
    : formatUptime(currentUptime.value)
)

const minimalBadgeTitle = computed(() =>
  `${(props.status?.currentStatus || 'unknown').toUpperCase()} · ${getTimeframeLabel(props.selectedTimeframe)} uptime: ${formatUptime(currentUptime.value)}`
)

const get24hResponseTimeData = (history: ResponseTimeHistory | null): number[] => {
  if (!history || !history.dataPoints || history.dataPoints.length === 0) {
    return []
  }
  return history.dataPoints.map((point: { maxResponseTimeMs: number }) => point.maxResponseTimeMs)
}

const get24hStatusDownPeriods = (history: ResponseTimeHistory | null): Array<{ start: string; end: string }> => {
  if (!history || !history.statusDownPeriods || history.statusDownPeriods.length === 0) {
    return []
  }
  return history.statusDownPeriods
}


</script>

<template>
  <div
    class="enhanced-monitor-card"
    :class="{ 'minimal-view': selectedViewMode === 'minimal', 'full-view': selectedViewMode === 'full', selectable, selected }"
    @click="selectable && $emit('toggle-select', monitor.id)"
  >
    <!-- Minimal view: only name and status -->
    <template v-if="selectedViewMode === 'minimal'">
      <div class="monitor-header minimal">
        <h3>
          <input
            v-if="selectable"
            type="checkbox"
            class="select-box"
            :checked="selected"
            :aria-label="`Select ${monitor.name}`"
            @click.stop
            @change="$emit('toggle-select', monitor.id)"
          />
          {{ monitor.name }}
          <span
            v-if="isPeer"
            class="peer-badge"
            title="Watches the other Status Tacos server. The other server checks DOWN monitors before alerts."
          >peer</span>
        </h3>
        <div class="monitor-status">
          <span v-if="silentChipText" class="silent-chip" :title="silentChipTitle">
            <i class="fas fa-bell-slash" aria-hidden="true"></i>{{ silentChipText }}
          </span>
          <span
            :class="getStatusBadgeClass(status?.currentStatus || 'down')"
            :title="minimalBadgeTitle"
          >
            {{ minimalBadgeText }}
          </span>
        </div>
      </div>
    </template>

    <!-- Default and Full views -->
    <template v-else>
      <div class="monitor-header">
        <h3>
          <input
            v-if="selectable"
            type="checkbox"
            class="select-box"
            :checked="selected"
            :aria-label="`Select ${monitor.name}`"
            @click.stop
            @change="$emit('toggle-select', monitor.id)"
          />
          {{ monitor.name }}
          <span
            v-if="isPeer"
            class="peer-badge"
            title="Watches the other Status Tacos server. The other server checks DOWN monitors before alerts."
          >peer</span>
        </h3>
        <div class="monitor-status">
          <span v-if="silentChipText" class="silent-chip" :title="silentChipTitle">
            <i class="fas fa-bell-slash" aria-hidden="true"></i>{{ silentChipText }}
          </span>
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
        <div class="detail-item" v-if="isExpanded || selectedViewMode === 'full'">
          <strong>Tenant:</strong>
          <span>{{ monitor.tenant?.name || 'Unknown' }}</span>
        </div>
        <div class="detail-item" v-if="isExpanded || selectedViewMode === 'full'">
          <strong>State:</strong>
          <span class="state-display">{{ monitor.state }}</span>
          <span v-if="silentEnd" class="state-until" :title="silentChipTitle">
            until {{ formatSilenceEndLong(silentEnd) }}
          </span>
        </div>
        <div class="detail-item" v-if="(isExpanded || selectedViewMode === 'full') && monitor.httpHeaders && Object.keys(monitor.httpHeaders).length > 0">
          <strong>Headers:</strong>
          <span class="headers-list">
            <span v-for="(value, key) in monitor.httpHeaders" :key="key" class="header-item" :title="`${key}: ${value}`">
              {{ key.length > 20 ? key.substring(0, 20) + '...' : key }}: {{ value.length > 30 ? value.substring(0, 30) + '...' : value }}
            </span>
          </span>
        </div>
        <div class="detail-item" v-if="(isExpanded || selectedViewMode === 'full') && monitor.statusCodeRegex">
          <strong>Status Code:</strong>
          <span class="criteria-code">{{ monitor.statusCodeRegex }}</span>
        </div>
        <div class="detail-item" v-if="(isExpanded || selectedViewMode === 'full') && monitor.responseBodyRegex">
          <strong>Body:</strong>
          <span class="criteria-code">{{ monitor.responseBodyRegex }}</span>
        </div>
        <div class="detail-item" v-if="(isExpanded || selectedViewMode === 'full') && monitor.prometheusKey">
          <strong>Prometheus:</strong>
          <span class="criteria-code">{{ monitor.prometheusKey }}</span>
          <span v-if="monitor.prometheusMinValue !== undefined || monitor.prometheusMaxValue !== undefined" class="prometheus-range">
            (<span v-if="monitor.prometheusMinValue !== undefined">{{ monitor.prometheusMinValue }}</span><span v-if="monitor.prometheusMinValue !== undefined && monitor.prometheusMaxValue !== undefined">-</span><span v-if="monitor.prometheusMaxValue !== undefined">{{ monitor.prometheusMaxValue }}</span>)
          </span>
        </div>
        <div class="detail-item" v-if="isExpanded || selectedViewMode === 'full'">
          <strong>Check Interval:</strong>
          <span class="criteria-value">{{ monitor.checkInterval ?? 15 }}s</span>
        </div>
        <div class="detail-item" v-if="(isExpanded || selectedViewMode === 'full') && monitor.useSystemDns">
          <strong>DNS:</strong>
          <span class="criteria-value">DNS server of the backend</span>
        </div>
        <div class="detail-item" v-if="isExpanded || selectedViewMode === 'full'">
          <strong>Alert Delay:</strong>
          <span class="criteria-value">{{ monitor.alertingThreshold }}s</span>
        </div>
      </div>

      <!-- Expanded view with detailed metrics -->
      <div v-if="isExpanded || selectedViewMode === 'full'">
        <!-- Show comprehensive metrics for 7d/90d -->
        <UptimeMetrics
          v-if="selectedTimeframe !== '24h'"
          :stats="currentStats"
          :timeframe="selectedTimeframe"
          :is-loading="isLoadingStats"
        />

        <!-- Show basic 24h stats -->
        <div v-else-if="status" class="monitor-stats">
          <div class="stat-item">
            <span class="stat-label">Last Checked:</span>
            <span class="stat-value">
              {{ formatDate(status.lastCheckedAt) }}
            </span>
          </div>
          <div class="stat-item">
            <span class="stat-label">Response Time:</span>
            <span class="stat-value">
              {{ status.lastResponseTimeMs }}ms
            </span>
          </div>
          <div class="stat-item">
            <span class="stat-label">Status Code:</span>
            <span v-if="status.lastStatusCode != null" class="stat-value">
              {{ status.lastStatusCode }}
            </span>
            <span v-else-if="status.lastErrorMessage" class="stat-value stat-error">
              {{ status.lastErrorMessage }}
            </span>
          </div>
          <div v-if="status.currentStatus === 'down' && status.peerResult" class="stat-item">
            <span class="stat-label">Other Server:</span>
            <span
              class="stat-value"
              :class="{ 'peer-only-here': status.peerResult === 'PEER_UP' }"
            >
              {{ PEER_RESULT_TEXT[status.peerResult] }}
            </span>
          </div>
          <div v-if="status.consecutiveFailures > 0" class="stat-item">
            <span class="stat-label">Consecutive Failures:</span>
            <span class="stat-value failure-count">
              {{ status.consecutiveFailures }}
            </span>
          </div>
        </div>
      </div>

      <!-- Charts always shown regardless of expanded state -->
      <template v-if="selectedTimeframe === '24h'">
        <ResponseTimeChart
          :data="get24hResponseTimeData(responseTimeHistory)"
        />
        <EnhancedStatusChart
          :status-down-periods="get24hStatusDownPeriods(responseTimeHistory)"
          :uptime-percentage="responseTimeHistory?.uptimePercentage24h"
          :first-check-at="responseTimeHistory?.firstCheckAt"
          :total-checks="responseTimeHistory?.totalChecks24h"
          :timeframe="'24h'"
        />
      </template>

      <template v-else-if="currentStats">
        <EnhancedResponseTimeChart
          :data="currentStats.responseTimeDataPoints"
          :timeframe="selectedTimeframe"
          :window-start="currentStats.periodStart"
          :window-end="currentStats.periodEnd"
        />
        <EnhancedStatusChart
          :status-down-periods="currentStats.statusDownPeriods"
          :uptime-percentage="currentStats.uptimePercentage"
          :first-check-at="currentStats.firstCheckAt"
          :total-checks="currentStats.totalChecks"
          :timeframe="selectedTimeframe"
          :window-start="currentStats.periodStart"
          :window-end="currentStats.periodEnd"
        />
      </template>

      <div class="monitor-toggle" v-if="selectedViewMode === 'default'">
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

      <div class="monitor-actions" v-if="isExpanded || selectedViewMode === 'full'">
        <button
          @click="$emit('edit', monitor)"
          class="btn btn-sm btn-primary"
        >
          Edit
        </button>
        <MonitorStateButton
          :state="monitor.state"
          :silent-until="monitor.silentUntil"
          @state-change="(newState, silentFor) => $emit('state-change', monitor.id, newState, silentFor)"
        />
        <button
          @click="$emit('delete', monitor.id)"
          class="btn btn-sm btn-danger"
        >
          Delete
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.silent-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  margin-right: 0.5rem;
  padding: 0.125rem 0.5rem;
  border: 1px solid var(--warning-soft-border);
  border-radius: 999px;
  background: var(--warning-soft);
  color: var(--warning-soft-text);
  font-size: 0.75rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.state-until {
  margin-left: 0.375rem;
  color: var(--text-muted);
}

.enhanced-monitor-card.selectable {
  cursor: pointer;
  user-select: none;
}

.enhanced-monitor-card.selectable :deep(a),
.enhanced-monitor-card.selectable :deep(button) {
  pointer-events: none;
}

.enhanced-monitor-card.selected {
  border-color: var(--accent) !important;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.35) !important;
  background: var(--surface-2);
}

.select-box {
  width: 1.05rem;
  height: 1.05rem;
  margin: 0 0.5rem 0 0;
  vertical-align: -0.15rem;
  cursor: pointer;
  accent-color: var(--accent);
}

.enhanced-monitor-card {
  background: var(--surface);
  border-radius: 8px;
  border: 1px solid var(--border-subtle);
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.enhanced-monitor-card.minimal-view {
  padding: 0.75rem;
  margin-bottom: 0.5rem;
}

.enhanced-monitor-card.full-view {
  padding: 2rem;
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.monitor-header.minimal {
  margin-bottom: 0;
  align-items: center;
}

.monitor-header h3 {
  margin: 0;
  color: var(--text);
  font-size: 1.1rem;
}

.minimal-view .monitor-header h3 {
  font-size: 0.95rem;
  font-weight: 500;
}

.monitor-status .status-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
}

.minimal-view .monitor-status .status-badge {
  padding: 0.2rem 0.4rem;
  font-size: 0.7rem;
}

.status-up {
  background: var(--success-soft);
  color: var(--success-soft-text);
}

.status-down {
  background: var(--danger-soft);
  color: var(--danger-soft-text);
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
  color: var(--text-2);
  min-width: 80px;
}

.detail-item a {
  color: var(--link);
  text-decoration: none;
  word-break: break-all;
}

.detail-item a:hover {
  text-decoration: underline;
}

.detail-item .active {
  color: var(--success-text);
  font-weight: 500;
}

.detail-item .inactive {
  color: var(--danger-text);
  font-weight: 500;
}

.headers-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.header-item {
  font-size: 0.8rem;
  background: var(--surface-2);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  border: 1px solid var(--border-subtle);
}

.criteria-code {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 0.8rem;
  color: var(--text);
}

.prometheus-range {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-left: 0.5rem;
}

.monitor-stats,
.monitor-stats-compact {
  background: var(--surface-2);
  padding: 1rem 0;
  border-radius: 6px;
  margin-bottom: 1rem;
  margin-left: -1.5rem;
  margin-right: -1.5rem;
  padding-left: 1.5rem;
  padding-right: 1.5rem;
}

.monitor-stats-compact {
  padding: 0.75rem 0;
  margin-bottom: 0.5rem;
  margin-left: 0;
  margin-right: 0;
  padding-left: 0;
  padding-right: 0;
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
  color: var(--text-muted);
}

.stat-value {
  font-weight: 500;
  color: var(--text);
}

.peer-badge {
  margin-left: 0.4rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: var(--info-soft);
  color: var(--info-soft-text);
  font-size: 0.7rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  vertical-align: middle;
}

.stat-value.peer-only-here {
  color: var(--warning-soft-text);
  font-weight: 600;
}

.stat-value.stat-error {
  color: var(--danger-text);
  overflow-wrap: anywhere;
  text-align: right;
  margin-left: 1rem;
}

.stat-value.failure-count {
  color: var(--danger-text);
  font-weight: 600;
}

.stat-value.uptime-excellent {
  color: var(--success-text);
  font-weight: 600;
}

.stat-value.uptime-good {
  color: var(--olive-text);
  font-weight: 600;
}

.stat-value.uptime-warning {
  color: #fd7e14;
  font-weight: 600;
}

.stat-value.uptime-poor {
  color: var(--danger-text);
  font-weight: 600;
}

.stat-value.no-data {
  color: var(--text-muted);
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
  border: 1px solid var(--border);
  border-radius: 4px;
  background: var(--surface);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-icon:hover {
  background: var(--surface-2);
  color: var(--text-2);
  border-color: var(--input-border);
}

.btn-expand:hover {
  color: var(--link);
  border-color: var(--accent);
}

.btn-collapse:hover {
  color: var(--text-muted);
  border-color: var(--secondary);
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
  background: var(--accent);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: var(--accent-hover);
}

.btn-secondary {
  background: var(--secondary);
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: var(--secondary-hover);
}

.btn-success {
  background: var(--success);
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: var(--success-hover);
}

.btn-danger {
  background: var(--danger);
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: var(--danger-hover);
}

/* Mobile styles */
@media (max-width: 768px) {
  .enhanced-monitor-card {
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

  .monitor-stats-compact {
    margin-left: 0;
    margin-right: 0;
    padding-left: 0;
    padding-right: 0;
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

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMonitorsStore } from '../stores/monitors'
import EnhancedMonitorCard from '../components/EnhancedMonitorCard.vue'
import AppShell from '../components/AppShell.vue'
import PageHeader from '../components/PageHeader.vue'
import type { ResponseTimeHistory, UptimeStats } from '../stores/monitors'

const monitorsStore = useMonitorsStore()
const selectedMonitorId = ref<number | null>(null)
const isRefreshing = ref(false)
const selectedTimeframe = ref<'24h' | '7d' | '90d'>('24h')

// Get all data from store
const monitors = computed(() => monitorsStore.monitors)
const monitorStatuses = computed(() => monitorsStore.monitorStatuses)
const isLoading = computed(() => monitorsStore.isLoading)
const error = computed(() => monitorsStore.error)

// Get selected monitor data
const selectedMonitor = computed(() => {
  if (!selectedMonitorId.value) return null
  return monitors.value.find(m => m.id === selectedMonitorId.value) || null
})

const selectedMonitorStatus = computed(() => {
  if (!selectedMonitorId.value) return null
  return monitorStatuses.value.find(ms => ms.monitorId === selectedMonitorId.value) || null
})

const selectedMonitorHistory = ref<ResponseTimeHistory | null>(null)
const selectedMonitorStats7d = ref<UptimeStats | null>(null)
const selectedMonitorStats90d = ref<UptimeStats | null>(null)

// Load initial data
const loadInitialData = async () => {
  await Promise.all([
    monitorsStore.fetchMonitors(),
    monitorsStore.fetchMonitorStatuses()
  ])
}

// Load monitor-specific data when a monitor is selected
const loadMonitorData = async (monitorId: number) => {
  selectedMonitorHistory.value = null
  selectedMonitorStats7d.value = null
  selectedMonitorStats90d.value = null

  const [history, stats] = await Promise.all([
    monitorsStore.fetchResponseTimeHistory(monitorId),
    monitorsStore.fetchAllUptimeStats(monitorId)
  ])

  selectedMonitorHistory.value = history
  selectedMonitorStats7d.value = stats['7d']
  selectedMonitorStats90d.value = stats['90d']
}

// Handle monitor selection
const selectMonitor = async (monitorId: number) => {
  selectedMonitorId.value = monitorId
  await loadMonitorData(monitorId)
}

// Manual refresh function
const refreshData = async () => {
  isRefreshing.value = true
  try {
    await loadInitialData()
    if (selectedMonitorId.value) {
      await loadMonitorData(selectedMonitorId.value)
    }
  } finally {
    isRefreshing.value = false
  }
}

// Format JSON for display
const formatJson = (obj: unknown) => {
  return JSON.stringify(obj, null, 2)
}

// Raw data tables
const rawMonitorData = computed(() => {
  if (!selectedMonitor.value) return null
  return {
    monitor: selectedMonitor.value,
    status: selectedMonitorStatus.value,
    responseTimeHistory: selectedMonitorHistory.value,
    uptimeStats7d: selectedMonitorStats7d.value,
    uptimeStats90d: selectedMonitorStats90d.value
  }
})

onMounted(() => {
  loadInitialData()
})

// The global badge for a monitor state and for a check status
const stateBadge = (state: string) =>
  state === 'ACTIVE' ? 'badge-up' : state === 'SILENT' ? 'badge-warning' : 'badge-neutral'
const statusBadge = (status?: string) =>
  status === 'up' ? 'badge-up' : status === 'down' ? 'badge-down' : 'badge-neutral'
</script>

<template>
  <AppShell>
    <PageHeader title="Monitor debug" subtitle="Internal page: one monitor card with its raw backend data.">
      <template #actions>
        <button
          @click="refreshData"
          class="btn btn-primary"
          :disabled="isRefreshing"
        >
          {{ isRefreshing ? 'Refreshing...' : 'Refresh data' }}
        </button>
      </template>
    </PageHeader>

    <!-- Monitor List Section -->
    <section class="page-section">
      <h2 class="page-section-title">Monitors <small v-if="monitors.length" class="num">{{ monitors.length }}</small></h2>
      <p v-if="isLoading" class="muted">Loading monitors...</p>
      <p v-else-if="error" class="callout callout-danger">Error: {{ error }}</p>
      <p v-else-if="monitors.length === 0" class="muted">No monitors found</p>
      <div v-else class="monitor-grid">
        <button
          v-for="monitor in monitors"
          :key="monitor.id"
          type="button"
          class="monitor-item"
          :class="{ 'selected': selectedMonitorId === monitor.id }"
          :aria-pressed="selectedMonitorId === monitor.id"
          @click="selectMonitor(monitor.id)"
        >
          <span class="monitor-name">{{ monitor.name }}</span>
          <span class="monitor-url mono">{{ monitor.url }}</span>
          <span class="monitor-tenant">{{ monitor.tenant?.name || 'Unknown Tenant' }}</span>
          <span class="monitor-status-info">
            <span class="badge" :class="stateBadge(monitor.state)">
              {{ monitor.state }}
            </span>
            <span v-if="monitorStatuses.find(ms => ms.monitorId === monitor.id)"
                  class="badge"
                  :class="statusBadge(monitorStatuses.find(ms => ms.monitorId === monitor.id)?.currentStatus)">
              {{ monitorStatuses.find(ms => ms.monitorId === monitor.id)?.currentStatus || 'unknown' }}
            </span>
          </span>
        </button>
      </div>
    </section>

    <!-- Selected Monitor Tile -->
    <section class="page-section" v-if="selectedMonitor">
      <h2 class="page-section-title">Card <small>{{ selectedMonitor.name }}</small></h2>

      <div class="toolbar timeframe">
        <span class="field-label">Timeframe</span>
        <div class="button-group" role="group" aria-label="Timeframe">
          <button
            v-for="timeframe in (['24h', '7d', '90d'] as const)"
            :key="timeframe"
            type="button"
            class="btn"
            :aria-pressed="selectedTimeframe === timeframe"
            @click="selectedTimeframe = timeframe"
          >
            {{ timeframe }}
          </button>
        </div>
      </div>

      <EnhancedMonitorCard
        :monitor="selectedMonitor"
        :status="selectedMonitorStatus"
        :response-time-history="selectedMonitorHistory"
        :uptime-stats7d="selectedMonitorStats7d"
        :uptime-stats90d="selectedMonitorStats90d"
        :selected-timeframe="selectedTimeframe"
        :selected-view-mode="'full'"
        :is-expanded="true"
        @toggle-expanded="() => {}"
        @edit="() => {}"
        @state-change="() => {}"
        @delete="() => {}"
      />
    </section>

    <!-- Raw Data Tables -->
    <section class="page-section" v-if="rawMonitorData">
      <h2 class="page-section-title">Raw backend data</h2>

      <div class="data-tables">
        <div class="card data-table">
          <h3>Monitor</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.monitor) }}</pre>
        </div>

        <div class="card data-table" v-if="rawMonitorData.status">
          <h3>Status</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.status) }}</pre>
        </div>

        <div class="card data-table" v-if="rawMonitorData.responseTimeHistory">
          <h3>Response time history (24h)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.responseTimeHistory) }}</pre>
        </div>

        <div class="card data-table" v-if="rawMonitorData.uptimeStats7d">
          <h3>Uptime stats (7 days)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.uptimeStats7d) }}</pre>
        </div>

        <div class="card data-table" v-if="rawMonitorData.uptimeStats90d">
          <h3>Uptime stats (90 days)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.uptimeStats90d) }}</pre>
        </div>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.monitor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 18rem), 1fr));
  gap: var(--space-2);
}

.monitor-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-1);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.monitor-item:hover {
  border-color: var(--input-border);
}

.monitor-item.selected {
  border-color: var(--link);
  box-shadow: inset 0 0 0 1px var(--link);
}

.monitor-name {
  font-weight: 700;
}

.monitor-url {
  color: var(--text-2);
  font-size: var(--text-sm);
  word-break: break-all;
}

.monitor-tenant {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.monitor-status-info {
  display: flex;
  gap: var(--space-2);
  margin-top: var(--space-1);
}

.timeframe {
  margin-bottom: var(--space-3);
}

.data-tables {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 24rem), 1fr));
  gap: var(--space-4);
}

.data-table h3 {
  margin: 0 0 var(--space-2);
  font-size: var(--text-md);
}

.code-block {
  margin: 0;
  max-height: 400px;
  padding: var(--space-3);
  overflow: auto;
  background: var(--surface-2);
  border-radius: var(--radius-md);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
}
</style>

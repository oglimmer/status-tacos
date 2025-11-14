<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useMonitorsStore } from '../stores/monitors'
import EnhancedMonitorCard from '../components/EnhancedMonitorCard.vue'
import type { ResponseTimeHistory, ParsedUptimeStats } from '../stores/monitors'

const monitorsStore = useMonitorsStore()
const selectedMonitorId = ref<number | null>(null)
const isRefreshing = ref(false)
const selectedTimeframe = ref<'24h' | '7d' | '90d' | '365d'>('24h')

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
const selectedMonitorStats7d = ref<ParsedUptimeStats | null>(null)
const selectedMonitorStats90d = ref<ParsedUptimeStats | null>(null)
const selectedMonitorStats365d = ref<ParsedUptimeStats | null>(null)

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
  selectedMonitorStats365d.value = null

  const [history, stats] = await Promise.all([
    monitorsStore.fetchResponseTimeHistory(monitorId),
    monitorsStore.fetchAllUptimeStats(monitorId)
  ])

  selectedMonitorHistory.value = history
  selectedMonitorStats7d.value = stats?.['7d'] ?? null
  selectedMonitorStats90d.value = stats?.['90d'] ?? null
  selectedMonitorStats365d.value = stats?.['365d'] ?? null
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
    uptimeStats90d: selectedMonitorStats90d.value,
    uptimeStats365d: selectedMonitorStats365d.value
  }
})

onMounted(() => {
  loadInitialData()
})
</script>

<template>
  <div class="debug-container">
    <h1>Monitor Debug Page</h1>

    <!-- Monitor List Section -->
    <div class="debug-section">
      <h2>Available Monitors</h2>
      <div class="monitor-list">
        <div v-if="isLoading" class="loading">Loading monitors...</div>
        <div v-else-if="error" class="error">Error: {{ error }}</div>
        <div v-else-if="monitors.length === 0" class="no-data">No monitors found</div>
        <div v-else class="monitor-grid">
          <div
            v-for="monitor in monitors"
            :key="monitor.id"
            class="monitor-item"
            :class="{ 'selected': selectedMonitorId === monitor.id }"
            @click="selectMonitor(monitor.id)"
          >
            <div class="monitor-basic-info">
              <h3>{{ monitor.name }}</h3>
              <p class="monitor-url">{{ monitor.url }}</p>
              <p class="monitor-tenant">{{ monitor.tenant?.name || 'Unknown Tenant' }}</p>
              <div class="monitor-status-info">
                <span class="status-badge" :class="monitor.state.toLowerCase()">
                  {{ monitor.state }}
                </span>
                <span v-if="monitorStatuses.find(ms => ms.monitorId === monitor.id)"
                      class="status-badge"
                      :class="monitorStatuses.find(ms => ms.monitorId === monitor.id)?.currentStatus || 'unknown'">
                  {{ monitorStatuses.find(ms => ms.monitorId === monitor.id)?.currentStatus || 'unknown' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Selected Monitor Tile -->
    <div class="debug-section" v-if="selectedMonitor">
      <h2>Monitor Tile ({{ selectedMonitor.name }})</h2>

      <!-- Timeframe Selector -->
      <div class="timeframe-selector">
        <h3>Timeframe</h3>
        <div class="timeframe-buttons">
          <button
            v-for="timeframe in (['24h', '7d', '90d', '365d'] as const)"
            :key="timeframe"
            class="timeframe-btn"
            :class="{ active: selectedTimeframe === timeframe }"
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
        :uptime-stats365d="selectedMonitorStats365d"
        :selected-timeframe="selectedTimeframe"
        :selected-view-mode="'full'"
        :is-expanded="true"
        @toggle-expanded="() => {}"
        @edit="() => {}"
        @state-change="() => {}"
        @delete="() => {}"
      />
    </div>

    <!-- Raw Data Tables -->
    <div class="debug-section" v-if="rawMonitorData">
      <h2>Raw Backend Data</h2>

      <div class="data-tables">
        <div class="data-table">
          <h3>Monitor Data</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.monitor) }}</pre>
        </div>

        <div class="data-table" v-if="rawMonitorData.status">
          <h3>Monitor Status</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.status) }}</pre>
        </div>

        <div class="data-table" v-if="rawMonitorData.responseTimeHistory">
          <h3>Response Time History (24h)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.responseTimeHistory) }}</pre>
        </div>

        <div class="data-table" v-if="rawMonitorData.uptimeStats7d">
          <h3>Uptime Stats (7 days)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.uptimeStats7d) }}</pre>
        </div>

        <div class="data-table" v-if="rawMonitorData.uptimeStats90d">
          <h3>Uptime Stats (90 days)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.uptimeStats90d) }}</pre>
        </div>

        <div class="data-table" v-if="rawMonitorData.uptimeStats365d">
          <h3>Uptime Stats (365 days)</h3>
          <pre class="code-block">{{ formatJson(rawMonitorData.uptimeStats365d) }}</pre>
        </div>
      </div>
    </div>

    <!-- Actions -->
    <div class="debug-section">
      <h2>Actions</h2>
      <div class="actions">
        <button
          @click="refreshData"
          class="btn btn-primary"
          :disabled="isRefreshing"
        >
          {{ isRefreshing ? 'Refreshing...' : 'Refresh Data' }}
        </button>
        <router-link to="/" class="btn btn-secondary">
          Back to Dashboard
        </router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.debug-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 2rem;
}

h1 {
  color: #2c3e50;
  margin-bottom: 2rem;
}

.debug-section {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
}

.debug-section h2 {
  color: #495057;
  margin-bottom: 1rem;
  font-size: 1.5rem;
}

.debug-section h3 {
  color: #6c757d;
  margin-bottom: 0.5rem;
  font-size: 1.2rem;
}

.loading, .error, .no-data {
  text-align: center;
  padding: 2rem;
  color: #6c757d;
}

.error {
  color: #dc3545;
}

.monitor-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}

.monitor-item {
  background: white;
  border: 2px solid #dee2e6;
  border-radius: 8px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.monitor-item:hover {
  border-color: #007bff;
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.1);
}

.monitor-item.selected {
  border-color: #007bff;
  background: #f8f9ff;
  box-shadow: 0 2px 8px rgba(0, 123, 255, 0.2);
}

.monitor-basic-info h3 {
  margin: 0 0 0.5rem 0;
  color: #2c3e50;
  font-size: 1.1rem;
}

.monitor-url {
  color: #007bff;
  font-size: 0.9rem;
  margin: 0.25rem 0;
  word-break: break-all;
}

.monitor-tenant {
  color: #6c757d;
  font-size: 0.85rem;
  margin: 0.25rem 0;
}

.monitor-status-info {
  margin-top: 0.5rem;
  display: flex;
  gap: 0.5rem;
}

.status-badge {
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
}

.status-badge.active {
  background: #d4edda;
  color: #155724;
}

.status-badge.silent {
  background: #fff3cd;
  color: #856404;
}

.status-badge.inactive {
  background: #f8d7da;
  color: #721c24;
}

.status-badge.up {
  background: #d4edda;
  color: #155724;
}

.status-badge.down {
  background: #f8d7da;
  color: #721c24;
}

.status-badge.unknown {
  background: #e2e3e5;
  color: #6c757d;
}

.data-tables {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 1.5rem;
}

.data-table {
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  padding: 1rem;
}

.code-block {
  background: #2c3e50;
  color: #ecf0f1;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.8rem;
  max-height: 400px;
  overflow-y: auto;
}

.actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
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

.timeframe-selector {
  margin-bottom: 1.5rem;
}

.timeframe-selector h3 {
  margin-bottom: 0.75rem;
  color: #495057;
  font-size: 1.1rem;
}

.timeframe-buttons {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.timeframe-btn {
  padding: 0.5rem 1rem;
  border: 2px solid #dee2e6;
  border-radius: 6px;
  background: white;
  color: #6c757d;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.timeframe-btn:hover {
  border-color: #007bff;
  color: #007bff;
  background: #f8f9ff;
}

.timeframe-btn.active {
  border-color: #007bff;
  background: #007bff;
  color: white;
  box-shadow: 0 2px 4px rgba(0, 123, 255, 0.2);
}

/* Mobile responsive */
@media (max-width: 768px) {
  .debug-container {
    padding: 1rem;
  }

  .monitor-grid {
    grid-template-columns: 1fr;
  }

  .data-tables {
    grid-template-columns: 1fr;
  }

  .code-block {
    font-size: 0.7rem;
  }
}
</style>

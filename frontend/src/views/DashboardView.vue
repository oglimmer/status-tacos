<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useMonitorsStore, type MonitorResponse, type MonitorState } from '../stores/monitors'
import MonitorForm from '../components/MonitorForm.vue'
import MonitorEditForm from '../components/MonitorEditForm.vue'
import DashboardHeader from '../components/DashboardHeader.vue'
import PageNavigation from '../components/PageNavigation.vue'
import MonitorsHeader from '../components/MonitorsHeader.vue'
import DashboardSummary from '../components/DashboardSummary.vue'
import EnhancedMonitorCard from '../components/EnhancedMonitorCard.vue'
import type { TimeframeType } from '../components/TimeframeSwitcher.vue'
import type { ViewModeType } from '../components/ViewModeSwitcher.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import type { CurrentUser } from '../services/api'
import { userConfigService } from '../services/userConfig'

const authStore = useAuthStore()
const monitorsStore = useMonitorsStore()

const showAddMonitorForm = ref(false)
const showEditMonitorForm = ref(false)
const editingMonitor = ref<MonitorResponse | null>(null)
const expandedMonitors = ref(new Set<number>())
const currentUser = ref<CurrentUser | null>(null)
const selectedTenantId = ref<number | null>(userConfigService.getMonitorTenantFilter())
const selectedStatusFilter = ref<'all' | 'up' | 'down' | 'active'>('all')
const selectedNameFilter = ref<string>('')
const selectedTimeframe = ref<TimeframeType>('24h')
const selectedViewMode = ref<ViewModeType>('default')
const loadingUptimeStats = ref(new Set<number>())
let refreshInterval: ReturnType<typeof setInterval> | null = null

const filteredMonitors = computed(() => {
  let monitors = selectedTenantId.value
    ? monitorsStore.getMonitorsByTenant(selectedTenantId.value)
    : monitorsStore.monitors

  // Apply status filter
  if (selectedStatusFilter.value !== 'all') {
    if (selectedStatusFilter.value === 'up') {
      const upMonitorIds = new Set(monitorsStore.upMonitors.map(s => s.monitorId))
      monitors = monitors.filter(m => upMonitorIds.has(m.id))
    } else if (selectedStatusFilter.value === 'down') {
      const downMonitorIds = new Set(monitorsStore.downMonitors.map(s => s.monitorId))
      monitors = monitors.filter(m => downMonitorIds.has(m.id))
    } else if (selectedStatusFilter.value === 'active') {
      monitors = monitors.filter(m => m.state === 'ACTIVE')
    }
  }

  // Apply name filter
  if (selectedNameFilter.value.trim()) {
    monitors = monitors.filter(m =>
      m.name.toLowerCase().includes(selectedNameFilter.value.toLowerCase().trim())
    )
  }

  return monitors
})

const toggleMonitorExpanded = async (monitorId: number) => {
  if (expandedMonitors.value.has(monitorId)) {
    expandedMonitors.value.delete(monitorId)
  } else {
    expandedMonitors.value.add(monitorId)
    // Fetch uptime stats when expanding, based on current timeframe
    if (selectedTimeframe.value !== '24h') {
      const periodType = selectedTimeframe.value === '7d' ? 'seven_days' :
                        selectedTimeframe.value === '90d' ? 'ninety_days' :
                        'three_sixty_five_days'

      const existing = monitorsStore.getUptimeStatsById(monitorId, periodType)
      if (!existing) {
        loadingUptimeStats.value.add(monitorId)
        try {
          await monitorsStore.fetchUptimeStats(monitorId, periodType)
        } catch (error) {
          console.error('Failed to fetch uptime stats for monitor', monitorId, error)
        } finally {
          loadingUptimeStats.value.delete(monitorId)
        }
      }
    }
  }
}


const fetchUptimeStatsForTimeframe = async (timeframe: TimeframeType) => {
  if (timeframe === '24h') return

  const periodType = timeframe === '7d' ? 'seven_days' :
                    timeframe === '90d' ? 'ninety_days' :
                    'three_sixty_five_days'

  // Fetch for all visible monitors since charts are always shown
  const visibleMonitorIds = filteredMonitors.value.map(m => m.id)

  for (const monitorId of visibleMonitorIds) {
    const existing = monitorsStore.getUptimeStatsById(monitorId, periodType)
    if (!existing) {
      loadingUptimeStats.value.add(monitorId)
      try {
        await monitorsStore.fetchUptimeStats(monitorId, periodType)
      } catch (error) {
        console.error('Failed to fetch uptime stats for timeframe', timeframe, 'monitor', monitorId, error)
      } finally {
        loadingUptimeStats.value.delete(monitorId)
      }
    }
  }
}

const isMonitorExpanded = (monitorId: number): boolean => {
  return expandedMonitors.value.has(monitorId) || selectedViewMode.value === 'full'
}

const openAddMonitorForm = () => {
  showAddMonitorForm.value = true
}

const closeAddMonitorForm = () => {
  showAddMonitorForm.value = false
}

const openEditModal = (monitor: MonitorResponse) => {
  editingMonitor.value = monitor
  showEditMonitorForm.value = true
}

const closeEditMonitorForm = () => {
  showEditMonitorForm.value = false
  editingMonitor.value = null
}

const handleMonitorCreated = async () => {
  await monitorsStore.fetchMonitors()
  await monitorsStore.fetchMonitorStatuses()

  // Fetch response time history for all monitors
  const monitorIds = monitorsStore.monitors.map(m => m.id)
  await Promise.all(
    monitorIds.map(id => monitorsStore.fetchResponseTimeHistory(id))
  )
}

const handleMonitorUpdated = async () => {
  await monitorsStore.fetchMonitors()
  await monitorsStore.fetchMonitorStatuses()

  // Fetch response time history for all monitors
  const monitorIds = monitorsStore.monitors.map(m => m.id)
  await Promise.all(
    monitorIds.map(id => monitorsStore.fetchResponseTimeHistory(id))
  )
}

const handleMonitorDelete = async (id: number) => {
  const monitor = monitorsStore.getMonitorById(id)
  const monitorName = monitor?.name || 'this monitor'

  if (confirm(`Are you sure you want to delete ${monitorName}? This action cannot be undone.`)) {
    try {
      await monitorsStore.deleteMonitor(id)
    } catch (err) {
      console.error('Failed to delete monitor:', err)
    }
  }
}

const handleStateChange = async (id: number, newState: MonitorState) => {
  try {
    await monitorsStore.updateMonitorState(id, newState)
    // Refresh monitor statuses to immediately show current UP/DOWN status
    await monitorsStore.fetchMonitorStatusesSilently()
  } catch (err) {
    console.error('Failed to update monitor state:', err)
  }
}

const refreshMonitorData = async () => {
  try {
    await Promise.all([
      monitorsStore.fetchMonitorsSilently(),
      monitorsStore.fetchMonitorStatusesSilently()
    ])

    // Fetch response time history for all monitors
    const monitorIds = monitorsStore.monitors.map(m => m.id)
    await Promise.all(
      monitorIds.map(id => monitorsStore.fetchResponseTimeHistory(id))
    )
  } catch (error) {
    console.error('Error refreshing monitor data:', error)
  }
}

// Summary data computed property
const summaryData = computed(() => {
  const filtered = filteredMonitors.value
  const filteredIds = new Set(filtered.map(m => m.id))

  return {
    total: filtered.length,
    up: monitorsStore.upMonitors.filter(m => filteredIds.has(m.monitorId)).length,
    down: monitorsStore.downMonitors.filter(m => filteredIds.has(m.monitorId)).length,
    active: filtered.filter(m => m.state === 'ACTIVE').length
  }
})

onMounted(async () => {
  try {
    // Fetch current user for tenant filtering
    const userData = await authStore.fetchCurrentUser()
    currentUser.value = userData

    // Set default tenant filter if not already set
    if (!selectedTenantId.value && userData.tenants.length > 0) {
      selectedTenantId.value = null // Default to 'All Tenants'
    }
  } catch (err) {
    console.error('Failed to load user data:', err)
  }

  // Initial load
  await refreshMonitorData()

  // Set up 5-second refresh interval
  refreshInterval = setInterval(refreshMonitorData, 5000)
})

// Watch for changes to selectedTenantId and save to localStorage
watch(selectedTenantId, (newValue) => {
  userConfigService.setMonitorTenantFilter(newValue)
})

// Watch for changes to selectedTimeframe and fetch data
watch(selectedTimeframe, async (newTimeframe) => {
  await fetchUptimeStatsForTimeframe(newTimeframe)
})

onUnmounted(() => {
  // Clean up interval when component is unmounted
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
})
</script>

<template>
  <div class="dashboard-container">
    <DashboardHeader />

    <main class="dashboard-main">
      <PageNavigation />

      <div class="monitors-content">
        <MonitorsHeader
          :selected-timeframe="selectedTimeframe"
          :selected-view-mode="selectedViewMode"
          @add-monitor="openAddMonitorForm"
          @update:selected-timeframe="selectedTimeframe = $event"
          @update:selected-view-mode="selectedViewMode = $event"
        />

        <DashboardSummary
          v-if="!monitorsStore.isLoading && !monitorsStore.error && monitorsStore.monitors.length > 0"
          :summary="summaryData"
          :tenants="currentUser?.tenants"
          :selected-tenant-id="selectedTenantId"
          :selected-status-filter="selectedStatusFilter"
          :selected-name-filter="selectedNameFilter"
          @update:selected-tenant-id="selectedTenantId = $event"
          @update:selected-status-filter="selectedStatusFilter = $event"
          @update:selected-name-filter="selectedNameFilter = $event"
        />

        <LoadingState
          v-if="monitorsStore.isLoading"
          message="Loading monitors..."
        />

        <ErrorState
          v-else-if="monitorsStore.error"
          :error="monitorsStore.error"
        />

        <EmptyState
          v-else-if="monitorsStore.monitors.length === 0"
          title="No monitors yet"
          message="Get started by adding your first monitor to track the status of your endpoints."
          button-text="Add Your First Monitor"
          @action="openAddMonitorForm"
        />

        <div v-else class="monitors-grid" :class="{ 'minimal-grid': selectedViewMode === 'minimal' }">
          <EnhancedMonitorCard
            v-for="monitor in filteredMonitors"
            :key="monitor.id"
            :monitor="monitor"
            :status="monitorsStore.getMonitorStatusById(monitor.id) || null"
            :response-time-history="monitorsStore.getResponseTimeHistoryById(monitor.id) || null"
            :uptime-stats7d="monitorsStore.getUptimeStatsById(monitor.id, 'seven_days') || null"
            :uptime-stats90d="monitorsStore.getUptimeStatsById(monitor.id, 'ninety_days') || null"
            :uptime-stats365d="monitorsStore.getUptimeStatsById(monitor.id, 'three_sixty_five_days') || null"
            :selected-timeframe="selectedTimeframe"
            :selected-view-mode="selectedViewMode"
            :is-expanded="isMonitorExpanded(monitor.id)"
            :is-loading-stats="loadingUptimeStats.has(monitor.id)"
            @toggle-expanded="toggleMonitorExpanded"
            @edit="openEditModal"
            @state-change="handleStateChange"
            @delete="handleMonitorDelete"
          />
        </div>
      </div>
    </main>

    <MonitorForm
      v-if="showAddMonitorForm"
      :preselected-tenant-id="selectedTenantId"
      @close="closeAddMonitorForm"
      @success="handleMonitorCreated"
    />

    <MonitorEditForm
      v-if="showEditMonitorForm"
      :monitor="editingMonitor"
      @close="closeEditMonitorForm"
      @success="handleMonitorUpdated"
    />
  </div>
</template>

<style scoped>
.dashboard-container {
  min-height: 100vh;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 50%, #dee2e6 100%);
  background-attachment: fixed;
}

.dashboard-main {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  margin-top: 2rem;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.monitors-content {
  min-height: 400px;
}

.monitors-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 1.5rem;
  margin-bottom: 2rem;
}

.monitors-grid.minimal-grid {
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 0.5rem;
}

/* Mobile styles */
@media (max-width: 768px) {
  .dashboard-main {
    padding: 1rem;
    margin: 0;
    border-radius: 0;
    border: none;
    background: transparent;
    backdrop-filter: none;
    box-shadow: none;
  }

  .monitors-grid {
    grid-template-columns: 1fr !important;
    gap: 1rem;
    margin-bottom: 1rem;
  }
}
</style>

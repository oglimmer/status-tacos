<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { askConfirm } from '../services/confirm'
import { useAuthStore } from '../stores/auth'
import { useMonitorsStore, isPeerMonitor, type MonitorResponse, type MonitorState, type SilentFor } from '../stores/monitors'
import MonitorForm from '../components/MonitorForm.vue'
import MonitorEditForm from '../components/MonitorEditForm.vue'
import AppShell from '../components/AppShell.vue'
import PageHeader from '../components/PageHeader.vue'
import MonitorLimitInfo from '../components/MonitorLimitInfo.vue'
import PeerSetupWarning from '../components/PeerSetupWarning.vue'
import DashboardSummary from '../components/DashboardSummary.vue'
import EnhancedMonitorCard from '../components/EnhancedMonitorCard.vue'
import BulkEditMonitorsModal from '../components/BulkEditMonitorsModal.vue'
import TimeframeSwitcher, { type TimeframeType } from '../components/TimeframeSwitcher.vue'
import ViewModeSwitcher, { type ViewModeType } from '../components/ViewModeSwitcher.vue'
import NameFilter from '../components/NameFilter.vue'
import TenantFilter from '../components/TenantFilter.vue'
import MonitorSortSelect from '../components/MonitorSortSelect.vue'
import LoadingState from '../components/LoadingState.vue'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import type { CurrentUser } from '../services/api'
import { userConfigService } from '../services/userConfig'
import { timeframeToPeriod } from '../utils/uptime'
import { sortMonitors, type MonitorSort } from '../utils/monitorSort'

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
const selectedSort = ref<MonitorSort>(userConfigService.getMonitorSort())
const selectedTimeframe = ref<TimeframeType>('24h')
const selectedViewMode = ref<ViewModeType>('default')
const loadingUptimeStats = ref(new Set<number>())
// Multi edit
const selecting = ref(false)
const selectedIds = ref(new Set<number>())
const showBulkEdit = ref(false)
// The monitors at the time the dialog opened, so polling does not change the list in the dialog
const bulkEditMonitors = ref<MonitorResponse[]>([])
let refreshInterval: ReturnType<typeof setInterval> | null = null
// The backend checks each monitor every 15 seconds, so polling more often shows nothing new.
const MONITOR_REFRESH_MS = 15_000
// The 7d/90d stats change slowly, so they refresh less often than the monitor data.
const UPTIME_STATS_REFRESH_MS = 60_000
let lastUptimeStatsFetch = 0

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

const sortedMonitors = computed(() =>
  sortMonitors(filteredMonitors.value, monitorsStore.getMonitorStatusById, selectedSort.value)
)

// Down first: the monitors that are down now are a group of their own at the top.
// Each group keeps the sort of the user.
const downMonitorIds = computed(() => new Set(monitorsStore.downMonitors.map(s => s.monitorId)))
const downGroup = computed(() => sortedMonitors.value.filter(m => downMonitorIds.value.has(m.id)))
const otherGroup = computed(() => sortedMonitors.value.filter(m => !downMonitorIds.value.has(m.id)))
const otherGroupTitle = computed(() =>
  otherGroup.value.every(m => monitorsStore.getMonitorStatusById(m.id)?.currentStatus === 'up')
    ? 'Up'
    : 'Other monitors'
)
const monitorGroups = computed(() => {
  const groups = [
    { key: 'down', title: 'Down', monitors: downGroup.value },
    { key: 'other', title: otherGroupTitle.value, monitors: otherGroup.value }
  ].filter(group => group.monitors.length > 0)
  // Without a monitor down, one list without a heading is enough
  return groups.map(group => ({ ...group, showTitle: downGroup.value.length > 0 }))
})

const toggleMonitorExpanded = async (monitorId: number) => {
  if (expandedMonitors.value.has(monitorId)) {
    expandedMonitors.value.delete(monitorId)
  } else {
    expandedMonitors.value.add(monitorId)
    await fetchUptimeStatsForTimeframe(selectedTimeframe.value)
  }
}

/**
 * Loads the stats of all monitors for the timeframe in one request. Without force, it only loads
 * when a visible monitor has no stats yet.
 */
const fetchUptimeStatsForTimeframe = async (timeframe: TimeframeType, force = false) => {
  const periodType = timeframeToPeriod(timeframe)
  if (!periodType) return

  const missing = filteredMonitors.value
    .map(m => m.id)
    .filter(id => !monitorsStore.getUptimeStatsById(id, periodType))
  if (!force && missing.length === 0) return

  missing.forEach(id => loadingUptimeStats.value.add(id))
  try {
    await monitorsStore.fetchUptimeStatsOfAllMonitors(periodType)
    lastUptimeStatsFetch = Date.now()
  } finally {
    missing.forEach(id => loadingUptimeStats.value.delete(id))
  }
}

const toggleSelectMode = () => {
  selecting.value = !selecting.value
  if (!selecting.value) selectedIds.value = new Set()
}

const toggleSelected = (id: number) => {
  const next = new Set(selectedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selectedIds.value = next
}

// Only monitors that are still there and pass the filter count
const selectedMonitors = computed(() => sortedMonitors.value.filter(m => selectedIds.value.has(m.id)))
const allVisibleSelected = computed(
  () => sortedMonitors.value.length > 0 && selectedMonitors.value.length === sortedMonitors.value.length
)

const toggleSelectAllVisible = () => {
  selectedIds.value = allVisibleSelected.value ? new Set() : new Set(sortedMonitors.value.map(m => m.id))
}

const openBulkEdit = () => {
  bulkEditMonitors.value = [...selectedMonitors.value]
  showBulkEdit.value = true
}

const closeBulkEdit = () => {
  showBulkEdit.value = false
}

const handleBulkEditFinished = () => reloadMonitorsInPlace()

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

// Silent loads: the list stays on the page and keeps its scroll position
const reloadMonitorsInPlace = async () => {
  await Promise.all([monitorsStore.fetchMonitorsSilently(), monitorsStore.fetchMonitorStatusesSilently()])
  await monitorsStore.fetchAllResponseTimeHistories()
}

const handleMonitorCreated = reloadMonitorsInPlace
const handleMonitorUpdated = reloadMonitorsInPlace

const handleMonitorDelete = async (id: number) => {
  const monitor = monitorsStore.getMonitorById(id)
  const monitorName = monitor?.name || 'this monitor'

  const confirmed = await askConfirm({
    title: `Delete ${monitorName}?`,
    message: 'You cannot undo this.',
    confirmLabel: 'Delete monitor',
    danger: true,
  })
  if (confirmed) {
    try {
      await monitorsStore.deleteMonitor(id)
    } catch (err) {
      console.error('Failed to delete monitor:', err)
    }
  }
}

const handleStateChange = async (id: number, newState: MonitorState, silentFor?: SilentFor) => {
  try {
    await monitorsStore.updateMonitorState(id, newState, silentFor)
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

    // One request for the 24h history of all monitors
    await monitorsStore.fetchAllResponseTimeHistories()

    if (Date.now() - lastUptimeStatsFetch >= UPTIME_STATS_REFRESH_MS) {
      await fetchUptimeStatsForTimeframe(selectedTimeframe.value, true)
    }
  } catch (error) {
    console.error('Error refreshing monitor data:', error)
  }
}

// Null: the user has no monitor limit
const maxMonitors = computed(() => currentUser.value?.maxMonitors ?? null)
const peerTenantId = computed(() => currentUser.value?.peerTenantId ?? null)
// The monitors of the peer tenant watch the server itself: they do not count for the limit
const countedMonitorCount = computed(
  () => monitorsStore.monitors.filter(m => m.tenantId !== peerTenantId.value).length
)
const monitorLimitReached = computed(
  () => maxMonitors.value !== null && countedMonitorCount.value >= maxMonitors.value
)

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

  startPolling()
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

const startPolling = () => {
  stopPolling()
  refreshInterval = setInterval(refreshMonitorData, MONITOR_REFRESH_MS)
}

const stopPolling = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = null
  }
}

// No polling while the tab is hidden. When it is shown again, refresh at once.
const handleVisibilityChange = () => {
  if (document.hidden) {
    stopPolling()
  } else {
    refreshMonitorData()
    startPolling()
  }
}

// Watch for changes to selectedTenantId and save to localStorage
watch(selectedTenantId, (newValue) => {
  userConfigService.setMonitorTenantFilter(newValue)
})

watch(selectedSort, (newValue) => {
  userConfigService.setMonitorSort(newValue)
})

// Watch for changes to selectedTimeframe and fetch data
watch(selectedTimeframe, async (newTimeframe) => {
  await fetchUptimeStatsForTimeframe(newTimeframe)
})

onUnmounted(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <AppShell>
    <div class="monitors-page" :class="{ 'is-selecting': selecting }">
      <PageHeader title="Monitors">
        <template #actions>
          <button
            v-if="monitorsStore.monitors.length > 0"
            type="button"
            class="btn btn-outline"
            :aria-pressed="selecting"
            title="Select monitors to change settings of many monitors at once"
            @click="toggleSelectMode"
          >
            {{ selecting ? 'Done' : 'Select' }}
          </button>
          <button
            type="button"
            class="btn btn-primary"
            :disabled="monitorLimitReached"
            :title="monitorLimitReached ? 'You have the maximum number of monitors' : undefined"
            @click="openAddMonitorForm"
          >
            Add Monitor
          </button>
        </template>

        <div
          v-if="(maxMonitors !== null && !monitorsStore.isLoading && !monitorsStore.error) || peerTenantId !== null"
          class="monitors-notices"
        >
          <MonitorLimitInfo
            v-if="maxMonitors !== null && !monitorsStore.isLoading && !monitorsStore.error"
            :monitor-count="countedMonitorCount"
            :max-monitors="maxMonitors"
          />
          <PeerSetupWarning v-if="peerTenantId !== null" />
        </div>
      </PageHeader>

      <template v-if="!monitorsStore.isLoading && !monitorsStore.error && monitorsStore.monitors.length > 0">
        <DashboardSummary
          class="monitors-stats"
          :summary="summaryData"
          :selected-status-filter="selectedStatusFilter"
          @update:selected-status-filter="selectedStatusFilter = $event"
        />

        <div class="toolbar monitors-toolbar">
          <NameFilter v-model="selectedNameFilter" class="toolbar-search" />
          <TenantFilter
            v-if="currentUser?.tenants"
            v-model="selectedTenantId"
            :tenants="currentUser.tenants"
          />
          <MonitorSortSelect v-model="selectedSort" />
          <span class="toolbar-spacer" aria-hidden="true"></span>
          <TimeframeSwitcher v-model="selectedTimeframe" />
          <ViewModeSwitcher v-model="selectedViewMode" />
        </div>
      </template>

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

      <template v-else>
        <div v-if="selecting" class="selection-bar" role="region" aria-label="Selected monitors">
          <label class="check">
            <input
              type="checkbox"
              :checked="allVisibleSelected"
              :indeterminate="selectedMonitors.length > 0 && !allVisibleSelected"
              @change="toggleSelectAllVisible"
            />
            Select all <span class="num">{{ sortedMonitors.length }}</span> shown
          </label>
          <span class="selection-count muted"><span class="num">{{ selectedMonitors.length }}</span> selected</span>
          <span class="toolbar-spacer" aria-hidden="true"></span>
          <button type="button" class="btn btn-sm btn-outline" @click="toggleSelectMode">Cancel</button>
          <button type="button" class="btn btn-sm btn-primary" :disabled="selectedMonitors.length === 0" @click="openBulkEdit">
            Edit {{ selectedMonitors.length || '' }} {{ selectedMonitors.length === 1 ? 'monitor' : 'monitors' }}
          </button>
        </div>

        <p v-if="sortedMonitors.length === 0" class="monitors-none">
          No monitor matches the filters.
        </p>

        <section
          v-for="group in monitorGroups"
          :key="group.key"
          class="page-section monitor-group"
          :class="`monitor-group-${group.key}`"
          :aria-label="group.showTitle ? undefined : 'Monitors'"
          :aria-labelledby="group.showTitle ? `monitor-group-${group.key}` : undefined"
        >
          <h2 v-if="group.showTitle" :id="`monitor-group-${group.key}`" class="page-section-title">
            {{ group.title }} <small class="num">{{ group.monitors.length }}</small>
          </h2>
          <ul class="monitor-list" :class="{ 'is-minimal': selectedViewMode === 'minimal' }">
            <li v-for="monitor in group.monitors" :key="monitor.id">
              <EnhancedMonitorCard
                :monitor="monitor"
                :status="monitorsStore.getMonitorStatusById(monitor.id) || null"
                :response-time-history="monitorsStore.getResponseTimeHistoryById(monitor.id) || null"
                :uptime-stats7d="monitorsStore.getUptimeStatsById(monitor.id, 'seven_days') || null"
                :uptime-stats90d="monitorsStore.getUptimeStatsById(monitor.id, 'ninety_days') || null"
                :selected-timeframe="selectedTimeframe"
                :selected-view-mode="selectedViewMode"
                :is-expanded="isMonitorExpanded(monitor.id)"
                :is-loading-stats="loadingUptimeStats.has(monitor.id)"
                :is-peer="isPeerMonitor(monitor, peerTenantId)"
                :selectable="selecting"
                :selected="selectedIds.has(monitor.id)"
                @toggle-select="toggleSelected"
                @toggle-expanded="toggleMonitorExpanded"
                @edit="openEditModal"
                @state-change="handleStateChange"
                @delete="handleMonitorDelete"
              />
            </li>
          </ul>
        </section>
      </template>
    </div>

    <MonitorForm
      v-if="showAddMonitorForm"
      :preselected-tenant-id="selectedTenantId"
      @close="closeAddMonitorForm"
      @success="handleMonitorCreated"
    />

    <BulkEditMonitorsModal
      v-if="showBulkEdit"
      :monitors="bulkEditMonitors"
      @close="closeBulkEdit"
      @finished="handleBulkEditFinished"
    />

    <MonitorEditForm
      v-if="showEditMonitorForm"
      :monitor="editingMonitor"
      @close="closeEditMonitorForm"
      @success="handleMonitorUpdated"
    />
  </AppShell>
</template>

<style scoped>
.monitors-page {
  min-height: 400px;
}

.monitors-notices {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: calc(-1 * var(--space-2));
}

.monitors-stats {
  margin-bottom: var(--space-3);
}

.monitors-toolbar {
  margin-bottom: var(--space-5);
}

.toolbar-search {
  flex: 1 1 14rem;
  max-width: 20rem;
}

.monitors-none {
  margin: 0 0 var(--space-5);
  padding: var(--space-5);
  border: 1px dashed var(--border);
  border-radius: var(--radius-lg);
  color: var(--text-muted);
  text-align: center;
}

.monitor-group {
  margin-bottom: var(--space-5);
}

.monitor-group-down .page-section-title {
  color: var(--down);
}

/* The rows of the list. Each monitor draws its own row; the list draws the lines between. */
.monitor-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
}

.monitor-list > li + li {
  border-top: 1px solid var(--border-subtle);
}

.monitor-group-down .monitor-list {
  border-color: var(--danger-soft-border);
}

/* Minimal: short lines in as many columns as fit. The lines between come from a shadow. */
.monitor-list.is-minimal {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 22rem), 1fr));
}

.monitor-list.is-minimal > li,
.monitor-list.is-minimal > li + li {
  border-top: 0;
  box-shadow:
    1px 0 0 var(--border-subtle),
    0 1px 0 var(--border-subtle);
}

/* Multi edit: the bar stays under the top bar while the user scrolls through the monitors. */
.selection-bar {
  position: sticky;
  top: calc(var(--bar-height) + var(--space-2));
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  margin-bottom: var(--space-4);
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-4);
  border: 1px solid var(--link);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-2);
}

.selection-count {
  font-size: var(--text-sm);
}

/*
 * Small screens: the top bar has two rows there, so the selection bar sits at the bottom
 * of the screen instead. The page gets room at the end, so the bar does not hide the last row.
 */
@media (max-width: 920px) {
  .selection-bar {
    position: fixed;
    top: auto;
    right: var(--space-3);
    bottom: var(--space-3);
    left: var(--space-3);
    margin: 0;
  }

  .monitors-page.is-selecting {
    padding-bottom: 7rem;
  }
}

@media (max-width: 640px) {
  .toolbar-search {
    flex-basis: 100%;
    max-width: none;
  }

  .monitors-toolbar .toolbar-spacer {
    display: none;
  }

  /* Both switches fit in one line. */
  .monitors-toolbar :deep(.button-group > .btn) {
    padding-inline: var(--space-3);
  }
}
</style>

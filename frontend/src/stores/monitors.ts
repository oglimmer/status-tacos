import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { apiService } from '../services/api'
import type { StatsPeriodParam } from '../utils/uptime'

export type MonitorState = 'ACTIVE' | 'SILENT' | 'INACTIVE'

export interface MonitorRequest {
  name: string
  url: string
  tenantId: number
  state: MonitorState
  httpHeaders?: Record<string, string>
  statusCodeRegex?: string
  responseBodyRegex?: string
  prometheusKey?: string
  prometheusMinValue?: number
  prometheusMaxValue?: number
  alertingThreshold?: number
}

export interface MonitorResponse {
  id: number
  name: string
  url: string
  tenantId: number
  tenant: {
    id: number
    name: string
    code: string
    description: string
    isActive: boolean
    createdAt: string
    updatedAt: string
  }
  state: MonitorState
  httpHeaders?: Record<string, string>
  statusCodeRegex?: string
  responseBodyRegex?: string
  prometheusKey?: string
  prometheusMinValue?: number
  prometheusMaxValue?: number
  alertingThreshold: number
  createdAt: string
  updatedAt: string
}

export interface MonitorStatus {
  monitorId: number
  monitorName: string
  monitorUrl: string
  tenantId: number
  tenant: {
    id: number
    name: string
    code: string
    description: string
    isActive: boolean
    createdAt: string
    updatedAt: string
  }
  currentStatus: 'up' | 'down'
  lastCheckedAt: string
  lastUpAt: string
  lastDownAt: string
  consecutiveFailures: number
  lastResponseTimeMs: number
  lastStatusCode: number
  updatedAt: string
}

export interface ResponseTimeDataPoint {
  timestamp: string
  maxResponseTimeMs: number
}

export interface ResponseTimeHistory {
  monitorId: number
  monitorName: string
  intervalMinutes: number
  totalDataPoints: number
  // Rounded down by the backend. Missing when there are no checks.
  uptimePercentage24h?: number
  totalChecks24h: number
  successfulChecks24h: number
  dataPoints: ResponseTimeDataPoint[]
  statusDownPeriods: StatusDownPeriod[]
}

/**
 * Uptime stats of one monitor for the window [periodStart, periodEnd) (UTC).
 * Response times count successful checks only.
 */
export interface UptimeStats {
  monitorId: number
  monitorName: string
  periodType: 'SEVEN_DAYS' | 'NINETY_DAYS'
  periodStart: string
  periodEnd: string
  intervalMinutes: number
  totalChecks: number
  successfulChecks: number
  // Rounded down by the backend. Missing when there are no checks.
  uptimePercentage?: number
  minResponseTimeMs?: number
  maxResponseTimeMs?: number
  avgResponseTimeMs?: number
  p99ResponseTimeMs?: number
  responseTimeDataPoints: ResponseTimeDataPoint[]
  statusDownPeriods: StatusDownPeriod[]
}

export interface StatusDownPeriod {
  start: string
  end: string
}

export const useMonitorsStore = defineStore('monitors', () => {
  const monitors = ref<MonitorResponse[]>([])
  const monitorStatuses = ref<MonitorStatus[]>([])
  const responseTimeHistories = ref<Map<number, ResponseTimeHistory>>(new Map())
  const uptimeStats = ref<Map<string, UptimeStats>>(new Map())
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  const authStore = useAuthStore()


  const fetchMonitors = async () => {
    isLoading.value = true
    error.value = null

    try {
      const data = await apiService.get<MonitorResponse[]>('/monitors', authStore.user)
      monitors.value = data
    } catch (err) {
      error.value = 'Failed to fetch monitors'
      console.error('Fetch monitors error:', err)
    } finally {
      isLoading.value = false
    }
  }

  const fetchMonitorStatuses = async () => {
    isLoading.value = true
    error.value = null

    try {
      const data = await apiService.get<MonitorStatus[]>('/monitor-statuses', authStore.user)
      monitorStatuses.value = data
    } catch (err) {
      error.value = 'Failed to fetch monitor statuses'
      console.error('Fetch monitor statuses error:', err)
    } finally {
      isLoading.value = false
    }
  }

  const fetchMonitorsSilently = async () => {
    try {
      const data = await apiService.get<MonitorResponse[]>('/monitors', authStore.user)
      monitors.value = data
    } catch (err) {
      console.error('Silent fetch monitors error:', err)
    }
  }

  const fetchMonitorStatusesSilently = async () => {
    try {
      const data = await apiService.get<MonitorStatus[]>('/monitor-statuses', authStore.user)
      monitorStatuses.value = data
    } catch (err) {
      console.error('Silent fetch monitor statuses error:', err)
    }
  }

  const fetchResponseTimeHistory = async (monitorId: number): Promise<ResponseTimeHistory | null> => {
    try {
      const data = await apiService.get<ResponseTimeHistory>(`/monitor-statuses/${monitorId}/response-time-history-24h`, authStore.user)
      responseTimeHistories.value.set(monitorId, data)
      return data
    } catch (err) {
      console.error('Fetch response time history error:', err)
      return null
    }
  }

  /** 24h history of all monitors of the user in one request. Replaces the stored histories. */
  const fetchAllResponseTimeHistories = async (): Promise<void> => {
    try {
      const data = await apiService.get<ResponseTimeHistory[]>('/monitor-statuses/response-time-history-24h', authStore.user)
      responseTimeHistories.value = new Map(data.map(history => [history.monitorId, history]))
    } catch (err) {
      console.error('Fetch response time histories error:', err)
    }
  }

  const fetchUptimeStats = async (monitorId: number, periodType: StatsPeriodParam): Promise<UptimeStats | null> => {
    try {
      const data = await apiService.get<UptimeStats>(`/uptime-stats/${monitorId}/${periodType}`, authStore.user)
      uptimeStats.value.set(`${monitorId}-${periodType}`, data)
      return data
    } catch (err) {
      console.error('Fetch uptime stats error:', err)
      return null
    }
  }

  /** Stats of all monitors of the user in one request. */
  const fetchUptimeStatsOfAllMonitors = async (periodType: StatsPeriodParam): Promise<void> => {
    try {
      const data = await apiService.get<UptimeStats[]>(`/uptime-stats?period=${periodType}`, authStore.user)
      for (const stats of data) {
        uptimeStats.value.set(`${stats.monitorId}-${periodType}`, stats)
      }
    } catch (err) {
      console.error('Fetch uptime stats of all monitors error:', err)
    }
  }

  const fetchAllUptimeStats = async (monitorId: number): Promise<Record<'7d' | '90d', UptimeStats | null>> => {
    const [sevenDays, ninetyDays] = await Promise.all([
      fetchUptimeStats(monitorId, 'seven_days'),
      fetchUptimeStats(monitorId, 'ninety_days')
    ])
    return { '7d': sevenDays, '90d': ninetyDays }
  }

  const createMonitor = async (monitorData: MonitorRequest): Promise<MonitorResponse> => {
    isLoading.value = true
    error.value = null

    try {
      const newMonitor = await apiService.post<MonitorResponse, MonitorRequest>('/monitors', monitorData, authStore.user)
      monitors.value.push(newMonitor)
      return newMonitor
    } catch (err) {
      error.value = 'Failed to create monitor'
      console.error('Create monitor error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const updateMonitor = async (id: number, monitorData: MonitorRequest): Promise<MonitorResponse> => {
    isLoading.value = true
    error.value = null

    try {
      const updatedMonitor = await apiService.put<MonitorResponse, MonitorRequest>(`/monitors/${id}`, monitorData, authStore.user)
      const index = monitors.value.findIndex(m => m.id === id)
      if (index !== -1) {
        monitors.value[index] = updatedMonitor
      }
      return updatedMonitor
    } catch (err) {
      error.value = 'Failed to update monitor'
      console.error('Update monitor error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const deleteMonitor = async (id: number): Promise<void> => {
    isLoading.value = true
    error.value = null

    try {
      await apiService.delete(`/monitors/${id}`, authStore.user)
      monitors.value = monitors.value.filter(m => m.id !== id)
      monitorStatuses.value = monitorStatuses.value.filter(ms => ms.monitorId !== id)
    } catch (err) {
      error.value = 'Failed to delete monitor'
      console.error('Delete monitor error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const updateMonitorState = async (id: number, state: MonitorState): Promise<MonitorResponse> => {
    isLoading.value = true
    error.value = null

    try {
      const updatedMonitor = await apiService.patch<MonitorResponse>(`/monitors/${id}/state?state=${state}`, authStore.user)
      const index = monitors.value.findIndex(m => m.id === id)
      if (index !== -1) {
        monitors.value[index] = updatedMonitor
      }
      return updatedMonitor
    } catch (err) {
      error.value = 'Failed to update monitor state'
      console.error('Update monitor state error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  // Moves the monitor with all its history to another tenant. Alert contacts that are limited to
  // selected monitors lose this monitor.
  const moveMonitorToTenant = async (id: number, tenantId: number): Promise<MonitorResponse> => {
    isLoading.value = true
    error.value = null

    try {
      const movedMonitor = await apiService.patch<MonitorResponse>(`/monitors/${id}/tenant?tenantId=${tenantId}`, authStore.user)
      const index = monitors.value.findIndex(m => m.id === id)
      if (index !== -1) {
        monitors.value[index] = movedMonitor
      }
      return movedMonitor
    } catch (err) {
      error.value = 'Failed to move monitor'
      console.error('Move monitor error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  // Legacy method for backward compatibility
  const toggleMonitorStatus = async (id: number): Promise<MonitorResponse> => {
    isLoading.value = true
    error.value = null

    try {
      const updatedMonitor = await apiService.patch<MonitorResponse>(`/monitors/${id}/toggle-status`, authStore.user)
      const index = monitors.value.findIndex(m => m.id === id)
      if (index !== -1) {
        monitors.value[index] = updatedMonitor
      }
      return updatedMonitor
    } catch (err) {
      error.value = 'Failed to toggle monitor status'
      console.error('Toggle monitor status error:', err)
      throw err
    } finally {
      isLoading.value = false
    }
  }

  const getMonitorById = computed(() => {
    return (id: number) => monitors.value.find(m => m.id === id)
  })

  const getMonitorStatusById = computed(() => {
    return (monitorId: number) => monitorStatuses.value.find(ms => ms.monitorId === monitorId)
  })

  const getResponseTimeHistoryById = computed(() => {
    return (monitorId: number) => responseTimeHistories.value.get(monitorId)
  })

  const getUptimeStatsById = computed(() => {
    return (monitorId: number, periodType: StatsPeriodParam) => {
      const key = `${monitorId}-${periodType}`
      return uptimeStats.value.get(key)
    }
  })

  const activeMonitors = computed(() => monitors.value.filter(m => m.state === 'ACTIVE'))
  const silentMonitors = computed(() => monitors.value.filter(m => m.state === 'SILENT'))
  const inactiveMonitors = computed(() => monitors.value.filter(m => m.state === 'INACTIVE'))
  const monitoringMonitors = computed(() => monitors.value.filter(m => m.state === 'ACTIVE' || m.state === 'SILENT'))

  const upMonitors = computed(() =>
    monitorStatuses.value.filter(ms => ms.currentStatus === 'up')
  )

  const downMonitors = computed(() =>
    monitorStatuses.value.filter(ms => ms.currentStatus === 'down')
  )

  const getMonitorsByTenant = computed(() => {
    return (tenantId: number) => monitors.value.filter(m => m.tenantId === tenantId)
  })

  return {
    monitors: computed(() => monitors.value),
    monitorStatuses: computed(() => monitorStatuses.value),
    isLoading: computed(() => isLoading.value),
    error: computed(() => error.value),
    fetchMonitors,
    fetchMonitorStatuses,
    fetchMonitorsSilently,
    fetchMonitorStatusesSilently,
    fetchResponseTimeHistory,
    fetchAllResponseTimeHistories,
    fetchUptimeStats,
    fetchUptimeStatsOfAllMonitors,
    fetchAllUptimeStats,
    createMonitor,
    updateMonitor,
    deleteMonitor,
    updateMonitorState,
    moveMonitorToTenant,
    toggleMonitorStatus,
    getMonitorById,
    getMonitorStatusById,
    getResponseTimeHistoryById,
    getUptimeStatsById,
    activeMonitors,
    silentMonitors,
    inactiveMonitors,
    monitoringMonitors,
    upMonitors,
    downMonitors,
    getMonitorsByTenant
  }
})

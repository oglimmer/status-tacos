import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { useAuthStore } from './auth'
import { apiService } from '../services/api'

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
  uptimePercentage24h: number
  totalChecks24h: number
  successfulChecks24h: number
  dataPoints: ResponseTimeDataPoint[]
  statusDownPeriods: StatusDownPeriod[]
}

export interface UptimeStats {
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

export interface StatusDownPeriod {
  start: string
  end: string
}

export interface ParsedUptimeStats extends Omit<UptimeStats, 'responseTimeData' | 'statusChangeData'> {
  responseTimeDataPoints: ResponseTimeDataPoint[]
  statusDownPeriods: StatusDownPeriod[]
}


export const useMonitorsStore = defineStore('monitors', () => {
  const monitors = ref<MonitorResponse[]>([])
  const monitorStatuses = ref<MonitorStatus[]>([])
  const responseTimeHistories = ref<Map<number, ResponseTimeHistory>>(new Map())
  const uptimeStats = ref<Map<string, ParsedUptimeStats>>(new Map())
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

  const fetchUptimeStats = async (monitorId: number, periodType: 'seven_days' | 'ninety_days' | 'three_sixty_five_days'): Promise<ParsedUptimeStats | null> => {
    try {
      const data = await apiService.get<UptimeStats>(`/uptime-stats/${monitorId}/${periodType}`, authStore.user)

      // Parse JSON data
      const parsed: ParsedUptimeStats = {
        ...data,
        responseTimeDataPoints: data.responseTimeData ? JSON.parse(data.responseTimeData) : [],
        statusDownPeriods: data.statusChangeData ? JSON.parse(data.statusChangeData) : []
      }

      const key = `${monitorId}-${periodType}`
      uptimeStats.value.set(key, parsed)
      return parsed
    } catch (err) {
      console.error('Fetch uptime stats error:', err)
      return null
    }
  }

  const fetchAllUptimeStats = async (monitorId: number): Promise<Record<string, ParsedUptimeStats | null>> => {
    const results = await Promise.allSettled([
      fetchUptimeStats(monitorId, 'seven_days'),
      fetchUptimeStats(monitorId, 'ninety_days'),
      fetchUptimeStats(monitorId, 'three_sixty_five_days')
    ])

    return {
      '7d': results[0].status === 'fulfilled' ? results[0].value : null,
      '90d': results[1].status === 'fulfilled' ? results[1].value : null,
      '365d': results[2].status === 'fulfilled' ? results[2].value : null
    }
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
    return (monitorId: number, periodType: 'seven_days' | 'ninety_days' | 'three_sixty_five_days') => {
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
    fetchUptimeStats,
    fetchAllUptimeStats,
    createMonitor,
    updateMonitor,
    deleteMonitor,
    updateMonitorState,
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

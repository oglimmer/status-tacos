import { DEFAULT_MONITOR_SORT, isMonitorSort, type MonitorSort } from '../utils/monitorSort'

export type MonitorTimeframe = '24h' | '7d' | '90d'
export type MonitorViewMode = 'minimal' | 'default' | 'full'
// NONE: the monitors without an environment
export type MonitorEnvironmentFilter = 'PROD' | 'STAGE' | 'DEV' | 'NONE'

const TIMEFRAMES: readonly MonitorTimeframe[] = ['24h', '7d', '90d']
const VIEW_MODES: readonly MonitorViewMode[] = ['minimal', 'default', 'full']
const ENVIRONMENT_FILTERS: readonly MonitorEnvironmentFilter[] = ['PROD', 'STAGE', 'DEV', 'NONE']

interface UserConfig {
  tenantFilters: {
    monitors: number | null
    alertContacts: number | null
  }
  monitorSort: MonitorSort
  monitorTimeframe: MonitorTimeframe
  monitorViewMode: MonitorViewMode
  monitorEnvironmentFilter: MonitorEnvironmentFilter | null
}

const DEFAULT_CONFIG: UserConfig = {
  tenantFilters: {
    monitors: null,
    alertContacts: null
  },
  monitorSort: DEFAULT_MONITOR_SORT,
  monitorTimeframe: '24h',
  monitorViewMode: 'default',
  monitorEnvironmentFilter: null
}

const CONFIG_KEY = 'status-tacos-user-config'

export class UserConfigService {
  private config: UserConfig

  constructor() {
    this.config = this.loadFromStorage()
  }

  private loadFromStorage(): UserConfig {
    try {
      const stored = localStorage.getItem(CONFIG_KEY)
      if (stored) {
        const parsed = JSON.parse(stored)
        // Deep merge to preserve nested structure
        return {
          ...DEFAULT_CONFIG,
          ...parsed,
          tenantFilters: {
            ...DEFAULT_CONFIG.tenantFilters,
            ...(parsed.tenantFilters || {})
          },
          // An unknown sort (from a newer or broken config) falls back to the default
          monitorSort: isMonitorSort(parsed.monitorSort) ? parsed.monitorSort : { ...DEFAULT_CONFIG.monitorSort },
          // Unknown values fall back to the default
          monitorTimeframe: TIMEFRAMES.includes(parsed.monitorTimeframe)
            ? parsed.monitorTimeframe
            : DEFAULT_CONFIG.monitorTimeframe,
          monitorViewMode: VIEW_MODES.includes(parsed.monitorViewMode)
            ? parsed.monitorViewMode
            : DEFAULT_CONFIG.monitorViewMode,
          monitorEnvironmentFilter: ENVIRONMENT_FILTERS.includes(parsed.monitorEnvironmentFilter)
            ? parsed.monitorEnvironmentFilter
            : null
        }
      }
    } catch (error) {
      console.warn('Failed to load user config from localStorage:', error)
    }
    return {
      tenantFilters: {
        ...DEFAULT_CONFIG.tenantFilters
      },
      monitorSort: { ...DEFAULT_CONFIG.monitorSort },
      monitorTimeframe: DEFAULT_CONFIG.monitorTimeframe,
      monitorViewMode: DEFAULT_CONFIG.monitorViewMode,
      monitorEnvironmentFilter: null
    }
  }

  private saveToStorage(): void {
    try {
      localStorage.setItem(CONFIG_KEY, JSON.stringify(this.config))
    } catch (error) {
      console.warn('Failed to save user config to localStorage:', error)
    }
  }

  getMonitorTenantFilter(): number | null {
    return this.config.tenantFilters.monitors
  }

  setMonitorTenantFilter(tenantId: number | null): void {
    this.config.tenantFilters.monitors = tenantId
    this.saveToStorage()
  }

  getAlertContactTenantFilter(): number | null {
    return this.config.tenantFilters.alertContacts
  }

  setAlertContactTenantFilter(tenantId: number | null): void {
    this.config.tenantFilters.alertContacts = tenantId
    this.saveToStorage()
  }

  getMonitorSort(): MonitorSort {
    return { ...this.config.monitorSort }
  }

  setMonitorSort(sort: MonitorSort): void {
    this.config.monitorSort = { ...sort }
    this.saveToStorage()
  }

  getMonitorTimeframe(): MonitorTimeframe {
    return this.config.monitorTimeframe
  }

  setMonitorTimeframe(timeframe: MonitorTimeframe): void {
    this.config.monitorTimeframe = timeframe
    this.saveToStorage()
  }

  getMonitorViewMode(): MonitorViewMode {
    return this.config.monitorViewMode
  }

  setMonitorViewMode(viewMode: MonitorViewMode): void {
    this.config.monitorViewMode = viewMode
    this.saveToStorage()
  }

  getMonitorEnvironmentFilter(): MonitorEnvironmentFilter | null {
    return this.config.monitorEnvironmentFilter
  }

  setMonitorEnvironmentFilter(environment: MonitorEnvironmentFilter | null): void {
    this.config.monitorEnvironmentFilter = environment
    this.saveToStorage()
  }

  reset(): void {
    this.config = {
      tenantFilters: {
        ...DEFAULT_CONFIG.tenantFilters
      },
      monitorSort: { ...DEFAULT_CONFIG.monitorSort },
      monitorTimeframe: DEFAULT_CONFIG.monitorTimeframe,
      monitorViewMode: DEFAULT_CONFIG.monitorViewMode,
      monitorEnvironmentFilter: null
    }
    this.saveToStorage()
  }
}

export const userConfigService = new UserConfigService()

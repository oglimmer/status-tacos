interface UserConfig {
  tenantFilters: {
    monitors: number | null
    alertContacts: number | null
  }
}

const DEFAULT_CONFIG: UserConfig = {
  tenantFilters: {
    monitors: null,
    alertContacts: null
  }
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
          }
        }
      }
    } catch (error) {
      console.warn('Failed to load user config from localStorage:', error)
    }
    return {
      tenantFilters: {
        ...DEFAULT_CONFIG.tenantFilters
      }
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

  reset(): void {
    this.config = {
      tenantFilters: {
        ...DEFAULT_CONFIG.tenantFilters
      }
    }
    this.saveToStorage()
  }
}

export const userConfigService = new UserConfigService()

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { UserConfigService } from './userConfig'

describe('UserConfigService', () => {
  let service: UserConfigService

  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear()
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('constructor and initialization', () => {
    it('should initialize with default config when localStorage is empty', () => {
      service = new UserConfigService()

      expect(service.getMonitorTenantFilter()).toBeNull()
      expect(service.getAlertContactTenantFilter()).toBeNull()
    })

    it('should load existing config from localStorage', () => {
      const existingConfig = {
        tenantFilters: {
          monitors: 42,
          alertContacts: 99
        }
      }
      localStorage.setItem('status-tacos-user-config', JSON.stringify(existingConfig))

      service = new UserConfigService()

      expect(service.getMonitorTenantFilter()).toBe(42)
      expect(service.getAlertContactTenantFilter()).toBe(99)
    })

    it('should handle corrupted localStorage data gracefully', () => {
      localStorage.setItem('status-tacos-user-config', 'invalid-json{')

      service = new UserConfigService()

      // Should fall back to defaults
      expect(service.getMonitorTenantFilter()).toBeNull()
      expect(service.getAlertContactTenantFilter()).toBeNull()
    })

    it('should merge stored config with defaults for missing properties', () => {
      // Simulate an older config version missing some properties
      const partialConfig = {
        tenantFilters: {
          monitors: 10
        }
      }
      localStorage.setItem('status-tacos-user-config', JSON.stringify(partialConfig))

      service = new UserConfigService()

      expect(service.getMonitorTenantFilter()).toBe(10)
      // Deep merge ensures missing properties get default values
      expect(service.getAlertContactTenantFilter()).toBeNull()
    })
  })

  describe('monitor tenant filter', () => {
    beforeEach(() => {
      service = new UserConfigService()
    })

    it('should get monitor tenant filter', () => {
      expect(service.getMonitorTenantFilter()).toBeNull()
    })

    it('should set monitor tenant filter and persist to localStorage', () => {
      service.setMonitorTenantFilter(123)

      expect(service.getMonitorTenantFilter()).toBe(123)

      // Verify it was saved to localStorage
      const stored = JSON.parse(localStorage.getItem('status-tacos-user-config') || '{}')
      expect(stored.tenantFilters.monitors).toBe(123)
    })

    it('should allow setting monitor tenant filter to null', () => {
      service.setMonitorTenantFilter(123)
      service.setMonitorTenantFilter(null)

      expect(service.getMonitorTenantFilter()).toBeNull()

      const stored = JSON.parse(localStorage.getItem('status-tacos-user-config') || '{}')
      expect(stored.tenantFilters.monitors).toBeNull()
    })
  })

  describe('alert contact tenant filter', () => {
    beforeEach(() => {
      service = new UserConfigService()
    })

    it('should get alert contact tenant filter', () => {
      expect(service.getAlertContactTenantFilter()).toBeNull()
    })

    it('should set alert contact tenant filter and persist to localStorage', () => {
      service.setAlertContactTenantFilter(456)

      expect(service.getAlertContactTenantFilter()).toBe(456)

      // Verify it was saved to localStorage
      const stored = JSON.parse(localStorage.getItem('status-tacos-user-config') || '{}')
      expect(stored.tenantFilters.alertContacts).toBe(456)
    })

    it('should allow setting alert contact tenant filter to null', () => {
      service.setAlertContactTenantFilter(456)
      service.setAlertContactTenantFilter(null)

      expect(service.getAlertContactTenantFilter()).toBeNull()

      const stored = JSON.parse(localStorage.getItem('status-tacos-user-config') || '{}')
      expect(stored.tenantFilters.alertContacts).toBeNull()
    })
  })

  describe('reset', () => {
    beforeEach(() => {
      service = new UserConfigService()
    })

    it('should reset all filters to default values', () => {
      service.setMonitorTenantFilter(123)
      service.setAlertContactTenantFilter(456)

      service.reset()

      expect(service.getMonitorTenantFilter()).toBeNull()
      expect(service.getAlertContactTenantFilter()).toBeNull()
    })

    it('should persist reset to localStorage', () => {
      service.setMonitorTenantFilter(123)
      service.setAlertContactTenantFilter(456)

      service.reset()

      const stored = JSON.parse(localStorage.getItem('status-tacos-user-config') || '{}')
      expect(stored.tenantFilters.monitors).toBeNull()
      expect(stored.tenantFilters.alertContacts).toBeNull()
    })
  })

  describe('localStorage error handling', () => {
    beforeEach(() => {
      service = new UserConfigService()
    })

    it('should handle localStorage.setItem errors gracefully', () => {
      // Mock localStorage.setItem to throw an error
      const originalSetItem = localStorage.setItem
      localStorage.setItem = () => {
        throw new Error('QuotaExceededError')
      }

      // Should not throw, just warn
      expect(() => service.setMonitorTenantFilter(123)).not.toThrow()

      // Restore original
      localStorage.setItem = originalSetItem
    })
  })

  describe('persistence across instances', () => {
    it('should persist config across service instances', () => {
      const service1 = new UserConfigService()
      service1.setMonitorTenantFilter(789)
      service1.setAlertContactTenantFilter(321)

      // Create a new instance
      const service2 = new UserConfigService()

      expect(service2.getMonitorTenantFilter()).toBe(789)
      expect(service2.getAlertContactTenantFilter()).toBe(321)
    })
  })
})

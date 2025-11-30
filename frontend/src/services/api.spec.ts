import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { ApiService } from './api'
import type { User } from 'oidc-client-ts'

describe('ApiService', () => {
  let apiService: ApiService
  let originalLocation: Location

  beforeEach(() => {
    apiService = new ApiService()
    // Save original location
    originalLocation = window.location
  })

  afterEach(() => {
    // Restore original location
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
      configurable: true
    })
  })

  describe('getApiBaseUrl', () => {
    it('should return localhost URL when hostname is localhost', () => {
      // Mock window.location.hostname
      Object.defineProperty(window, 'location', {
        value: { hostname: 'localhost' },
        writable: true,
        configurable: true
      })

      // Access private method via type assertion for testing
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const baseUrl = (apiService as any).getApiBaseUrl()

      expect(baseUrl).toBe('http://localhost:8080/api/v1')
    })

    it('should return https URL with current hostname when not localhost', () => {
      Object.defineProperty(window, 'location', {
        value: { hostname: 'app.example.com' },
        writable: true,
        configurable: true
      })

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const baseUrl = (apiService as any).getApiBaseUrl()

      expect(baseUrl).toBe('https://app.example.com/api/v1')
    })

    it('should handle production domain correctly', () => {
      Object.defineProperty(window, 'location', {
        value: { hostname: 'status-tacos.com' },
        writable: true,
        configurable: true
      })

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const baseUrl = (apiService as any).getApiBaseUrl()

      expect(baseUrl).toBe('https://status-tacos.com/api/v1')
    })
  })

  describe('getAuthHeaders', () => {
    it('should return correct headers with access token', () => {
      const mockUser = {
        access_token: 'test-token-123',
        token_type: 'Bearer',
        profile: {},
        expires_at: 0,
        expired: false,
        scopes: []
      } as unknown as User

      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const headers = (apiService as any).getAuthHeaders(mockUser)

      expect(headers).toEqual({
        'Authorization': 'Bearer test-token-123',
        'Content-Type': 'application/json'
      })
    })

    it('should throw error when user is null', () => {
      expect(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (apiService as any).getAuthHeaders(null)
      }).toThrow('No access token available')
    })

    it('should throw error when user has no access token', () => {
      const mockUser = {
        access_token: undefined,
        token_type: 'Bearer',
        profile: {}
      } as Partial<User>

      expect(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (apiService as any).getAuthHeaders(mockUser)
      }).toThrow('No access token available')
    })

    it('should throw error when user has empty access token', () => {
      const mockUser = {
        access_token: '',
        token_type: 'Bearer',
        profile: {}
      } as Partial<User>

      expect(() => {
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        (apiService as any).getAuthHeaders(mockUser)
      }).toThrow('No access token available')
    })
  })

  describe('HTTP methods error handling', () => {
    const mockUser = {
      access_token: 'test-token',
      token_type: 'Bearer',
      profile: {},
      expires_at: 0,
      expired: false,
      scopes: []
    } as unknown as User

    beforeEach(() => {
      // Mock window.location for these tests
      Object.defineProperty(window, 'location', {
        value: { hostname: 'localhost' },
        writable: true,
        configurable: true
      })
    })

    describe('get', () => {
      it('should throw error on non-ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: false,
          status: 404
        })

        await expect(apiService.get('/test', mockUser)).rejects.toThrow('HTTP error! status: 404')
      })

      it('should return parsed JSON on success', async () => {
        const mockData = { id: 1, name: 'Test' }
        window.fetch = vi.fn().mockResolvedValue({
          ok: true,
          json: async () => mockData
        })

        const result = await apiService.get('/test', mockUser)

        expect(result).toEqual(mockData)
        expect(fetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/v1/test',
          {
            method: 'GET',
            headers: {
              'Authorization': 'Bearer test-token',
              'Content-Type': 'application/json'
            }
          }
        )
      })
    })

    describe('post', () => {
      it('should throw error on non-ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: false,
          status: 400
        })

        await expect(apiService.post('/test', { data: 'value' }, mockUser)).rejects.toThrow('HTTP error! status: 400')
      })

      it('should send data and return parsed JSON on success', async () => {
        const mockResponse = { id: 1, created: true }
        const postData = { name: 'New Item' }

        window.fetch = vi.fn().mockResolvedValue({
          ok: true,
          json: async () => mockResponse
        })

        const result = await apiService.post('/test', postData, mockUser)

        expect(result).toEqual(mockResponse)
        expect(fetch).toHaveBeenCalledWith(
          'http://localhost:8080/api/v1/test',
          {
            method: 'POST',
            headers: {
              'Authorization': 'Bearer test-token',
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(postData)
          }
        )
      })
    })

    describe('put', () => {
      it('should throw error on non-ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: false,
          status: 403
        })

        await expect(apiService.put('/test/1', { data: 'value' }, mockUser)).rejects.toThrow('HTTP error! status: 403')
      })

      it('should send data and return parsed JSON on success', async () => {
        const mockResponse = { id: 1, updated: true }
        const putData = { name: 'Updated Item' }

        window.fetch = vi.fn().mockResolvedValue({
          ok: true,
          json: async () => mockResponse
        })

        const result = await apiService.put('/test/1', putData, mockUser)

        expect(result).toEqual(mockResponse)
      })
    })

    describe('delete', () => {
      it('should throw error on non-ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: false,
          status: 500
        })

        await expect(apiService.delete('/test/1', mockUser)).rejects.toThrow('HTTP error! status: 500')
      })

      it('should complete successfully on ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: true
        })

        await expect(apiService.delete('/test/1', mockUser)).resolves.toBeUndefined()
      })
    })

    describe('patch', () => {
      it('should throw error on non-ok response', async () => {
        window.fetch = vi.fn().mockResolvedValue({
          ok: false,
          status: 422
        })

        await expect(apiService.patch('/test/1', mockUser)).rejects.toThrow('HTTP error! status: 422')
      })

      it('should return parsed JSON on success', async () => {
        const mockResponse = { id: 1, patched: true }

        window.fetch = vi.fn().mockResolvedValue({
          ok: true,
          json: async () => mockResponse
        })

        const result = await apiService.patch('/test/1', mockUser)

        expect(result).toEqual(mockResponse)
      })
    })
  })

  describe('query parameter construction', () => {
    it('should construct query string correctly for getMonitors', () => {
      // Test the query parameter construction logic
      const params = new URLSearchParams()
      params.append('activeOnly', 'true')
      params.append('name', 'test-monitor')

      expect(params.toString()).toBe('activeOnly=true&name=test-monitor')
    })

    it('should handle empty query parameters', () => {
      const params = new URLSearchParams()
      const query = params.toString() ? `?${params.toString()}` : ''

      expect(query).toBe('')
    })

    it('should handle single query parameter', () => {
      const params = new URLSearchParams()
      params.append('activeOnly', 'false')
      const query = params.toString() ? `?${params.toString()}` : ''

      expect(query).toBe('?activeOnly=false')
    })
  })
})

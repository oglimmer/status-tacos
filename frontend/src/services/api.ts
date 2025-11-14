import { User } from 'oidc-client-ts'
import type { AlertContactRequest, AlertContactResponse } from '../stores/alertContacts'
import type { MonitorRequest, MonitorResponse } from '../stores/monitors'
import { useAuthStore } from '../stores/auth'

export interface Tenant {
  id: number
  name: string
  code: string
  description?: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface CurrentUser {
  id: number
  email: string
  firstName?: string
  lastName?: string
  tenantIds: number[]
  tenants: Tenant[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export interface TenantRequest {
  name: string
  code: string
  description?: string
}

export interface TenantUser {
  id: number
  email: string
  firstName?: string
  lastName?: string
  isActive: boolean
}

export interface Monitor {
  id: number
  name: string
  url: string
  tenant: Tenant
  isActive: boolean
  createdAt: string
  updatedAt: string
}

export class ApiService {
  private getApiBaseUrl(): string {
    const hostname = window.location.hostname
    if (hostname === 'localhost') {
      return 'http://localhost:8080/api/v1'
    } else {
      return `https://${hostname}/api/v1`
    }
  }

  private getAuthHeaders(user: User | null): HeadersInit {
    if (!user?.access_token) {
      throw new Error('No access token available')
    }
    return {
      'Authorization': `Bearer ${user.access_token}`,
      'Content-Type': 'application/json'
    }
  }

  async get<T>(endpoint: string, user: User | null): Promise<T> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'GET',
      headers: this.getAuthHeaders(user)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  }

  async post<T, U>(endpoint: string, data: U, user: User | null): Promise<T> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'POST',
      headers: this.getAuthHeaders(user),
      body: JSON.stringify(data)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  }

  async postVoid<U>(endpoint: string, data: U, user: User | null): Promise<void> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'POST',
      headers: this.getAuthHeaders(user),
      body: JSON.stringify(data)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    // Don't try to parse JSON for void responses
  }

  async put<T, U>(endpoint: string, data: U, user: User | null): Promise<T> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'PUT',
      headers: this.getAuthHeaders(user),
      body: JSON.stringify(data)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  }

  async delete(endpoint: string, user: User | null): Promise<void> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'DELETE',
      headers: this.getAuthHeaders(user)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }
  }

  async deleteVoid(endpoint: string, user: User | null): Promise<void> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'DELETE',
      headers: this.getAuthHeaders(user)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    // Don't try to parse JSON for void responses
  }

  async patch<T>(endpoint: string, user: User | null): Promise<T> {
    const response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, {
      method: 'PATCH',
      headers: this.getAuthHeaders(user)
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    return response.json()
  }

  // User methods
  async getCurrentUser(user: User | null): Promise<CurrentUser> {
    return this.get<CurrentUser>('/user/current', user)
  }

  // Monitor methods
  async getMonitors(activeOnly?: boolean, name?: string): Promise<MonitorResponse[]> {
    const params = new URLSearchParams()
    if (activeOnly !== undefined) {
      params.append('activeOnly', activeOnly.toString())
    }
    if (name) {
      params.append('name', name)
    }
    const query = params.toString() ? `?${params.toString()}` : ''

    const user = await this.getAuthenticatedUser()
    return this.get<MonitorResponse[]>(`/monitors${query}`, user)
  }

  async createMonitor(data: MonitorRequest): Promise<MonitorResponse> {
    const user = await this.getAuthenticatedUser()
    return this.post<MonitorResponse, MonitorRequest>('/monitors', data, user)
  }

  async updateMonitor(id: number, data: MonitorRequest): Promise<MonitorResponse> {
    const user = await this.getAuthenticatedUser()
    return this.put<MonitorResponse, MonitorRequest>(`/monitors/${id}`, data, user)
  }

  async deleteMonitor(id: number): Promise<void> {
    const user = await this.getAuthenticatedUser()
    return this.delete(`/monitors/${id}`, user)
  }

  async toggleMonitorStatus(id: number): Promise<MonitorResponse> {
    const user = await this.getAuthenticatedUser()
    return this.patch<MonitorResponse>(`/monitors/${id}/toggle-status`, user)
  }

  // AlertContact methods
  async getAlertContacts(activeOnly?: boolean): Promise<AlertContactResponse[]> {
    const params = new URLSearchParams()
    if (activeOnly !== undefined) {
      params.append('activeOnly', activeOnly.toString())
    }
    const query = params.toString() ? `?${params.toString()}` : ''

    const user = await this.getAuthenticatedUser()
    return this.get<AlertContactResponse[]>(`/alert-contacts${query}`, user)
  }

  async getAlertContactsByTenant(tenantId: number): Promise<AlertContactResponse[]> {
    const user = await this.getAuthenticatedUser()
    return this.get<AlertContactResponse[]>(`/alert-contacts/tenant/${tenantId}`, user)
  }

  async getAlertContact(id: number): Promise<AlertContactResponse> {
    const user = await this.getAuthenticatedUser()
    return this.get<AlertContactResponse>(`/alert-contacts/${id}`, user)
  }

  async createAlertContact(tenantId: number, data: AlertContactRequest): Promise<AlertContactResponse> {
    const user = await this.getAuthenticatedUser()
    return this.post<AlertContactResponse, AlertContactRequest>(`/alert-contacts?tenantId=${tenantId}`, data, user)
  }

  async updateAlertContact(id: number, data: AlertContactRequest): Promise<AlertContactResponse> {
    const user = await this.getAuthenticatedUser()
    return this.put<AlertContactResponse, AlertContactRequest>(`/alert-contacts/${id}`, data, user)
  }

  async deleteAlertContact(id: number): Promise<void> {
    const user = await this.getAuthenticatedUser()
    return this.delete(`/alert-contacts/${id}`, user)
  }

  async toggleAlertContactStatus(id: number): Promise<AlertContactResponse> {
    const user = await this.getAuthenticatedUser()
    return this.patch<AlertContactResponse>(`/alert-contacts/${id}/toggle-status`, user)
  }

  async sendTestNotification(id: number): Promise<void> {
    const user = await this.getAuthenticatedUser()
    return this.postVoid(`/alert-contacts/${id}/test`, null, user)
  }

  // Tenant methods
  async getTenants(activeOnly?: boolean): Promise<Tenant[]> {
    const params = new URLSearchParams()
    if (activeOnly !== undefined) {
      params.append('activeOnly', activeOnly.toString())
    }
    const query = params.toString() ? `?${params.toString()}` : ''

    const user = await this.getAuthenticatedUser()
    return this.get<Tenant[]>(`/tenants${query}`, user)
  }

  async getTenant(id: number): Promise<Tenant> {
    const user = await this.getAuthenticatedUser()
    return this.get<Tenant>(`/tenants/${id}`, user)
  }

  async createTenant(data: TenantRequest): Promise<Tenant> {
    const user = await this.getAuthenticatedUser()
    return this.post<Tenant, TenantRequest>('/tenants', data, user)
  }

  async updateTenant(id: number, data: Omit<TenantRequest, 'code'>): Promise<Tenant> {
    const user = await this.getAuthenticatedUser()
    return this.put<Tenant, Omit<TenantRequest, 'code'>>(`/tenants/${id}`, data, user)
  }

  async toggleTenantStatus(id: number): Promise<Tenant> {
    const user = await this.getAuthenticatedUser()
    return this.patch<Tenant>(`/tenants/${id}/toggle-status`, user)
  }

  async getTenantUsers(id: number): Promise<TenantUser[]> {
    const user = await this.getAuthenticatedUser()
    return this.get<TenantUser[]>(`/tenants/${id}/users`, user)
  }

  async assignUserToTenant(tenantId: number, email: string): Promise<void> {
    const user = await this.getAuthenticatedUser()
    const params = new URLSearchParams()
    params.append('email', email)
    return this.postVoid(`/tenants/${tenantId}/users?${params.toString()}`, null, user)
  }

  async removeUserFromTenant(tenantId: number, email: string): Promise<void> {
    const user = await this.getAuthenticatedUser()
    const params = new URLSearchParams()
    params.append('email', email)
    return this.deleteVoid(`/tenants/${tenantId}/users?${params.toString()}`, user)
  }

  private async getAuthenticatedUser(): Promise<User | null> {
    const authStore = useAuthStore()
    return authStore.user
  }
}

export const apiService = new ApiService()

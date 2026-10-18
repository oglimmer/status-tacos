import { User } from 'oidc-client-ts'
import type { AlertContactRequest, AlertContactResponse } from '../stores/alertContacts'
import type { MonitorRequest, MonitorResponse } from '../stores/monitors'
import { useAuthStore } from '../stores/auth'
import { ApiError, friendlyMessage } from './apiError'
import { showErrorToast } from './toasts'

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

export interface AccountDeletionResult {
  /** False if the server has no access to the identity provider: the login account still exists. */
  loginAccountDeleted: boolean
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

/** One monitor on the public status page. Timestamps are UTC without a zone suffix. */
export interface PublicMonitorStatus {
  name: string
  // Missing when the monitor has no check yet
  currentStatus?: 'up' | 'down'
  // Start of the current outage. Missing when the monitor is up.
  downSince?: string
  // Rounded down. Missing when there are no checks in the 90 days.
  uptimePercentage90d?: number
}

/** The public status page of one tenant. */
export interface PublicStatusPage {
  tenantName: string
  monitors: PublicMonitorStatus[]
}

/** The look of the public status page of a tenant. */
export interface StatusPageLook {
  // Mustache template of the page body. Blank or missing: the default template.
  htmlTemplate?: string | null
  // Blank or missing: no tenant CSS
  css?: string | null
}

export interface StatusPageConfig extends StatusPageLook {
  // Missing when the tenant never saved a look
  updatedAt?: string | null
  defaultHtmlTemplate: string
  baseCss: string
  starterCss: string
}

/** On an invalid template or CSS, html is missing and error tells why. */
export interface StatusPagePreview {
  html?: string | null
  error?: string | null
  removed: string[]
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

  private unauthorizedHandler: ((error: ApiError) => void) | null = null
  private userProvider: (() => Promise<User | null>) | null = null

  /** Called on every 401, for example to sign the user out. The auth store sets it. */
  setUnauthorizedHandler(handler: (error: ApiError) => void) {
    this.unauthorizedHandler = handler
  }

  /**
   * Gives the signed-in user with a valid access token, and refreshes the token when necessary.
   * The auth store sets it.
   */
  setUserProvider(provider: () => Promise<User | null>) {
    this.userProvider = provider
  }

  /**
   * Sends the request. On a network error or HTTP status >= 400 it shows a message to the user and
   * throws an ApiError. A 401 goes to the unauthorized handler instead of a message.
   */
  private async request(endpoint: string, method: string, user: User | null, data?: unknown): Promise<Response> {
    if (this.userProvider) {
      try {
        user = await this.userProvider()
      } catch (err) {
        console.error(`Token refresh before ${endpoint} failed:`, err)
        const error = new ApiError(friendlyMessage(0), 0)
        showErrorToast(error.message)
        throw error
      }
    }
    let headers: HeadersInit
    try {
      headers = this.getAuthHeaders(user)
    } catch {
      // No token (any more): the same as a 401 from the server
      const error = new ApiError(friendlyMessage(401), 401)
      this.handleUnauthorized(error)
      throw error
    }
    const init: RequestInit = { method, headers }
    if (data !== undefined) {
      init.body = JSON.stringify(data)
    }

    let response: Response
    try {
      response = await fetch(`${this.getApiBaseUrl()}${endpoint}`, init)
    } catch (err) {
      console.error(`Request to ${endpoint} failed:`, err)
      const error = new ApiError(friendlyMessage(0), 0)
      showErrorToast(error.message)
      throw error
    }

    if (!response.ok) {
      const body = await this.readErrorBody(response)
      const error = new ApiError(friendlyMessage(response.status, body.message), response.status, body.error)
      console.error(`Request to ${endpoint} failed: HTTP ${response.status}`, body)
      if (response.status === 401) {
        this.handleUnauthorized(error)
      } else {
        showErrorToast(error.message)
      }
      throw error
    }

    return response
  }

  private handleUnauthorized(error: ApiError) {
    if (this.unauthorizedHandler) {
      this.unauthorizedHandler(error)
    } else {
      showErrorToast(error.message)
    }
  }

  private async readErrorBody(response: Response): Promise<{ error?: string; message?: string }> {
    try {
      const body = await response.json()
      return {
        error: typeof body?.error === 'string' ? body.error : undefined,
        message: typeof body?.message === 'string' && body.message.trim() ? body.message : undefined,
      }
    } catch {
      return {}
    }
  }

  /**
   * The public status page of a tenant. No sign-in, and no error message: the page shows its own.
   * An unknown tenant code gives an ApiError with status 404.
   */
  async getPublicStatusPage(tenantCode: string): Promise<PublicStatusPage> {
    const response = await fetch(`${this.getApiBaseUrl()}/public/${encodeURIComponent(tenantCode)}/status`)
    if (!response.ok) {
      throw new ApiError(friendlyMessage(response.status), response.status)
    }
    return response.json()
  }

  /**
   * The URL of the branded status page (HTML). The server sends it with a sandbox CSP, so show it
   * in a frame or open it directly; any site may embed it.
   */
  publicStatusPageUrl(tenantCode: string): string {
    return `${this.getApiBaseUrl()}/public/${encodeURIComponent(tenantCode)}/status/page`
  }

  async get<T>(endpoint: string, user: User | null): Promise<T> {
    const response = await this.request(endpoint, 'GET', user)
    return response.json()
  }

  async post<T, U>(endpoint: string, data: U, user: User | null): Promise<T> {
    const response = await this.request(endpoint, 'POST', user, data)
    return response.json()
  }

  async postVoid<U>(endpoint: string, data: U, user: User | null): Promise<void> {
    await this.request(endpoint, 'POST', user, data)
  }

  async put<T, U>(endpoint: string, data: U, user: User | null): Promise<T> {
    const response = await this.request(endpoint, 'PUT', user, data)
    return response.json()
  }

  async delete(endpoint: string, user: User | null): Promise<void> {
    await this.request(endpoint, 'DELETE', user)
  }

  async deleteVoid(endpoint: string, user: User | null): Promise<void> {
    await this.request(endpoint, 'DELETE', user)
  }

  async patch<T>(endpoint: string, user: User | null): Promise<T> {
    const response = await this.request(endpoint, 'PATCH', user)
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

  // Status page look methods
  async getStatusPage(tenantId: number): Promise<StatusPageConfig> {
    const user = await this.getAuthenticatedUser()
    return this.get<StatusPageConfig>(`/tenants/${tenantId}/status-page`, user)
  }

  async saveStatusPage(tenantId: number, look: StatusPageLook): Promise<StatusPageConfig> {
    const user = await this.getAuthenticatedUser()
    return this.put<StatusPageConfig, StatusPageLook>(`/tenants/${tenantId}/status-page`, look, user)
  }

  async resetStatusPage(tenantId: number): Promise<void> {
    const user = await this.getAuthenticatedUser()
    return this.deleteVoid(`/tenants/${tenantId}/status-page`, user)
  }

  async previewStatusPage(tenantId: number, look: StatusPageLook): Promise<StatusPagePreview> {
    const user = await this.getAuthenticatedUser()
    return this.post<StatusPagePreview, StatusPageLook>(`/tenants/${tenantId}/status-page/preview`, look, user)
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

  /** A new code moves the public status page to /<code>/status. The old URL then gives 404. */
  async updateTenant(id: number, data: TenantRequest): Promise<Tenant> {
    const user = await this.getAuthenticatedUser()
    return this.put<Tenant, TenantRequest>(`/tenants/${id}`, data, user)
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

  /** Deletes the account of the signed-in user, with all tenants only they belong to. */
  async deleteAccount(user: User | null): Promise<AccountDeletionResult> {
    const response = await this.request('/users/me', 'DELETE', user)
    return response.json()
  }

  private async getAuthenticatedUser(): Promise<User | null> {
    const authStore = useAuthStore()
    return authStore.user
  }
}

export const apiService = new ApiService()

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()

interface OidcUserData {
  refresh_token?: string
  [key: string]: unknown
}

const oidcUserData = ref<OidcUserData | null>(null)
const refreshToken = ref<string | null>(null)

const formattedUser = computed(() => {
  if (!authStore.user) return null
  return {
    profile: authStore.user.profile,
    expired: authStore.user.expired,
    expires_at: authStore.user.expires_at,
    expires_in_seconds: authStore.user.expires_at
      ? authStore.user.expires_at - Math.floor(Date.now() / 1000)
      : null,
    has_refresh_token: !!authStore.user.refresh_token,
    access_token: authStore.user.access_token?.substring(0, 50) + '...',
    refresh_token: authStore.user.refresh_token?.substring(0, 50) + '...',
  }
})

const loadDebugInfo = () => {
  // Get OIDC user data from localStorage
  const oidcKey = Object.keys(localStorage).find((key) => key.startsWith('oidc.user:'))
  if (oidcKey) {
    try {
      oidcUserData.value = JSON.parse(localStorage.getItem(oidcKey) || '{}') as OidcUserData
      refreshToken.value = oidcUserData.value?.refresh_token || null
    } catch (e) {
      console.error('Failed to parse OIDC user data:', e)
    }
  }
}

const clearAllAuth = () => {
  // Clear all auth-related data
  const oidcKeys = Object.keys(localStorage).filter((key) => key.startsWith('oidc.'))
  oidcKeys.forEach((key) => localStorage.removeItem(key))
  localStorage.removeItem('status-tacos-user-config')

  // Reload to reset state
  window.location.reload()
}

const testRefreshToken = async () => {
  try {
    await authStore.renewToken()
    alert('Token refresh successful!')
    loadDebugInfo()
  } catch (error) {
    alert('Token refresh failed: ' + error)
  }
}

onMounted(() => {
  loadDebugInfo()
})
</script>

<template>
  <div class="debug-container">
    <h1>Authentication Debug Info</h1>

    <div class="debug-section">
      <h2>Current Auth State</h2>
      <div class="info-grid">
        <div class="info-item">
          <strong>Authenticated:</strong>
          <span :class="authStore.isAuthenticated ? 'success' : 'error'">
            {{ authStore.isAuthenticated }}
          </span>
        </div>
        <div class="info-item">
          <strong>Loading:</strong>
          <span>{{ authStore.isLoading }}</span>
        </div>
        <div class="info-item" v-if="authStore.error">
          <strong>Error:</strong>
          <span class="error">{{ authStore.error }}</span>
        </div>
      </div>
    </div>

    <div class="debug-section" v-if="formattedUser">
      <h2>User Info</h2>
      <pre class="code-block">{{ JSON.stringify(formattedUser, null, 2) }}</pre>
    </div>

    <div class="debug-section">
      <h2>Stored Tokens</h2>
      <div class="token-info">
        <h3>OIDC User Data (from localStorage)</h3>
        <div v-if="oidcUserData">
          <p><strong>Has Refresh Token:</strong> {{ !!refreshToken }}</p>
          <p v-if="refreshToken">
            <strong>Refresh Token (first 50 chars):</strong> {{ refreshToken.substring(0, 50) }}...
          </p>
        </div>
        <p v-else>No OIDC user data found in localStorage</p>
      </div>
    </div>

    <div class="debug-section">
      <h2>Actions</h2>
      <div class="actions">
        <button @click="loadDebugInfo" class="btn btn-secondary">Reload Debug Info</button>
        <button
          @click="testRefreshToken"
          class="btn btn-secondary"
          :disabled="!authStore.isAuthenticated"
        >
          Test Token Refresh
        </button>
        <button @click="clearAllAuth" class="btn btn-danger">Clear All Auth Data</button>
        <router-link to="/" class="btn btn-primary"> Back to Home </router-link>
      </div>
    </div>

    <div class="debug-section">
      <h2>Instructions</h2>
      <ol>
        <li>Go to the home page and sign in</li>
        <li>After successful login, come back to this page</li>
        <li>Check if OIDC user data with refresh token is stored in localStorage</li>
        <li>Wait for the access token to expire (check expires_in_seconds)</li>
        <li>Refresh the page to see if authentication persists automatically</li>
        <li>Authentication data is now always stored in localStorage for persistence</li>
      </ol>
    </div>
  </div>
</template>

<style scoped>
.debug-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

h1 {
  color: #2c3e50;
  margin-bottom: 2rem;
}

.debug-section {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 2rem;
}

.debug-section h2 {
  color: #495057;
  margin-bottom: 1rem;
  font-size: 1.5rem;
}

.debug-section h3 {
  color: #6c757d;
  margin-bottom: 0.5rem;
  font-size: 1.2rem;
}

.info-grid {
  display: grid;
  gap: 1rem;
}

.info-item {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.info-item strong {
  min-width: 120px;
}

.success {
  color: #28a745;
  font-weight: bold;
}

.error {
  color: #dc3545;
  font-weight: bold;
}

.code-block {
  background: #2c3e50;
  color: #ecf0f1;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 0.9rem;
}

.token-info {
  margin-bottom: 1.5rem;
}

.token-info p {
  margin: 0.5rem 0;
  word-break: break-all;
}

.actions {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  transition: all 0.2s;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #c82333;
}

ol {
  margin: 0;
  padding-left: 1.5rem;
}

ol li {
  margin: 0.5rem 0;
  line-height: 1.6;
}
</style>

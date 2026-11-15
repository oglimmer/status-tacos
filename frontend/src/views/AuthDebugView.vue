<script setup lang="ts">
import AppShell from '../components/AppShell.vue'
import PageHeader from '../components/PageHeader.vue'
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
  <AppShell>
    <PageHeader title="Authentication debug" subtitle="Internal page: the sign-in state of this browser.">
      <template #actions>
        <button @click="loadDebugInfo" class="btn btn-secondary">Reload</button>
        <button
          @click="testRefreshToken"
          class="btn btn-secondary"
          :disabled="!authStore.isAuthenticated"
        >
          Test token refresh
        </button>
        <button @click="clearAllAuth" class="btn btn-danger">Clear all auth data</button>
      </template>
    </PageHeader>

    <section class="page-section">
      <h2 class="page-section-title">Current auth state</h2>
      <div class="card">
        <dl class="kv">
          <dt>Authenticated</dt>
          <dd>
            <span class="badge" :class="authStore.isAuthenticated ? 'badge-up' : 'badge-down'">
              {{ authStore.isAuthenticated }}
            </span>
          </dd>
          <dt>Loading</dt>
          <dd class="mono">{{ authStore.isLoading }}</dd>
          <template v-if="authStore.error">
            <dt>Error</dt>
            <dd class="error">{{ authStore.error }}</dd>
          </template>
        </dl>
      </div>
    </section>

    <section v-if="formattedUser" class="page-section">
      <h2 class="page-section-title">User info</h2>
      <pre class="code-block">{{ JSON.stringify(formattedUser, null, 2) }}</pre>
    </section>

    <section class="page-section">
      <h2 class="page-section-title">Stored tokens</h2>
      <div class="card">
        <p class="muted">OIDC user data from localStorage</p>
        <dl v-if="oidcUserData" class="kv">
          <dt>Has refresh token</dt>
          <dd class="mono">{{ !!refreshToken }}</dd>
          <template v-if="refreshToken">
            <dt>Refresh token (first 50 chars)</dt>
            <dd class="mono">{{ refreshToken.substring(0, 50) }}...</dd>
          </template>
        </dl>
        <p v-else>No OIDC user data found in localStorage</p>
      </div>
    </section>

    <section class="page-section">
      <h2 class="page-section-title">Instructions</h2>
      <div class="card">
        <ol>
          <li>Go to the home page and sign in</li>
          <li>After successful login, come back to this page</li>
          <li>Check if OIDC user data with refresh token is stored in localStorage</li>
          <li>Wait for the access token to expire (check expires_in_seconds)</li>
          <li>Refresh the page to see if authentication persists automatically</li>
          <li>Authentication data is now always stored in localStorage for persistence</li>
        </ol>
      </div>
    </section>
  </AppShell>
</template>

<style scoped>
.card > p.muted {
  margin: 0 0 var(--space-2);
  font-size: var(--text-sm);
}

.error {
  color: var(--down);
  font-weight: 700;
}

.code-block {
  margin: 0;
  padding: var(--space-4);
  overflow-x: auto;
  background: var(--surface-2);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--text-sm);
}

ol {
  margin: 0;
  padding-left: var(--space-5);
}

ol li + li {
  margin-top: var(--space-1);
}
</style>

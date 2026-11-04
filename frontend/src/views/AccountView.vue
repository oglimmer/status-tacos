<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore, ACCOUNT_CONSOLE_URL } from '../stores/auth'
import type { CurrentUser } from '../services/api'
import DashboardHeader from '../components/DashboardHeader.vue'
import PageNavigation from '../components/PageNavigation.vue'
import DeleteAccountModal from '../components/DeleteAccountModal.vue'
import ApiTokens from '../components/ApiTokens.vue'
import VersionInfo from '../components/VersionInfo.vue'

const authStore = useAuthStore()

const currentUser = ref<CurrentUser | null>(null)
const loadError = ref(false)
const showDeleteAccount = ref(false)

const profile = computed(() => authStore.user?.profile)

const memberSince = computed(() =>
  currentUser.value ? new Date(currentUser.value.createdAt).toLocaleDateString() : null
)

onMounted(async () => {
  try {
    currentUser.value = await authStore.fetchCurrentUser()
  } catch {
    loadError.value = true
  }
})
</script>

<template>
  <div class="account-container">
    <DashboardHeader />

    <main class="dashboard-main">
      <PageNavigation />

      <div class="account-content">
        <section class="card">
          <h2>Account</h2>
          <dl class="fields">
            <template v-if="profile?.name">
              <dt>Name</dt>
              <dd>{{ profile.name }}</dd>
            </template>
            <template v-if="profile?.email">
              <dt>Email</dt>
              <dd>
                {{ profile.email }}
                <span v-if="profile.email_verified === false" class="badge">not verified</span>
              </dd>
            </template>
            <template v-if="profile?.preferred_username">
              <dt>Username</dt>
              <dd>{{ profile.preferred_username }}</dd>
            </template>
            <template v-if="memberSince">
              <dt>Member since</dt>
              <dd>{{ memberSince }}</dd>
            </template>
          </dl>
          <div class="actions">
            <a :href="ACCOUNT_CONSOLE_URL" target="_blank" rel="noopener" class="btn btn-outline">
              Manage Login &amp; Password ↗
            </a>
            <button type="button" class="btn btn-secondary" @click="authStore.logout()">
              Sign Out
            </button>
          </div>
        </section>

        <section class="card">
          <h2>Your Tenants</h2>
          <p v-if="loadError" class="muted">Your tenants could not be loaded.</p>
          <p v-else-if="!currentUser" class="muted">Loading...</p>
          <p v-else-if="currentUser.tenants.length === 0" class="muted">
            You do not belong to a tenant.
          </p>
          <ul v-else class="tenant-list">
            <li v-for="tenant in currentUser.tenants" :key="tenant.id">
              <span class="tenant-name">{{ tenant.name }}</span>
              <code class="tenant-code">{{ tenant.code }}</code>
            </li>
          </ul>
          <router-link to="/tenants" class="link">Manage tenants →</router-link>
        </section>

        <ApiTokens :peer-tenant-id="currentUser?.peerTenantId ?? null" />

        <section class="card card-danger">
          <h2>Delete Account</h2>
          <p class="muted">
            Deletes your login account and every tenant only you belong to, with its monitors and
            alert contacts. Shared tenants stay with their other members.
          </p>
          <button type="button" class="btn btn-danger" @click="showDeleteAccount = true">
            Delete Account
          </button>
        </section>

        <section class="card">
          <h2>About</h2>
          <div class="about">
            <VersionInfo />
            <nav class="legal" aria-label="Legal">
              <router-link to="/privacy">Privacy Policy</router-link>
              <router-link to="/terms">Terms of Service</router-link>
              <router-link to="/imprint">Imprint &amp; Support</router-link>
            </nav>
          </div>
        </section>
      </div>
    </main>

    <DeleteAccountModal v-if="showDeleteAccount" @close="showDeleteAccount = false" />
  </div>
</template>

<style scoped>
.account-container {
  min-height: 100vh;
  background: var(--page-bg);
  background-attachment: fixed;
}

.dashboard-main {
  padding: 2rem;
  max-width: 1200px;
  margin: 0 auto;
  background: var(--glass);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  margin-top: 2rem;
  border: 1px solid var(--glass-border);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
}

.account-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 720px;
}

.card {
  background: var(--surface);
  border: 1px solid var(--border-subtle);
  border-radius: 12px;
  padding: 1.5rem;
}

.card h2 {
  margin: 0 0 1rem;
  font-size: 1.15rem;
  color: var(--text);
}

.card-danger {
  border-color: var(--danger-soft-border);
}

.card-danger h2 {
  color: var(--danger-text);
}

.fields {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.6rem 1.5rem;
  margin: 0 0 1.5rem;
}

.fields dt {
  color: var(--text-muted);
}

.fields dd {
  margin: 0;
  color: var(--text);
  font-weight: 500;
  word-break: break-word;
}

.badge {
  margin-left: 0.5rem;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: var(--warning-soft);
  color: var(--warning-soft-text);
  font-size: 0.75rem;
  font-weight: 600;
}

.muted {
  color: var(--text-muted);
  margin: 0 0 1rem;
  line-height: 1.5;
}

.tenant-list {
  list-style: none;
  margin: 0 0 1rem;
  padding: 0;
}

.tenant-list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--border-subtle);
}

.tenant-name {
  color: var(--text);
  font-weight: 500;
}

.tenant-code {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.link {
  color: var(--link);
  text-decoration: none;
  font-weight: 500;
}

.link:hover {
  text-decoration: underline;
}

.about {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.legal {
  display: flex;
  flex-wrap: wrap;
  gap: 1.25rem;
}

.legal a {
  color: var(--text-2);
  text-underline-offset: 0.2em;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  text-decoration: none;
}

.btn-outline {
  background: transparent;
  color: var(--link);
  border: 2px solid var(--accent);
}

.btn-outline:hover {
  background: var(--accent);
  color: white;
}

.btn-secondary {
  background: var(--surface-2);
  color: var(--text-2);
  border: 1px solid var(--input-border);
}

.btn-secondary:hover {
  background: var(--surface-3);
}

.btn-danger {
  background: var(--danger);
  color: white;
}

.btn-danger:hover {
  background: var(--danger-hover);
}

/* Mobile styles */
@media (max-width: 768px) {
  .dashboard-main {
    padding: 1rem;
    margin: 0;
    border-radius: 0;
    border: none;
    background: transparent;
    backdrop-filter: none;
    box-shadow: none;
  }

  .fields {
    grid-template-columns: 1fr;
    gap: 0.2rem;
  }

  .fields dd {
    margin-bottom: 0.6rem;
  }
}
</style>

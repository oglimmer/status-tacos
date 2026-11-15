<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore, ACCOUNT_CONSOLE_URL } from '../stores/auth'
import type { CurrentUser } from '../services/api'
import AppShell from '../components/AppShell.vue'
import PageHeader from '../components/PageHeader.vue'
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
  <AppShell>
    <div class="account">
      <PageHeader title="Account" subtitle="Your login, your API tokens and this app.">
        <template #actions>
          <a :href="ACCOUNT_CONSOLE_URL" target="_blank" rel="noopener" class="btn btn-outline">
            Manage login and password
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" />
            </svg>
            <span class="sr-only">(opens in a new tab)</span>
          </a>
          <button type="button" class="btn btn-secondary" @click="authStore.logout()">
            Sign out
          </button>
        </template>
      </PageHeader>

      <section class="page-section" aria-labelledby="profile-title">
        <h2 id="profile-title" class="page-section-title">Profile</h2>
        <div class="card">
          <dl class="kv">
            <template v-if="profile?.name">
              <dt>Name</dt>
              <dd>{{ profile.name }}</dd>
            </template>
            <template v-if="profile?.email">
              <dt>Email</dt>
              <dd>
                {{ profile.email }}
                <span v-if="profile.email_verified === false" class="badge badge-warning">not verified</span>
              </dd>
            </template>
            <template v-if="profile?.preferred_username">
              <dt>Username</dt>
              <dd class="mono">{{ profile.preferred_username }}</dd>
            </template>
            <template v-if="memberSince">
              <dt>Member since</dt>
              <dd class="num">{{ memberSince }}</dd>
            </template>
            <dt>Tenants</dt>
            <dd>
              <span v-if="loadError" class="muted">Your tenants could not be loaded.</span>
              <span v-else-if="!currentUser" class="muted">Loading...</span>
              <span v-else-if="currentUser.tenants.length === 0" class="muted">
                You do not belong to a tenant.
              </span>
              <ul v-else class="tenant-list">
                <li v-for="tenant in currentUser.tenants" :key="tenant.id">
                  <span>{{ tenant.name }}</span>
                  <code class="tenant-code">{{ tenant.code }}</code>
                </li>
              </ul>
              <router-link to="/tenants" class="manage-link">Manage tenants</router-link>
            </dd>
          </dl>
        </div>
      </section>

      <ApiTokens :peer-tenant-id="currentUser?.peerTenantId ?? null" />

      <section class="page-section" aria-labelledby="about-title">
        <h2 id="about-title" class="page-section-title">About</h2>
        <div class="card about">
          <VersionInfo />
          <nav class="legal" aria-label="Legal">
            <router-link to="/privacy">Privacy policy</router-link>
            <router-link to="/terms">Terms of service</router-link>
            <router-link to="/imprint">Imprint and support</router-link>
          </nav>
        </div>
      </section>

      <!-- Last and set apart: the one action on this page that cannot be undone -->
      <section class="page-section danger-zone" aria-labelledby="danger-title">
        <h2 id="danger-title" class="page-section-title">Danger zone</h2>
        <div class="card danger-card">
          <div class="danger-text">
            <h3 class="danger-name">Delete account</h3>
            <p class="muted">
              Deletes your login account and every tenant only you belong to, with its monitors and
              alert contacts. Shared tenants stay with their other members.
            </p>
          </div>
          <button type="button" class="btn btn-danger" @click="showDeleteAccount = true">
            Delete account
          </button>
        </div>
      </section>
    </div>

    <DeleteAccountModal v-if="showDeleteAccount" @close="showDeleteAccount = false" />
  </AppShell>
</template>

<style scoped>
.account {
  max-width: 52rem;
}

.kv .badge {
  margin-left: var(--space-2);
  vertical-align: 0.1em;
}

.tenant-list {
  margin: 0 0 var(--space-1);
  padding: 0;
  list-style: none;
}

.tenant-list li {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
}

.tenant-code {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.manage-link {
  font-size: var(--text-sm);
  color: var(--link);
}

.about {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
}

.legal {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-5);
  font-size: var(--text-sm);
}

.legal a {
  color: var(--text-2);
  text-underline-offset: 0.2em;
}

.legal a:hover {
  color: var(--link);
}

.danger-zone {
  margin-top: var(--space-7);
  padding-top: var(--space-5);
  border-top: 1px solid var(--border);
}

.danger-zone .page-section-title {
  color: var(--down);
}

.danger-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
  border-color: var(--danger-soft-border);
}

.danger-text {
  flex: 1 1 20rem;
}

.danger-name {
  margin: 0 0 var(--space-1);
  font-size: var(--text-md);
}

.danger-text p {
  margin: 0;
  font-size: var(--text-sm);
}
</style>

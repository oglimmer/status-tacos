<script setup lang="ts">
import { computed, ref } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import type { Tenant } from '../services/api'
import UserManagement from './UserManagement.vue'
import StatusPageEditor from './StatusPageEditor.vue'

// One tenant as a row in the list. Below the facts are two sections: the status page and the users.
// The section labels sit in the same column as the fact labels, so all labels line up.

const props = defineProps<{
  tenant: Tenant
}>()

defineEmits<{
  'edit': [tenant: Tenant]
  'toggle-status': [id: number]
}>()

const tenantsStore = useTenantsStore()
const showUserManagement = ref(false)
const showStatusPageEditor = ref(false)

// The public status page of this tenant (the same path as in StatusPageEditor).
const statusPagePath = computed(() => `/${encodeURIComponent(props.tenant.code)}/status`)

const usersPanelId = computed(() => `tenant-users-${props.tenant.id}`)

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString()
}

const handleToggleUserManagement = async (tenant: Tenant) => {
  if (!showUserManagement.value) {
    try {
      await tenantsStore.fetchTenantUsers(tenant.id)
    } catch (error) {
      console.error('Error fetching users:', error)
    }
  }
  showUserManagement.value = !showUserManagement.value
}
</script>

<template>
  <li
    class="list-row entity-row tenant-row"
    :class="{ 'is-inactive': !tenant.isActive }"
  >
    <div class="entity-head">
      <h3 class="entity-name">{{ tenant.name }}</h3>
      <div class="entity-badges">
        <span class="badge badge-neutral tenant-code mono">{{ tenant.code }}</span>
        <span class="badge" :class="tenant.isActive ? 'badge-up' : 'badge-neutral'">
          {{ tenant.isActive ? 'Active' : 'Inactive' }}
        </span>
      </div>
    </div>

    <dl class="facts">
      <div v-if="tenant.description">
        <dt>Description</dt>
        <dd class="description">{{ tenant.description }}</dd>
      </div>
      <div>
        <dt>Created</dt>
        <dd class="num">{{ formatDate(tenant.createdAt) }}</dd>
      </div>
      <div>
        <dt>Updated</dt>
        <dd class="num">{{ formatDate(tenant.updatedAt) }}</dd>
      </div>
    </dl>

    <div class="entity-actions">
      <button type="button" class="btn btn-outline btn-sm" @click="$emit('edit', tenant)">
        Edit
      </button>
      <button type="button" class="btn btn-quiet btn-sm" @click="$emit('toggle-status', tenant.id)">
        {{ tenant.isActive ? 'Deactivate' : 'Activate' }}
      </button>
    </div>

    <div class="tenant-sections">
      <section class="tenant-section" :aria-label="`Status page of ${tenant.name}`">
        <h4 class="tenant-section-label">Status page</h4>
        <div class="tenant-section-body">
          <a
            :href="statusPagePath"
            class="status-link mono truncate"
            target="_blank"
            rel="noopener"
            :title="`Open the public status page ${statusPagePath}`"
          >{{ statusPagePath }}</a>
          <button type="button" class="btn btn-quiet btn-sm" @click="showStatusPageEditor = true">
            Edit status page
          </button>
        </div>
      </section>

      <section class="tenant-section" :aria-label="`Users of ${tenant.name}`">
        <h4 class="tenant-section-label">Users</h4>
        <div class="tenant-section-body">
          <span class="muted">Who can see and change this tenant.</span>
          <button
            type="button"
            class="btn btn-quiet btn-sm"
            :aria-expanded="showUserManagement"
            :aria-controls="usersPanelId"
            @click="handleToggleUserManagement(tenant)"
          >
            {{ showUserManagement ? 'Hide users' : 'Manage users' }}
            <svg
              class="chevron"
              :class="{ open: showUserManagement }"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M6 9l6 6 6-6" />
            </svg>
          </button>
        </div>
        <UserManagement
          v-if="showUserManagement"
          :id="usersPanelId"
          class="tenant-users"
          :tenant="tenant"
          :users="tenantsStore.getTenantUsers(tenant.id)"
          :is-loading="tenantsStore.isLoadingUsers"
          @assign-user="(email) => tenantsStore.assignUserToTenant(tenant.id, email)"
          @remove-user="(email) => tenantsStore.removeUserFromTenant(tenant.id, email)"
        />
      </section>
    </div>

    <StatusPageEditor
      v-if="showStatusPageEditor"
      :tenant="tenant"
      @close="showStatusPageEditor = false"
    />
  </li>
</template>

<style scoped>
.description {
  overflow-wrap: anywhere;
}

/* Under the facts, from the facts column to the end of the row. */
.tenant-sections {
  grid-column: 2 / -1;
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-2);
  border-top: 1px solid var(--border-subtle);
}

.tenant-section {
  display: grid;
  grid-template-columns: var(--fact-label) minmax(0, 1fr);
  column-gap: var(--space-3);
  align-items: center;
  font-size: var(--text-sm);
}

.tenant-section-label {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
  color: var(--text-2);
}

.tenant-section-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-1) var(--space-3);
  min-width: 0;
}

.status-link {
  min-width: 0;
  color: var(--link);
  text-decoration: none;
}

.status-link:hover {
  color: var(--link-hover);
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

.chevron {
  transition: transform 0.15s;
}

.chevron.open {
  transform: rotate(180deg);
}

/* The open user list starts under the section text, in the value column. */
.tenant-users {
  grid-column: 2;
  margin-top: var(--space-2);
}

@media (max-width: 760px) {
  .tenant-sections {
    grid-column: 1 / -1;
  }
}

@media (max-width: 520px) {
  .tenant-section {
    grid-template-columns: minmax(0, 1fr);
    row-gap: var(--space-1);
  }

  .tenant-users {
    grid-column: 1;
  }
}
</style>

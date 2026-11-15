<script setup lang="ts">
import type { AlertContactResponse } from '../stores/alertContacts'

// One alert contact as a row in the list: name and badges, the facts, the actions.

defineProps<{
  contact: AlertContactResponse
}>()

defineEmits<{
  'edit': [contact: AlertContactResponse]
  'toggle-status': [id: number]
  'delete': [id: number]
  'test-notification': [id: number]
}>()

const typeLabel = (type: AlertContactResponse['type']) => {
  switch (type) {
    case 'EMAIL':
      return 'Email'
    case 'HTTP':
      return 'HTTP'
    case 'TEAMS':
      return 'Teams'
    case 'IOS_PUSH':
      return 'iOS Push'
  }
}

const ownerLabel = (owner: NonNullable<AlertContactResponse['owner']>) =>
  owner.name ? `${owner.name} (${owner.email})` : owner.email

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString()
}

const monitorNames = (contact: AlertContactResponse) =>
  (contact.monitors ?? []).map(m => m.name).join(', ')
</script>

<template>
  <li
    class="list-row entity-row contact-row"
    :class="{ 'is-inactive': !contact.isActive }"
  >
    <div class="entity-head">
      <h3 class="entity-name">{{ contact.name || 'Unnamed Contact' }}</h3>
      <div class="entity-badges">
        <span class="badge badge-neutral contact-type" :data-type="contact.type">
          {{ typeLabel(contact.type) }}
        </span>
        <span class="badge" :class="contact.isActive ? 'badge-up' : 'badge-neutral'">
          {{ contact.isActive ? 'Active' : 'Inactive' }}
        </span>
      </div>
    </div>

    <dl class="facts">
      <!-- IOS_PUSH: value is an internal key, show the owning user instead -->
      <div v-if="contact.type === 'IOS_PUSH'">
        <dt>Owner</dt>
        <dd class="contact-owner truncate">{{ contact.owner ? ownerLabel(contact.owner) : 'Unknown' }}</dd>
      </div>
      <div v-else>
        <dt>{{ contact.type === 'EMAIL' ? 'Email' : 'URL' }}</dt>
        <dd class="contact-value mono truncate" :title="contact.value">{{ contact.value }}</dd>
      </div>
      <div v-if="contact.type === 'HTTP'">
        <dt>Method</dt>
        <dd>
          <span class="mono">{{ contact.httpMethod || 'GET' }}</span>
          <span v-if="contact.httpMethod === 'POST' && contact.httpContentType" class="muted">
            ({{ contact.httpContentType }})
          </span>
        </dd>
      </div>
      <div v-if="contact.type === 'HTTP' && contact.httpHeaders && Object.keys(contact.httpHeaders).length > 0">
        <dt>Headers</dt>
        <dd>
          <span
            v-for="(value, key) in contact.httpHeaders"
            :key="key"
            class="header-item mono truncate"
            :title="`${key}: ${value}`"
          >{{ key }}: {{ value }}</span>
        </dd>
      </div>
      <div v-if="contact.type === 'HTTP' && contact.httpBody">
        <dt>Body</dt>
        <dd class="mono truncate" :title="contact.httpBody">{{ contact.httpBody }}</dd>
      </div>
      <div>
        <dt>Alerts for</dt>
        <dd v-if="contact.allMonitors">All monitors</dd>
        <dd v-else-if="!contact.monitors?.length" class="no-scope">No monitor selected</dd>
        <dd v-else class="monitor-scope truncate" :title="monitorNames(contact)">{{ monitorNames(contact) }}</dd>
      </div>
      <div>
        <dt>Tenant</dt>
        <dd class="truncate">{{ contact.tenant.name }}</dd>
      </div>
      <div>
        <dt>Created</dt>
        <dd class="num">{{ formatDate(contact.createdAt) }}</dd>
      </div>
      <div v-if="contact.updatedAt !== contact.createdAt">
        <dt>Updated</dt>
        <dd class="num">{{ formatDate(contact.updatedAt) }}</dd>
      </div>
    </dl>

    <p v-if="contact.type === 'IOS_PUSH'" class="managed-note">
      Managed in the iOS app by its owner.
    </p>
    <div v-else class="entity-actions">
      <button
        type="button"
        class="btn btn-outline btn-sm"
        @click="$emit('edit', contact)"
      >
        Edit
      </button>
      <button
        type="button"
        class="btn btn-quiet btn-sm"
        :disabled="!contact.isActive"
        :title="!contact.isActive ? 'Contact must be active to send test notification' : 'Send test notification'"
        @click="$emit('test-notification', contact.id)"
      >
        Test
      </button>
      <button
        type="button"
        class="btn btn-quiet btn-sm"
        @click="$emit('toggle-status', contact.id)"
      >
        {{ contact.isActive ? 'Disable' : 'Enable' }}
      </button>
      <button
        type="button"
        class="btn btn-quiet-danger btn-sm"
        @click="$emit('delete', contact.id)"
      >
        Delete
      </button>
    </div>
  </li>
</template>

<style scoped>
/* A contact without monitors gets no alerts: say it in the warning colour. */
.no-scope {
  color: var(--warning-soft-text);
  font-weight: 700;
}

.header-item {
  font-size: var(--text-xs);
  line-height: 1.6;
}

.managed-note {
  margin: 0;
  max-width: 14rem;
  font-size: var(--text-sm);
  color: var(--text-muted);
}
</style>

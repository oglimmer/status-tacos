<script setup lang="ts">
import { ref } from 'vue'
import type { Tenant, TenantUser } from '../services/api'

// The users of one tenant: a form to add a user by email, then one row per user.

const props = defineProps<{
  tenant: Tenant
  users: TenantUser[]
  isLoading: boolean
}>()

const emit = defineEmits<{
  'assign-user': [email: string]
  'remove-user': [email: string]
}>()

const newUserEmail = ref('')
const isAssigning = ref(false)
const inputId = `add-user-${props.tenant.id}`

const fullName = (user: TenantUser) =>
  [user.firstName, user.lastName].filter(Boolean).join(' ')

const handleAssignUser = async () => {
  if (!newUserEmail.value.trim() || isAssigning.value) return

  isAssigning.value = true
  try {
    await emit('assign-user', newUserEmail.value.trim())
    newUserEmail.value = ''
  } catch (err) {
    console.error('Failed to assign user:', err)
  } finally {
    isAssigning.value = false
  }
}

const handleRemoveUser = async (email: string) => {
  if (confirm(`Are you sure you want to remove ${email} from this tenant?`)) {
    try {
      await emit('remove-user', email)
    } catch (err) {
      console.error('Failed to remove user:', err)
    }
  }
}
</script>

<template>
  <div class="user-management">
    <h4 class="sr-only">Users in {{ tenant.name }}</h4>

    <form class="add-user" @submit.prevent="handleAssignUser">
      <label :for="inputId" class="sr-only">Email of the user to add</label>
      <input
        :id="inputId"
        v-model="newUserEmail"
        type="email"
        class="input"
        placeholder="Email of the user to add"
        autocomplete="off"
        :disabled="isAssigning"
      />
      <button
        type="submit"
        :disabled="!newUserEmail.trim() || isAssigning"
        class="btn btn-secondary btn-sm"
      >
        {{ isAssigning ? 'Adding...' : 'Add User' }}
      </button>
    </form>

    <p v-if="isLoading" class="users-note">
      Loading users...
    </p>

    <p v-else-if="users.length === 0" class="users-note">
      No users assigned to this tenant.
    </p>

    <ul v-else class="users-list">
      <li v-for="user in users" :key="user.id" class="user-row">
        <span class="user-name truncate" :class="{ muted: !fullName(user) }">
          {{ fullName(user) || 'No name' }}
        </span>
        <span class="user-email mono truncate" :title="user.email">{{ user.email }}</span>
        <button
          type="button"
          class="btn btn-quiet-danger btn-sm"
          :aria-label="`Remove ${user.email}`"
          @click="handleRemoveUser(user.email)"
        >
          Remove
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.user-management {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

/* Input and button on one row. */
.add-user {
  display: flex;
  gap: var(--space-2);
}

.add-user .input {
  flex: 1 1 auto;
  min-width: 0;
  min-height: 1.875rem;
  padding-block: var(--space-1);
  font-size: var(--text-sm);
}

.users-note {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-muted);
}

.users-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.user-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-1) var(--space-1) var(--space-1) var(--space-3);
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--text-sm);
}

.user-row:last-child {
  border-bottom: 0;
}

.user-name {
  font-weight: 700;
  color: var(--text);
}

.user-name.muted {
  font-weight: 400;
  color: var(--text-muted);
}

.user-email {
  color: var(--text-2);
}

/* On a phone the name and the email stack, the button stays on the right. */
@media (max-width: 520px) {
  .user-row {
    grid-template-columns: minmax(0, 1fr) auto;
    row-gap: 0;
  }

  .user-email {
    grid-row: 2;
  }

  .user-row .btn {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
}
</style>

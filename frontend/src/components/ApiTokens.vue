<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { askConfirm } from '../services/confirm'
import { apiService, type ApiToken, type ApiTokenScope, type CreatedApiToken } from '../services/api'
import { showErrorToast } from '../services/toasts'
import { parseUtc } from '../utils/uptime'

const MAX_NAME_LENGTH = 100

// Set when the user is a member of the peer tenant: then the user can create PEER tokens
const props = defineProps<{ peerTenantId?: number | null }>()

const tokens = ref<ApiToken[]>([])
const isLoading = ref(true)
const loadError = ref(false)

const newName = ref('')
const newScope = ref<ApiTokenScope>('GRAFANA')
const isCreating = ref(false)
// Shown once after creation; the server cannot show it again
const createdToken = ref<CreatedApiToken | null>(null)
const copied = ref(false)

const deletingId = ref<number | null>(null)

const dataSourceUrl = computed(() => `${window.location.origin}/api/prom`)
const peerHeartbeatUrl = computed(() => `${window.location.origin}/api/peer/heartbeat`)

const formatDate = (value: string) =>
  parseUtc(value).toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })

const loadTokens = async () => {
  try {
    tokens.value = await apiService.getApiTokens()
  } catch {
    loadError.value = true
  } finally {
    isLoading.value = false
  }
}

const createToken = async () => {
  const name = newName.value.trim()
  if (!name || isCreating.value) return
  isCreating.value = true
  try {
    const created = await apiService.createApiToken(
      newScope.value === 'PEER' ? { name, scope: 'PEER' } : { name }
    )
    createdToken.value = created
    copied.value = false
    tokens.value = [...tokens.value, created]
    newName.value = ''
    newScope.value = 'GRAFANA'
  } catch (err) {
    // The API shows the message, for example when the user has too many tokens
    console.error('Creating the API token failed:', err)
  } finally {
    isCreating.value = false
  }
}

const copyToken = async () => {
  if (!createdToken.value) return
  try {
    await navigator.clipboard.writeText(createdToken.value.token)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    showErrorToast('Could not copy. Select the token and copy it by hand.')
  }
}

const deleteToken = async (token: ApiToken) => {
  const confirmed = await askConfirm({
    title: `Delete the token ${token.name}?`,
    message: 'Grafana and other tools that use it lose access. You cannot undo this.',
    confirmLabel: 'Delete token',
    danger: true,
  })
  if (!confirmed) {
    return
  }
  deletingId.value = token.id
  try {
    await apiService.deleteApiToken(token.id)
    tokens.value = tokens.value.filter((t) => t.id !== token.id)
    if (createdToken.value?.id === token.id) {
      createdToken.value = null
    }
  } catch (err) {
    console.error(`Deleting the API token ${token.id} failed:`, err)
  } finally {
    deletingId.value = null
  }
}

onMounted(loadTokens)
</script>

<template>
  <section class="page-section" aria-labelledby="api-tokens-title">
    <h2 id="api-tokens-title" class="page-section-title">
      API tokens
      <small v-if="!isLoading && !loadError" class="num">{{ tokens.length }}</small>
    </h2>
    <p class="intro muted">
      A token lets Grafana and other tools read the check results of your monitors with Prometheus
      queries. It can only read.
    </p>

    <!-- The one time the secret is shown -->
    <div v-if="createdToken" class="new-token callout callout-success" role="status">
      <p class="new-token-title">Token "{{ createdToken.name }}" created</p>
      <div class="token-row">
        <code class="token-value">{{ createdToken.token }}</code>
        <button type="button" class="btn btn-secondary btn-sm" @click="copyToken">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path v-if="copied" d="M5 12.5l4.5 4.5L19 7.5" />
            <template v-else>
              <rect x="9" y="9" width="11" height="11" rx="2" />
              <path d="M5 15V5a1 1 0 0 1 1-1h10" />
            </template>
          </svg>
          {{ copied ? 'Copied' : 'Copy' }}
        </button>
      </div>
      <div class="new-token-foot">
        <p class="new-token-note">
          Copy the token now. It is not shown again. If you lose it, create a new one.
        </p>
        <button type="button" class="btn btn-link btn-sm link-btn" @click="createdToken = null">Done</button>
      </div>
    </div>

    <p v-if="loadError" class="callout callout-danger">Your API tokens could not be loaded.</p>
    <p v-else-if="isLoading" class="muted">Loading...</p>
    <p v-else-if="tokens.length === 0" class="empty muted">You have no API tokens yet. Create one below.</p>
    <ul v-else class="list token-list">
      <li v-for="token in tokens" :key="token.id" class="list-row">
        <div class="token-main">
          <span class="token-name">{{ token.name }}</span>
          <code class="token-prefix">{{ token.prefix }}…</code>
          <span v-if="token.scope === 'PEER'" class="badge badge-info token-scope">peer</span>
        </div>
        <span class="token-dates">
          <span>Created <span class="num">{{ formatDate(token.createdAt) }}</span></span>
          <span>Last used <span class="num">{{ token.lastUsedAt ? formatDate(token.lastUsedAt) : 'never' }}</span></span>
        </span>
        <button
          type="button"
          class="btn btn-link btn-sm token-delete"
          :disabled="deletingId === token.id"
          @click="deleteToken(token)"
        >
          {{ deletingId === token.id ? 'Deleting...' : 'Delete' }}
        </button>
      </li>
    </ul>

    <form v-if="!loadError" class="create-form" @submit.prevent="createToken">
      <div class="field create-name">
        <label for="api-token-name">New token</label>
        <input
          id="api-token-name"
          v-model="newName"
          type="text"
          required
          :maxlength="MAX_NAME_LENGTH"
          :disabled="isCreating"
          placeholder="Name, for example Grafana"
          autocomplete="off"
        />
      </div>
      <div v-if="props.peerTenantId != null" class="field">
        <label for="api-token-scope">Type</label>
        <select id="api-token-scope" v-model="newScope" :disabled="isCreating">
          <option value="GRAFANA">Grafana (read)</option>
          <option value="PEER">Peer server</option>
        </select>
      </div>
      <button type="submit" class="btn btn-primary" :disabled="isCreating || !newName.trim()">
        {{ isCreating ? 'Creating...' : 'Create token' }}
      </button>
    </form>

    <details v-if="props.peerTenantId != null" class="grafana">
      <summary>Use for the other Status Tacos server (peer)</summary>
      <ol>
        <li>Create a token of the type <strong>Peer server</strong> here.</li>
        <li>
          On the other server, in its peer tenant, add a monitor with the URL
          <code>{{ peerHeartbeatUrl }}</code>
        </li>
        <li>
          Give it the HTTP header <code>Authorization</code> with the value
          <code>Bearer &lt;token&gt;</code>, and the response body regex
          <code>"status":"ok"</code>.
        </li>
        <li>Do the same in the other direction.</li>
      </ol>
    </details>

    <details class="grafana">
      <summary>Use with Grafana</summary>
      <ol>
        <li>In Grafana, add a data source of type <strong>Prometheus</strong>.</li>
        <li>
          Prometheus server URL: <code>{{ dataSourceUrl }}</code>
        </li>
        <li>
          Authentication, one of:
          <ul>
            <li><strong>Basic auth</strong>: any user name, the token as password.</li>
            <li>
              A custom HTTP header <code>Authorization</code> with the value
              <code>Bearer &lt;token&gt;</code>.
            </li>
          </ul>
        </li>
        <li>Click <strong>Save &amp; test</strong>.</li>
      </ol>
    </details>
  </section>
</template>

<style scoped>
.intro {
  margin: calc(-1 * var(--space-1)) 0 var(--space-3);
  max-width: 65ch;
}

.empty {
  margin: 0 0 var(--space-3);
}

.callout {
  margin-bottom: var(--space-3);
}

/* New token: the secret in a box, the copy button next to it */
.new-token-title {
  margin: 0 0 var(--space-2);
  font-weight: 700;
  color: var(--text);
}

.token-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.token-value {
  flex: 1;
  min-width: 0;
  padding: var(--space-2) var(--space-3);
  background: var(--surface);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-sm);
  color: var(--text);
  font-size: var(--text-sm);
  word-break: break-all;
  user-select: all;
}

.new-token-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.new-token-note {
  margin: 0;
  font-size: var(--text-sm);
  font-weight: 700;
}

/* Token list: name and prefix, then the dates, then delete. The columns line up. */
.token-list {
  margin-bottom: var(--space-4);
}

.token-list .list-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: var(--space-1) var(--space-4);
}

.token-main {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
}

.token-name {
  font-weight: 700;
  color: var(--text);
  overflow-wrap: anywhere;
}

.token-prefix {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.token-dates {
  display: grid;
  grid-template-columns: auto;
  justify-items: end;
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.token-delete {
  color: var(--down);
}

.token-delete:hover:not(:disabled) {
  color: var(--down);
}

@media (max-width: 640px) {
  .token-list .list-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .token-dates {
    grid-column: 1;
    grid-row: 2;
    justify-items: start;
  }

  .token-delete {
    grid-column: 2;
    grid-row: 1 / span 2;
  }
}

/* Create: name, type, button in one row on wide screens */
.create-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin: 0 0 var(--space-4);
}

.create-name {
  flex: 1 1 16rem;
}

/* Help: quiet disclosures */
.grafana {
  max-width: 65ch;
  color: var(--text-2);
  font-size: var(--text-sm);
}

.grafana + .grafana {
  margin-top: var(--space-2);
}

.grafana summary {
  cursor: pointer;
  color: var(--link);
  font-weight: 700;
}

.grafana ol {
  margin: var(--space-2) 0 0;
  padding-left: var(--space-5);
}

.grafana ul {
  margin: var(--space-1) 0 0;
  padding-left: var(--space-5);
}

.grafana code {
  word-break: break-all;
}
</style>

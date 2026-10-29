<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiService, type ApiToken, type CreatedApiToken } from '../services/api'
import { showErrorToast } from '../services/toasts'
import { parseUtc } from '../utils/uptime'

const MAX_NAME_LENGTH = 100

const tokens = ref<ApiToken[]>([])
const isLoading = ref(true)
const loadError = ref(false)

const newName = ref('')
const isCreating = ref(false)
// Shown once after creation; the server cannot show it again
const createdToken = ref<CreatedApiToken | null>(null)
const copied = ref(false)

const deletingId = ref<number | null>(null)

const dataSourceUrl = computed(() => `${window.location.origin}/api/prom`)

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
    const created = await apiService.createApiToken({ name })
    createdToken.value = created
    copied.value = false
    tokens.value = [...tokens.value, created]
    newName.value = ''
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
  if (!confirm(`Delete the token "${token.name}"? Grafana and other tools that use it lose access.`)) {
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
  <section class="card">
    <h2>API Tokens</h2>
    <p class="muted">
      A token lets Grafana and other tools read the check results of your monitors with Prometheus
      queries. It can only read.
    </p>

    <div v-if="createdToken" class="new-token" role="status">
      <p class="new-token-title">Token "{{ createdToken.name }}" created</p>
      <div class="token-row">
        <code class="token-value">{{ createdToken.token }}</code>
        <button type="button" class="btn btn-outline btn-sm" @click="copyToken">
          {{ copied ? 'Copied' : 'Copy' }}
        </button>
      </div>
      <p class="new-token-note">
        Copy the token now. It is not shown again. If you lose it, create a new one.
      </p>
      <button type="button" class="link-btn" @click="createdToken = null">Done</button>
    </div>

    <p v-if="loadError" class="muted">Your API tokens could not be loaded.</p>
    <p v-else-if="isLoading" class="muted">Loading...</p>
    <p v-else-if="tokens.length === 0" class="muted">You have no API tokens.</p>
    <ul v-else class="token-list">
      <li v-for="token in tokens" :key="token.id">
        <div class="token-info">
          <span class="token-name">{{ token.name }}</span>
          <code class="token-prefix">{{ token.prefix }}…</code>
          <span class="token-dates">
            Created {{ formatDate(token.createdAt) }} · Last used
            {{ token.lastUsedAt ? formatDate(token.lastUsedAt) : 'never' }}
          </span>
        </div>
        <button
          type="button"
          class="link-btn danger"
          :disabled="deletingId === token.id"
          @click="deleteToken(token)"
        >
          {{ deletingId === token.id ? 'Deleting...' : 'Delete' }}
        </button>
      </li>
    </ul>

    <form v-if="!loadError" class="create-form" @submit.prevent="createToken">
      <label for="api-token-name" class="visually-hidden">Token name</label>
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
      <button type="submit" class="btn btn-primary" :disabled="isCreating || !newName.trim()">
        {{ isCreating ? 'Creating...' : 'Create Token' }}
      </button>
    </form>

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
.card {
  background: white;
  border: 1px solid #e9ecef;
  border-radius: 12px;
  padding: 1.5rem;
}

.card h2 {
  margin: 0 0 1rem;
  font-size: 1.15rem;
  color: #2c3e50;
}

.muted {
  color: #6c757d;
  margin: 0 0 1rem;
  line-height: 1.5;
}

.new-token {
  background: #e8f5e9;
  border: 1px solid #a5d6a7;
  border-radius: 8px;
  padding: 0.75rem 1rem;
  margin: 0 0 1rem;
}

.new-token-title {
  margin: 0 0 0.5rem;
  color: #2c3e50;
  font-weight: 600;
}

.token-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.token-value {
  flex: 1;
  min-width: 0;
  padding: 0.4rem 0.6rem;
  background: white;
  border: 1px solid #ced4da;
  border-radius: 4px;
  font-size: 0.85rem;
  word-break: break-all;
  user-select: all;
}

.new-token-note {
  margin: 0.5rem 0;
  color: #856404;
  font-weight: 600;
  font-size: 0.9rem;
}

.token-list {
  list-style: none;
  margin: 0 0 1rem;
  padding: 0;
}

.token-list li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid #f1f3f5;
}

.token-info {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.25rem 0.75rem;
  min-width: 0;
}

.token-name {
  color: #2c3e50;
  font-weight: 500;
  word-break: break-word;
}

.token-prefix {
  color: #6c757d;
  font-size: 0.85rem;
}

.token-dates {
  flex-basis: 100%;
  color: #6c757d;
  font-size: 0.85rem;
}

.create-form {
  display: flex;
  gap: 0.75rem;
  margin: 0 0 1rem;
}

.create-form input {
  flex: 1;
  min-width: 0;
  padding: 0.75rem;
  border: 1px solid #ced4da;
  border-radius: 8px;
  font-size: 1rem;
}

.create-form input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 3px rgba(0, 123, 255, 0.15);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.grafana {
  color: #495057;
  line-height: 1.6;
}

.grafana summary {
  cursor: pointer;
  color: #007bff;
  font-weight: 500;
}

.grafana ol {
  margin: 0.75rem 0 0;
  padding-left: 1.25rem;
}

.grafana ul {
  margin: 0.25rem 0 0;
  padding-left: 1.25rem;
}

.grafana code {
  font-size: 0.85rem;
  word-break: break-all;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: #007bff;
  cursor: pointer;
  font-size: inherit;
}

.link-btn:hover:not(:disabled) {
  text-decoration: underline;
}

.link-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.link-btn.danger {
  color: #dc3545;
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
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-outline {
  background: transparent;
  color: #007bff;
  border: 2px solid #007bff;
}

.btn-outline:hover:not(:disabled) {
  background: #007bff;
  color: white;
}

/* Mobile styles */
@media (max-width: 768px) {
  .create-form {
    flex-direction: column;
  }
}
</style>

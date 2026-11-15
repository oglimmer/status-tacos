<script setup lang="ts">
import { computed } from 'vue'
import type { MonitorResponse, PeerResult } from '../stores/monitors'

// The settings and the last check of a monitor, as label and value pairs in aligned columns.

interface LastCheck {
  currentStatus: 'up' | 'down'
  lastCheckedAt: string
  lastResponseTimeMs: number
  lastStatusCode: number
  lastErrorMessage?: string | null
  peerResult?: PeerResult | null
  consecutiveFailures: number
}

const props = defineProps<{
  monitor: MonitorResponse
  status: LastCheck | null
  // "until Saturday, 14 November 2026 at 14:00", for a silent monitor
  silentUntilText?: string | null
  // The daily pause, with the time of the user
  dailyPauseText?: string | null
}>()

const PEER_RESULT_TEXT: Record<PeerResult, string> = {
  CONFIRMED: 'Also DOWN for the other server',
  PEER_UP: 'Only this server sees DOWN: no alert sent',
  NO_ANSWER: 'The other server did not answer: alert sent'
}

const STATE_TEXT: Record<MonitorResponse['state'], string> = {
  ACTIVE: 'Active',
  SILENT: 'Silent',
  INACTIVE: 'Inactive'
}

const formatDate = (dateString: string) => {
  // Backend returns UTC timestamps without 'Z' suffix, so we need to append it
  // to ensure proper UTC interpretation before converting to local timezone
  const utcTimestamp = dateString.endsWith('Z') ? dateString : dateString + 'Z'
  const date = new Date(utcTimestamp)
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZoneName: 'short'
  })
}

const headers = computed(() => Object.entries(props.monitor.httpHeaders ?? {}))

const prometheusRange = computed(() => {
  const { prometheusMinValue: min, prometheusMaxValue: max } = props.monitor
  if (min === undefined && max === undefined) return null
  return `${min ?? ''}${min !== undefined && max !== undefined ? ' to ' : ''}${max ?? ''}`
})

const shorten = (text: string, length: number) => (text.length > length ? text.substring(0, length) + '…' : text)
</script>

<template>
  <dl class="details">
    <div class="detail">
      <dt>Tenant</dt>
      <dd>{{ monitor.tenant?.name || 'Unknown' }}</dd>
    </div>
    <div class="detail">
      <dt>State</dt>
      <dd>
        {{ STATE_TEXT[monitor.state] ?? monitor.state }}
        <span v-if="silentUntilText" class="muted">{{ silentUntilText }}</span>
      </dd>
    </div>
    <div v-if="dailyPauseText" class="detail">
      <dt>Daily pause</dt>
      <dd class="num">{{ dailyPauseText }}</dd>
    </div>
    <div class="detail">
      <dt>Check interval</dt>
      <dd class="num">{{ monitor.checkInterval ?? 15 }} s</dd>
    </div>
    <div class="detail">
      <dt>Alert delay</dt>
      <dd class="num">{{ monitor.alertingThreshold }} s</dd>
    </div>
    <div v-if="monitor.useSystemDns" class="detail">
      <dt>DNS</dt>
      <dd>DNS server of the backend</dd>
    </div>
    <div v-if="monitor.statusCodeRegex" class="detail">
      <dt>Status code</dt>
      <dd><code>{{ monitor.statusCodeRegex }}</code></dd>
    </div>
    <div v-if="monitor.responseBodyRegex" class="detail">
      <dt>Body</dt>
      <dd><code>{{ monitor.responseBodyRegex }}</code></dd>
    </div>
    <div v-if="monitor.prometheusKey" class="detail">
      <dt>Prometheus</dt>
      <dd>
        <code>{{ monitor.prometheusKey }}</code>
        <span v-if="prometheusRange" class="num muted"> ({{ prometheusRange }})</span>
      </dd>
    </div>
    <div v-if="headers.length > 0" class="detail">
      <dt>Headers</dt>
      <dd class="header-list">
        <code v-for="[key, value] in headers" :key="key" :title="`${key}: ${value}`">
          {{ shorten(key, 20) }}: {{ shorten(value, 30) }}
        </code>
      </dd>
    </div>

    <template v-if="status">
      <div class="detail">
        <dt>Last check</dt>
        <dd class="num">{{ formatDate(status.lastCheckedAt) }}</dd>
      </div>
      <div class="detail">
        <dt>Response time</dt>
        <dd class="num">{{ status.lastResponseTimeMs }} ms</dd>
      </div>
      <div v-if="status.lastStatusCode != null || status.lastErrorMessage" class="detail">
        <dt>{{ status.lastStatusCode != null ? 'Status code' : 'Error' }}</dt>
        <dd
          class="num"
          :class="{ 'is-bad': status.currentStatus === 'down' }"
        >
          {{ status.lastStatusCode != null ? status.lastStatusCode : status.lastErrorMessage }}
        </dd>
      </div>
      <div v-if="status.currentStatus === 'down' && status.peerResult" class="detail">
        <dt>Other server</dt>
        <dd :class="{ 'is-warn': status.peerResult === 'PEER_UP' }">{{ PEER_RESULT_TEXT[status.peerResult] }}</dd>
      </div>
      <div v-if="status.consecutiveFailures > 0" class="detail">
        <dt>Failures in a row</dt>
        <dd class="num is-bad">{{ status.consecutiveFailures }}</dd>
      </div>
    </template>
  </dl>
</template>

<style scoped>
/* Columns of label and value. The labels in one column have the same width, so the values line up. */
.details {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr));
  column-gap: var(--space-5);
  margin: 0;
}

.detail {
  display: grid;
  grid-template-columns: 8.5rem minmax(0, 1fr);
  gap: var(--space-3);
  align-items: baseline;
  padding: var(--space-1) 0;
  border-bottom: 1px solid var(--border-subtle);
  font-size: var(--text-sm);
}

.detail dt {
  color: var(--text-muted);
}

.detail dd {
  margin: 0;
  min-width: 0;
  color: var(--text);
  overflow-wrap: anywhere;
}

.detail code {
  color: var(--text);
}

.header-list {
  display: flex;
  flex-direction: column;
  gap: 0.125rem;
}

.is-bad {
  color: var(--down);
  font-weight: 700;
}

.is-warn {
  color: var(--warn);
  font-weight: 700;
}

@media (max-width: 420px) {
  .detail {
    grid-template-columns: 7rem minmax(0, 1fr);
  }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiService, type PeerStatus } from '../services/api'
import { parseUtc } from '../utils/uptime'
import MonitorIcon from './MonitorIcon.vue'

// Shown to members of the peer tenant when the peer setup does not work
const status = ref<PeerStatus | null>(null)

const SETUP_TEXT: Record<Exclude<PeerStatus['setup'], 'OK'>, string> = {
  NO_PEER_MONITOR:
    'The peer tenant has no monitor of the other server. Add one with the URL https://<other server>/api/peer/heartbeat.',
  MULTIPLE_PEER_MONITORS:
    'The peer tenant has more than one monitor of a /peer/heartbeat URL. Keep exactly one.',
  NO_TOKEN:
    'The peer monitor has no Authorization header. Add "Authorization: Bearer <peer token of the other server>".',
}

const setupProblem = computed(() =>
  status.value && status.value.setup !== 'OK' ? SETUP_TEXT[status.value.setup] : null,
)

onMounted(async () => {
  try {
    status.value = await apiService.getPeerStatus()
  } catch (err) {
    console.error('Loading the peer status failed:', err)
  }
})
</script>

<template>
  <div v-if="status && (setupProblem || !status.canAlert)" class="peer-warning" role="alert">
    <MonitorIcon name="alert" class="peer-warning-icon" />
    <div class="peer-warning-text">
      <p v-if="setupProblem">
        <strong>Peer setup not valid.</strong> {{ setupProblem }} Until then, this server does not ask
        the other server before DOWN alerts.
      </p>
      <p v-if="!status.canAlert">
        <strong>This server cannot send alerts</strong>
        <span v-if="status.cannotAlertSince">
          since <span class="num">{{ parseUtc(status.cannotAlertSince).toLocaleString() }}</span></span
        >. The other server was told. Reason: {{ status.cannotAlertReason || 'unknown' }}
      </p>
    </div>
  </div>
</template>

<style scoped>
/* One compact line (or two) with a warning edge: it must stay visible, but not take the page. */
.peer-warning {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--warning-soft-border);
  border-left: 3px solid var(--masa);
  border-radius: var(--radius-md);
  background: var(--warning-soft);
  color: var(--warning-soft-text);
  font-size: var(--text-sm);
}

.peer-warning-icon {
  margin-top: 0.2em;
}

.peer-warning-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.peer-warning p {
  margin: 0;
}

.peer-warning p + p {
  margin-top: var(--space-1);
}
</style>

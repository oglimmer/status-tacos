<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { apiService, type PeerStatus } from '../services/api'
import { parseUtc } from '../utils/uptime'

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
    <p v-if="setupProblem">
      <strong>Peer setup not valid:</strong> {{ setupProblem }} Until then, this server does not ask
      the other server before DOWN alerts.
    </p>
    <p v-if="!status.canAlert">
      <strong>This server cannot send alerts</strong>
      <span v-if="status.cannotAlertSince">
        since {{ parseUtc(status.cannotAlertSince).toLocaleString() }}</span
      >. The other server was told. Reason: {{ status.cannotAlertReason || 'unknown' }}
    </p>
  </div>
</template>

<style scoped>
.peer-warning {
  margin-bottom: 1.5rem;
  padding: 0.75rem 1rem;
  border: 1px solid #ffd699;
  border-radius: 8px;
  background: #fff6e5;
  color: #7a4d00;
  font-size: 0.9rem;
}

.peer-warning p {
  margin: 0;
}

.peer-warning p + p {
  margin-top: 0.5rem;
}
</style>

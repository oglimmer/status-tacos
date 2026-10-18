<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import '../assets/brand.css'
import { apiService } from '../services/api'
import { ApiError } from '../services/apiError'

const props = defineProps<{ tenantCode: string }>()

// The server renders the branded page of the tenant. It runs in a sandbox (CSP header and the
// iframe attribute): no scripts, and an opaque origin, so tenant HTML never reaches this app.
const pageUrl = computed(() => apiService.publicStatusPageUrl(props.tenantCode))

const state = ref<'loading' | 'ready' | 'not-found' | 'failed'>('loading')

// The data call tells an unknown tenant apart and gives the tab title. Both calls are cached.
onMounted(async () => {
  try {
    const page = await apiService.getPublicStatusPage(props.tenantCode)
    document.title = `${page.tenantName} status`
    state.value = 'ready'
  } catch (err) {
    console.error('Failed to load the public status:', err)
    state.value = err instanceof ApiError && err.status === 404 ? 'not-found' : 'failed'
  }
})
</script>

<template>
  <iframe
    v-if="state === 'ready'"
    class="status-frame"
    :src="pageUrl"
    title="Status"
    sandbox="allow-popups allow-popups-to-escape-sandbox allow-top-navigation-by-user-activation"
    referrerpolicy="no-referrer"
  ></iframe>
  <main v-else class="status-fallback tacos-page">
    <router-link to="/" class="tacos-brand">
      <img src="../assets/logo.png" alt="" />
      Status Tacos
    </router-link>
    <p v-if="state === 'loading'">Loading…</p>
    <p v-else-if="state === 'not-found'">There is no status page at this address.</p>
    <p v-else>The status is not available now. Try again later.</p>
  </main>
</template>

<style scoped>
.status-frame {
  display: block;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  border: 0;
}

.status-fallback {
  padding: 1.5rem var(--gutter);
}
</style>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import { useRouter } from 'vue-router'
import { ApiError, LOGIN_NOT_ALLOWED } from '../services/apiError'

const authStore = useAuthStore()
const router = useRouter()
const isProcessing = ref(true)
const error = ref<string | null>(null)
// The app signs a not allowed user out and sends them to the landing page by itself
const isLeaving = ref(false)

onMounted(async () => {
  try {
    console.log('Processing callback...')
    await authStore.handleCallback()
    router.push('/monitors')
  } catch (err) {
    if (err instanceof ApiError && err.code === LOGIN_NOT_ALLOWED) {
      error.value = 'This account is not allowed to use Status Tacos. We sign you out now.'
      isLeaving.value = true
    } else if (err instanceof ApiError) {
      error.value = err.message
    } else {
      error.value = 'Sign-in failed. Please try again.'
    }
    console.error('Callback processing error:', err)
  } finally {
    isProcessing.value = false
  }
})
</script>

<template>
  <div class="callback-container">
    <div class="callback-content">
      <div v-if="isProcessing" class="processing">
        <div class="spinner"></div>
        <h2>Signing you in...</h2>
        <p>Please wait while we complete the authentication process.</p>
      </div>

      <div v-else-if="error" class="error-state">
        <h2>Sign-in failed</h2>
        <p>{{ error }}</p>
        <div v-if="isLeaving" class="spinner"></div>
        <button v-else @click="router.push('/')" class="btn btn-primary">
          Return to Home
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.callback-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f8f9fa;
}

.callback-content {
  text-align: center;
  max-width: 400px;
  padding: 2rem;
}

.processing h2,
.error-state h2 {
  margin-bottom: 1rem;
  color: #2c3e50;
}

.processing p,
.error-state p {
  color: #6c757d;
  margin-bottom: 2rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #e9ecef;
  border-top: 4px solid #007bff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 2rem;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.btn {
  padding: 0.75rem 2rem;
  border: none;
  border-radius: 6px;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #007bff;
  color: white;
}

.btn-primary:hover {
  background-color: #0056b3;
}
</style>

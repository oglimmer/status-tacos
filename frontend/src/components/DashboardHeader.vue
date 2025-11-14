<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import VersionInfo from './VersionInfo.vue'

const authStore = useAuthStore()

const handleLogout = () => {
  authStore.logout()
}
</script>

<template>
  <header class="dashboard-header">
    <div class="nav-brand">
      <img src="../assets/logo.png" alt="Status Tacos" class="nav-logo" />
      <span class="brand-name">Status Tacos</span>
    </div>
    <div class="user-info">
      <span class="welcome-text">¡Hola {{ authStore.user?.profile?.name || 'Amigo' }}! 🌮</span>
      <div class="user-actions">
        <VersionInfo />
        <button @click="handleLogout" class="btn btn-outline">
          Sign Out
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 2rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  position: sticky;
  top: 0;
  z-index: 100;
  border-radius: 20px 20px 0 0;
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.nav-logo {
  height: 40px;
  width: auto;
}

.brand-name {
  font-size: 1.5rem;
  font-weight: 700;
  color: #2c3e50;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.welcome-text {
  color: #2c3e50;
  font-weight: 500;
  font-size: 1rem;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

/* Mobile styles */
@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.75rem;
    padding: 1rem;
    border-radius: 0;
    position: static;
    background: rgba(255, 255, 255, 0.95);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  }

  .user-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.5rem;
    width: 100%;
  }

  .user-actions {
    justify-content: space-between;
    width: 100%;
    gap: 0.75rem;
  }

  .welcome-text {
    font-size: 0.9rem;
  }
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
  text-decoration: none;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none !important;
}

.btn-outline {
  background: transparent;
  color: #007bff;
  border: 2px solid #007bff;
}

.btn-outline:hover:not(:disabled) {
  background: #007bff;
  color: white;
  transform: translateY(-2px);
}
</style>

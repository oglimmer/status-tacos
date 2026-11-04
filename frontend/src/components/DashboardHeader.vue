<script setup lang="ts">
import { useAuthStore } from '../stores/auth'
import VersionInfo from './VersionInfo.vue'
import ThemeToggle from './ThemeToggle.vue'

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
        <ThemeToggle class="header-theme" />
        <router-link to="/account" class="btn btn-link" active-class="active">
          Account
        </router-link>
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
  background: var(--glass-strong);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--glass-border);
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
  color: var(--text);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.welcome-text {
  color: var(--text);
  font-weight: 500;
  font-size: 1rem;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.header-theme {
  color: var(--text-muted);
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
    background: var(--glass-strong);
    backdrop-filter: blur(10px);
    border-bottom: 1px solid var(--glass-border);
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
  color: var(--link);
  border: 2px solid var(--accent);
}

.btn-outline:hover:not(:disabled) {
  background: var(--accent);
  color: white;
  transform: translateY(-2px);
}

.btn-link {
  background: transparent;
  color: var(--text-muted);
  padding: 0.75rem 0.5rem;
  font-weight: 500;
}

.btn-link:hover,
.btn-link.active {
  color: var(--link);
}
</style>

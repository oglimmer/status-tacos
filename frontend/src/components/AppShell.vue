<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import VersionInfo from './VersionInfo.vue'
import ThemeMenu from './ThemeMenu.vue'

// The layout of every page after login: the top bar with the one navigation, then the page.

const authStore = useAuthStore()

const navigationItems = [
  { path: '/monitors', label: 'Monitors' },
  { path: '/alert-contacts', label: 'Alert Contacts' },
  { path: '/tenants', label: 'Tenants' },
  { path: '/account', label: 'Account' },
]

const signedInAs = computed(() => {
  const profile = authStore.user?.profile
  const name = profile?.name || profile?.email
  return name ? `Signed in as ${name}` : undefined
})

const handleLogout = () => {
  authStore.logout()
}
</script>

<template>
  <div class="app-shell">
    <a href="#main" class="skip-link">Skip to content</a>

    <header class="app-bar">
      <div class="app-bar-inner">
        <router-link to="/monitors" class="app-brand" aria-label="Status Tacos, monitors">
          <img src="../assets/logo.png" alt="" class="app-logo" width="32" height="32" />
          <span class="app-wordmark">Status Tacos</span>
        </router-link>

        <nav class="app-nav" aria-label="Main">
          <router-link
            v-for="item in navigationItems"
            :key="item.path"
            :to="item.path"
            class="app-nav-link"
            active-class="active"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div class="app-tools">
          <VersionInfo class="app-version" />
          <ThemeMenu class="app-theme" />
          <button
            type="button"
            class="btn btn-outline btn-sm"
            :title="signedInAs"
            @click="handleLogout"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
              <path d="M16 17l5-5-5-5" />
              <path d="M21 12H9" />
            </svg>
            Sign out
          </button>
        </div>
      </div>
    </header>

    <main id="main" class="app-main" tabindex="-1">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--page-bg);
}

.skip-link {
  position: absolute;
  left: var(--space-4);
  top: -3rem;
  z-index: 200;
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--accent);
  color: var(--on-accent);
  font-weight: 700;
}

.skip-link:focus {
  top: var(--space-2);
}

/* The bar stays on top while the page scrolls. The teal line at the top is the brand. */
.app-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--surface);
  border-top: 3px solid var(--brand);
  border-bottom: 1px solid var(--border);
}

.app-bar-inner {
  display: grid;
  grid-template-columns: auto 1fr auto;
  grid-template-areas: 'brand nav tools';
  align-items: center;
  column-gap: var(--space-6);
  max-width: var(--content-width);
  min-height: var(--bar-height);
  margin: 0 auto;
  padding: 0 var(--space-5);
}

.app-brand {
  grid-area: brand;
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text);
  text-decoration: none;
}

.app-logo {
  width: 2rem;
  height: 2rem;
  border-radius: var(--radius-md);
}

.app-wordmark {
  font-family: var(--font-sign);
  font-size: var(--text-md);
  line-height: 1;
  white-space: nowrap;
}

/* The nav links fill the bar height, so the active line sits on the bottom border. */
.app-nav {
  grid-area: nav;
  display: flex;
  align-self: stretch;
  gap: var(--space-1);
  min-width: 0;
  overflow-x: auto;
  scrollbar-width: none;
}

.app-nav::-webkit-scrollbar {
  display: none;
}

.app-nav-link {
  display: flex;
  align-items: center;
  padding: 0 var(--space-3);
  border-bottom: 3px solid transparent;
  margin-bottom: -1px;
  color: var(--text-muted);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.app-nav-link:hover {
  color: var(--text);
}

.app-nav-link.active {
  color: var(--text);
  border-bottom-color: var(--brand);
}

.app-nav-link:focus-visible {
  outline-offset: -4px;
}

.app-tools {
  grid-area: tools;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.app-theme {
  color: var(--text-2);
}

.app-main {
  max-width: var(--content-width);
  margin: 0 auto;
  padding: var(--space-5) var(--space-5) var(--space-7);
}

.app-main:focus {
  outline: none;
}

/* Small screens (the one-row bar needs about 910px): brand and tools on top, the navigation
   below it scrolls sideways. DashboardView uses the same width for its selection bar. */
@media (max-width: 920px) {
  .app-bar-inner {
    grid-template-columns: 1fr auto;
    grid-template-areas:
      'brand tools'
      'nav nav';
    column-gap: var(--space-3);
    padding: var(--space-2) var(--space-4) 0;
  }

  .app-nav {
    height: 2.75rem;
    margin-inline: calc(-1 * var(--space-3));
  }

  .app-main {
    padding: var(--space-4) var(--space-4) var(--space-6);
  }
}

@media (max-width: 520px) {
  .app-wordmark,
  .app-version {
    display: none;
  }

  .app-theme :deep(.theme-trigger-text) {
    display: none;
  }
}
</style>

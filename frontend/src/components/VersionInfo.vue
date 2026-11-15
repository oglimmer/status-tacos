<script setup lang="ts">
import { ref, onMounted } from 'vue'

interface VersionInfo {
  version: string
  git: string
}

interface ApiVersionInfo {
  version: string
  git: string
}

const versionInfo = ref<VersionInfo | null>(null)
const apiVersionInfo = ref<ApiVersionInfo | null>(null)
const isVisible = ref(false)

const loadVersionInfo = async () => {
  try {
    // Try to get version from build-time environment variables first
    const gitVersion = import.meta.env.VITE_GIT_VERSION
    const appVersion = import.meta.env.VITE_APP_VERSION

    if (gitVersion && appVersion) {
      versionInfo.value = {
        version: appVersion,
        git: gitVersion
      }
    } else {
      // Fallback to version.json file
      const response = await fetch('/version.json')
      if (response.ok) {
        versionInfo.value = await response.json()
      }
    }
  } catch (error) {
    console.error('Failed to load frontend version info:', error)
  }
}

const loadApiVersionInfo = async () => {
  try {
    const response = await fetch('/api/actuator/info')
    if (response.ok) {
      const data = await response.json()
      apiVersionInfo.value = {
        version: data.build?.version || 'Unknown',
        git: data.git?.commit?.id || 'Unknown'
      }
    }
  } catch (error) {
    console.error('Failed to load API version info:', error)
  }
}

const toggleVisibility = () => {
  isVisible.value = !isVisible.value
}

onMounted(() => {
  loadVersionInfo()
  loadApiVersionInfo()
})
</script>

<template>
  <div class="version-info">
    <button
      type="button"
      class="version-toggle"
      :class="{ 'active': isVisible }"
      title="Version Information"
      :aria-expanded="isVisible"
      @click="toggleVisibility"
    >
      <span class="version-text">v{{ versionInfo?.version || '?' }}</span>
    </button>

    <div v-if="isVisible && (versionInfo || apiVersionInfo)" class="version-details">
      <div v-if="versionInfo" class="version-section">
        <div class="version-section-title">Frontend</div>
        <div class="version-item">
          <strong>Version:</strong> {{ versionInfo.version }}
        </div>
        <div class="version-item">
          <strong>Git:</strong> {{ versionInfo.git }}
        </div>
      </div>

      <div v-if="apiVersionInfo" class="version-section">
        <div class="version-section-title">API</div>
        <div class="version-item">
          <strong>Version:</strong> {{ apiVersionInfo.version }}
        </div>
        <div class="version-item">
          <strong>Git:</strong> {{ apiVersionInfo.git }}
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.version-info {
  position: relative;
  display: inline-block;
}

.version-toggle {
  display: inline-flex;
  align-items: center;
  min-height: 1.5rem;
  padding: 0 var(--space-2);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-size: var(--text-xs);
  cursor: pointer;
}

.version-toggle:hover,
.version-toggle.active {
  border-color: var(--border);
  background: var(--surface-2);
  color: var(--text);
}

.version-text {
  font-family: var(--font-mono);
}

.version-details {
  position: absolute;
  top: 100%;
  right: 0;
  z-index: 1000;
  margin-top: var(--space-2);
  padding: var(--space-3);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-2);
  color: var(--text);
  font-size: var(--text-sm);
  white-space: nowrap;
}

@media (max-width: 768px) {
  .version-details {
    left: 0;
    right: auto;
  }
}

.version-item {
  margin-bottom: var(--space-1);
  font-family: var(--font-mono);
}

.version-item:last-child {
  margin-bottom: 0;
}

.version-item strong {
  margin-right: var(--space-2);
  color: var(--text-muted);
  font-family: var(--font-text);
  font-weight: 400;
}

.version-section {
  margin-bottom: var(--space-3);
}

.version-section:last-child {
  margin-bottom: 0;
}

.version-section-title {
  margin-bottom: var(--space-1);
  padding-bottom: var(--space-1);
  border-bottom: 1px solid var(--border-subtle);
  font-weight: 700;
  color: var(--text);
}
</style>

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
      @click="toggleVisibility"
      class="version-toggle"
      :class="{ 'active': isVisible }"
      title="Version Information"
    >
      <span class="version-icon">ℹ</span>
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
  display: flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.5rem;
  background: transparent;
  border: 1px solid #dee2e6;
  border-radius: 12px;
  cursor: pointer;
  font-size: 0.75rem;
  color: #6c757d;
  transition: all 0.2s ease;
}

.version-toggle:hover {
  background: #f8f9fa;
  border-color: #adb5bd;
  color: #495057;
}

.version-toggle.active {
  background: #e9ecef;
  border-color: #adb5bd;
  color: #495057;
}

.version-icon {
  font-size: 0.8rem;
  font-weight: bold;
}

.version-text {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-weight: 500;
}

.version-details {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 0.5rem;
  background: white;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  padding: 0.75rem;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  white-space: nowrap;
  font-size: 0.8rem;
}

@media (max-width: 768px) {
  .version-details {
    left: 0;
    right: auto;
  }
}

.version-item {
  margin-bottom: 0.25rem;
}

.version-item:last-child {
  margin-bottom: 0;
}

.version-item strong {
  color: #495057;
  margin-right: 0.5rem;
}

.version-section {
  margin-bottom: 0.75rem;
}

.version-section:last-child {
  margin-bottom: 0;
}

.version-section-title {
  font-weight: 600;
  color: #343a40;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 0.5rem;
  border-bottom: 1px solid #e9ecef;
  padding-bottom: 0.25rem;
}
</style>

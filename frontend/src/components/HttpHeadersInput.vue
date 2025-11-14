<script setup lang="ts">
import { ref, watch } from 'vue'

interface HeaderItem {
  id: string
  key: string
  value: string
}

const props = defineProps<{
  modelValue: Record<string, string> | undefined
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>]
}>()

// Manage headers as an array with stable IDs for proper reactivity
const headerItems = ref<HeaderItem[]>([])
const isInternalUpdate = ref(false)

// Initialize headerItems from modelValue
const initializeHeaders = () => {
  headerItems.value = Object.entries(props.modelValue || {}).map(([key, value]) => ({
    id: `header-${Date.now()}-${Math.random()}`,
    key,
    value
  }))
}

// Watch for external changes to modelValue
watch(() => props.modelValue, (newValue, oldValue) => {
  console.log('watch triggered, newValue:', newValue, 'oldValue:', oldValue, 'isInternalUpdate:', isInternalUpdate.value)
  // Only reinitialize if this is not an internal update (from our own emit)
  if (!isInternalUpdate.value) {
    initializeHeaders()
  }
  isInternalUpdate.value = false
}, { immediate: true })

const addHeader = (event?: Event) => {
  event?.preventDefault()
  event?.stopPropagation()
  const id = `header-${Date.now()}-${Math.random()}`
  headerItems.value.push({ id, key: '', value: '' })
  updateHttpHeaders()
}

const removeHeader = (id: string, event?: Event) => {
  if (event) {
    event.preventDefault()
    event.stopPropagation()
  }

  const item = headerItems.value.find(item => item.id === id)
  if (item && (item.key || item.value)) {
    if (!confirm('Are you sure you want to remove this header?')) {
      return
    }
  }

  const index = headerItems.value.findIndex(item => item.id === id)
  if (index !== -1) {
    headerItems.value.splice(index, 1)
    updateHttpHeaders()
  }
}

const updateHeaderKey = (id: string, newKey: string) => {
  const item = headerItems.value.find(item => item.id === id)
  if (item) {
    item.key = newKey
    updateHttpHeaders()
  }
}

const updateHeaderValue = (id: string, newValue: string) => {
  const item = headerItems.value.find(item => item.id === id)
  if (item) {
    item.value = newValue
    updateHttpHeaders()
  }
}

// Sync headerItems with modelValue
const updateHttpHeaders = () => {
  const headers: Record<string, string> = {}
  headerItems.value.forEach(item => {
    if (item.key.trim()) {
      headers[item.key] = item.value
    }
  })
  isInternalUpdate.value = true
  emit('update:modelValue', headers)
}
</script>

<template>
  <div class="form-group">
    <label>Custom Headers</label>
    <div class="headers-container">
      <div v-for="item in headerItems" :key="item.id" class="header-row">
        <input
          :value="item.key"
          @input="updateHeaderKey(item.id, ($event.target as HTMLInputElement).value)"
          placeholder="Header name"
          class="header-key"
          maxlength="100"
        />
        <input
          :value="item.value"
          @input="updateHeaderValue(item.id, ($event.target as HTMLInputElement).value)"
          placeholder="Header value"
          class="header-value"
          maxlength="200"
        />
        <button @click="(event) => removeHeader(item.id, event)" type="button" class="remove-header-btn">×</button>
      </div>
      <button @click="() => { console.log('Add header clicked!'); addHeader(); }" type="button" class="add-header-btn">+ Add Header</button>
    </div>
    <small class="form-help-text">
      Add custom HTTP headers to include with monitor requests.
    </small>
  </div>
</template>

<style scoped>
.form-group {
  margin-bottom: 1.5rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #2c3e50;
}

.headers-container {
  border: 1px solid #dee2e6;
  border-radius: 4px;
  padding: 1rem;
  background: #f8f9fa;
  box-sizing: border-box;
  width: 100%;
}

.header-row {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  align-items: center;
  max-width: 100%;
  box-sizing: border-box;
}

.header-key,
.header-value {
  flex: 1;
  min-width: 120px;
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.875rem;
}

.header-key:focus,
.header-value:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.remove-header-btn {
  background: #dc3545;
  color: white;
  border: none;
  border-radius: 4px;
  width: 2rem;
  height: 2rem;
  cursor: pointer;
  font-size: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.remove-header-btn:hover {
  background: #c82333;
}

.add-header-btn {
  background: #28a745;
  color: white;
  border: none;
  border-radius: 4px;
  padding: 0.5rem 1rem;
  cursor: pointer;
  font-size: 0.875rem;
  margin-top: 0.5rem;
  width: fit-content;
  position: relative;
  z-index: 1;
  pointer-events: auto;
}

.add-header-btn:hover {
  background: #218838;
}

.form-help-text {
  color: #6c757d;
  font-size: 0.85rem;
  margin-top: 0.5rem;
  display: block;
}
</style>

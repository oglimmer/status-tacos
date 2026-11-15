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
watch(() => props.modelValue, () => {
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
  <div class="repeat-rows">
    <p v-if="headerItems.length === 0" class="field-hint">No headers. Checks send the default headers only.</p>
    <div v-for="(item, index) in headerItems" :key="item.id" class="repeat-row">
      <input
        :value="item.key"
        @input="updateHeaderKey(item.id, ($event.target as HTMLInputElement).value)"
        placeholder="Name"
        class="input mono header-key"
        :aria-label="`Header ${index + 1} name`"
        maxlength="100"
      />
      <input
        :value="item.value"
        @input="updateHeaderValue(item.id, ($event.target as HTMLInputElement).value)"
        placeholder="Value"
        class="input mono header-value"
        :aria-label="`Header ${index + 1} value`"
        maxlength="200"
      />
      <button
        @click="(event) => removeHeader(item.id, event)"
        type="button"
        class="btn btn-icon btn-sm repeat-remove"
        :aria-label="`Remove header ${item.key || index + 1}`"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
          <path d="M6 6l12 12M18 6L6 18" />
        </svg>
      </button>
    </div>
    <button @click="() => addHeader()" type="button" class="btn btn-link btn-sm repeat-add">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true">
        <path d="M12 5v14M5 12h14" />
      </svg>
      Add header
    </button>
  </div>
</template>

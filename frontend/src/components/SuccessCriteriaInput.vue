<script setup lang="ts">
import { ref, watch } from 'vue'

interface SuccessCriteriaData {
  statusCodeRegex: string
  responseBodyRegex: string
  prometheusKey: string
  prometheusMinValue?: number
  prometheusMaxValue?: number
}

const props = defineProps<{
  modelValue: SuccessCriteriaData
  isInitializing?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: SuccessCriteriaData]
}>()

// Initialize criteria type based on existing data
const initializeCriteriaType = () => {
  const { responseBodyRegex, prometheusKey } = props.modelValue

  if (responseBodyRegex && responseBodyRegex.trim()) {
    return 'response_body'
  } else if (prometheusKey && prometheusKey.trim()) {
    return 'prometheus'
  } else {
    return 'status_code'
  }
}

const successCriteriaType = ref<'status_code' | 'response_body' | 'prometheus'>(initializeCriteriaType())

// Watch for external changes to modelValue (only during initialization)
watch(() => props.modelValue, () => {
  if (props.isInitializing) {
    successCriteriaType.value = initializeCriteriaType()
  }
}, { immediate: true })

// Watch for changes in success criteria type and clear unused fields
watch(successCriteriaType, (newType, oldType) => {
  // Skip clearing during initialization
  if (props.isInitializing) return

  // Only clear fields if the type actually changed
  if (newType === oldType) return

  const updatedValue = { ...props.modelValue }

  if (newType === 'status_code') {
    updatedValue.responseBodyRegex = ''
    updatedValue.prometheusKey = ''
    updatedValue.prometheusMinValue = undefined
    updatedValue.prometheusMaxValue = undefined
    if (!updatedValue.statusCodeRegex) {
      updatedValue.statusCodeRegex = '^[23]\\d{2}$'
    }
  } else if (newType === 'response_body') {
    updatedValue.statusCodeRegex = ''
    updatedValue.prometheusKey = ''
    updatedValue.prometheusMinValue = undefined
    updatedValue.prometheusMaxValue = undefined
    if (!updatedValue.responseBodyRegex) {
      updatedValue.responseBodyRegex = 'success'
    }
  } else if (newType === 'prometheus') {
    updatedValue.statusCodeRegex = ''
    updatedValue.responseBodyRegex = ''
    if (!updatedValue.prometheusKey) {
      updatedValue.prometheusKey = 'cpu_usage'
    }
  }

  emit('update:modelValue', updatedValue)
})

const criteriaOptions = [
  { value: 'status_code', label: 'Status code' },
  { value: 'response_body', label: 'Response body' },
  { value: 'prometheus', label: 'Prometheus metric' }
] as const

const updateField = (field: keyof SuccessCriteriaData, value: unknown) => {
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value
  })
}
</script>

<template>
  <div class="criteria">
    <div class="button-group criteria-switch" role="group" aria-label="What counts as up">
      <button
        v-for="option in criteriaOptions"
        :key="option.value"
        type="button"
        class="btn"
        :aria-pressed="successCriteriaType === option.value"
        @click="() => { successCriteriaType = option.value; }"
      >
        {{ option.label }}
      </button>
    </div>

    <div v-if="successCriteriaType === 'status_code'" class="field">
      <label for="statusCodeRegex">Status code pattern</label>
      <input
        id="statusCodeRegex"
        class="mono"
        :value="modelValue.statusCodeRegex"
        @input="updateField('statusCodeRegex', ($event.target as HTMLInputElement).value)"
        type="text"
        placeholder="^[23]\d{2}$"
        required
      />
      <p class="field-hint">A regex for the HTTP status codes that count as up. The default is 2xx and 3xx.</p>
    </div>

    <div v-if="successCriteriaType === 'response_body'" class="field">
      <label for="responseBodyRegex">Response body pattern</label>
      <input
        id="responseBodyRegex"
        class="mono"
        :value="modelValue.responseBodyRegex"
        @input="updateField('responseBodyRegex', ($event.target as HTMLInputElement).value)"
        type="text"
        placeholder="success|ok|healthy"
        required
      />
      <p class="field-hint">A regex that must match the body of the response.</p>
    </div>

    <div v-if="successCriteriaType === 'prometheus'" class="form-grid">
      <div class="field span-all">
        <label for="prometheusKey">Metric key</label>
        <input
          id="prometheusKey"
          class="mono"
          :value="modelValue.prometheusKey"
          @input="updateField('prometheusKey', ($event.target as HTMLInputElement).value)"
          type="text"
          placeholder="cpu_usage"
          required
        />
        <p class="field-hint">The Prometheus metric that decides if the service is up.</p>
      </div>
      <div class="field">
        <label for="prometheusMinValue">Min value</label>
        <input
          id="prometheusMinValue"
          class="num"
          :value="modelValue.prometheusMinValue ?? ''"
          @input="updateField('prometheusMinValue', ($event.target as HTMLInputElement).value === '' ? undefined : parseFloat(($event.target as HTMLInputElement).value))"
          type="number"
          step="0.01"
          placeholder="0"
        />
      </div>
      <div class="field">
        <label for="prometheusMaxValue">Max value</label>
        <input
          id="prometheusMaxValue"
          class="num"
          :value="modelValue.prometheusMaxValue ?? ''"
          @input="updateField('prometheusMaxValue', ($event.target as HTMLInputElement).value === '' ? undefined : parseFloat(($event.target as HTMLInputElement).value))"
          type="number"
          step="0.01"
          placeholder="100"
        />
      </div>
      <p class="field-hint span-all">Set at least one of min and max.</p>
    </div>
  </div>
</template>

<style scoped>
.criteria {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

/* The three choices share the width; on a phone they still fit in one row */
.criteria-switch {
  align-self: flex-start;
  max-width: 100%;
}

.criteria-switch > .btn {
  flex: 1 1 auto;
  white-space: normal;
}
</style>

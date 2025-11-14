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

const updateField = (field: keyof SuccessCriteriaData, value: unknown) => {
  emit('update:modelValue', {
    ...props.modelValue,
    [field]: value
  })
}
</script>

<template>
  <div class="form-group">
    <label>Success Criteria *</label>
    <div class="success-criteria-container">
      <div class="criteria-toggle">
        <button
          type="button"
          @click="() => { successCriteriaType = 'status_code'; }"
          :class="['toggle-option', { active: successCriteriaType === 'status_code' }]"
        >
          HTTP Status Code
        </button>
        <button
          type="button"
          @click="() => { successCriteriaType = 'response_body'; }"
          :class="['toggle-option', { active: successCriteriaType === 'response_body' }]"
        >
          Response Body
        </button>
        <button
          type="button"
          @click="() => { successCriteriaType = 'prometheus'; }"
          :class="['toggle-option', { active: successCriteriaType === 'prometheus' }]"
        >
          Prometheus Metrics
        </button>
      </div>

      <div v-if="successCriteriaType === 'status_code'" class="criteria-fields">
        <div class="form-subgroup">
          <label for="statusCodeRegex">Status Code Pattern *</label>
          <input
            id="statusCodeRegex"
            :value="modelValue.statusCodeRegex"
            @input="updateField('statusCodeRegex', ($event.target as HTMLInputElement).value)"
            type="text"
            placeholder="^[23]\d{2}$"
            required
          />
          <small class="form-help-text">
            Regex pattern for successful HTTP status codes (default: 2xx and 3xx)
          </small>
        </div>
      </div>

      <div v-if="successCriteriaType === 'response_body'" class="criteria-fields">
        <div class="form-subgroup">
          <label for="responseBodyRegex">Response Body Pattern *</label>
          <input
            id="responseBodyRegex"
            :value="modelValue.responseBodyRegex"
            @input="updateField('responseBodyRegex', ($event.target as HTMLInputElement).value)"
            type="text"
            placeholder="success|ok|healthy"
            required
          />
          <small class="form-help-text">
            Regex pattern to match against response body content
          </small>
        </div>
      </div>

      <div v-if="successCriteriaType === 'prometheus'" class="criteria-fields">
        <div class="form-subgroup">
          <label for="prometheusKey">Metric Key *</label>
          <input
            id="prometheusKey"
            :value="modelValue.prometheusKey"
            @input="updateField('prometheusKey', ($event.target as HTMLInputElement).value)"
            type="text"
            placeholder="cpu_usage"
            required
          />
          <small class="form-help-text">
            Prometheus metric key for success evaluation
          </small>
        </div>
        <div class="prometheus-thresholds">
          <div class="form-subgroup">
            <label for="prometheusMinValue">Min Value</label>
            <input
              id="prometheusMinValue"
              :value="modelValue.prometheusMinValue ?? ''"
              @input="updateField('prometheusMinValue', ($event.target as HTMLInputElement).value === '' ? undefined : parseFloat(($event.target as HTMLInputElement).value))"
              type="number"
              step="0.01"
              placeholder="0"
            />
          </div>
          <div class="form-subgroup">
            <label for="prometheusMaxValue">Max Value</label>
            <input
              id="prometheusMaxValue"
              :value="modelValue.prometheusMaxValue ?? ''"
              @input="updateField('prometheusMaxValue', ($event.target as HTMLInputElement).value === '' ? undefined : parseFloat(($event.target as HTMLInputElement).value))"
              type="number"
              step="0.01"
              placeholder="100"
            />
          </div>
        </div>
        <small class="form-help-text">
          At least one threshold (min or max) is required
        </small>
      </div>
    </div>
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

.success-criteria-container {
  border: 1px solid #dee2e6;
  border-radius: 4px;
  padding: 1rem;
  background: #f8f9fa;
  box-sizing: border-box;
  width: 100%;
}

.criteria-toggle {
  display: flex;
  background: #fff;
  border: 1px solid #dee2e6;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 1rem;
}

.toggle-option {
  flex: 1;
  padding: 0.75rem 1rem;
  border: none;
  background: #fff;
  color: #6c757d;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  border-right: 1px solid #dee2e6;
  position: relative;
  z-index: 1;
  pointer-events: auto;
}

.toggle-option:last-child {
  border-right: none;
}

.toggle-option:hover {
  background: #f8f9fa;
  color: #495057;
}

.toggle-option.active {
  background: #007bff;
  color: white;
}

.criteria-fields {
  padding-top: 0.5rem;
}

.form-subgroup {
  margin-bottom: 0.75rem;
}

.form-subgroup:last-child {
  margin-bottom: 0;
}

.form-subgroup label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: #495057;
  font-size: 0.875rem;
}

.form-subgroup input[type="text"],
.form-subgroup input[type="number"] {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #dee2e6;
  border-radius: 4px;
  font-size: 0.875rem;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.form-subgroup input:focus {
  outline: none;
  border-color: #007bff;
  box-shadow: 0 0 0 2px rgba(0, 123, 255, 0.25);
}

.prometheus-thresholds {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  margin-top: 0.5rem;
}

.form-help-text {
  color: #6c757d;
  font-size: 0.85rem;
  margin-top: 0.5rem;
  display: block;
}

@media (max-width: 768px) {
  .prometheus-thresholds {
    grid-template-columns: 1fr;
    gap: 0.5rem;
  }
}
</style>

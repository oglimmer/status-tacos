<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useTenantsStore } from '../stores/tenants'
import type { Tenant, TenantRequest } from '../services/api'
import FormModal from './FormModal.vue'

const props = defineProps<{
  tenant?: Tenant | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const tenantsStore = useTenantsStore()

const form = ref<TenantRequest>({
  name: '',
  code: '',
  description: ''
})

const isSubmitting = ref(false)
const error = ref<string | null>(null)

const title = props.tenant ? 'Edit tenant' : 'Add tenant'
const isEditing = !!props.tenant

/** The public status page is at /<code>/status, so a new code breaks the old link. */
const codeChanged = computed(() => isEditing && form.value.code !== props.tenant?.code)

onMounted(() => {
  if (props.tenant) {
    form.value = {
      name: props.tenant.name,
      code: props.tenant.code,
      description: props.tenant.description || ''
    }
  }
})

const handleSubmit = async () => {
  if (isSubmitting.value) return

  error.value = null
  isSubmitting.value = true

  try {
    if (isEditing && props.tenant) {
      await tenantsStore.updateTenant(props.tenant.id, form.value)
    } else {
      await tenantsStore.createTenant(form.value)
    }

    emit('success')
    emit('close')
  } catch (err: unknown) {
    error.value = (err instanceof Error ? err.message : String(err)) || `Failed to ${isEditing ? 'update' : 'create'} tenant`
  } finally {
    isSubmitting.value = false
  }
}

const handleClose = () => {
  emit('close')
}
</script>

<template>
  <FormModal :title="title" @close="handleClose">
    <form class="form-stack" @submit.prevent="handleSubmit">
      <p v-if="error" class="callout callout-danger" role="alert">{{ error }}</p>

      <div class="field">
        <label for="name">Name</label>
        <input
          id="name"
          v-model="form.name"
          type="text"
          required
          :disabled="isSubmitting"
          placeholder="For example Acme production"
        />
      </div>

      <div class="field">
        <label for="code">Code</label>
        <input
          id="code"
          v-model="form.code"
          class="mono"
          type="text"
          required
          :disabled="isSubmitting"
          placeholder="acme"
          maxlength="50"
          pattern="[a-zA-Z0-9\-_]+"
          title="Only letters, numbers, hyphens, and underscores are allowed"
        />
        <p class="field-hint">
          Letters, numbers, hyphens and underscores. Your public status page is at
          <code>/{{ form.code || 'code' }}/status</code>.
        </p>
        <p v-if="codeChanged" class="callout callout-warning">
          The old status page link <code>/{{ tenant?.code }}/status</code> stops working.
          Update all links and embedded pages.
        </p>
      </div>

      <div class="field">
        <label for="description">Description <span class="optional">(optional)</span></label>
        <textarea
          id="description"
          v-model="form.description"
          :disabled="isSubmitting"
          rows="3"
        ></textarea>
      </div>

      <div class="form-actions">
        <button type="button" @click="handleClose" :disabled="isSubmitting" class="btn btn-secondary">
          Cancel
        </button>
        <button type="submit" :disabled="isSubmitting || !form.name || !form.code" class="btn btn-primary">
          {{ isSubmitting ? 'Saving...' : (isEditing ? 'Save changes' : 'Add tenant') }}
        </button>
      </div>
    </form>
  </FormModal>
</template>

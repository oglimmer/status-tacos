<script setup lang="ts">
import MonitorFormBase from './MonitorFormBase.vue'

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const props = defineProps<{
  preselectedTenantId?: number | null
}>()

const emit = defineEmits<{
  close: []
  success: []
}>()

const handleClose = () => {
  emit('close')
}

const handleSuccess = () => {
  emit('success')
}
</script>

<template>
  <MonitorFormBase
    title="Add monitor"
    submit-text="Add monitor"
    submitting-text="Adding..."
    :preselected-tenant-id="preselectedTenantId"
    @close="handleClose"
    @success="handleSuccess"
  />
</template>

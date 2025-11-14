<script setup lang="ts">
import MonitorFormBase from './MonitorFormBase.vue'
import type { MonitorResponse } from '../stores/monitors'

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const props = defineProps<{
  monitor: MonitorResponse | null
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
    :monitor="monitor"
    title="Edit Monitor"
    submit-text="Update Monitor"
    submitting-text="Updating..."
    @close="handleClose"
    @success="handleSuccess"
  />
</template>

<style scoped>
/* No styles needed - all handled by MonitorFormBase */
</style>

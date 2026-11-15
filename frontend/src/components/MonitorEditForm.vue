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
    title="Edit monitor"
    submit-text="Save changes"
    submitting-text="Saving..."
    @close="handleClose"
    @success="handleSuccess"
  />
</template>

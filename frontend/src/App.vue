<script setup lang="ts">
import { RouterView } from 'vue-router'
import ToastContainer from './components/ToastContainer.vue'
import ConfirmDialog from './components/ConfirmDialog.vue'
</script>

<template>
  <RouterView />
  <ToastContainer />
  <ConfirmDialog />
</template>

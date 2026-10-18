<script setup lang="ts">
defineProps<{
  title: string
  // For editors that need the room, for example the status page editor
  wide?: boolean
}>()

defineEmits<{
  close: []
}>()
</script>

<template>
  <!-- In body: a parent with backdrop-filter (the dashboard card) would hold position: fixed -->
  <Teleport to="body">
    <div class="modal-overlay" @click="$emit('close')">
      <div class="modal-content" :class="{ wide }" @click.stop>
        <div class="modal-header">
          <h2>{{ title }}</h2>
          <button @click="$emit('close')" class="close-btn" type="button">
            ×
          </button>
        </div>

        <div class="modal-body">
          <slot />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  width: 90%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

/* The wide modal fills the window height. Its body gives the space to the content, which keeps
   its own buttons in view. */
.modal-content.wide {
  max-width: 1400px;
  width: 96%;
  height: 92vh;
  height: 92dvh;
  max-height: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-content.wide .modal-header {
  flex: none;
  padding: 0.75rem 1.5rem;
}

.modal-content.wide .modal-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 1rem 1.5rem;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1.5rem;
  border-bottom: 1px solid #e9ecef;
}

.modal-header h2 {
  margin: 0;
  color: #2c3e50;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  color: #6c757d;
  padding: 0;
  width: 2rem;
  height: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  color: #495057;
}

.modal-body {
  padding: 1.5rem;
}
</style>

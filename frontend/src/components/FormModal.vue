<script setup lang="ts">
defineProps<{
  title: string
  // For editors that need the room, for example the status page editor
  wide?: boolean
  // 'md' gives a medium dialog for long forms with fields side by side.
  // Without it the dialog stays small.
  size?: 'md'
}>()

defineEmits<{
  close: []
}>()
</script>

<template>
  <!-- In body: a parent with a transform or filter would hold position: fixed -->
  <Teleport to="body">
    <div class="modal-overlay" @click="$emit('close')">
      <div
        class="modal-content"
        :class="{ wide, 'size-md': size === 'md' && !wide }"
        role="dialog"
        aria-modal="true"
        :aria-label="title"
        @click.stop
      >
        <div class="modal-header">
          <h2>{{ title }}</h2>
          <button
            type="button"
            class="btn btn-icon close-btn"
            aria-label="Close"
            @click="$emit('close')"
          >
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              aria-hidden="true"
            >
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
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
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: var(--overlay);
}

.modal-content {
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: var(--text);
  box-shadow: var(--shadow-2);
}

/* The medium dialog has room for two form columns. On a phone it is full width like the others. */
.modal-content.size-md {
  max-width: 720px;
}

/* The wide modal fills the window height. Its body gives the space to the content, which keeps
   its own buttons in view. */
.modal-content.wide {
  max-width: 1400px;
  height: 92vh;
  height: 92dvh;
  max-height: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-content.wide .modal-header {
  flex: none;
}

.modal-content.wide .modal-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--space-4) var(--space-5);
}

/* The header stays in view while a long form scrolls. */
.modal-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-3) var(--space-3) var(--space-5);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--surface);
}

.modal-header h2 {
  margin: 0;
  font-size: var(--text-lg);
  color: var(--text);
}

.modal-body {
  padding: var(--space-5);
}

@media (max-width: 520px) {
  .modal-overlay {
    padding: var(--space-2);
  }

  .modal-body {
    padding: var(--space-4);
  }
}
</style>

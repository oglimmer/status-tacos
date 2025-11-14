<script setup lang="ts">
import type { AlertContactResponse } from '../stores/alertContacts'

defineProps<{
  contact: AlertContactResponse
}>()

defineEmits<{
  'edit': [contact: AlertContactResponse]
  'toggle-status': [id: number]
  'delete': [id: number]
  'test-notification': [id: number]
}>()

const formatDate = (dateString: string) => {
  return new Date(dateString).toLocaleDateString()
}
</script>

<template>
  <div
    class="contact-card"
    :class="{ inactive: !contact.isActive }"
  >
    <div class="contact-header">
      <h3>{{ contact.name || 'Unnamed Contact' }}</h3>
      <div class="contact-status">
        <span class="contact-type" :data-type="contact.type">{{ contact.type }}</span>
      </div>
    </div>

    <div class="contact-details">
      <div class="detail-item">
        <strong>{{ contact.type === 'EMAIL' ? 'Email:' : 'URL:' }}</strong>
        <span
          :title="contact.type === 'HTTP' && contact.value.length > 25 ? contact.value : undefined"
          class="contact-value"
          :class="{ 'truncated': contact.type === 'HTTP' && contact.value.length > 25 }"
        >
          {{ contact.value }}
        </span>
      </div>
      <div v-if="contact.type === 'HTTP'" class="detail-item">
        <strong>Method:</strong>
        <span>{{ contact.httpMethod || 'GET' }}{{ contact.httpMethod === 'POST' && contact.httpContentType ? ` (${contact.httpContentType})` : '' }}</span>
      </div>
      <div v-if="contact.type === 'HTTP' && contact.httpHeaders && Object.keys(contact.httpHeaders).length > 0" class="detail-item">
        <strong>Headers:</strong>
        <span class="headers-list">
          <span v-for="(value, key) in contact.httpHeaders" :key="key" class="header-item" :title="`${key}: ${value}`">
            {{ key.length > 20 ? key.substring(0, 20) + '...' : key }}: {{ value.length > 30 ? value.substring(0, 30) + '...' : value }}
          </span>
        </span>
      </div>
      <div v-if="contact.type === 'HTTP' && contact.httpBody" class="detail-item">
        <strong>Body:</strong>
        <span class="body-preview">{{ contact.httpBody.substring(0, 50) }}{{ contact.httpBody.length > 50 ? '...' : '' }}</span>
      </div>
      <div class="detail-item">
        <strong>Tenant:</strong>
        <span>{{ contact.tenant.name }}</span>
      </div>
      <div class="detail-item">
        <strong>Status:</strong>
        <span :class="contact.isActive ? 'active' : 'inactive'">
          {{ contact.isActive ? 'Active' : 'Inactive' }}
        </span>
      </div>
      <div class="detail-item">
        <strong>Created:</strong>
        <span>{{ formatDate(contact.createdAt) }}</span>
      </div>
      <div v-if="contact.updatedAt !== contact.createdAt" class="detail-item">
        <strong>Updated:</strong>
        <span>{{ formatDate(contact.updatedAt) }}</span>
      </div>
    </div>

    <div class="contact-actions">
      <button
        @click="$emit('edit', contact)"
        class="btn btn-sm btn-primary"
      >
        Edit
      </button>
      <button
        @click="$emit('test-notification', contact.id)"
        class="btn btn-sm btn-info"
        :disabled="!contact.isActive"
        :title="!contact.isActive ? 'Contact must be active to send test notification' : 'Send test notification'"
      >
        Test
      </button>
      <button
        @click="$emit('toggle-status', contact.id)"
        class="btn btn-sm"
        :class="contact.isActive ? 'btn-secondary' : 'btn-success'"
      >
        {{ contact.isActive ? 'Disable' : 'Enable' }}
      </button>
      <button
        @click="$emit('delete', contact.id)"
        class="btn btn-sm btn-danger"
      >
        Delete
      </button>
    </div>
  </div>
</template>

<style scoped>
.contact-card {
  background: white;
  border-radius: 8px;
  border: 1px solid #e9ecef;
  padding: 1.5rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.contact-card.inactive {
  opacity: 0.7;
  background-color: #f8f9fa;
}

.contact-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
}

.contact-header h3 {
  margin: 0;
  color: #2c3e50;
  font-size: 1.1rem;
}

.contact-status .contact-type {
  padding: 0.25rem 0.5rem;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 500;
  text-transform: uppercase;
  background: #e2e8f0;
  color: #4a5568;
}

.contact-status .contact-type[data-type="HTTP"] {
  background: #dbeafe;
  color: #1e40af;
}

.contact-status .contact-type[data-type="EMAIL"] {
  background: #dcfce7;
  color: #166534;
}

.headers-list {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.header-item {
  font-size: 0.8rem;
  background: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  border: 1px solid #e9ecef;
}

.body-preview {
  font-family: monospace;
  font-size: 0.8rem;
  background: #f8f9fa;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  border: 1px solid #e9ecef;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-details {
  margin-bottom: 1rem;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 0.5rem;
  font-size: 0.9rem;
}

.detail-item strong {
  color: #495057;
  min-width: 80px;
}

.detail-item .active {
  color: #28a745;
  font-weight: 500;
}

.detail-item .inactive {
  color: #dc3545;
  font-weight: 500;
}

.contact-value.truncated {
  cursor: help;
  text-decoration: underline;
  text-decoration-style: dotted;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 55ch;
}

@media (max-width: 768px) {
  .contact-value.truncated {
    max-width: 25ch;
  }
}

.contact-actions {
  display: flex;
  gap: 0.5rem;
  margin-top: 1rem;
}

.btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.2s;
  font-weight: 500;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-sm {
  padding: 0.25rem 0.75rem;
  font-size: 0.8rem;
}

.btn-primary {
  background: #007bff;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0056b3;
}

.btn-secondary {
  background: #6c757d;
  color: white;
}

.btn-secondary:hover:not(:disabled) {
  background: #545b62;
}

.btn-success {
  background: #28a745;
  color: white;
}

.btn-success:hover:not(:disabled) {
  background: #1e7e34;
}

.btn-danger {
  background: #dc3545;
  color: white;
}

.btn-danger:hover:not(:disabled) {
  background: #c82333;
}

.btn-info {
  background: #17a2b8;
  color: white;
}

.btn-info:hover:not(:disabled) {
  background: #117a8b;
}
</style>

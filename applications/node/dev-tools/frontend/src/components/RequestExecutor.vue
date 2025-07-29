<template>
  <div class="request-executor" v-if="request">
    <div class="section-header">
      <h3>Execute Request</h3>
      <p class="help-text">Send the request to the connected module</p>
    </div>

    <div class="request-summary">
      <div class="summary-item">
        <span class="label">Document ID:</span>
        <span class="value">{{ request.document?.id || 'N/A' }}</span>
      </div>
      <div class="summary-item">
        <span class="label">Stream ID:</span>
        <span class="value">{{ request.metadata?.stream_id || 'N/A' }}</span>
      </div>
      <div class="summary-item" v-if="request.document?.blob">
        <span class="label">Blob Size:</span>
        <span class="value">{{ formatFileSize(request.document.blob.size) }}</span>
      </div>
    </div>

    <div class="actions">
      <button 
        @click="executeRequest" 
        class="execute-button"
        :disabled="loading || !moduleAddress"
      >
        {{ loading ? 'Sending...' : 'Send to Module' }}
      </button>
    </div>

    <div v-if="response" class="response-section">
      <h4>Response</h4>
      <div class="response-status" :class="{ success: response.success, error: !response.success }">
        <span class="status-label">Status:</span>
        <span class="status-value">{{ response.success ? 'Success' : 'Failed' }}</span>
      </div>
      
      <div v-if="response.processor_logs?.length" class="logs-section">
        <h5>Processor Logs:</h5>
        <div v-for="(log, index) in response.processor_logs" :key="index" class="log-entry">
          {{ log }}
        </div>
      </div>

      <div v-if="response.output_doc" class="output-section">
        <h5>Output Document:</h5>
        <pre>{{ JSON.stringify(response.output_doc, null, 2) }}</pre>
      </div>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { moduleService } from '../services/moduleService'

const props = defineProps<{
  request: any
  moduleAddress: string
  configData?: any
}>()

const emit = defineEmits<{
  'response-received': [response: any]
}>()

const loading = ref(false)
const response = ref<any>(null)
const error = ref('')

const executeRequest = async () => {
  if (!props.moduleAddress || !props.request) return
  
  loading.value = true
  error.value = ''
  response.value = null
  
  try {
    // Add the current config to the request
    const requestWithConfig = {
      ...props.request,
      config: {
        custom_json_config: props.configData || {}
      }
    }
    
    // TODO: Call backend endpoint to execute request
    // For now, we'll need to add this to the backend
    const result = await moduleService.executeRequest(props.moduleAddress, requestWithConfig)
    
    response.value = result
    emit('response-received', result)
  } catch (err: any) {
    error.value = err.response?.data?.details || err.message || 'Failed to execute request'
  } finally {
    loading.value = false
  }
}

const formatFileSize = (bytes: number): string => {
  if (!bytes) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.request-executor {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.section-header {
  margin-bottom: 1rem;
}

.section-header h3 {
  margin: 0 0 0.5rem 0;
  color: #333;
}

.help-text {
  margin: 0;
  color: #666;
  font-size: 0.9rem;
}

.request-summary {
  background: #f5f5f5;
  padding: 1rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.summary-item {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.summary-item:last-child {
  margin-bottom: 0;
}

.label {
  font-weight: 500;
  color: #666;
}

.value {
  color: #333;
}

.actions {
  margin-bottom: 1rem;
}

.execute-button {
  padding: 0.75rem 1.5rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.execute-button:hover:not(:disabled) {
  background: #357abd;
}

.execute-button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.response-section {
  border-top: 1px solid #eee;
  padding-top: 1rem;
}

.response-section h4 {
  margin: 0 0 1rem 0;
  color: #333;
}

.response-status {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem;
  border-radius: 4px;
  margin-bottom: 1rem;
}

.response-status.success {
  background: #d4edda;
  color: #155724;
}

.response-status.error {
  background: #f8d7da;
  color: #721c24;
}

.status-label {
  font-weight: 500;
}

.logs-section, .output-section {
  margin-top: 1rem;
}

.logs-section h5, .output-section h5 {
  margin: 0 0 0.5rem 0;
  color: #333;
  font-size: 1rem;
}

.log-entry {
  background: #f5f5f5;
  padding: 0.5rem;
  margin-bottom: 0.25rem;
  border-radius: 4px;
  font-size: 0.9rem;
}

.output-section pre {
  background: #f5f5f5;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 0.85rem;
  margin: 0;
}

.error-message {
  padding: 0.5rem;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c00;
  font-size: 0.9rem;
}
</style>
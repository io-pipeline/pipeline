<template>
  <div class="seed-data-builder">
    <div class="section-header">
      <h3>Create Seed Data</h3>
      <p class="help-text">Upload a file to create a PipeDoc with blob data for testing</p>
    </div>

    <div class="upload-section">
      <label for="file-upload" class="file-upload-label">
        <div class="upload-area" :class="{ 'has-file': fileData }">
          <svg v-if="!fileData" class="upload-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="7 10 12 15 17 10"></polyline>
            <line x1="12" y1="15" x2="12" y2="3"></line>
          </svg>
          <p v-if="!fileData">Click to upload or drag and drop</p>
          <div v-else class="file-info">
            <p class="file-name">{{ fileName }}</p>
            <p class="file-size">{{ formatFileSize(fileSize) }}</p>
            <p class="file-type">{{ mimeType }}</p>
          </div>
        </div>
      </label>
      <input
        id="file-upload"
        type="file"
        @change="handleFileUpload"
        accept=".txt,.pdf,.html,.htm,.docx,.doc,.json,.xml,.csv,.md"
        style="display: none"
      />
    </div>

    <div v-if="fileData" class="options-section">
      <div class="form-group">
        <label>Document ID</label>
        <input 
          v-model="docId" 
          placeholder="auto-generated"
          class="form-input"
        />
      </div>
      
      <div class="form-group">
        <label>Stream ID</label>
        <input 
          v-model="streamId" 
          placeholder="auto-generated"
          class="form-input"
        />
      </div>

      <div class="form-group">
        <label>Title</label>
        <input 
          v-model="title" 
          placeholder="Document title"
          class="form-input"
        />
      </div>
    </div>

    <div v-if="createdRequest" class="preview-section">
      <h4>Created ModuleProcessRequest:</h4>
      <pre>{{ JSON.stringify(createdRequest, null, 2) }}</pre>
    </div>

    <div class="actions">
      <button 
        v-if="fileData"
        @click="createSeedRequest" 
        class="primary-button"
        :disabled="processing"
      >
        Create PipeDoc Request
      </button>
      
      <button 
        v-if="fileData"
        @click="clearFile" 
        class="secondary-button"
      >
        Clear
      </button>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  currentConfig?: any
}>()

const emit = defineEmits<{
  'request-created': [request: any]
}>()

const fileData = ref<File | null>(null)
const fileName = ref('')
const fileSize = ref(0)
const mimeType = ref('')
const docId = ref('')
const streamId = ref('')
const title = ref('')
const processing = ref(false)
const error = ref('')
const createdRequest = ref<any>(null)

const handleFileUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  
  if (!file) return
  
  try {
    error.value = ''
    fileName.value = file.name
    fileSize.value = file.size
    mimeType.value = file.type || 'application/octet-stream'
    title.value = file.name.replace(/\.[^/.]+$/, '') // Remove extension
    
    // Store the file object directly
    fileData.value = file
  } catch (err) {
    error.value = 'Error processing file: ' + (err as Error).message
  }
}

const createSeedRequest = async () => {
  if (!fileData.value) return
  
  processing.value = true
  error.value = ''
  
  try {
    // Create FormData for file upload
    const formData = new FormData()
    formData.append('file', fileData.value)
    formData.append('docId', docId.value || '')
    formData.append('streamId', streamId.value || '')
    formData.append('title', title.value || fileName.value)
    formData.append('config', JSON.stringify(props.currentConfig || {}))
    
    // Upload file and create request
    const response = await fetch('http://localhost:3000/api/seed/create', {
      method: 'POST',
      body: formData
    })
    
    if (!response.ok) {
      const error = await response.json()
      throw new Error(error.details || error.error || 'Failed to create seed data')
    }
    
    const { request } = await response.json()
    createdRequest.value = request
    emit('request-created', request)
    
  } catch (err) {
    error.value = 'Error creating request: ' + (err as Error).message
  } finally {
    processing.value = false
  }
}

const clearFile = () => {
  fileData.value = null
  fileName.value = ''
  fileSize.value = 0
  mimeType.value = ''
  docId.value = ''
  streamId.value = ''
  title.value = ''
  createdRequest.value = null
  error.value = ''
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 Bytes'
  const k = 1024
  const sizes = ['Bytes', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
</script>

<style scoped>
.seed-data-builder {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.section-header {
  margin-bottom: 1.5rem;
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

.upload-section {
  margin-bottom: 1.5rem;
}

.file-upload-label {
  cursor: pointer;
}

.upload-area {
  border: 2px dashed #ddd;
  border-radius: 8px;
  padding: 2rem;
  text-align: center;
  transition: all 0.3s ease;
  background: #fafafa;
}

.upload-area:hover {
  border-color: #4a90e2;
  background: #f0f7ff;
}

.upload-area.has-file {
  border-style: solid;
  background: #f0f7ff;
}

.upload-icon {
  color: #999;
  margin-bottom: 0.5rem;
}

.file-info {
  text-align: left;
}

.file-name {
  font-weight: 600;
  color: #333;
  margin: 0 0 0.25rem 0;
}

.file-size, .file-type {
  font-size: 0.9rem;
  color: #666;
  margin: 0;
}

.options-section {
  display: grid;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-group label {
  font-size: 0.9rem;
  font-weight: 500;
  color: #333;
}

.form-input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.form-input:focus {
  outline: none;
  border-color: #4a90e2;
}

.preview-section {
  background: #f5f5f5;
  border-radius: 4px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.preview-section h4 {
  margin: 0 0 0.5rem 0;
  color: #333;
}

.preview-section pre {
  margin: 0;
  font-size: 0.85rem;
  overflow-x: auto;
  white-space: pre-wrap;
}

.actions {
  display: flex;
  gap: 1rem;
}

.primary-button, .secondary-button {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.primary-button {
  background: #4a90e2;
  color: white;
}

.primary-button:hover:not(:disabled) {
  background: #357abd;
}

.primary-button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.secondary-button {
  background: #f5f5f5;
  color: #333;
  border: 1px solid #ddd;
}

.secondary-button:hover {
  background: #e8e8e8;
}

.error-message {
  margin-top: 1rem;
  padding: 0.5rem;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c00;
  font-size: 0.9rem;
}
</style>
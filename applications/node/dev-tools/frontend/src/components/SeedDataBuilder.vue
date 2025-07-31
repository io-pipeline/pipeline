<template>
  <div>

      <v-file-input
        v-model="fileData"
        @update:model-value="handleFileChange"
        label="Select file"
        prepend-icon="mdi-upload"
        accept=".txt,.pdf,.html,.htm,.docx,.doc,.json,.xml,.csv,.md"
        show-size
        variant="outlined"
        :hint="fileData ? `${mimeType}` : 'Click to upload or drag and drop'"
        persistent-hint
        class="mb-4"
      />

      <v-expand-transition>
        <div v-if="fileData">
          <v-text-field
            v-model="docId"
            label="Document ID"
            placeholder="auto-generated"
            variant="outlined"
            density="compact"
            class="mb-3"
          />
          
          <v-text-field
            v-model="streamId"
            label="Stream ID"
            placeholder="auto-generated"
            variant="outlined"
            density="compact"
            class="mb-3"
          />
          
          <v-text-field
            v-model="title"
            label="Title"
            placeholder="Document title"
            variant="outlined"
            density="compact"
            class="mb-3"
          />
        </div>
      </v-expand-transition>

      <v-alert
        v-if="error"
        type="error"
        variant="tonal"
        closable
        @click:close="error = ''"
        class="mb-4"
      >
        {{ error }}
      </v-alert>
      
    <div class="mt-4 d-flex justify-end" v-if="fileData">
      <v-btn
        @click="clearFile"
        variant="text"
        class="mr-2"
      >
        Clear
      </v-btn>
      <v-btn
        @click="createSeedRequest"
        color="primary"
        variant="flat"
        :loading="processing"
        :disabled="processing"
      >
        Create Module Process Request
      </v-btn>
    </div>
    
      <v-expand-transition>
        <v-card v-if="createdRequest" class="mt-4">
          <v-card-title>ModuleProcessRequest Output</v-card-title>
          
          <v-divider />
          
          <v-card-text>
            <CodeBlock
              :code="JSON.stringify(createdRequest, null, 2)"
              language="json"
              title="Request JSON"
              :show-save="true"
              filename="module-process-request.bin"
            />
          </v-card-text>
          
          <v-card-actions>
            <v-spacer />
            <v-btn
              color="primary"
              variant="flat"
              size="large"
              prepend-icon="mdi-arrow-right"
              @click="goToProcessDocument"
            >
              Process This Document
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-expand-transition>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import CodeBlock from './CodeBlock.vue'

const props = defineProps<{
  currentConfig?: any
}>()

const emit = defineEmits<{
  'request-created': [request: any]
}>()

const fileData = ref<File | File[] | null>(null)
const fileName = ref('')
const fileSize = ref(0)
const mimeType = ref('')
const docId = ref('')
const streamId = ref('')
const title = ref('')
const processing = ref(false)
const error = ref('')
const createdRequest = ref<any>(null)

const handleFileChange = async (files: File | File[] | null) => {
  // v-file-input with single file returns a File object, not an array
  let file: File | null = null
  
  if (!files) {
    clearFile()
    return
  }
  
  if (Array.isArray(files)) {
    if (files.length === 0) {
      clearFile()
      return
    }
    file = files[0]
  } else {
    file = files
  }
  
  try {
    error.value = ''
    
    // Check if file is valid
    if (!file || !(file instanceof File)) {
      throw new Error('Invalid file object')
    }
    
    fileName.value = file.name
    fileSize.value = file.size
    mimeType.value = file.type || 'application/octet-stream'
    title.value = file.name.replace(/\.[^/.]+$/, '') // Remove extension
  } catch (err) {
    error.value = 'Error processing file: ' + (err as Error).message
  }
}

const createSeedRequest = async () => {
  if (!fileData.value) return
  
  let file: File
  if (Array.isArray(fileData.value)) {
    if (fileData.value.length === 0) return
    file = fileData.value[0]
  } else {
    file = fileData.value
  }
  
  processing.value = true
  error.value = ''
  
  try {
    // Read file as base64
    const reader = new FileReader()
    const fileData64 = await new Promise<string>((resolve, reject) => {
      reader.onload = () => {
        const base64 = (reader.result as string).split(',')[1] // Remove data:mime;base64, prefix
        resolve(base64)
      }
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
    
    // Create PipeDoc with file data (client-side)
    const pipeDoc = {
      id: docId.value || `doc-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
      title: title.value || file.name,
      source_uri: `file://${file.name}`,
      source_mime_type: file.type || 'application/octet-stream',
      document_type: 'seed-data',
      blob: {
        data: fileData64,
        mime_type: file.type || 'application/octet-stream',
        size: file.size,
        file_name: file.name
      },
      metadata: {
        source: 'dev-tool-seed-builder',
        created_at: new Date().toISOString(),
        file_name: file.name,
        file_size: file.size,
        mime_type: file.type || 'application/octet-stream'
      }
    }
    
    // Create the ModuleProcessRequest
    const request = {
      document: pipeDoc,
      config: {
        custom_json_config: props.currentConfig || {}
      },
      metadata: {
        pipeline_name: 'dev-tools-test',
        pipe_step_name: 'manual-test',
        stream_id: streamId.value || `stream-${Date.now()}`,
        current_hop_number: 1,
        context_params: {
          'source': 'seed-data-builder',
          'test_mode': 'true'
        }
      }
    }
    
    createdRequest.value = request
    // Don't emit immediately - wait for user to click "Process This Document"
    
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

const goToProcessDocument = () => {
  if (createdRequest.value) {
    emit('request-created', createdRequest.value)
  }
}
</script>

<style scoped>
/* Styles handled by CodeBlock component */
</style>
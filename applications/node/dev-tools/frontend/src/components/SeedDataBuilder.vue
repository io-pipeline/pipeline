<template>
  <div>
    <!-- Use the native PipeDoc editor -->
    <div v-show="!createdRequest">
      <PipeDocEditorNative
        :module-context="moduleContext"
        :show-test-data-button="false"
        @save="handleDocumentSave"
      />
    </div>
    
    <!-- Created Request Output -->
    <v-card v-show="createdRequest" class="mt-4">
        <v-card-title class="d-flex align-center">
          <v-icon start>mdi-package-variant-closed</v-icon>
          ModuleProcessRequest Created
          <v-spacer />
          <v-chip size="small" color="success">
            Ready to Process
          </v-chip>
        </v-card-title>
        
        <v-divider />
        
        <v-card-text>
          <v-tabs v-model="outputTab" class="mb-4">
            <v-tab value="summary">Summary</v-tab>
            <v-tab value="json">JSON</v-tab>
            <v-tab value="protobuf">Protobuf</v-tab>
          </v-tabs>
          
          <v-tabs-window v-model="outputTab">
            <!-- Summary Tab -->
            <v-tabs-window-item value="summary">
              <v-list density="compact">
                <v-list-item>
                  <v-list-item-title>Document ID</v-list-item-title>
                  <v-list-item-subtitle>{{ createdRequest?.document?.id || 'N/A' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title>Stream ID</v-list-item-title>
                  <v-list-item-subtitle>{{ createdRequest?.metadata?.stream_id || 'N/A' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item>
                  <v-list-item-title>Processing Step</v-list-item-title>
                  <v-list-item-subtitle>{{ createdRequest?.metadata?.pipe_step_name || 'N/A' }}</v-list-item-subtitle>
                </v-list-item>
                <v-list-item v-if="createdRequest?.document?.blob">
                  <v-list-item-title>File Size</v-list-item-title>
                  <v-list-item-subtitle>{{ formatFileSize(createdRequest.document.blob.size) }}</v-list-item-subtitle>
                </v-list-item>
              </v-list>
            </v-tabs-window-item>
            
            <!-- JSON Tab -->
            <v-tabs-window-item value="json">
              <CodeBlock
                :code="JSON.stringify(createdRequest, null, 2)"
                language="json"
                title="Request JSON"
                :show-save="true"
                filename="module-process-request.json"
              />
            </v-tabs-window-item>
            
            <!-- Protobuf Tab -->
            <v-tabs-window-item value="protobuf">
              <v-alert type="info" variant="tonal" class="mb-4">
                Binary protobuf format ready for processing
              </v-alert>
              <div class="d-flex justify-end">
                <v-btn
                  @click="downloadProtobuf"
                  prepend-icon="mdi-download"
                  variant="tonal"
                >
                  Download .bin file
                </v-btn>
              </div>
            </v-tabs-window-item>
          </v-tabs-window>
        </v-card-text>
        
        <v-divider />
        
        <v-card-actions class="flex-column align-start pa-4">
          <div class="text-h6 mb-3">What would you like to do with this request?</div>
          
          <v-row class="w-100">
            <v-col cols="12" md="4">
              <v-btn
                block
                size="large"
                :color="savedToRepository ? 'primary' : 'success'"
                variant="flat"
                :prepend-icon="savedToRepository ? 'mdi-pencil' : 'mdi-cloud-upload'"
                @click="savedToRepository ? goToDataSeeding() : saveToRepository()"
                :disabled="!repositoryStore.isConnected"
              >
                {{ savedToRepository ? 'Edit in Repository' : 'Save to Repository' }}
              </v-btn>
              <div class="text-caption mt-1 text-center">
                <template v-if="repositoryStore.isConnected">
                  {{ savedToRepository ? 'Saved as' : 'Save as' }} {{ getRepositoryPath() }}
                  <!-- Debug: blob.fileName = {{ createdRequest?.document?.blob?.fileName || 'none' }} -->
                </template>
                <template v-else>
                  Repository not connected
                </template>
              </div>
            </v-col>
            
            <v-col cols="12" md="4">
              <v-btn
                block
                size="large"
                color="primary"
                variant="flat"
                prepend-icon="mdi-download"
                @click="downloadToDisk"
              >
                Download to Disk
              </v-btn>
              <div class="text-caption mt-1 text-center">
                Save as {{ getDownloadFilename() }}
              </div>
            </v-col>
            
            <v-col cols="12" md="4">
              <v-btn
                block
                size="large"
                color="indigo"
                variant="flat"
                prepend-icon="mdi-play"
                @click="processDocument"
              >
                Process Document
              </v-btn>
              <div class="text-caption mt-1 text-center">
                Send to Process Document tab
              </div>
            </v-col>
          </v-row>
          
          <v-divider class="my-4 w-100" />
          
          <div class="d-flex w-100 justify-space-between">
            <v-btn
              @click="copyToClipboard"
              prepend-icon="mdi-content-copy"
              variant="text"
            >
              Copy JSON
            </v-btn>
            <v-btn
              @click="clearRequest"
              variant="tonal"
            >
              Create Another Request
            </v-btn>
          </div>
        </v-card-actions>
      </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, getCurrentInstance } from 'vue'
import { useModuleStore } from '../stores/moduleStore'
import { useRepositoryStore } from '../stores/repositoryStore'
import { useNotification } from '../composables/useNotification'
import PipeDocEditorNative from './PipeDocEditorNative.vue'
import CodeBlock from './CodeBlock.vue'
import { create, toBinary } from '@bufbuild/protobuf'
import { 
  ModuleProcessRequestSchema,
  PipeDocSchema,
  ProcessingMetadataSchema,
  BlobSchema,
  type ModuleProcessRequest
} from '../gen/pipe_step_processor_service_pb'

const moduleStore = useModuleStore()
const repositoryStore = useRepositoryStore()
const { showSuccess, showError } = useNotification()

// Props
interface Props {
  currentConfig: any
}

const props = defineProps<Props>()

// Emits
const emit = defineEmits<{
  'request-created': [request: any]
  'process-document': [request: any]
}>()

// State
const createdRequest = ref<any>(null)
const outputTab = ref('summary')
const savedToRepository = ref(false)

// Computed
const moduleContext = computed(() => {
  if (!moduleStore.activeModule) return null
  
  // Extract the module type from capabilities
  let moduleType = undefined
  if (moduleStore.activeModule.capabilities && moduleStore.activeModule.capabilities.length > 0) {
    // Get the first capability type (e.g., "PARSER", "CHUNKER", etc.)
    moduleType = moduleStore.activeModule.capabilities[0]
  }
  
  return {
    name: moduleStore.activeModule.name,
    address: moduleStore.activeModuleAddress,
    type: moduleType
  }
})

const repositoryPath = computed(() => {
  return getRepositoryPath()
})

// Handle document save from editor
const handleDocumentSave = async (pipeDoc: any) => {
  try {
    // Create the ModuleProcessRequest
    const request = {
      document: pipeDoc,
      metadata: {
        stream_id: `stream-${Date.now()}`,
        pipe_step_name: moduleStore.activeModule?.name || 'unknown',
        processing_timestamp: new Date().toISOString()
      },
      config: {
        custom_json_config: props.currentConfig || {}
      }
    }
    
    createdRequest.value = request
    showSuccess('Process request created successfully')
    
    // Don't emit here - just show the options
  } catch (error) {
    showError(`Failed to create request: ${error}`)
  }
}

// Clear the created request
const clearRequest = () => {
  createdRequest.value = null
  outputTab.value = 'summary'
  savedToRepository.value = false
}

// Navigate to data seeding tab
const goToDataSeeding = () => {
  // Navigate to the data seeding tab
  const appInstance = getCurrentInstance()?.proxy?.$parent?.$parent
  if (appInstance && appInstance.activeTab !== undefined) {
    appInstance.activeTab = 'data-seeding'
  }
}

// Get repository path for saving
const getRepositoryPath = () => {
  if (!createdRequest.value || !moduleStore.activeModule) return ''
  
  // Use the actual module name, convert to lowercase
  const moduleName = moduleStore.activeModule.name.toLowerCase().replace(/[^a-zA-Z0-9-_]/g, '_')
  const date = new Date()
  const dateStr = date.toISOString().split('T')[0].replace(/-/g, '')
  
  // Check if we have a blob with fileName
  const blobFileName = createdRequest.value.document?.blob?.fileName
  
  let filename
  if (blobFileName) {
    // Keep the original filename and just append .bin
    filename = `${blobFileName}.bin`
  } else {
    // Fallback to document title
    const title = createdRequest.value.document.title.replace(/[^a-zA-Z0-9-_\.]/g, '_')
    filename = `${title}.bin`
  }
  
  return `${moduleName}/${dateStr}/${filename}`
}

// Get download filename  
const getDownloadFilename = () => {
  if (!createdRequest.value) return 'document.bin'
  
  // Use the original filename if available, otherwise use title
  if (createdRequest.value.document.blob?.fileName) {
    // Keep the original filename and just append .bin
    return `${createdRequest.value.document.blob.fileName}.bin`
  } else {
    const docName = createdRequest.value.document.title.replace(/[^a-zA-Z0-9-_\.]/g, '_')
    return `${docName}.bin`
  }
}

// Save to repository service
const saveToRepository = async () => {
  if (!createdRequest.value || !repositoryStore.isConnected) return
  
  try {
    const path = getRepositoryPath()
    console.log('Saving to repository path:', path)
    console.log('Request to save:', createdRequest.value)
    
    // Prepare the data for protobuf - convert base64 blob data to Uint8Array
    const requestForProto = JSON.parse(JSON.stringify(createdRequest.value))
    if (requestForProto.document?.blob?.data) {
      // Convert base64 string to Uint8Array for protobuf bytes field
      const base64Data = requestForProto.document.blob.data
      const binaryString = atob(base64Data)
      const bytes = new Uint8Array(binaryString.length)
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i)
      }
      requestForProto.document.blob.data = bytes
    }
    
    // Create protobuf message
    const message = create(ModuleProcessRequestSchema, requestForProto)
    const bytes = toBinary(ModuleProcessRequestSchema, message)
    
    // Convert to base64 for storage
    const base64 = btoa(String.fromCharCode(...new Uint8Array(bytes)))
    
    await repositoryStore.saveFile(path, base64, 'application/octet-stream')
    savedToRepository.value = true
    showSuccess(`Saved to repository: ${path}`)
  } catch (error) {
    console.error('Save to repository error:', error)
    showError(`Failed to save to repository: ${error}`)
  }
}

// Download to disk
const downloadToDisk = () => {
  downloadProtobuf()
}

// Process document - send to process tab
const processDocument = () => {
  if (createdRequest.value) {
    console.log('Sending request to process tab:', createdRequest.value)
    emit('process-document', createdRequest.value)
    showSuccess('Switching to Process Document tab...')
  }
}

// Download as protobuf binary
const downloadProtobuf = async () => {
  if (!createdRequest.value) return
  
  try {
    // Prepare the data for protobuf - convert base64 blob data to Uint8Array
    const requestForProto = JSON.parse(JSON.stringify(createdRequest.value))
    if (requestForProto.document?.blob?.data) {
      // Convert base64 string to Uint8Array for protobuf bytes field
      const base64Data = requestForProto.document.blob.data
      const binaryString = atob(base64Data)
      const bytes = new Uint8Array(binaryString.length)
      for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i)
      }
      requestForProto.document.blob.data = bytes
    }
    
    // Create protobuf message
    const message = create(ModuleProcessRequestSchema, requestForProto)
    const bytes = toBinary(ModuleProcessRequestSchema, message)
    
    // Create download
    const blob = new Blob([bytes], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = getDownloadFilename()
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    
    showSuccess('Downloaded protobuf binary')
  } catch (error) {
    console.error('Download error:', error)
    showError(`Failed to create protobuf: ${error}`)
  }
}

// Copy JSON to clipboard
const copyToClipboard = async () => {
  if (!createdRequest.value) return
  
  try {
    await navigator.clipboard.writeText(JSON.stringify(createdRequest.value, null, 2))
    showSuccess('JSON copied to clipboard')
  } catch (error) {
    showError('Failed to copy to clipboard')
  }
}

// Format file size
const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}
</script>

<style scoped>
.v-tabs {
  margin-bottom: 0 !important;
}
</style>
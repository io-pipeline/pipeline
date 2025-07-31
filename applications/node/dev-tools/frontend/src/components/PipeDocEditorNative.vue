<template>
  <v-card>
    <v-card-title class="d-flex align-center">
      <v-icon start>mdi-file-document-edit</v-icon>
      <span>{{ cardTitle }}</span>
      <v-spacer />
      <v-chip 
        v-if="moduleContext"
        size="small"
        variant="tonal"
      >
        {{ moduleContext.name }}
      </v-chip>
    </v-card-title>
    
    <v-card-text>
      <!-- Core Fields -->
      <v-row>
        <v-col cols="12" md="6">
          <v-text-field
            v-model="pipeDoc.id"
            label="Document ID"
            density="compact"
            :readonly="mode === 'edit'"
            hint="Unique identifier for this document"
          />
        </v-col>
        
        <v-col cols="12" md="6">
          <v-text-field
            v-model="pipeDoc.title"
            label="Title"
            density="compact"
            :rules="[v => !!v || 'Title is required']"
          />
        </v-col>
      </v-row>
      
      <v-row>
        <v-col cols="12" md="6">
          <v-text-field
            v-model="pipeDoc.sourceUri"
            label="Source URI"
            density="compact"
            hint="Original source location"
          />
        </v-col>
        
        <v-col cols="12" md="6">
          <v-select
            v-model="pipeDoc.sourceMimeType"
            :items="commonMimeTypes"
            label="MIME Type"
            density="compact"
          />
        </v-col>
      </v-row>
      
      <v-row>
        <v-col cols="12" md="6">
          <v-select
            v-model="pipeDoc.documentType"
            :items="documentTypes"
            label="Document Type"
            density="compact"
          />
        </v-col>
        
        <v-col cols="12" md="6">
          <v-text-field
            v-model.number="pipeDoc.version"
            label="Version"
            type="number"
            density="compact"
            :min="1"
          />
        </v-col>
      </v-row>
      
      <!-- Body Content -->
      <v-textarea
        v-model="pipeDoc.body"
        label="Document Body"
        rows="5"
        auto-grow
        max-rows="15"
        variant="outlined"
        density="compact"
        class="mt-4"
      />
      
      <!-- Keywords -->
      <v-combobox
        v-model="pipeDoc.keywords"
        label="Keywords"
        chips
        multiple
        clearable
        variant="outlined"
        density="compact"
        class="mt-4"
        hint="Press Enter to add keywords"
      >
        <template v-slot:chip="{ props, item }">
          <v-chip
            v-bind="props"
            size="small"
            closable
            @click:close="removeKeyword(item.raw)"
          >
            {{ item.raw }}
          </v-chip>
        </template>
      </v-combobox>
      
      <!-- File Upload Alert for Parser modules -->
      <v-alert
        v-if="moduleContext?.name?.toLowerCase().includes('parser') && (!pipeDoc.blob || !pipeDoc.blob.data)"
        type="info"
        variant="tonal"
        class="mt-4"
        prepend-icon="mdi-information"
      >
        <v-alert-title>File Upload Required</v-alert-title>
        Upload a file in the Binary Blob section below to test the {{ moduleContext.name }}
      </v-alert>

      <!-- Metadata and other expandable sections -->
      <v-expansion-panels class="mt-4" v-model="expandedPanels">
        <v-expansion-panel>
          <v-expansion-panel-title>
            <v-icon start>mdi-tag-multiple</v-icon>
            Metadata
            <v-chip size="x-small" class="ml-2">
              {{ Object.keys(pipeDoc.metadata || {}).length }} items
            </v-chip>
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <v-row
              v-for="(value, key, index) in pipeDoc.metadata"
              :key="index"
              class="align-center"
            >
              <v-col cols="5">
                <v-text-field
                  :model-value="key"
                  label="Key"
                  density="compact"
                  @update:model-value="updateMetadataKey(key, $event)"
                />
              </v-col>
              <v-col cols="6">
                <v-text-field
                  v-model="pipeDoc.metadata[key]"
                  label="Value"
                  density="compact"
                />
              </v-col>
              <v-col cols="1">
                <v-btn
                  icon="mdi-delete"
                  size="small"
                  variant="text"
                  @click="deleteMetadata(key)"
                />
              </v-col>
            </v-row>
            
            <v-btn
              prepend-icon="mdi-plus"
              variant="tonal"
              size="small"
              @click="addMetadata"
              class="mt-2"
            >
              Add Metadata
            </v-btn>
          </v-expansion-panel-text>
        </v-expansion-panel>
        
        <!-- Blob (always show for parsers, optional for others) -->
        <v-expansion-panel v-if="pipeDoc.blob || showBlob || moduleContext?.type?.toLowerCase().includes('parser') || moduleContext?.name?.toLowerCase().includes('parser')">
          <v-expansion-panel-title>
            <v-icon start>mdi-file-upload</v-icon>
            Binary Blob
            <v-chip 
              v-if="(moduleContext?.type?.toLowerCase().includes('parser') || moduleContext?.name?.toLowerCase().includes('parser')) && (!pipeDoc.blob || !pipeDoc.blob.data)"
              size="x-small" 
              color="warning"
              class="ml-2"
            >
              Required
            </v-chip>
            <v-chip v-else-if="pipeDoc.blob" size="x-small" class="ml-2">
              {{ formatFileSize(pipeDoc.blob.size || 0) }}
            </v-chip>
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <v-file-input
              v-model="blobFile"
              label="Select file to process"
              prepend-icon="mdi-paperclip"
              density="compact"
              variant="outlined"
              @update:model-value="handleFileUpload"
              clearable
              show-size
              :hint="getFileUploadHint()"
              persistent-hint
              accept=".txt,.pdf,.html,.htm,.docx,.doc,.json,.xml,.csv,.md,.rtf,.odt"
            />
            
            <v-row v-if="pipeDoc.blob" class="mt-2">
              <v-col cols="12" md="6">
                <v-text-field
                  v-model="pipeDoc.blob.fileName"
                  label="File Name"
                  density="compact"
                  readonly
                />
              </v-col>
              <v-col cols="12" md="6">
                <v-text-field
                  v-model="pipeDoc.blob.mimeType"
                  label="MIME Type"
                  density="compact"
                  readonly
                />
              </v-col>
            </v-row>
          </v-expansion-panel-text>
        </v-expansion-panel>
        
        <!-- Processing Info -->
        <v-expansion-panel v-if="mode === 'edit' || pipeDoc.stepExecutionRecords?.length">
          <v-expansion-panel-title>
            <v-icon start>mdi-cog-transfer</v-icon>
            Processing History
            <v-chip size="x-small" class="ml-2">
              {{ pipeDoc.stepExecutionRecords?.length || 0 }} steps
            </v-chip>
          </v-expansion-panel-title>
          <v-expansion-panel-text>
            <v-timeline density="compact" side="end">
              <v-timeline-item
                v-for="(record, index) in pipeDoc.stepExecutionRecords"
                :key="index"
                :dot-color="record.success ? 'success' : 'error'"
                size="small"
              >
                <div>
                  <strong>{{ record.processorName }}</strong>
                  <div class="text-caption">
                    {{ formatTimestamp(record.timestamp) }}
                  </div>
                  <div v-if="record.error" class="text-error text-caption">
                    {{ record.error }}
                  </div>
                </div>
              </v-timeline-item>
            </v-timeline>
          </v-expansion-panel-text>
        </v-expansion-panel>
      </v-expansion-panels>
      
      <!-- View Mode Toggle -->
      <div class="d-flex justify-end mt-4">
        <v-btn-toggle
          v-model="viewMode"
          mandatory
          density="compact"
          variant="outlined"
        >
          <v-btn value="form" size="small">
            <v-icon start>mdi-form-select</v-icon>
            Form
          </v-btn>
          <v-btn value="json" size="small">
            <v-icon start>mdi-code-json</v-icon>
            JSON
          </v-btn>
        </v-btn-toggle>
      </div>
      
      <!-- JSON View -->
      <v-expand-transition>
        <v-sheet
          v-if="viewMode === 'json'"
          class="mt-4 pa-3"
          color="grey-lighten-5"
          rounded
        >
          <pre class="json-view">{{ JSON.stringify(pipeDoc, null, 2) }}</pre>
        </v-sheet>
      </v-expand-transition>
    </v-card-text>
    
    <v-divider />
    
    <v-card-actions>
      <v-btn
        v-if="showTestDataButton"
        @click="generateTestData"
        prepend-icon="mdi-test-tube"
      >
        Generate Test Data
      </v-btn>
      <v-spacer />
      <v-btn @click="resetForm">
        Reset
      </v-btn>
      <v-btn
        color="primary"
        @click="saveDocument"
        :loading="saving"
        prepend-icon="mdi-package-variant"
      >
        {{ saveButtonText || 'Create Process Request' }}
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useNotification } from '../composables/useNotification'
import type { PipeDoc } from '../gen/pipeline_core_types_pb'

const { showSuccess, showError } = useNotification()

// Props
interface Props {
  mode?: 'create' | 'edit'
  moduleContext?: {
    name: string
    address: string
    type?: string
  }
  initialDocument?: Partial<PipeDoc>
  showTestDataButton?: boolean
  saveButtonText?: string
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'create',
  showTestDataButton: false,
  saveButtonText: 'Save'
})

// Emits
const emit = defineEmits<{
  'save': [doc: any]
  'generate-test-data': [doc: any]
}>()

// State
const pipeDoc = ref<any>({
  id: '',
  title: '',
  sourceUri: '',
  sourceMimeType: 'text/plain',
  documentType: 'manual',
  body: '',
  keywords: [],
  metadata: {},
  version: 1
})

const blobFile = ref<File | File[] | null>(null)
const expandedPanels = ref<number[]>([])
const viewMode = ref<'form' | 'json'>('form')
const saving = ref(false)
const showBlob = ref(false)

// Auto-expand blob panel if we're in a module context (likely need file upload)
const defaultExpandedPanels = computed(() => {
  const panels = []
  if (props.moduleContext && (props.moduleContext.type?.toLowerCase().includes('parser') || props.moduleContext.name?.toLowerCase().includes('parser'))) {
    panels.push(1) // Blob panel is second (index 1)
  }
  return panels
})

// Constants
const commonMimeTypes = [
  'text/plain',
  'text/html',
  'text/markdown',
  'application/json',
  'application/xml',
  'application/pdf',
  'application/x-protobuf',
  'image/jpeg',
  'image/png'
]

const documentTypes = [
  'manual',
  'parsed',
  'chunked',
  'embedded',
  'enriched',
  'classified',
  'summarized',
  'semantic',
  'processed'
]

// Helper function to generate consistent document ID
const generateDocumentId = () => {
  // Use timestamp and a small random component for uniqueness
  // This will be updated with file hash when a file is uploaded
  return `doc-${Date.now()}-${Math.random().toString(36).substr(2, 5)}`
}

// Simple hash function for generating consistent IDs
const hashString = async (str: string): Promise<string> => {
  const encoder = new TextEncoder()
  const data = encoder.encode(str)
  const hashBuffer = await crypto.subtle.digest('SHA-256', data)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
  return hashHex.substring(0, 12) // Use first 12 chars of hash
}

// Computed
const cardTitle = computed(() => {
  if (props.moduleContext?.name) {
    return `${props.moduleContext.name} Document Editor`
  }
  return props.mode === 'edit' ? 'Edit PipeDoc' : 'Create PipeDoc'
})

// Initialize document
const initializeDocument = () => {
  // Only initialize if pipeDoc is not already set or is being reset
  if (pipeDoc.value && Object.keys(pipeDoc.value).length > 0 && pipeDoc.value.id) {
    return // Don't reinitialize if we already have a document
  }
  
  const baseDoc = {
    id: generateDocumentId(),
    title: props.moduleContext?.name ? `${props.moduleContext.name} Output` : 'New Document',
    sourceUri: props.moduleContext ? `${props.moduleContext.name.toLowerCase()}://generated` : 'manual://user-input',
    sourceMimeType: 'text/plain',
    documentType: getDocumentTypeForModule(),
    body: '',
    keywords: [],
    metadata: {},
    version: 1
  }
  
  // Add module context to metadata
  if (props.moduleContext) {
    baseDoc.metadata = {
      generatedBy: props.moduleContext.name,
      moduleAddress: props.moduleContext.address,
      generatedAt: new Date().toISOString()
    }
  }
  
  // Merge with initial document
  if (props.initialDocument) {
    Object.assign(baseDoc, props.initialDocument)
  }
  
  pipeDoc.value = baseDoc
}

// Get document type based on module
const getDocumentTypeForModule = () => {
  if (!props.moduleContext?.type) return 'manual'
  
  const typeMap: Record<string, string> = {
    'parser': 'parsed',
    'tikaparser': 'parsed',
    'chunker': 'chunked',
    'embedder': 'embedded',
    'enricher': 'enriched',
    'classifier': 'classified',
    'summarizer': 'summarized'
  }
  
  // Convert to lowercase for case-insensitive matching
  const moduleType = props.moduleContext.type.toLowerCase()
  return typeMap[moduleType] || 'processed'
}

// Methods
const getFileUploadHint = () => {
  if (props.moduleContext?.type?.toLowerCase().includes('parser') || props.moduleContext?.name?.toLowerCase().includes('parser')) {
    return `Upload a file to test ${props.moduleContext.name}`
  }
  return 'Optional: Upload a file to include binary data'
}

const handleFileUpload = async (fileOrFiles: File | File[] | null) => {
  // Handle both single file and array of files
  if (!fileOrFiles) {
    pipeDoc.value.blob = null
    return
  }
  
  // Convert to array if single file
  const files = Array.isArray(fileOrFiles) ? fileOrFiles : [fileOrFiles]
  
  if (files.length === 0) {
    pipeDoc.value.blob = null
    return
  }
  
  const file = files[0]
  const buffer = await file.arrayBuffer()
  const base64 = btoa(String.fromCharCode(...new Uint8Array(buffer)))
  
  pipeDoc.value.blob = {
    data: base64,
    mimeType: file.type || 'application/octet-stream',
    size: file.size,
    fileName: file.name
  }
  
  // Auto-populate fields based on uploaded file
  if (!pipeDoc.value.title || pipeDoc.value.title === 'New Document' || 
      pipeDoc.value.title === `${props.moduleContext?.name} Output`) {
    // Use filename without extension as title
    pipeDoc.value.title = file.name.replace(/\.[^/.]+$/, '')
  }
  
  // Update source URI to reference the file
  if (pipeDoc.value.sourceUri === 'manual://user-input' || 
      pipeDoc.value.sourceUri === `${props.moduleContext?.name.toLowerCase()}://generated`) {
    pipeDoc.value.sourceUri = `file://${file.name}`
  }
  
  // Update MIME type to match the file
  if (file.type) {
    pipeDoc.value.sourceMimeType = file.type
  }
  
  // Generate consistent document ID based on file content and title
  const contentHash = await hashString(base64 + pipeDoc.value.title)
  pipeDoc.value.id = `doc-${contentHash}`
  
  // Don't read content into body - that's what the parser/processor does
}

const addMetadata = () => {
  const key = `key_${Object.keys(pipeDoc.value.metadata).length + 1}`
  pipeDoc.value.metadata[key] = ''
}

const updateMetadataKey = (oldKey: string, newKey: string) => {
  if (oldKey === newKey) return
  
  const value = pipeDoc.value.metadata[oldKey]
  delete pipeDoc.value.metadata[oldKey]
  pipeDoc.value.metadata[newKey] = value
}

const deleteMetadata = (key: string) => {
  delete pipeDoc.value.metadata[key]
}

const removeKeyword = (keyword: string) => {
  const index = pipeDoc.value.keywords.indexOf(keyword)
  if (index > -1) {
    pipeDoc.value.keywords.splice(index, 1)
  }
}

const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatTimestamp = (timestamp: any) => {
  if (!timestamp) return ''
  const date = new Date(timestamp)
  return date.toLocaleString()
}

const resetForm = () => {
  // Clear the existing document first
  pipeDoc.value = {}
  blobFile.value = null
  expandedPanels.value = []
  // Now reinitialize
  initializeDocument()
}

const saveDocument = () => {
  saving.value = true
  
  // Validate
  if (!pipeDoc.value.title) {
    showError('Title is required')
    saving.value = false
    return
  }
  
  // Deep clone to prevent mutation issues
  const docToSave = JSON.parse(JSON.stringify(pipeDoc.value))
  
  // Emit save event
  emit('save', docToSave)
  
  // Don't show success here - let the parent component handle it
  saving.value = false
}

const generateTestData = () => {
  emit('generate-test-data', pipeDoc.value)
}

// Watch for prop changes
watch(() => props.moduleContext, (newVal, oldVal) => {
  // Only reinitialize if the module actually changed
  if (props.mode === 'create' && newVal?.address !== oldVal?.address) {
    initializeDocument()
  }
}, { deep: true })

watch(() => props.initialDocument, () => {
  initializeDocument()
}, { deep: true })

// Initialize
onMounted(() => {
  initializeDocument()
  // Auto-expand blob panel for parsers
  if (props.moduleContext?.type?.toLowerCase().includes('parser') || props.moduleContext?.name?.toLowerCase().includes('parser')) {
    expandedPanels.value = [1] // Blob is second panel
  }
})

// Expose
defineExpose({
  pipeDoc,
  resetForm,
  getDocument: () => pipeDoc.value
})
</script>

<style scoped>
.json-view {
  font-family: monospace;
  font-size: 12px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.v-expansion-panel-text :deep(.v-expansion-panel-text__wrapper) {
  padding: 12px;
}
</style>
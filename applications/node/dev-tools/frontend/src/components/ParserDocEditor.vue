<template>
  <v-card>
    <v-card-title>
      <v-icon start>mdi-file-upload</v-icon>
      Parser Document Creator
    </v-card-title>
    
    <v-card-text>
      <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
        {{ error }}
      </v-alert>
      
      <!-- Phase 1: File Upload (when no file is selected) -->
      <div v-if="!fileUploaded" class="upload-phase">
        <v-row>
          <v-col cols="12">
            <div class="upload-section">
              <h3 class="text-h6 mb-4">Upload a file to parse</h3>
              <v-file-input
                v-model="selectedFile"
                label="Choose file"
                prepend-icon="mdi-paperclip"
                variant="outlined"
                @change="handleFileSelect"
                :loading="isProcessing"
                class="mb-4"
              />
              
              <v-text-field
                v-model="documentType"
                label="Document Type"
                placeholder="e.g., invoice, contract, report"
                variant="outlined"
                density="compact"
                hint="Categorize your document for processing"
                persistent-hint
                class="mb-4"
              />
              
              <v-textarea
                v-model="description"
                label="Description (optional)"
                placeholder="Brief description of the document"
                variant="outlined"
                density="compact"
                rows="2"
                class="mb-4"
              />
            </div>
          </v-col>
        </v-row>
      </div>
      
      <!-- Phase 2: Document Editor (after file upload) -->
      <div v-else class="editor-phase">
        <v-alert type="success" variant="tonal" class="mb-4" closable @click:close="resetUpload">
          <strong>{{ uploadedFileName }}</strong> uploaded successfully
        </v-alert>
        
        <v-tabs v-model="tab" class="mb-4">
          <v-tab value="metadata">Document Metadata</v-tab>
          <v-tab value="content">Full Editor</v-tab>
          <v-tab value="json">JSON View</v-tab>
        </v-tabs>
        
        <v-tabs-window v-model="tab">
          <!-- Metadata Tab - Focused view for parsers -->
          <v-tabs-window-item value="metadata">
            <div class="metadata-editor">
              <v-row>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="pipeDoc.title"
                    label="Document Title"
                    variant="outlined"
                    density="compact"
                    class="mb-3"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="pipeDoc.documentType"
                    label="Document Type"
                    variant="outlined"
                    density="compact"
                    class="mb-3"
                  />
                </v-col>
              </v-row>
              
              <v-row>
                <v-col cols="12">
                  <v-text-field
                    v-model="pipeDoc.sourceUri"
                    label="Source URI"
                    variant="outlined"
                    density="compact"
                    readonly
                    class="mb-3"
                  />
                </v-col>
              </v-row>
              
              <v-row>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="pipeDoc.sourceMimeType"
                    label="MIME Type"
                    variant="outlined"
                    density="compact"
                    readonly
                    class="mb-3"
                  />
                </v-col>
                <v-col cols="12" md="6">
                  <v-text-field
                    v-model="pipeDoc.documentStatus"
                    label="Status"
                    variant="outlined"
                    density="compact"
                    class="mb-3"
                  />
                </v-col>
              </v-row>
              
              <!-- Tags -->
              <v-row>
                <v-col cols="12">
                  <v-combobox
                    v-model="pipeDoc.tags"
                    label="Tags"
                    variant="outlined"
                    density="compact"
                    multiple
                    chips
                    closable-chips
                    class="mb-3"
                  />
                </v-col>
              </v-row>
              
              <!-- Additional Metadata as key-value pairs -->
              <v-card variant="outlined" class="mb-3">
                <v-card-title class="text-subtitle-2 pa-3">
                  Additional Metadata
                </v-card-title>
                <v-card-text class="pa-3">
                  <metadata-editor v-model="additionalMetadata" />
                </v-card-text>
              </v-card>
              
              <!-- Blob Preview (read-only) -->
              <v-expansion-panels class="mb-3">
                <v-expansion-panel>
                  <v-expansion-panel-title>
                    <v-icon start>mdi-file</v-icon>
                    File Data ({{ formatFileSize(pipeDoc.blob?.data?.length || 0) }})
                  </v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <div class="blob-info">
                      <p><strong>Filename:</strong> {{ pipeDoc.blob?.filename }}</p>
                      <p><strong>MIME Type:</strong> {{ pipeDoc.blob?.mimeType }}</p>
                      <p><strong>Encoding:</strong> {{ pipeDoc.blob?.encoding }}</p>
                      <p><strong>Blob ID:</strong> {{ pipeDoc.blob?.blobId }}</p>
                    </div>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>
            </div>
          </v-tabs-window-item>
          
          <!-- Full Editor Tab -->
          <v-tabs-window-item value="content">
            <div v-if="schema" class="compact-form">
              <json-forms
                :data="pipeDoc"
                :schema="schema"
                :renderers="renderers"
                @change="handleChange"
              />
            </div>
          </v-tabs-window-item>
          
          <!-- JSON View Tab -->
          <v-tabs-window-item value="json">
            <pre class="json-view">{{ JSON.stringify(pipeDoc, null, 2) }}</pre>
          </v-tabs-window-item>
        </v-tabs-window>
      </div>
    </v-card-text>
    
    <v-card-actions>
      <v-btn v-if="fileUploaded" @click="resetUpload" variant="text">
        Upload New File
      </v-btn>
      <v-spacer />
      <v-btn @click="resetForm">Reset</v-btn>
      <v-btn 
        color="primary" 
        @click="saveDocument"
        :disabled="!fileUploaded"
      >
        Save Document
      </v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'
import { customRenderers } from '../renderers'
import MetadataEditor from './MetadataEditor.vue'
import type { PipeDoc } from '@pipeline/protobuf-forms'
import type { JsonSchema } from '@pipeline/protobuf-forms'
import { schemaConnectService } from '../services/schemaConnectService'

// State
const tab = ref('metadata')
const schema = ref<JsonSchema | null>(null)
const error = ref<string | null>(null)
const selectedFile = ref<File | null>(null)
const fileUploaded = ref(false)
const uploadedFileName = ref('')
const isProcessing = ref(false)
const documentType = ref('')
const description = ref('')
const additionalMetadata = ref<Array<{key: string, value: string}>>([])

// Renderers
const renderers = Object.freeze([...vuetifyRenderers, ...customRenderers, ...vanillaRenderers])

// PipeDoc data
const pipeDoc = ref<Partial<PipeDoc>>({
  id: `doc-${Date.now()}`,
  documentStatus: 'PENDING'
})

// Load schema using Connect service
onMounted(async () => {
  try {
    schema.value = await schemaConnectService.getMessageSchema('PipeDoc')
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schema'
  }
})

// Handle file selection
async function handleFileSelect() {
  const file = selectedFile.value
  if (!file) return
  
  isProcessing.value = true
  error.value = null
  
  try {
    // Read file as base64
    const reader = new FileReader()
    
    await new Promise((resolve, reject) => {
      reader.onload = resolve
      reader.onerror = reject
      reader.readAsDataURL(file)
    })
    
    const base64Data = reader.result as string
    const base64Content = base64Data.split(',')[1]
    
    // Create PipeDoc with file data
    pipeDoc.value = {
      id: `doc-${Date.now()}`,
      title: file.name.replace(/\.[^/.]+$/, ''), // Remove extension
      sourceUri: `file://${file.name}`,
      sourceMimeType: file.type || 'application/octet-stream',
      documentType: documentType.value || 'unspecified',
      documentStatus: 'UPLOADED',
      description: description.value,
      creationDate: new Date().toISOString(),
      blob: {
        blobId: `blob-${Date.now()}`,
        data: base64Content,
        mimeType: file.type || 'application/octet-stream',
        filename: file.name,
        encoding: 'base64',
        metadata: [
          { key: 'original-size', value: file.size.toString() },
          { key: 'upload-date', value: new Date().toISOString() }
        ]
      },
      tags: [],
      metadata: {}
    }
    
    // Convert additional metadata to object
    if (additionalMetadata.value.length > 0) {
      const metadataObj: Record<string, string> = {}
      additionalMetadata.value.forEach(item => {
        if (item.key) {
          metadataObj[item.key] = item.value
        }
      })
      pipeDoc.value.metadata = metadataObj
    }
    
    uploadedFileName.value = file.name
    fileUploaded.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to process file'
  } finally {
    isProcessing.value = false
  }
}

// Handle form changes
const handleChange = (event: any) => {
  pipeDoc.value = event.data
}

// Reset upload to start over
function resetUpload() {
  fileUploaded.value = false
  selectedFile.value = null
  uploadedFileName.value = ''
  tab.value = 'metadata'
  documentType.value = ''
  description.value = ''
  additionalMetadata.value = []
}

// Reset form completely
function resetForm() {
  resetUpload()
  pipeDoc.value = {
    id: `doc-${Date.now()}`,
    documentStatus: 'PENDING'
  }
}

// Save document
function saveDocument() {
  console.log('Saving PipeDoc:', pipeDoc.value)
  emit('document-ready', pipeDoc.value)
}

// Format file size
function formatFileSize(base64Length: number): string {
  const bytes = Math.floor(base64Length * 0.75)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

// Define emits
const emit = defineEmits<{
  'document-ready': [data: any]
}>()

// Expose methods and data for parent component
defineExpose({
  pipeDoc,
  resetForm,
  getDocumentData: () => pipeDoc.value
})
</script>

<style scoped>
.upload-section {
  padding: 24px;
  border: 2px dashed rgba(0, 0, 0, 0.12);
  border-radius: 8px;
  text-align: center;
}

.v-theme--dark .upload-section {
  border-color: rgba(255, 255, 255, 0.12);
}

.upload-section h3 {
  margin-bottom: 24px;
}

.metadata-editor {
  padding: 16px;
}

.blob-info p {
  margin: 4px 0;
  font-size: 14px;
}

.json-view {
  background-color: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 14px;
}

.v-theme--dark .json-view {
  background-color: #1e1e1e;
}

/* Reuse compact form styles */
.compact-form :deep(.v-input) {
  margin-bottom: 4px !important;
}

.compact-form :deep(.v-messages) {
  min-height: 14px !important;
  font-size: 11px !important;
}

/* Additional compact styles from PipeDocEditor */
.compact-form :deep(.vertical-layout-item.v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

.compact-form :deep(.array-list.v-card) {
  margin-bottom: 4px !important;
  border: 1px solid rgba(0, 0, 0, 0.12) !important;
}
</style>
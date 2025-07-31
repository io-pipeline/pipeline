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
      <v-chip 
        v-if="mode === 'edit' && sourceInfo"
        size="small"
        color="primary"
        variant="tonal"
        class="ml-2"
      >
        {{ sourceInfo }}
      </v-chip>
    </v-card-title>
    
    <v-card-text>
      <!-- Mode selector -->
      <v-btn-toggle 
        v-if="showModeToggle"
        v-model="mode" 
        mandatory 
        density="compact" 
        class="mb-4"
      >
        <v-btn value="create" prepend-icon="mdi-plus">
          Create New
        </v-btn>
        <v-btn 
          value="edit" 
          prepend-icon="mdi-pencil"
          :disabled="!repositoryStore.isConnected"
        >
          Edit Existing
        </v-btn>
      </v-btn-toggle>
      
      <!-- File selector for edit mode -->
      <v-alert
        v-if="mode === 'edit' && !repositoryStore.isConnected"
        type="info"
        variant="tonal"
        class="mb-4"
      >
        Connect to a repository to edit existing PipeDocs
      </v-alert>
      
      <v-card 
        v-if="mode === 'edit' && repositoryStore.isConnected && !currentDocument"
        variant="outlined"
        class="mb-4"
      >
        <v-card-text>
          <v-btn 
            color="primary" 
            @click="showFileBrowser = true"
            prepend-icon="mdi-folder-open"
          >
            Browse Repository
          </v-btn>
        </v-card-text>
      </v-card>
      
      <!-- Editor tabs -->
      <v-tabs v-model="tab" class="mb-4">
        <v-tab value="form">Form Editor</v-tab>
        <v-tab value="json">JSON View</v-tab>
        <v-tab value="schema">Schema</v-tab>
      </v-tabs>
      
      <v-tabs-window v-model="tab">
        <v-tabs-window-item value="form">
          <div v-if="schema">
            <json-forms
              :data="pipeDoc"
              :schema="schema"
              :renderers="renderers"
              @change="handleChange"
            />
          </div>
          <v-skeleton-loader
            v-else
            type="article"
            :loading="loading"
          />
        </v-tabs-window-item>
        
        <v-tabs-window-item value="json">
          <v-textarea
            :model-value="JSON.stringify(pipeDoc, null, 2)"
            readonly
            auto-grow
            variant="outlined"
            class="json-view"
          />
        </v-tabs-window-item>
        
        <v-tabs-window-item value="schema">
          <v-textarea
            :model-value="JSON.stringify(schema, null, 2)"
            readonly
            auto-grow
            variant="outlined"
            class="json-view"
          />
        </v-tabs-window-item>
      </v-tabs-window>
    </v-card-text>
    
    <v-divider />
    
    <v-card-actions>
      <v-btn 
        v-if="mode === 'edit' && currentDocument"
        @click="cancelEdit"
      >
        Cancel Edit
      </v-btn>
      <v-spacer />
      <v-btn 
        @click="resetForm"
        :disabled="loading"
      >
        Reset
      </v-btn>
      <v-btn 
        color="primary" 
        @click="saveDocument"
        :loading="saving"
      >
        {{ mode === 'create' ? 'Create' : 'Save' }}
      </v-btn>
    </v-card-actions>
    
    <!-- File Browser Dialog -->
    <v-dialog 
      v-model="showFileBrowser" 
      max-width="800"
      height="600"
    >
      <v-card>
        <v-card-title>
          Select PipeDoc from Repository
          <v-spacer />
          <v-btn 
            icon="mdi-close" 
            variant="text"
            @click="showFileBrowser = false"
          />
        </v-card-title>
        <v-divider />
        <v-card-text class="pa-0" style="height: 500px; overflow-y: auto;">
          <RepositoryFileBrowser 
            :file-filter="isPipeDoc"
            @file-selected="loadDocument"
          />
        </v-card-text>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'
import { customRenderers } from '../renderers'
import { useRepositoryStore } from '../stores/repositoryStore'
import { useNotification } from '../composables/useNotification'
import { schemaConnectService } from '../services/schemaConnectService'
import { filesystemService } from '../services/connectService'
import RepositoryFileBrowser from './RepositoryFileBrowser.vue'
import type { JsonSchema } from '@pipeline/protobuf-forms'
import type { Node } from '../gen/filesystem_service_pb'
import { 
  Node_NodeType,
  type GetNodeRequest,
  type UpdateNodeRequest,
  type CreateNodeRequest
} from '../gen/filesystem_service_pb'
import { create, fromBinary, toBinary, type MessageInitShape } from '@bufbuild/protobuf'
import { PipeDocSchema } from '../gen/pipeline_core_types_pb'
import { Any } from '@bufbuild/protobuf/wkt'

const repositoryStore = useRepositoryStore()
const { showSuccess, showError } = useNotification()

// Props
interface Props {
  initialMode?: 'create' | 'edit'
  serviceType?: string // e.g., 'Parser', 'Embedder', 'Chunker'
  moduleContext?: {
    name: string
    address: string
    type?: string
  }
  initialDocument?: any
  showModeToggle?: boolean
  saveToRepository?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  initialMode: 'create',
  showModeToggle: true,
  saveToRepository: true
})

// Emits
const emit = defineEmits<{
  'document-saved': [doc: any]
  'document-loaded': [doc: any]
  'document-created': [doc: any]
}>()

// State
const mode = ref<'create' | 'edit'>(props.initialMode)
const tab = ref('form')
const schema = ref<JsonSchema | null>(null)
const loading = ref(false)
const saving = ref(false)
const showFileBrowser = ref(false)
const currentDocument = ref<Node | null>(null)

// Renderers
const renderers = Object.freeze([...vuetifyRenderers, ...customRenderers, ...vanillaRenderers])

// Get document type based on service context
const getDocumentType = () => {
  if (!props.moduleContext?.type && !props.serviceType) return 'manual'
  
  const serviceType = props.serviceType || props.moduleContext?.type || ''
  
  // Map service types to document types
  const typeMap: Record<string, string> = {
    'Parser': 'parsed',
    'TikaParser': 'parsed', 
    'Embedder': 'embedded',
    'Chunker': 'chunked',
    'Semantic': 'semantic',
    'Enricher': 'enriched',
    'Classifier': 'classified',
    'Summarizer': 'summarized'
  }
  
  return typeMap[serviceType] || 'processed'
}

// Get appropriate MIME type based on context
const getMimeType = () => {
  if (!props.moduleContext?.type && !props.serviceType) {
    return 'application/x-protobuf'
  }
  
  const serviceType = props.serviceType || props.moduleContext?.type || ''
  
  // Special handling for parsers - they might produce different content types
  if (serviceType.includes('Parser')) {
    return 'text/plain' // Default, but parser might override
  }
  
  return 'application/x-protobuf'
}

// Generate source URI based on context
const getSourceUri = () => {
  if (props.moduleContext?.name) {
    return `${props.moduleContext.name.toLowerCase()}://generated`
  }
  return 'pipedoc://new'
}

// Initial PipeDoc data
const defaultPipeDoc = () => {
  const baseDoc = {
    id: `doc-${Date.now()}`,
    title: props.moduleContext?.name ? `${props.moduleContext.name} Output` : 'New Document',
    sourceUri: getSourceUri(),
    sourceMimeType: getMimeType(),
    documentType: getDocumentType(),
    body: '',
    keywords: [],
    metadata: {}
  }
  
  // Add module context to metadata if available
  if (props.moduleContext) {
    baseDoc.metadata = {
      generatedBy: props.moduleContext.name,
      moduleAddress: props.moduleContext.address,
      generatedAt: new Date().toISOString()
    }
  }
  
  // Merge with any initial document data
  if (props.initialDocument) {
    return { ...baseDoc, ...props.initialDocument }
  }
  
  return baseDoc
}

const pipeDoc = ref<any>(defaultPipeDoc())

// Computed properties
const moduleContext = computed(() => props.moduleContext)
const showModeToggle = computed(() => props.showModeToggle)

const cardTitle = computed(() => {
  if (props.moduleContext?.name) {
    return `${props.moduleContext.name} Output Editor`
  }
  return 'PipeDoc Editor'
})

const sourceInfo = computed(() => {
  if (!currentDocument.value) return null
  return currentDocument.value.name
})

// Load schema on mount
onMounted(async () => {
  loading.value = true
  try {
    schema.value = await schemaConnectService.getMessageSchema('PipeDoc')
  } catch (err) {
    showError('Failed to load PipeDoc schema')
    console.error(err)
  } finally {
    loading.value = false
  }
})

// Handle form changes
const handleChange = (event: any) => {
  pipeDoc.value = event.data
}

// File filter for browser
const isPipeDoc = (node: Node) => {
  return node.type === Node_NodeType.FILE && 
    (node.payloadType === 'PipeDoc' || 
     node.mimeType?.includes('pipedoc') ||
     node.name.endsWith('.pipedoc'))
}

// Load document from repository
const loadDocument = async (node: Node) => {
  showFileBrowser.value = false
  loading.value = true
  
  try {
    const request: GetNodeRequest = { id: node.id }
    const response = await filesystemService.getNode(request)
    
    if (response.payload) {
      // Deserialize the PipeDoc from the Any field
      if (response.payload.typeUrl.endsWith('PipeDoc')) {
        const pipeDocMessage = fromBinary(PipeDocSchema, response.payload.value)
        pipeDoc.value = pipeDocMessage.toJson()
        currentDocument.value = response
        showSuccess('Document loaded successfully')
        emit('document-loaded', pipeDoc.value)
      } else {
        showError('Selected file is not a PipeDoc')
      }
    }
  } catch (error) {
    showError(`Failed to load document: ${error}`)
  } finally {
    loading.value = false
  }
}

// Save document
const saveDocument = async () => {
  saving.value = true
  
  try {
    // If not saving to repository, just emit the document
    if (!props.saveToRepository) {
      emit('document-created', pipeDoc.value)
      emit('document-saved', pipeDoc.value)
      showSuccess('Document prepared successfully')
      saving.value = false
      return
    }
    
    // Create PipeDoc message
    const pipeDocMessage = create(PipeDocSchema, pipeDoc.value as MessageInitShape<typeof PipeDocSchema>)
    const pipeDocBytes = toBinary(PipeDocSchema, pipeDocMessage)
    
    // Create Any wrapper
    const payload = create(Any, {
      typeUrl: 'type.googleapis.com/io.pipeline.search.model.PipeDoc',
      value: pipeDocBytes
    })
    
    if (mode.value === 'create') {
      // Create new node
      const metadata: Record<string, string> = {
        createdBy: 'dev-tools',
        documentType: pipeDoc.value.documentType
      }
      
      // Add module context to metadata
      if (props.moduleContext) {
        metadata.generatedByModule = props.moduleContext.name
        metadata.moduleAddress = props.moduleContext.address
        if (props.moduleContext.type) {
          metadata.moduleType = props.moduleContext.type
        }
      }
      
      const request: CreateNodeRequest = {
        parentId: '', // Root for now, could add folder selection
        name: `${pipeDoc.value.title}.pipedoc`,
        type: Node_NodeType.FILE,
        payload,
        metadata
      }
      
      const response = await filesystemService.createNode(request)
      currentDocument.value = response
      mode.value = 'edit'
      showSuccess('Document created successfully')
      emit('document-created', pipeDoc.value)
    } else {
      // Update existing node
      if (!currentDocument.value) {
        showError('No document selected for editing')
        return
      }
      
      const request: UpdateNodeRequest = {
        id: currentDocument.value.id,
        name: `${pipeDoc.value.title}.pipedoc`,
        payload,
        metadata: {
          ...currentDocument.value.metadata,
          updatedBy: 'dev-tools',
          lastModified: new Date().toISOString()
        }
      }
      
      const response = await filesystemService.updateNode(request)
      currentDocument.value = response
      showSuccess('Document updated successfully')
    }
    
    emit('document-saved', pipeDoc.value)
  } catch (error) {
    showError(`Failed to save document: ${error}`)
  } finally {
    saving.value = false
  }
}

// Reset form
const resetForm = () => {
  if (mode.value === 'create') {
    pipeDoc.value = defaultPipeDoc()
  } else if (currentDocument.value?.payload) {
    // Reset to original loaded document
    try {
      const pipeDocMessage = fromBinary(PipeDocSchema, currentDocument.value.payload.value)
      pipeDoc.value = pipeDocMessage.toJson()
    } catch (error) {
      showError('Failed to reset document')
    }
  }
}

// Cancel edit mode
const cancelEdit = () => {
  mode.value = 'create'
  currentDocument.value = null
  pipeDoc.value = defaultPipeDoc()
}

// Watch for mode changes
watch(mode, (newMode) => {
  if (newMode === 'create') {
    currentDocument.value = null
    pipeDoc.value = defaultPipeDoc()
  }
})

// Watch for module context changes
watch(() => props.moduleContext, () => {
  if (mode.value === 'create' && !currentDocument.value) {
    // Update the document with new context
    pipeDoc.value = defaultPipeDoc()
  }
}, { deep: true })

// Watch for initial document changes
watch(() => props.initialDocument, (newDoc) => {
  if (newDoc && mode.value === 'create') {
    pipeDoc.value = { ...defaultPipeDoc(), ...newDoc }
  }
}, { deep: true })

// Expose for parent components
defineExpose({
  pipeDoc,
  resetForm,
  loadDocument,
  getDocumentData: () => pipeDoc.value
})
</script>

<style scoped>
.json-view {
  font-family: monospace;
  font-size: 12px;
}

.json-view :deep(textarea) {
  font-family: monospace;
  font-size: 12px;
  line-height: 1.4;
}

/* Compact form styling */
:deep(.v-input) {
  margin-bottom: 8px;
}

:deep(.v-messages) {
  min-height: 18px;
  font-size: 11px;
}
</style>
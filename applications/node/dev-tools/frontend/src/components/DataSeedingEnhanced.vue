<template>
  <v-container fluid>
    <v-row>
      <!-- Left side - Create/Upload Section -->
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title>
            <v-icon start>mdi-file-upload</v-icon>
            Create Seed Data
          </v-card-title>
          
          <v-card-text>
            <!-- Module Selection -->
            <v-select
              v-model="selectedModuleId"
              :items="availableModules"
              item-title="displayName"
              item-value="id"
              label="Target Module"
              prepend-icon="mdi-puzzle"
              @update:model-value="loadModuleConfig"
              class="mb-4"
            />
            
            <!-- Adaptive PipeDoc Form -->
            <v-expand-transition>
              <div v-if="selectedModuleId">
                <v-divider class="mb-4" />
                
                <!-- Use ParserDocEditor for PARSER modules -->
                <ParserDocEditor 
                  v-if="isParserModule"
                  ref="docEditor"
                  @document-ready="handleDocumentReady"
                />
                
                <!-- Use regular PipeDocEditor for other modules -->
                <PipeDocEditor 
                  v-else
                  ref="docEditor"
                  @document-ready="handleDocumentReady"
                />
                
                <!-- Module Configuration -->
                <div v-if="moduleConfig?.schema" class="mt-4">
                  <v-divider class="mb-4" />
                  <h4 class="text-h6 mb-2">Module Configuration</h4>
                  <UniversalConfigCard
                    :schema="moduleConfig.schema"
                    :initial-data="{}"
                    @data-change="handleConfigChange"
                  />
                </div>
              </div>
            </v-expand-transition>
          </v-card-text>
          
          <v-card-actions>
            <v-spacer />
            <v-btn
              color="primary"
              variant="elevated"
              :disabled="!selectedModuleId"
              @click="createAndSaveSeed"
            >
              <v-icon start>mdi-content-save</v-icon>
              Create & Save
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
      
      <!-- Right side - Saved Documents Browser -->
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title>
            <v-icon start>mdi-database-search</v-icon>
            Saved Seed Data
            <v-spacer />
            <v-btn
              icon
              variant="text"
              @click="refreshDocuments"
            >
              <v-icon>mdi-refresh</v-icon>
            </v-btn>
          </v-card-title>
          
          <v-card-text>
            <!-- Search and Filter -->
            <v-text-field
              v-model="search"
              label="Search documents"
              prepend-inner-icon="mdi-magnify"
              clearable
              hide-details
              class="mb-2"
            />
            
            <v-chip-group
              v-model="selectedTags"
              column
              multiple
              filter
              class="mb-4"
            >
              <v-chip
                v-for="tag in allTags"
                :key="tag"
                :value="tag"
                size="small"
              >
                {{ tag }}
              </v-chip>
            </v-chip-group>
            
            <!-- Documents List -->
            <v-list
              v-if="filteredDocuments.length > 0"
              lines="two"
              class="document-list"
            >
              <template v-for="(doc, index) in filteredDocuments" :key="doc.storage_id">
                <v-list-item
                  :title="doc.description || doc.document.title"
                  :subtitle="`${doc.document.source_uri} • ${formatDate(doc.created_at)}`"
                  @click="selectDocument(doc)"
                >
                  <template v-slot:prepend>
                    <v-icon>mdi-file-document</v-icon>
                  </template>
                  
                  <template v-slot:append>
                    <v-menu>
                      <template v-slot:activator="{ props }">
                        <v-btn
                          icon
                          variant="text"
                          size="small"
                          v-bind="props"
                        >
                          <v-icon>mdi-dots-vertical</v-icon>
                        </v-btn>
                      </template>
                      
                      <v-list density="compact">
                        <v-list-item @click="useInPipeline(doc)">
                          <v-list-item-title>
                            <v-icon start size="small">mdi-play</v-icon>
                            Use in Pipeline
                          </v-list-item-title>
                        </v-list-item>
                        <v-list-item @click="viewDocument(doc)">
                          <v-list-item-title>
                            <v-icon start size="small">mdi-eye</v-icon>
                            View Details
                          </v-list-item-title>
                        </v-list-item>
                        <v-list-item @click="editDocument(doc)">
                          <v-list-item-title>
                            <v-icon start size="small">mdi-pencil</v-icon>
                            Edit Metadata
                          </v-list-item-title>
                        </v-list-item>
                        <v-list-item @click="deleteDocument(doc)" class="text-red">
                          <v-list-item-title>
                            <v-icon start size="small">mdi-delete</v-icon>
                            Delete
                          </v-list-item-title>
                        </v-list-item>
                      </v-list>
                    </v-menu>
                  </template>
                  
                  <div v-if="doc.tags && Object.keys(doc.tags).length > 0" class="mt-1">
                    <v-chip
                      v-for="(value, key) in doc.tags"
                      :key="key"
                      size="x-small"
                      class="mr-1"
                    >
                      {{ key }}: {{ value }}
                    </v-chip>
                  </div>
                </v-list-item>
                
                <v-divider v-if="index < filteredDocuments.length - 1" />
              </template>
            </v-list>
            
            <v-alert
              v-else
              type="info"
              variant="tonal"
            >
              No saved seed data found. Create your first one!
            </v-alert>
            
            <!-- Pagination -->
            <v-pagination
              v-if="totalPages > 1"
              v-model="currentPage"
              :length="totalPages"
              :total-visible="5"
              class="mt-4"
            />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
    
    <!-- Document View Dialog -->
    <v-dialog v-model="viewDialog" max-width="800">
      <v-card v-if="selectedDocument">
        <v-card-title>
          Document Details
          <v-spacer />
          <v-btn icon @click="viewDialog = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-card-title>
        
        <v-card-text>
          <pre>{{ formatDocumentForDisplay(selectedDocument) }}</pre>
        </v-card-text>
      </v-card>
    </v-dialog>
    
    <!-- Edit Dialog -->
    <v-dialog v-model="editDialog" max-width="600">
      <v-card v-if="editingDocument">
        <v-card-title>
          Edit Document Metadata
        </v-card-title>
        
        <v-card-text>
          <v-text-field
            v-model="editingDocument.description"
            label="Description"
            class="mb-4"
          />
          
          <v-combobox
            v-model="editingTags"
            label="Tags"
            multiple
            chips
            closable-chips
            clearable
          />
        </v-card-text>
        
        <v-card-actions>
          <v-spacer />
          <v-btn @click="editDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="saveDocumentEdits">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useModuleStore } from '@/stores/moduleStore'
import { useNotification } from '@/composables/useNotification'
import UniversalConfigCard from '@/components/UniversalConfigCard.vue'
import ParserDocEditor from '@/components/ParserDocEditor.vue'
import PipeDocEditor from '@/components/PipeDocEditor.vue'

const moduleStore = useModuleStore()
const { showSuccess, showError } = useNotification()

// Document editor reference
const docEditor = ref<any>(null)
const documentReady = ref(false)
const pipeDocData = ref<any>(null)

// Module state
const selectedModuleId = ref('')
const moduleConfig = ref<any>(null)
const configData = ref({})

// Document browser state
const documents = ref<any[]>([])
const search = ref('')
const selectedTags = ref<string[]>([])
const currentPage = ref(1)
const pageSize = 10
const totalCount = ref(0)

// Dialog state
const viewDialog = ref(false)
const editDialog = ref(false)
const selectedDocument = ref<any>(null)
const editingDocument = ref<any>(null)
const editingTags = ref<string[]>([])

// Computed
const availableModules = computed(() => 
  moduleStore.connectedModules.map(module => ({
    id: module.address,
    displayName: `${module.name} (${module.address})`
  }))
)

const isParserModule = computed(() => {
  const module = moduleStore.connectedModules.find(m => m.address === selectedModuleId.value)
  return module?.capabilities?.includes('PARSER') || false
})

const allTags = computed(() => {
  const tags = new Set<string>()
  documents.value.forEach(doc => {
    if (doc.tags) {
      Object.keys(doc.tags).forEach(tag => tags.add(tag))
    }
  })
  return Array.from(tags)
})

const filteredDocuments = computed(() => {
  let filtered = documents.value
  
  if (search.value) {
    const searchLower = search.value.toLowerCase()
    filtered = filtered.filter(doc => 
      doc.description?.toLowerCase().includes(searchLower) ||
      doc.document.title?.toLowerCase().includes(searchLower) ||
      doc.document.source_uri?.toLowerCase().includes(searchLower)
    )
  }
  
  if (selectedTags.value.length > 0) {
    filtered = filtered.filter(doc => {
      if (!doc.tags) return false
      return selectedTags.value.some(tag => tag in doc.tags)
    })
  }
  
  return filtered
})

const totalPages = computed(() => Math.ceil(totalCount.value / pageSize))

// Methods
const handleDocumentReady = (data: any) => {
  pipeDocData.value = data
  documentReady.value = true
}

const loadModuleConfig = async (moduleId: string) => {
  const module = moduleStore.connectedModules.find(m => m.address === moduleId)
  if (module) {
    moduleConfig.value = module
  }
}

const handleConfigChange = (data: any) => {
  configData.value = data
}

const createAndSaveSeed = async () => {
  if (!selectedModuleId.value || !docEditor.value) return
  
  try {
    // Get the PipeDoc data from the editor
    const docData = docEditor.value.pipeDoc || pipeDocData.value
    
    if (!docData) {
      showError('Please complete the document form')
      return
    }
    
    // Get module info
    const module = moduleStore.connectedModules.find(m => m.address === selectedModuleId.value)
    const moduleCapabilities = module?.capabilities || []
    
    // Save to repository with module metadata
    const tags: Record<string, string> = {
      module: selectedModuleId.value,
      moduleName: module?.name || 'unknown',
      type: 'seed-data'
    }
    
    // Add capability tags
    moduleCapabilities.forEach(cap => {
      tags[`capability-${cap.toLowerCase()}`] = 'true'
    })
    
    // Add document tags if any
    if (docData.tags?.length > 0) {
      docData.tags.forEach((tag: string) => {
        tags[tag] = 'true'
      })
    }
    
    const saveResponse = await fetch('http://localhost:3000/api/repository/save-seed', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        document: docData,
        config: configData.value,
        metadata: {
          name: docData.title || 'Untitled',
          description: docData.description || '',
          moduleId: selectedModuleId.value,
          tags
        }
      })
    })
    
    if (!saveResponse.ok) throw new Error('Failed to save to repository')
    
    showSuccess('Seed data created and saved!')
    resetForm()
    await refreshDocuments()
  } catch (error) {
    showError(`Error: ${error.message}`)
  }
}

const refreshDocuments = async () => {
  try {
    const response = await fetch(`http://localhost:3000/api/repository/list?pageSize=${pageSize}&pageToken=${currentPage.value - 1}`)
    if (!response.ok) throw new Error('Failed to load documents')
    
    const data = await response.json()
    documents.value = data.documents || []
    totalCount.value = data.totalCount || 0
  } catch (error) {
    showError(`Error loading documents: ${error.message}`)
  }
}

const selectDocument = (doc: any) => {
  selectedDocument.value = doc
}

const useInPipeline = (doc: any) => {
  // TODO: Navigate to Process Document tab with this document
  console.log('Use in pipeline:', doc)
}

const viewDocument = (doc: any) => {
  selectedDocument.value = doc
  viewDialog.value = true
}

const editDocument = (doc: any) => {
  editingDocument.value = { ...doc }
  editingTags.value = doc.tags ? Object.keys(doc.tags) : []
  editDialog.value = true
}

const saveDocumentEdits = async () => {
  if (!editingDocument.value) return
  
  try {
    const tags: Record<string, string> = {}
    editingTags.value.forEach(tag => {
      tags[tag] = 'true'
    })
    
    const response = await fetch(`http://localhost:3000/api/repository/${editingDocument.value.storage_id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        description: editingDocument.value.description,
        tags
      })
    })
    
    if (!response.ok) throw new Error('Failed to update document')
    
    showSuccess('Document updated!')
    editDialog.value = false
    await refreshDocuments()
  } catch (error) {
    showError(`Error updating document: ${error.message}`)
  }
}

const deleteDocument = async (doc: any) => {
  if (!confirm('Are you sure you want to delete this document?')) return
  
  try {
    const response = await fetch(`http://localhost:3000/api/repository/${doc.storage_id}`, {
      method: 'DELETE'
    })
    
    if (!response.ok) throw new Error('Failed to delete document')
    
    showSuccess('Document deleted!')
    await refreshDocuments()
  } catch (error) {
    showError(`Error deleting document: ${error.message}`)
  }
}

const resetForm = () => {
  documentReady.value = false
  pipeDocData.value = null
  configData.value = {}
  if (docEditor.value?.resetForm) {
    docEditor.value.resetForm()
  }
}

const formatDate = (timestamp: any) => {
  if (!timestamp || !timestamp.seconds) return 'Unknown'
  return new Date(parseInt(timestamp.seconds) * 1000).toLocaleString()
}

const formatDocumentForDisplay = (doc: any) => {
  // Create a copy of the document to avoid modifying the original
  const displayDoc = JSON.parse(JSON.stringify(doc))
  
  // Mask blob data if it exists
  if (displayDoc.document?.blob?.data) {
    const blobSize = displayDoc.document.blob.size || 'unknown'
    displayDoc.document.blob.data = `<blob data masked - ${blobSize} bytes>`
  }
  
  // Also mask any other large base64 data fields that might exist
  const maskLargeStrings = (obj: any) => {
    for (const key in obj) {
      if (typeof obj[key] === 'string' && obj[key].length > 1000) {
        // Check if it looks like base64
        if (/^[A-Za-z0-9+/=]+$/.test(obj[key].substring(0, 100))) {
          obj[key] = `<large data masked - ${obj[key].length} chars>`
        }
      } else if (typeof obj[key] === 'object' && obj[key] !== null) {
        maskLargeStrings(obj[key])
      }
    }
  }
  
  maskLargeStrings(displayDoc)
  
  return JSON.stringify(displayDoc, null, 2)
}

// Watch for page changes
watch(currentPage, () => {
  refreshDocuments()
})

// Load documents on mount
onMounted(() => {
  refreshDocuments()
})
</script>

<style scoped>
.document-list {
  max-height: 400px;
  overflow-y: auto;
}
</style>
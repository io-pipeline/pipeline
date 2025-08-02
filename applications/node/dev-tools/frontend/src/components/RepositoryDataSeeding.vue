<template>
  <v-container fluid>
    <v-card>
      <v-card-title>
        <v-icon start>mdi-database-plus-outline</v-icon>
        Repository Data Seeding
      </v-card-title>
      <v-card-subtitle>
        Browse and manage documents in the connected repository
      </v-card-subtitle>
      
      <v-divider />
      
      <v-card-text>
        <!-- Toolbar -->
        <v-toolbar density="compact" class="mb-4">
          <v-btn
            color="primary"
            variant="flat"
            prepend-icon="mdi-folder-plus"
            @click="createFolder"
            :disabled="loading"
          >
            New Folder
          </v-btn>
          
          <v-btn
            color="primary"
            variant="tonal"
            prepend-icon="mdi-file-plus"
            @click="createDocument"
            :disabled="loading"
            class="ml-2"
          >
            New Document
          </v-btn>
          
          <v-divider vertical class="mx-4" />
          
          <v-btn
            icon="mdi-refresh"
            @click="refreshCurrentPath"
            :loading="loading"
          />
          
          <v-spacer />
          
          <!-- Breadcrumbs -->
          <v-breadcrumbs :items="breadcrumbs" density="compact" class="pa-0">
            <template v-slot:item="{ item, index }">
              <v-breadcrumbs-item
                @click="navigateToBreadcrumb(index)"
                style="cursor: pointer;"
              >
                {{ item.title }}
              </v-breadcrumbs-item>
            </template>
          </v-breadcrumbs>
        </v-toolbar>
        
        <!-- File Browser -->
        <v-data-table
          :headers="headers"
          :items="items"
          :loading="loading"
          :items-per-page="50"
          class="elevation-1"
        >
          <template v-slot:item.name="{ item }">
            <div class="d-flex align-center">
              <v-icon 
                :icon="item.type === Node_NodeType.FOLDER ? 'mdi-folder' : getFileIcon(item.mimeType)"
                :color="item.type === Node_NodeType.FOLDER ? 'primary' : 'grey'"
                class="mr-2"
              />
              <a 
                v-if="item.type === Node_NodeType.FOLDER"
                @click.prevent="navigateToNode(item)"
                class="text-decoration-none"
                style="cursor: pointer;"
              >
                {{ item.name }}
              </a>
              <span v-else>{{ item.name }}</span>
            </div>
          </template>
          
          <template v-slot:item.size="{ item }">
            {{ item.type === Node_NodeType.FOLDER ? '-' : formatFileSize(item.size || 0) }}
          </template>
          
          <template v-slot:item.modified="{ item }">
            {{ formatDate(item.updatedAt) }}
          </template>
          
          <template v-slot:item.actions="{ item }">
            <v-btn
              v-if="item.type === Node_NodeType.FILE && isPipeDoc(item.mimeType)"
              icon="mdi-play"
              size="small"
              color="primary"
              @click="processDocument(item)"
              title="Process this document"
            />
            <v-btn
              icon="mdi-download"
              size="small"
              @click="downloadItem(item)"
              :disabled="item.type === Node_NodeType.FOLDER"
              title="Download"
            />
            <v-btn
              icon="mdi-delete"
              size="small"
              color="error"
              @click="deleteItem(item)"
              title="Delete"
            />
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
    
    <!-- Create Folder Dialog -->
    <v-dialog v-model="folderDialog" max-width="500">
      <v-card>
        <v-card-title>Create New Folder</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="newFolderName"
            label="Folder Name"
            @keyup.enter="confirmCreateFolder"
            autofocus
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="folderDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="confirmCreateFolder">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    
    <!-- Create Document Dialog -->
    <v-dialog v-model="documentDialog" max-width="600">
      <v-card>
        <v-card-title>Create New Document</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="newDocument.id"
            label="Document ID"
            hint="Unique identifier for the document"
            persistent-hint
            class="mb-3"
          />
          <v-text-field
            v-model="newDocument.title"
            label="Title"
            class="mb-3"
          />
          <v-textarea
            v-model="newDocument.body"
            label="Body"
            rows="5"
            class="mb-3"
          />
          <v-text-field
            v-model="newDocument.sourceUri"
            label="Source URI"
            hint="Original source of the document"
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="documentDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="confirmCreateDocument">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRepositoryStore } from '../stores/repositoryStore'
import { useModuleStore } from '../stores/moduleStore'
import { useNotification } from '../composables/useNotification'
import { filesystemService } from '../services/connectService'
import { 
  Node_NodeType,
  type GetChildrenRequest,
  type CreateNodeRequest,
  type DeleteNodeRequest,
  type GetNodeRequest,
  type Node
} from '../gen/filesystem_service_pb'

const repositoryStore = useRepositoryStore()
const moduleStore = useModuleStore()
const { showSuccess, showError } = useNotification()

// State
const currentNodeId = ref('') // Empty string for root
const currentPath = ref<string[]>(['Root'])
const items = ref<Node[]>([])
const loading = ref(false)
const folderDialog = ref(false)
const documentDialog = ref(false)
const newFolderName = ref('')
const newDocument = ref({
  id: '',
  title: '',
  body: '',
  sourceUri: ''
})

// Table headers
const headers = [
  { title: 'Name', key: 'name', width: '50%' },
  { title: 'Size', key: 'size' },
  { title: 'Modified', key: 'modified' },
  { title: 'Actions', key: 'actions', sortable: false }
]

// Computed breadcrumbs
const breadcrumbs = computed(() => {
  return currentPath.value.map((name, index) => ({
    title: name,
    nodeId: index === 0 ? '' : items.value[index - 1]?.id || '',
    disabled: false
  }))
})

// Load directory contents
const loadChildren = async (parentId: string = '') => {
  loading.value = true
  try {
    const request: GetChildrenRequest = { 
      drive: 'default',
      parentId,
      pageSize: 100,
      orderBy: 'name',
      ascending: true
    }
    const response = await filesystemService.getChildren(request)
    items.value = response.nodes
  } catch (error) {
    showError(`Failed to load directory: ${error}`)
  } finally {
    loading.value = false
  }
}

// Navigate to node
const navigateToNode = async (node: Node | null) => {
  if (!node) {
    // Navigate to root
    currentNodeId.value = ''
    currentPath.value = ['Root']
  } else {
    currentNodeId.value = node.id
    // Build path from ancestors
    try {
      const pathResponse = await filesystemService.getPath({ drive: 'default', id: node.id })
      currentPath.value = ['Root', ...pathResponse.ancestors.map(n => n.name)]
    } catch (error) {
      currentPath.value.push(node.name)
    }
  }
  await loadChildren(currentNodeId.value)
}

// Navigate by breadcrumb
const navigateToBreadcrumb = (index: number) => {
  if (index === 0) {
    navigateToNode(null)
  } else {
    // Find the node in current items
    const nodeId = breadcrumbs.value[index].nodeId
    const node = items.value.find(n => n.id === nodeId)
    if (node) navigateToNode(node)
  }
}

// Refresh current path
const refreshCurrentPath = () => {
  loadChildren(currentNodeId.value)
}

// Create folder
const createFolder = () => {
  newFolderName.value = ''
  folderDialog.value = true
}

const confirmCreateFolder = async () => {
  if (!newFolderName.value.trim()) return
  
  try {
    const request: CreateNodeRequest = {
      drive: 'default',
      parentId: currentNodeId.value,
      name: newFolderName.value,
      type: Node_NodeType.FOLDER,
      metadata: {}
    }
    await filesystemService.createNode(request)
    
    showSuccess('Folder created successfully')
    folderDialog.value = false
    refreshCurrentPath()
  } catch (error) {
    showError(`Failed to create folder: ${error}`)
  }
}

// Create document
const createDocument = () => {
  newDocument.value = {
    id: `doc-${Date.now()}`,
    title: '',
    body: '',
    sourceUri: 'repository://new-document'
  }
  documentDialog.value = true
}

const confirmCreateDocument = async () => {
  // TODO: Implement document creation via repository service
  showError('Document creation not yet implemented')
  documentDialog.value = false
}

// Delete item
const deleteItem = async (item: Node) => {
  const isFolder = item.type === Node_NodeType.FOLDER
  if (!confirm(`Delete ${isFolder ? 'folder' : 'file'} "${item.name}"?`)) return
  
  try {
    const request: DeleteNodeRequest = { 
      drive: 'default',
      id: item.id,
      recursive: isFolder
    }
    await filesystemService.deleteNode(request)
    
    showSuccess(`${isFolder ? 'Folder' : 'File'} deleted successfully`)
    refreshCurrentPath()
  } catch (error) {
    showError(`Failed to delete: ${error}`)
  }
}

// Download item
const downloadItem = async (item: Node) => {
  try {
    const request: GetNodeRequest = { drive: 'default', id: item.id }
    const response = await filesystemService.getNode(request)
    
    if (response.payload) {
      // TODO: Extract actual file content from payload
      // For now, download the JSON representation
      const content = JSON.stringify(response.payload, null, 2)
      const blob = new Blob([content], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = item.name + '.json'
      a.click()
      URL.revokeObjectURL(url)
    }
  } catch (error) {
    showError(`Failed to download: ${error}`)
  }
}

// Process document
const processDocument = async (item: Node) => {
  if (!moduleStore.activeModule) {
    showError('Please select a module first')
    return
  }
  
  // TODO: Load document payload and send to process tab
  showError('Document processing not yet implemented')
}

// Utility functions
const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp: any) => {
  if (!timestamp) return '-'
  // Handle protobuf Timestamp
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp)
  return date.toLocaleString()
}

const getFileIcon = (mimeType: string) => {
  if (!mimeType) return 'mdi-file'
  if (mimeType.startsWith('image/')) return 'mdi-file-image'
  if (mimeType.startsWith('video/')) return 'mdi-file-video'
  if (mimeType.includes('pdf')) return 'mdi-file-pdf-box'
  if (mimeType.includes('json')) return 'mdi-code-json'
  if (mimeType.includes('xml')) return 'mdi-xml'
  if (mimeType.includes('protobuf') || mimeType.includes('pipedoc')) return 'mdi-file-document'
  return 'mdi-file'
}

const isPipeDoc = (mimeType: string) => {
  return mimeType && (mimeType.includes('protobuf') || mimeType.includes('pipedoc'))
}

// Initialize
onMounted(() => {
  if (repositoryStore.isConnected) {
    loadChildren('') // Empty string for root
  }
})
</script>

<style scoped>
.v-breadcrumbs {
  flex-wrap: nowrap;
}
</style>
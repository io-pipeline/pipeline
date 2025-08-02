<template>
  <v-container fluid class="pa-0">
    <!-- Drive selector -->
    <v-toolbar density="compact" flat>
      <v-select
        v-model="selectedDrive"
        :items="drives"
        item-title="name"
        item-value="name"
        label="Drive"
        density="compact"
        variant="outlined"
        hide-details
        style="max-width: 200px"
        @update:model-value="onDriveChange"
      >
        <template v-slot:prepend-inner>
          <v-icon size="small">mdi-harddisk</v-icon>
        </template>
      </v-select>
      
      <v-btn
        icon="mdi-plus"
        size="small"
        @click="showCreateDriveDialog = true"
        title="Create new drive"
      />
      
      <v-divider vertical class="mx-2" />
      
      <v-btn
        icon="mdi-arrow-up"
        size="small"
        @click="navigateUp"
        :disabled="!currentNodeId || loading"
      />
      
      <v-btn
        icon="mdi-refresh"
        size="small"
        @click="refresh"
        :loading="loading"
      />
      
      <v-btn
        icon="mdi-folder-plus"
        size="small"
        @click="showCreateFolderDialog = true"
        :disabled="loading"
        title="Create folder"
      />
      
      <v-divider vertical class="mx-2" />
      
      <!-- Type filter -->
      <v-select
        v-model="typeFilter"
        :items="protobufTypes"
        label="Filter by type"
        density="compact"
        variant="outlined"
        hide-details
        clearable
        style="max-width: 250px"
        @update:model-value="refresh"
      />
      
      <v-spacer />
      
      <!-- Search -->
      <v-text-field
        v-model="searchQuery"
        density="compact"
        variant="outlined"
        placeholder="Search..."
        hide-details
        clearable
        style="max-width: 250px"
        @update:model-value="onSearchChange"
      >
        <template v-slot:prepend-inner>
          <v-icon size="small">mdi-magnify</v-icon>
        </template>
      </v-text-field>
    </v-toolbar>
    
    <v-divider />
    
    <!-- Breadcrumbs -->
    <v-breadcrumbs 
      :items="breadcrumbs" 
      density="compact" 
      class="pa-2"
    >
      <template v-slot:item="{ item, index }">
        <v-breadcrumbs-item
          @click="navigateToBreadcrumb(index)"
          style="cursor: pointer;"
        >
          <v-icon v-if="index === 0" size="small" class="mr-1">mdi-harddisk</v-icon>
          {{ item.title }}
        </v-breadcrumbs-item>
      </template>
    </v-breadcrumbs>
    
    <v-divider />
    
    <!-- File list -->
    <v-list 
      density="compact"
      :loading="loading"
      lines="two"
    >
      <v-list-item
        v-for="item in filteredItems"
        :key="item.id"
        @click="handleItemClick(item)"
        :class="{ 'v-list-item--active': selectedItem?.id === item.id }"
      >
        <template v-slot:prepend>
          <v-icon 
            :icon="getIcon(item)"
            :color="getIconColor(item)"
          />
        </template>
        
        <v-list-item-title>{{ item.name }}</v-list-item-title>
        
        <v-list-item-subtitle>
          <span v-if="item.type === Node_NodeType.FILE">
            <v-chip size="x-small" class="mr-1" v-if="item.payloadType">
              {{ getShortTypeName(item.payloadType) }}
            </v-chip>
            {{ formatFileSize(item.size || 0) }}
            <span v-if="item.updatedAt">
              • {{ formatDate(item.updatedAt) }}
            </span>
          </span>
          <span v-else>
            {{ getChildCount(item) }}
          </span>
        </v-list-item-subtitle>
        
        <template v-slot:append>
          <v-menu v-if="item.type === Node_NodeType.FILE">
            <template v-slot:activator="{ props }">
              <v-btn
                icon="mdi-dots-vertical"
                size="small"
                variant="text"
                v-bind="props"
                @click.stop
              />
            </template>
            <v-list density="compact">
              <v-list-item @click="viewProtobuf(item)">
                <template v-slot:prepend>
                  <v-icon size="small">mdi-eye</v-icon>
                </template>
                <v-list-item-title>View</v-list-item-title>
              </v-list-item>
              <v-list-item @click="downloadProtobuf(item)">
                <template v-slot:prepend>
                  <v-icon size="small">mdi-download</v-icon>
                </template>
                <v-list-item-title>Download</v-list-item-title>
              </v-list-item>
              <v-list-item @click="deleteItem(item)">
                <template v-slot:prepend>
                  <v-icon size="small" color="error">mdi-delete</v-icon>
                </template>
                <v-list-item-title>Delete</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </template>
      </v-list-item>
      
      <v-list-item v-if="!loading && filteredItems.length === 0">
        <v-list-item-title class="text-center text-grey">
          {{ searchQuery ? 'No matching files found' : 'Empty folder' }}
        </v-list-item-title>
      </v-list-item>
    </v-list>
    
    <!-- Create Drive Dialog -->
    <v-dialog v-model="showCreateDriveDialog" max-width="500">
      <v-card>
        <v-card-title>Create New Drive</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="newDriveName"
            label="Drive Name"
            placeholder="e.g., test-data, pipeline-configs"
            :rules="[v => !!v || 'Drive name is required', v => !v.includes(':') || 'Drive name cannot contain colons']"
          />
          <v-textarea
            v-model="newDriveDescription"
            label="Description (optional)"
            placeholder="Describe the purpose of this drive"
            rows="2"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showCreateDriveDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="createDrive" :disabled="!newDriveName">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    
    <!-- Create Folder Dialog -->
    <v-dialog v-model="showCreateFolderDialog" max-width="500">
      <v-card>
        <v-card-title>Create New Folder</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="newFolderName"
            label="Folder Name"
            :rules="[v => !!v || 'Folder name is required']"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showCreateFolderDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="createFolder" :disabled="!newFolderName">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    
    <!-- Protobuf Viewer Dialog -->
    <v-dialog v-model="showViewer" max-width="900">
      <v-card v-if="viewerContent">
        <v-card-title>
          {{ viewerContent.name }}
          <v-spacer />
          <v-btn
            icon="mdi-close"
            variant="text"
            @click="showViewer = false"
          />
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-chip size="small" class="mb-2">
            {{ viewerContent.payloadType }}
          </v-chip>
          <pre class="overflow-auto" style="max-height: 500px">{{ viewerContent.content }}</pre>
        </v-card-text>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { filesystemService } from '../services/connectService'
import { 
  Node_NodeType,
  type GetChildrenRequest,
  type GetPathRequest,
  type Node,
  type CreateDriveRequest,
  type ListDrivesRequest,
  type Drive,
  type CreateNodeRequest,
  type DeleteNodeRequest,
  type GetNodeRequest,
  type SearchNodesRequest
} from '../gen/filesystem_service_pb'
import { useSnackbar } from '../composables/useSnackbar'
import { typeRegistry } from '../services/connectService'

// Props
const props = defineProps<{
  initialDrive?: string
  initialPath?: string
  allowDriveSelection?: boolean
  fileFilter?: (node: Node) => boolean
}>()

// Emits
const emit = defineEmits<{
  'file-selected': [node: Node]
  'folder-changed': [nodeId: string]
  'drive-changed': [drive: string]
}>()

// Composables
const { showSuccess, showError } = useSnackbar()

// State
const selectedDrive = ref(props.initialDrive || 'default')
const drives = ref<Drive[]>([])
const currentNodeId = ref('')
const currentPath = ref<Node[]>([])
const items = ref<Node[]>([])
const loading = ref(false)
const searchQuery = ref('')
const typeFilter = ref<string>('')
const selectedItem = ref<Node | null>(null)

// Dialog state
const showCreateDriveDialog = ref(false)
const newDriveName = ref('')
const newDriveDescription = ref('')
const showCreateFolderDialog = ref(false)
const newFolderName = ref('')
const showViewer = ref(false)
const viewerContent = ref<any>(null)

// Common protobuf types in the system
const protobufTypes = [
  'PipeDoc',
  'ModuleProcessRequest',
  'ModuleProcessResponse',
  'ServiceRegistrationResponse',
  'PipeStream',
  'StepExecutionRecord',
  'SemanticProcessingResult'
]

// Computed
const breadcrumbs = computed(() => {
  const crumbs = [{ title: selectedDrive.value, nodeId: '' }]
  
  currentPath.value.forEach(node => {
    crumbs.push({
      title: node.name,
      nodeId: node.id
    })
  })
  
  return crumbs
})

const filteredItems = computed(() => {
  let filtered = items.value

  // Apply type filter
  if (typeFilter.value) {
    filtered = filtered.filter(item => 
      item.type === Node_NodeType.FOLDER || 
      item.payloadType === typeFilter.value
    )
  }

  // Apply custom file filter if provided
  if (props.fileFilter) {
    filtered = filtered.filter(item => 
      item.type === Node_NodeType.FOLDER || props.fileFilter!(item)
    )
  }

  return filtered
})

// Methods
const loadDrives = async () => {
  if (!props.allowDriveSelection) return
  
  try {
    const request: ListDrivesRequest = { pageSize: 100 }
    const response = await filesystemService.listDrives(request)
    drives.value = response.drives
  } catch (error) {
    console.error('Failed to load drives:', error)
  }
}

const loadChildren = async (parentId: string = '') => {
  loading.value = true
  try {
    if (searchQuery.value) {
      // Use search instead of getChildren
      const searchRequest: SearchNodesRequest = {
        drive: selectedDrive.value,
        query: searchQuery.value,
        pageSize: 1000
      }
      const searchResponse = await filesystemService.searchNodes(searchRequest)
      items.value = searchResponse.nodes
    } else {
      // Normal children loading
      const request: GetChildrenRequest = { 
        drive: selectedDrive.value,
        parentId,
        pageSize: 1000,
        orderBy: 'name',
        ascending: true
      }
      const response = await filesystemService.getChildren(request)
      items.value = response.nodes
    }
    
    // Update path if navigating to a new folder
    if (parentId !== currentNodeId.value && !searchQuery.value) {
      currentNodeId.value = parentId
      if (parentId) {
        const pathRequest: GetPathRequest = { 
          drive: selectedDrive.value,
          id: parentId 
        }
        const pathResponse = await filesystemService.getPath(pathRequest)
        currentPath.value = pathResponse.ancestors
      } else {
        currentPath.value = []
      }
    }
    
    emit('folder-changed', parentId)
  } catch (error) {
    console.error('Failed to load directory:', error)
    showError('Failed to load directory')
    items.value = []
  } finally {
    loading.value = false
  }
}

const handleItemClick = (item: Node) => {
  if (item.type === Node_NodeType.FOLDER) {
    searchQuery.value = '' // Clear search when navigating
    loadChildren(item.id)
  } else {
    selectedItem.value = item
    emit('file-selected', item)
  }
}

const navigateUp = () => {
  if (currentPath.value.length > 0) {
    const parent = currentPath.value[currentPath.value.length - 1]
    const grandparentId = parent.parentId || ''
    loadChildren(grandparentId)
  }
}

const navigateToBreadcrumb = (index: number) => {
  const nodeId = breadcrumbs.value[index].nodeId
  searchQuery.value = '' // Clear search when navigating
  loadChildren(nodeId)
}

const refresh = () => {
  loadChildren(currentNodeId.value)
}

const onDriveChange = () => {
  currentNodeId.value = ''
  currentPath.value = []
  searchQuery.value = ''
  emit('drive-changed', selectedDrive.value)
  loadChildren()
}

const onSearchChange = () => {
  // Debounce search
  setTimeout(() => {
    if (searchQuery.value || !searchQuery.value) {
      loadChildren(currentNodeId.value)
    }
  }, 300)
}

const createDrive = async () => {
  if (!newDriveName.value) return
  
  try {
    const request: CreateDriveRequest = {
      name: newDriveName.value,
      description: newDriveDescription.value
    }
    const drive = await filesystemService.createDrive(request)
    
    showSuccess(`Drive '${drive.name}' created successfully`)
    
    // Refresh drives and select the new one
    await loadDrives()
    selectedDrive.value = drive.name
    onDriveChange()
    
    // Reset dialog
    showCreateDriveDialog.value = false
    newDriveName.value = ''
    newDriveDescription.value = ''
  } catch (error) {
    showError(`Failed to create drive: ${error}`)
  }
}

const createFolder = async () => {
  if (!newFolderName.value) return
  
  try {
    const request: CreateNodeRequest = {
      drive: selectedDrive.value,
      parentId: currentNodeId.value,
      name: newFolderName.value,
      type: Node_NodeType.FOLDER,
      metadata: {}
    }
    await filesystemService.createNode(request)
    
    showSuccess('Folder created successfully')
    refresh()
    
    // Reset dialog
    showCreateFolderDialog.value = false
    newFolderName.value = ''
  } catch (error) {
    showError(`Failed to create folder: ${error}`)
  }
}

const viewProtobuf = async (item: Node) => {
  try {
    const request: GetNodeRequest = { 
      drive: selectedDrive.value,
      id: item.id 
    }
    const node = await filesystemService.getNode(request)
    
    if (node.payload) {
      const typeUrl = node.payload.typeUrl
      const typeName = typeUrl.split('/').pop() || ''
      
      try {
        // Import Any type to work with the payload
        const { Any } = await import('@bufbuild/protobuf/wkt')
        
        // The payload should be an Any message
        // Check if it's already an Any instance or needs to be created
        let anyValue = node.payload
        
        // If it's a plain object, we need to create an Any instance
        if (!(anyValue instanceof Any)) {
          anyValue = new Any({
            typeUrl: node.payload.typeUrl,
            value: node.payload.value
          })
        }
        
        // Try to unpack the message if we have the type in our registry
        // For now, just display the raw JSON representation
        viewerContent.value = {
          name: item.name,
          payloadType: typeName,
          content: JSON.stringify({
            '@type': typeUrl,
            _notice: 'Raw protobuf data - full deserialization not yet implemented',
            size: node.payload.value.length + ' bytes',
            // Show first 100 bytes as hex for debugging
            preview: Array.from(node.payload.value.slice(0, 100))
              .map(b => b.toString(16).padStart(2, '0'))
              .join(' ')
          }, null, 2)
        }
      } catch (e) {
        // Fallback to raw display
        viewerContent.value = {
          name: item.name,
          payloadType: typeName,
          content: `Type: ${typeUrl}\nSize: ${node.payload.value.length} bytes\n\nError: ${e}\n\nNote: Unable to deserialize this protobuf type.`
        }
      }
      
      showViewer.value = true
    }
  } catch (error) {
    showError(`Failed to view file: ${error}`)
  }
}

const downloadProtobuf = async (item: Node) => {
  try {
    const request: GetNodeRequest = { 
      drive: selectedDrive.value,
      id: item.id 
    }
    const node = await filesystemService.getNode(request)
    
    if (node.payload) {
      // Create download
      const blob = new Blob([node.payload.value], { type: 'application/octet-stream' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = item.name
      a.click()
      URL.revokeObjectURL(url)
      
      showSuccess('File downloaded')
    }
  } catch (error) {
    showError(`Failed to download file: ${error}`)
  }
}

const deleteItem = async (item: Node) => {
  if (!confirm(`Delete "${item.name}"?`)) return
  
  try {
    const request: DeleteNodeRequest = {
      drive: selectedDrive.value,
      id: item.id,
      recursive: item.type === Node_NodeType.FOLDER
    }
    await filesystemService.deleteNode(request)
    
    showSuccess('Item deleted successfully')
    refresh()
  } catch (error) {
    showError(`Failed to delete item: ${error}`)
  }
}

const getIcon = (item: Node) => {
  if (item.type === Node_NodeType.FOLDER) {
    return 'mdi-folder'
  }
  
  // File icons based on payload type
  const payloadType = item.payloadType || ''
  if (payloadType.includes('PipeDoc')) {
    return 'mdi-file-document'
  }
  if (payloadType.includes('Request')) {
    return 'mdi-send'
  }
  if (payloadType.includes('Response')) {
    return 'mdi-reply'
  }
  if (payloadType.includes('Stream')) {
    return 'mdi-water'
  }
  
  return 'mdi-file'
}

const getIconColor = (item: Node) => {
  if (item.type === Node_NodeType.FOLDER) {
    return 'primary'
  }
  
  if (props.fileFilter && !props.fileFilter(item)) {
    return 'grey'
  }
  
  return undefined
}

const getShortTypeName = (fullType: string) => {
  return fullType.split('.').pop() || fullType
}

const formatFileSize = (bytes: number | bigint) => {
  if (!bytes) return '0 B'
  // Convert BigInt to number for calculations
  const bytesNum = typeof bytes === 'bigint' ? Number(bytes) : bytes
  if (bytesNum === 0) return '0 B'
  
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytesNum) / Math.log(k))
  return Math.round(bytesNum / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp: any) => {
  if (!timestamp) return ''
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp)
  return date.toLocaleDateString()
}

const getChildCount = (folder: Node) => {
  // This would need to be implemented with metadata or a separate count query
  return 'Folder'
}

// Initialize
onMounted(() => {
  if (props.allowDriveSelection) {
    loadDrives()
  }
  loadChildren(props.initialPath || '')
})

// Watch for external changes
watch(() => props.initialDrive, (newDrive) => {
  if (newDrive && newDrive !== selectedDrive.value) {
    selectedDrive.value = newDrive
    onDriveChange()
  }
})

// Expose methods
defineExpose({
  refresh,
  navigateToFolder: loadChildren,
  getCurrentDrive: () => selectedDrive.value,
  getSelectedItem: () => selectedItem.value
})
</script>

<style scoped>
pre {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.4;
  background-color: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
}

.v-list-item--active {
  background-color: rgba(var(--v-theme-primary), 0.08);
}
</style>
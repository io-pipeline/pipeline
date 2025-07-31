<template>
  <div>
    <!-- Toolbar -->
    <v-toolbar density="compact" flat>
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
      
      <v-divider vertical class="mx-2" />
      
      <!-- Breadcrumbs -->
      <v-breadcrumbs 
        :items="breadcrumbs" 
        density="compact" 
        class="pa-0"
      >
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
    
    <v-divider />
    
    <!-- File list -->
    <v-list 
      density="compact"
      :loading="loading"
    >
      <v-list-item
        v-for="item in filteredItems"
        :key="item.id"
        @click="handleItemClick(item)"
        :disabled="item.type === Node_NodeType.FILE && fileFilter && !fileFilter(item)"
      >
        <template v-slot:prepend>
          <v-icon 
            :icon="getIcon(item)"
            :color="getIconColor(item)"
          />
        </template>
        
        <v-list-item-title>{{ item.name }}</v-list-item-title>
        
        <v-list-item-subtitle v-if="item.type === Node_NodeType.FILE">
          {{ formatFileSize(item.size || 0) }}
          <span v-if="item.updatedAt">
            • {{ formatDate(item.updatedAt) }}
          </span>
        </v-list-item-subtitle>
      </v-list-item>
      
      <v-list-item v-if="!loading && filteredItems.length === 0">
        <v-list-item-title class="text-center text-grey">
          {{ fileFilter ? 'No matching files found' : 'Empty folder' }}
        </v-list-item-title>
      </v-list-item>
    </v-list>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { filesystemService } from '../services/connectService'
import { 
  Node_NodeType,
  type GetChildrenRequest,
  type GetPathRequest,
  type Node
} from '../gen/filesystem_service_pb'

// Props
const props = defineProps<{
  fileFilter?: (node: Node) => boolean
  initialPath?: string
}>()

// Emits
const emit = defineEmits<{
  'file-selected': [node: Node]
  'folder-changed': [nodeId: string]
}>()

// State
const currentNodeId = ref('')
const currentPath = ref<Node[]>([])
const items = ref<Node[]>([])
const loading = ref(false)

// Computed
const breadcrumbs = computed(() => {
  const crumbs = [{ title: 'Root', nodeId: '' }]
  
  currentPath.value.forEach(node => {
    crumbs.push({
      title: node.name,
      nodeId: node.id
    })
  })
  
  return crumbs
})

const filteredItems = computed(() => {
  if (!props.fileFilter) return items.value
  
  // Show all folders and only files that match the filter
  return items.value.filter(item => 
    item.type === Node_NodeType.FOLDER || props.fileFilter!(item)
  )
})

// Methods
const loadChildren = async (parentId: string = '') => {
  loading.value = true
  try {
    const request: GetChildrenRequest = { 
      parentId,
      pageSize: 1000,
      orderBy: 'name',
      ascending: true
    }
    const response = await filesystemService.getChildren(request)
    items.value = response.nodes
    
    // Update path if navigating to a new folder
    if (parentId !== currentNodeId.value) {
      currentNodeId.value = parentId
      if (parentId) {
        const pathRequest: GetPathRequest = { id: parentId }
        const pathResponse = await filesystemService.getPath(pathRequest)
        currentPath.value = pathResponse.ancestors
      } else {
        currentPath.value = []
      }
    }
    
    emit('folder-changed', parentId)
  } catch (error) {
    console.error('Failed to load directory:', error)
    items.value = []
  } finally {
    loading.value = false
  }
}

const handleItemClick = (item: Node) => {
  if (item.type === Node_NodeType.FOLDER) {
    loadChildren(item.id)
  } else if (!props.fileFilter || props.fileFilter(item)) {
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
  loadChildren(nodeId)
}

const refresh = () => {
  loadChildren(currentNodeId.value)
}

const getIcon = (item: Node) => {
  if (item.type === Node_NodeType.FOLDER) {
    return 'mdi-folder'
  }
  
  // File icons based on type
  if (item.payloadType === 'PipeDoc' || item.name.endsWith('.pipedoc')) {
    return 'mdi-file-document'
  }
  if (item.mimeType?.includes('json')) {
    return 'mdi-code-json'
  }
  if (item.mimeType?.includes('xml')) {
    return 'mdi-xml'
  }
  if (item.mimeType?.includes('text')) {
    return 'mdi-file-document-outline'
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

const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp: any) => {
  if (!timestamp) return ''
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp)
  return date.toLocaleDateString()
}

// Initialize
onMounted(() => {
  loadChildren(props.initialPath || '')
})

// Expose methods
defineExpose({
  refresh,
  navigateToFolder: loadChildren
})
</script>
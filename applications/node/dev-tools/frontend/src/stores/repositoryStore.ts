import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { createRepositoryHealthClient, filesystemService } from '../services/connectService'
import type { PromiseClient } from '@connectrpc/connect'
import type { Health } from '../gen/health/v1/health_pb'
import { create } from '@bufbuild/protobuf'
import { 
  CreateNodeRequestSchema,
  GetChildrenRequestSchema,
  Node_NodeType
} from '../gen/filesystem_service_pb'

export type ConnectionStatus = 'connected' | 'disconnected' | 'error' | 'unknown'

// Constants for reconnection logic
const INITIAL_RECONNECT_ATTEMPTS = 3
const RECONNECT_DELAY_MS = 5000 // 5 seconds between attempts
const MAX_RECONNECT_DURATION_MS = 120000 // 2 minutes total

export const useRepositoryStore = defineStore('repository', () => {
  // State
  const connectionStatus = ref<ConnectionStatus>('unknown')
  const selectedAddress = ref<string>('')
  const isConnecting = ref(false)
  const lastError = ref<string | null>(null)
  const lastHealthCheckTime = ref<number | null>(null)
  const reconnectAttempts = ref(0)
  const initialConnectionTime = ref<number | null>(null)
  const hasConfiguredAddress = ref(false)
  
  // Health client
  let healthClient: PromiseClient<typeof Health> | null = null
  let lastUsedAddress: string | null = null
  let reconnectTimer: NodeJS.Timeout | null = null
  
  // Load saved address from localStorage
  const savedAddress = localStorage.getItem('pipeline-repository-address')
  if (savedAddress) {
    selectedAddress.value = savedAddress
    hasConfiguredAddress.value = true
  }
  
  // Computed
  const isConnected = computed(() => connectionStatus.value === 'connected')
  
  // Actions
  function setSelectedAddress(address: string) {
    selectedAddress.value = address
    hasConfiguredAddress.value = !!address
    
    // Save to localStorage
    if (address) {
      localStorage.setItem('pipeline-repository-address', address)
    } else {
      localStorage.removeItem('pipeline-repository-address')
    }
    
    // Reset connection when address changes
    connectionStatus.value = 'unknown'
    lastError.value = null
    lastHealthCheckTime.value = null
    reconnectAttempts.value = 0
    initialConnectionTime.value = null
    
    // Clear any existing reconnect timer
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }
  
  function setConnectionStatus(status: ConnectionStatus) {
    connectionStatus.value = status
  }
  
  async function checkHealth(isAutoReconnect = false) {
    if (!selectedAddress.value) {
      connectionStatus.value = 'disconnected'
      return
    }
    
    try {
      // Create client if needed (or recreate if address changed)
      if (!healthClient || lastUsedAddress !== selectedAddress.value) {
        healthClient = createRepositoryHealthClient(selectedAddress.value)
        lastUsedAddress = selectedAddress.value
      }
      
      // Make health check request with timeout
      const timeoutPromise = new Promise<never>((_, reject) => {
        setTimeout(() => reject(new Error('Health check timeout')), 5000) // 5 second timeout
      })
      
      const response = await Promise.race([
        healthClient.check({ service: '' }),
        timeoutPromise
      ])
      
      // Check status
      if (response.status === 1) { // SERVING
        connectionStatus.value = 'connected'
        lastHealthCheckTime.value = Date.now()
        lastError.value = null
        reconnectAttempts.value = 0
        initialConnectionTime.value = null
      } else {
        connectionStatus.value = 'error'
        lastError.value = `Repository not serving (status: ${response.status})`
        scheduleReconnect()
      }
    } catch (error) {
      connectionStatus.value = 'error'
      lastError.value = error instanceof Error ? error.message : 'Unknown error'
      
      // Only log on manual attempts, not auto-reconnects
      if (!isAutoReconnect) {
        console.error('[Repository] Health check failed:', error)
      }
      
      scheduleReconnect()
    }
  }
  
  // Save file to repository
  async function saveFile(path: string, content: string, mimeType = 'application/octet-stream') {
    if (!isConnected.value) {
      throw new Error('Repository not connected')
    }
    
    try {
      // Split path into parts
      const pathParts = path.split('/')
      const filename = pathParts.pop() || 'document.bin'
      
      // Create directories if needed
      let currentParentId = ''
      for (const part of pathParts) {
        if (!part) continue
        
        console.log(`Checking/creating directory: ${part} with parentId: ${currentParentId}`)
        
        // Check if directory exists
        const listRequest = create(GetChildrenRequestSchema, {
          nodeId: currentParentId
        })
        
        const listResponse = await filesystemService.getChildren(listRequest)
        console.log('List response:', listResponse)
        // The response has 'nodes' not 'children'
        const existingDir = listResponse.nodes?.find(n => n.name === part && n.type === Node_NodeType.FOLDER)
        
        if (existingDir) {
          currentParentId = existingDir.id
        } else {
          // Create directory
          const createDirRequest = create(CreateNodeRequestSchema, {
            parent_id: currentParentId,
            name: part,
            type: Node_NodeType.FOLDER
          })
          
          const createdDir = await filesystemService.createNode(createDirRequest)
          console.log('Created directory:', createdDir)
          
          if (!createdDir || !createdDir.id) {
            throw new Error(`Failed to create directory: ${part}`)
          }
          currentParentId = createdDir.id
        }
      }
      
      // Create the file
      // Import Any type
      const { create: createAny } = await import('@bufbuild/protobuf')
      const { AnySchema } = await import('@bufbuild/protobuf/wkt')
      
      // The content is base64 encoded ModuleProcessRequest protobuf binary
      // Convert it back to bytes
      const binaryData = Uint8Array.from(atob(content), c => c.charCodeAt(0))
      
      // Create Any message with the actual protobuf type
      const anyPayload = create(AnySchema, {
        typeUrl: 'type.googleapis.com/io.pipeline.process.ModuleProcessRequest',
        value: binaryData  // This is already the serialized ModuleProcessRequest
      })
      
      const createFileRequest = create(CreateNodeRequestSchema, {
        parent_id: currentParentId,
        name: filename,
        type: Node_NodeType.FILE,
        payload: anyPayload,
        metadata: {
          mimeType: mimeType
        }
      })
      
      const createdFile = await filesystemService.createNode(createFileRequest)
      console.log('Created file:', createdFile)
      
      if (!createdFile) {
        throw new Error(`Failed to create file: ${filename}`)
      }
      return createdFile
    } catch (error) {
      console.error('Failed to save file:', error)
      throw error
    }
  }
  
  function scheduleReconnect() {
    // Don't schedule if no address is configured
    if (!hasConfiguredAddress.value) return
    
    // Set initial connection time if not set
    if (!initialConnectionTime.value) {
      initialConnectionTime.value = Date.now()
    }
    
    // Check if we've exceeded the max reconnect duration
    const elapsedTime = Date.now() - initialConnectionTime.value
    if (elapsedTime > MAX_RECONNECT_DURATION_MS) {
      // Stop trying after 2 minutes
      connectionStatus.value = 'disconnected'
      reconnectAttempts.value = 0
      initialConnectionTime.value = null
      return
    }
    
    // Schedule next reconnect attempt
    reconnectAttempts.value++
    reconnectTimer = setTimeout(() => {
      checkHealth(true) // Pass true to indicate auto-reconnect
    }, RECONNECT_DELAY_MS)
  }
  
  async function connect() {
    if (isConnecting.value) return
    
    // Clear any existing reconnect timer
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    
    // Reset reconnect state
    reconnectAttempts.value = 0
    initialConnectionTime.value = null
    
    isConnecting.value = true
    try {
      await checkHealth(false) // Manual attempt
    } finally {
      isConnecting.value = false
    }
  }
  
  function disconnect() {
    // Clear any reconnect timer
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    
    connectionStatus.value = 'disconnected'
    lastHealthCheckTime.value = null
    reconnectAttempts.value = 0
    initialConnectionTime.value = null
    healthClient = null
  }
  
  // Auto-connect on startup if address is configured
  if (hasConfiguredAddress.value && selectedAddress.value) {
    // Delay initial connection to avoid startup noise
    setTimeout(async () => {
      try {
        await connect()
      } catch (error) {
        // If initial connection fails, make sure we're not stuck in connecting state
        console.error('[Repository] Initial connection failed:', error)
        isConnecting.value = false
      }
    }, 1000)
  }
  
  return {
    // State
    connectionStatus,
    selectedAddress,
    isConnecting,
    lastError,
    lastHealthCheckTime,
    hasConfiguredAddress,
    
    // Computed
    isConnected,
    
    // Actions
    setSelectedAddress,
    setConnectionStatus,
    checkHealth,
    connect,
    disconnect,
    saveFile
  }
})
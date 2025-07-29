import { ref, computed, watch } from 'vue'
import { moduleService } from '../services/moduleService'

// Persistence key
const STORAGE_KEY = 'pipeline-dev-tools-state'

export interface ConnectedModule {
  address: string
  name: string
  version: string
  description?: string
  schema?: any
  capabilities?: string[]
  status: 'connected' | 'disconnected' | 'error'
  healthStatus?: 'SERVING' | 'NOT_SERVING' | 'UNKNOWN'
  lastChecked: Date
}

// Module registry store
const modules = ref<Map<string, ConnectedModule>>(new Map())
const activeModuleAddress = ref<string>('')

// Load saved state on initialization
const loadState = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      const state = JSON.parse(saved)
      // Restore modules
      if (state.modules) {
        const restoredModules = new Map()
        state.modules.forEach(([address, module]: [string, any]) => {
          restoredModules.set(address, {
            ...module,
            lastChecked: new Date(module.lastChecked),
            healthStatus: module.healthStatus || 'UNKNOWN' // Ensure old saved modules get UNKNOWN status
          })
        })
        modules.value = restoredModules
      }
      // Restore active module
      if (state.activeModule) {
        activeModuleAddress.value = state.activeModule
      }
    }
  } catch (err) {
    console.error('Failed to load saved state:', err)
  }
}

// Save state whenever it changes
const saveState = () => {
  try {
    const state = {
      modules: Array.from(modules.value.entries()),
      activeModule: activeModuleAddress.value,
      savedAt: new Date().toISOString()
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch (err) {
    console.error('Failed to save state:', err)
  }
}

// Watch for changes and auto-save
watch([modules, activeModuleAddress], saveState, { deep: true })

// Load state on module initialization
loadState()

// Refresh health status for all loaded modules
setTimeout(async () => {
  for (const [address, module] of modules.value) {
    if (module.status === 'connected') {
      try {
        const health = await moduleService.checkModuleHealth(address)
        module.healthStatus = health.serving ? 'SERVING' : 'NOT_SERVING'
      } catch (error) {
        module.healthStatus = 'NOT_SERVING'
      }
    }
  }
}, 100)

export const useModuleStore = () => {
  
  const connectedModules = computed(() => 
    Array.from(modules.value.values()).filter(m => m.status === 'connected')
  )
  
  const activeModule = computed(() => 
    modules.value.get(activeModuleAddress.value)
  )
  
  const parserModules = computed(() =>
    connectedModules.value.filter(m => 
      m.capabilities?.includes('PARSER') || m.name === 'parser'
    )
  )
  
  const addModule = async (address: string) => {
    try {
      const data = await moduleService.getModuleSchema(address)
      
      // Check health status
      const health = await moduleService.checkModuleHealth(address)
      console.log('Health check result for', address, ':', health)
      
      const module: ConnectedModule = {
        address,
        name: data.module_name,
        version: data.version || '',
        description: data.description,
        schema: data.schema,
        capabilities: data.capabilities || [],
        status: 'connected',
        healthStatus: health.serving ? 'SERVING' : 'NOT_SERVING',
        lastChecked: new Date()
      }
      
      modules.value.set(address, module)
      
      // If first module, make it active
      if (modules.value.size === 1) {
        activeModuleAddress.value = address
      }
      
      return module
    } catch (error) {
      const errorModule: ConnectedModule = {
        address,
        name: 'Unknown',
        version: '',
        status: 'error',
        healthStatus: 'NOT_SERVING',
        lastChecked: new Date()
      }
      modules.value.set(address, errorModule)
      throw error
    }
  }
  
  const removeModule = (address: string) => {
    modules.value.delete(address)
    if (activeModuleAddress.value === address) {
      // Switch to next available module
      const next = connectedModules.value[0]
      activeModuleAddress.value = next?.address || ''
    }
  }
  
  const setActiveModule = (address: string) => {
    if (modules.value.has(address)) {
      activeModuleAddress.value = address
    }
  }
  
  const refreshModule = async (address: string) => {
    if (modules.value.has(address)) {
      try {
        await addModule(address) // Re-add updates the data
      } catch (error) {
        // Update just the health status if schema fetch fails
        const module = modules.value.get(address)
        if (module) {
          const health = await moduleService.checkModuleHealth(address)
          module.healthStatus = health.serving ? 'SERVING' : 'NOT_SERVING'
          module.status = health.serving ? 'connected' : 'disconnected'
          module.lastChecked = new Date()
        }
      }
    }
  }
  
  return {
    modules: computed(() => modules.value),
    connectedModules,
    activeModule,
    parserModules,
    activeModuleAddress,
    addModule,
    removeModule,
    setActiveModule,
    refreshModule
  }
}
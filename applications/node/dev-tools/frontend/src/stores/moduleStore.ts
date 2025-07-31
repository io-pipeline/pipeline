import { defineStore } from 'pinia'
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

interface ModuleState {
  modules: Map<string, ConnectedModule>
  activeModuleAddress: string
  currentConfigId: string
  currentConfig: any
}

export const useModuleStore = defineStore('modules', {
  state: (): ModuleState => ({
    modules: new Map(),
    activeModuleAddress: '',
    currentConfigId: '',
    currentConfig: {}
  }),

  getters: {
    connectedModules: (state) => Array.from(state.modules.values()),
    
    activeModule: (state) => {
      if (!state.activeModuleAddress) return null
      return state.modules.get(state.activeModuleAddress) || null
    },

    getSavedConfigs: (state) => (moduleAddress: string) => {
      const configStore = useConfigStore()
      return configStore.getConfigs(moduleAddress)
    }
  },

  actions: {
    // Initialize store from localStorage
    init() {
      this.loadState()
      // Watch for changes and save
      this.$subscribe(() => {
        this.saveState()
      })
    },

    // Load saved state on initialization
    loadState() {
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
                healthStatus: module.healthStatus || 'UNKNOWN'
              })
            })
            this.modules = restoredModules
          }
          // Restore active module
          if (state.activeModule) {
            this.activeModuleAddress = state.activeModule
          }
        }
      } catch (err) {
        console.error('Failed to load saved state:', err)
      }
    },

    // Save state to localStorage
    saveState() {
      try {
        const state = {
          modules: Array.from(this.modules.entries()),
          activeModule: this.activeModuleAddress
        }
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
      } catch (err) {
        console.error('Failed to save state:', err)
      }
    },

    // Connect to a module
    async connectModule(address: string) {
      try {
        const registration = await moduleService.getModuleSchema(address)
        
        const module: ConnectedModule = {
          address,
          name: registration.module_name,
          version: registration.version,
          description: registration.description,
          schema: registration.schema,
          capabilities: registration.capabilities || [],
          status: 'connected',
          healthStatus: 'UNKNOWN',
          lastChecked: new Date()
        }
        
        this.modules.set(address, module)
        
        // Check health status
        await this.checkModuleHealth(address)
        
        return module
      } catch (error: any) {
        const errorModule: ConnectedModule = {
          address,
          name: 'Unknown',
          version: 'Unknown',
          status: 'error',
          healthStatus: 'NOT_SERVING',
          lastChecked: new Date()
        }
        this.modules.set(address, errorModule)
        throw error
      }
    },

    // Check module health
    async checkModuleHealth(address: string) {
      try {
        const health = await moduleService.checkModuleHealth(address)
        const module = this.modules.get(address)
        if (module) {
          module.healthStatus = health.status as any
          module.lastChecked = new Date()
          this.modules.set(address, { ...module })
        }
      } catch (error) {
        const module = this.modules.get(address)
        if (module) {
          module.healthStatus = 'NOT_SERVING'
          module.lastChecked = new Date()
          this.modules.set(address, { ...module })
        }
      }
    },

    // Set active module
    setActiveModule(address: string) {
      if (this.modules.has(address)) {
        this.activeModuleAddress = address
      }
    },

    // Remove module
    removeModule(address: string) {
      this.modules.delete(address)
      if (this.activeModuleAddress === address) {
        this.activeModuleAddress = ''
      }
    },

    // Update current config
    updateCurrentConfig(config: any) {
      this.currentConfig = config
    },

    // Set current config with ID
    setCurrentConfig(configId: string, config: any) {
      this.currentConfigId = configId
      this.currentConfig = config
    },

    // Set current config ID
    setCurrentConfigId(configId: string) {
      this.currentConfigId = configId
    }
  }
})

// Import this at the end to avoid circular dependency
import { useConfigStore } from './configStore'
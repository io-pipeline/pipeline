import { defineStore } from 'pinia'

export interface ModuleConfig {
  id: string
  moduleAddress: string
  moduleName: string
  name: string
  description?: string
  config: any
  createdAt: Date
  updatedAt: Date
}

// Storage key
const CONFIGS_STORAGE_KEY = 'pipeline-module-configs-v2'

interface ConfigState {
  configs: Map<string, ModuleConfig>
}

export const useConfigStore = defineStore('configs', {
  state: (): ConfigState => ({
    configs: new Map()
  }),

  getters: {
    // Get all configs for a module
    getConfigs: (state) => (moduleAddress: string) => {
      return Array.from(state.configs.values())
        .filter(c => c.moduleAddress === moduleAddress)
        .sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())
    },
    
    // Get a specific config
    getConfig: (state) => (configId: string) => {
      return state.configs.get(configId)
    },
    
    // Get default config for a module (most recent)
    getDefaultConfig: (state) => (moduleAddress: string) => {
      const moduleConfigs = Array.from(state.configs.values())
        .filter(c => c.moduleAddress === moduleAddress)
        .sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())
      return moduleConfigs[0] || null
    }
  },

  actions: {
    // Initialize store from localStorage
    init() {
      this.loadConfigs()
      // Watch for changes and save
      this.$subscribe(() => {
        this.saveConfigs()
      })
    },

    // Load saved configs
    loadConfigs() {
      try {
        const saved = localStorage.getItem(CONFIGS_STORAGE_KEY)
        if (saved) {
          const data = JSON.parse(saved)
          if (data.configs) {
            const restoredConfigs = new Map()
            data.configs.forEach(([id, config]: [string, any]) => {
              restoredConfigs.set(id, {
                ...config,
                createdAt: new Date(config.createdAt),
                updatedAt: new Date(config.updatedAt)
              })
            })
            this.configs = restoredConfigs
          }
        }
      } catch (err) {
        console.error('Failed to load configs:', err)
      }
    },

    // Save configs
    saveConfigs() {
      try {
        const data = {
          configs: Array.from(this.configs.entries()),
          savedAt: new Date().toISOString()
        }
        localStorage.setItem(CONFIGS_STORAGE_KEY, JSON.stringify(data))
      } catch (err) {
        console.error('Failed to save configs:', err)
      }
    },

    // Create or update a config
    saveConfig(config: Partial<ModuleConfig> & { moduleAddress: string, config: any }) {
      const id = config.id || `config-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
      
      const moduleConfig: ModuleConfig = {
        id,
        moduleAddress: config.moduleAddress,
        moduleName: config.moduleName || 'Unknown',
        name: config.name || 'Unnamed Config',
        description: config.description,
        config: config.config,
        createdAt: config.id ? this.configs.get(id)?.createdAt || new Date() : new Date(),
        updatedAt: new Date()
      }
      
      this.configs.set(id, moduleConfig)
      return moduleConfig
    },

    // Delete a config
    deleteConfig(configId: string) {
      this.configs.delete(configId)
    },

    // Clone a config
    cloneConfig(configId: string, newName: string) {
      const original = this.configs.get(configId)
      if (!original) return null
      
      return this.saveConfig({
        ...original,
        id: undefined, // Generate new ID
        name: newName,
        description: `Cloned from ${original.name}`
      })
    }
  }
})
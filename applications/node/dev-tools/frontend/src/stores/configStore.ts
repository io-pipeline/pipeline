import { ref, computed, watch } from 'vue'

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

// Config store
const configs = ref<Map<string, ModuleConfig>>(new Map())

// Load saved configs
const loadConfigs = () => {
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
        configs.value = restoredConfigs
      }
    }
  } catch (err) {
    console.error('Failed to load configs:', err)
  }
}

// Save configs
const saveConfigs = () => {
  try {
    const data = {
      configs: Array.from(configs.value.entries()),
      savedAt: new Date().toISOString()
    }
    localStorage.setItem(CONFIGS_STORAGE_KEY, JSON.stringify(data))
  } catch (err) {
    console.error('Failed to save configs:', err)
  }
}

// Watch for changes
watch(configs, saveConfigs, { deep: true })

// Initialize
loadConfigs()

export const useConfigStore = () => {
  
  // Get all configs for a module
  const getModuleConfigs = (moduleAddress: string) => {
    return computed(() => 
      Array.from(configs.value.values())
        .filter(c => c.moduleAddress === moduleAddress)
        .sort((a, b) => b.updatedAt.getTime() - a.updatedAt.getTime())
    )
  }
  
  // Get a specific config
  const getConfig = (configId: string) => {
    return computed(() => configs.value.get(configId))
  }
  
  // Create or update a config
  const saveConfig = (config: Partial<ModuleConfig> & { moduleAddress: string, config: any }) => {
    const id = config.id || `config-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`
    
    const moduleConfig: ModuleConfig = {
      id,
      moduleAddress: config.moduleAddress,
      moduleName: config.moduleName || 'Unknown',
      name: config.name || 'Unnamed Config',
      description: config.description,
      config: config.config,
      createdAt: config.id ? configs.value.get(id)?.createdAt || new Date() : new Date(),
      updatedAt: new Date()
    }
    
    configs.value.set(id, moduleConfig)
    return moduleConfig
  }
  
  // Delete a config
  const deleteConfig = (configId: string) => {
    configs.value.delete(configId)
  }
  
  // Clone a config
  const cloneConfig = (configId: string, newName: string) => {
    const original = configs.value.get(configId)
    if (!original) return null
    
    return saveConfig({
      ...original,
      id: undefined, // Generate new ID
      name: newName,
      description: `Cloned from ${original.name}`
    })
  }
  
  // Get default config for a module (most recent)
  const getDefaultConfig = (moduleAddress: string) => {
    const moduleConfigs = getModuleConfigs(moduleAddress).value
    return moduleConfigs[0] || null
  }
  
  return {
    configs: computed(() => configs.value),
    getModuleConfigs,
    getConfig,
    saveConfig,
    deleteConfig,
    cloneConfig,
    getDefaultConfig
  }
}
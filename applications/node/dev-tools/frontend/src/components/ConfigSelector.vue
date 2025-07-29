<template>
  <div class="config-selector">
    <div class="selector-header">
      <h3>Configuration</h3>
      <button @click="showNewConfig = true" class="new-config-button">
        + New Config
      </button>
    </div>

    <!-- Config List -->
    <div class="config-list">
      <div
        v-for="config in moduleConfigs"
        :key="config.id"
        class="config-item"
        :class="{ active: config.id === selectedConfigId }"
        @click="selectConfig(config)"
      >
        <div class="config-info">
          <h4>{{ config.name }}</h4>
          <p v-if="config.description" class="config-description">
            {{ config.description }}
          </p>
          <p class="config-meta">
            Updated {{ formatDate(config.updatedAt) }}
          </p>
        </div>
        
        <div class="config-actions">
          <button @click.stop="editConfig(config)" class="icon-button" title="Edit">
            ✏️
          </button>
          <button @click.stop="cloneConfig(config)" class="icon-button" title="Clone">
            📋
          </button>
          <button @click.stop="deleteConfig(config)" class="icon-button" title="Delete">
            🗑️
          </button>
        </div>
      </div>
      
      <div v-if="moduleConfigs.length === 0" class="empty-configs">
        No configurations yet. Create your first config!
      </div>
    </div>

    <!-- New/Edit Config Dialog -->
    <div v-if="showNewConfig || editingConfig" class="config-dialog">
      <div class="dialog-content">
        <h3>{{ editingConfig ? 'Edit Configuration' : 'New Configuration' }}</h3>
        
        <div class="form-group">
          <label>Name</label>
          <input 
            v-model="configForm.name" 
            placeholder="e.g., Default Parser Config"
            class="form-input"
          />
        </div>
        
        <div class="form-group">
          <label>Description (optional)</label>
          <textarea 
            v-model="configForm.description" 
            placeholder="Describe this configuration..."
            class="form-textarea"
            rows="3"
          />
        </div>
        
        <div class="dialog-actions">
          <button @click="saveConfigForm" class="save-button">
            {{ editingConfig ? 'Update' : 'Create' }}
          </button>
          <button @click="cancelConfigForm" class="cancel-button">
            Cancel
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useConfigStore } from '../stores/configStore'

const props = defineProps<{
  moduleAddress: string
  moduleName: string
  currentConfig?: any
}>()

const emit = defineEmits<{
  'config-selected': [config: any]
  'config-changed': [configId: string]
}>()

const { 
  getModuleConfigs, 
  saveConfig, 
  deleteConfig: deleteConfigStore,
  cloneConfig: cloneConfigStore
} = useConfigStore()

const moduleConfigs = getModuleConfigs(props.moduleAddress)
const selectedConfigId = ref<string>('')
const showNewConfig = ref(false)
const editingConfig = ref<any>(null)

const configForm = ref({
  name: '',
  description: ''
})

// Define selectConfig before the watcher that uses it
const selectConfig = (config: any) => {
  selectedConfigId.value = config.id
  emit('config-selected', config.config)
  emit('config-changed', config.id)
}

// Auto-select first config or create default
watch(() => moduleConfigs.value, (configs) => {
  if (configs.length > 0 && !selectedConfigId.value) {
    selectConfig(configs[0])
  } else if (configs.length === 0 && props.currentConfig) {
    // Create a default config if we have current config data
    const defaultConfig = saveConfig({
      moduleAddress: props.moduleAddress,
      moduleName: props.moduleName,
      name: 'Default Configuration',
      config: props.currentConfig
    })
    selectConfig(defaultConfig)
  }
}, { immediate: true })

const editConfig = (config: any) => {
  editingConfig.value = config
  configForm.value = {
    name: config.name,
    description: config.description || ''
  }
}

const cloneConfig = (config: any) => {
  const newName = prompt('Name for cloned configuration:', `${config.name} (Copy)`)
  if (newName) {
    const cloned = cloneConfigStore(config.id, newName)
    if (cloned) {
      selectConfig(cloned)
    }
  }
}

const deleteConfig = (config: any) => {
  if (confirm(`Delete configuration "${config.name}"?`)) {
    deleteConfigStore(config.id)
    if (selectedConfigId.value === config.id) {
      selectedConfigId.value = ''
      const remaining = moduleConfigs.value
      if (remaining.length > 0) {
        selectConfig(remaining[0])
      }
    }
  }
}

const saveConfigForm = () => {
  if (!configForm.value.name) {
    alert('Please provide a name for the configuration')
    return
  }
  
  if (editingConfig.value) {
    // Update existing
    saveConfig({
      ...editingConfig.value,
      name: configForm.value.name,
      description: configForm.value.description
    })
  } else {
    // Create new with current config data
    const newConfig = saveConfig({
      moduleAddress: props.moduleAddress,
      moduleName: props.moduleName,
      name: configForm.value.name,
      description: configForm.value.description,
      config: props.currentConfig || {}
    })
    selectConfig(newConfig)
  }
  
  cancelConfigForm()
}

const cancelConfigForm = () => {
  showNewConfig.value = false
  editingConfig.value = null
  configForm.value = { name: '', description: '' }
}

const formatDate = (date: Date) => {
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  
  if (diff < 60000) return 'just now'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`
  return d.toLocaleDateString()
}

// Update config when it changes
const updateCurrentConfig = (newConfig: any) => {
  if (selectedConfigId.value) {
    const config = moduleConfigs.value.find(c => c.id === selectedConfigId.value)
    if (config) {
      saveConfig({
        ...config,
        config: newConfig
      })
    }
  }
}

defineExpose({ updateCurrentConfig })
</script>

<style scoped>
.config-selector {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.selector-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.selector-header h3 {
  margin: 0;
  color: #333;
}

.new-config-button {
  padding: 0.5rem 1rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.new-config-button:hover {
  background: #357abd;
}

.config-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.config-item {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-item:hover {
  border-color: #4a90e2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.config-item.active {
  border-color: #4a90e2;
  background: #f0f7ff;
}

.config-info h4 {
  margin: 0 0 0.25rem 0;
  color: #333;
}

.config-description {
  margin: 0 0 0.25rem 0;
  color: #666;
  font-size: 0.9rem;
}

.config-meta {
  margin: 0;
  color: #999;
  font-size: 0.85rem;
}

.config-actions {
  display: flex;
  gap: 0.5rem;
}

.icon-button {
  width: 32px;
  height: 32px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
}

.icon-button:hover {
  background: #f5f5f5;
}

.empty-configs {
  text-align: center;
  padding: 2rem;
  color: #999;
  font-style: italic;
}

/* Dialog */
.config-dialog {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background: white;
  border-radius: 8px;
  padding: 2rem;
  width: 90%;
  max-width: 500px;
}

.dialog-content h3 {
  margin: 0 0 1.5rem 0;
  color: #333;
}

.form-group {
  margin-bottom: 1rem;
}

.form-group label {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: #333;
}

.form-input, .form-textarea {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
  font-family: inherit;
}

.form-input:focus, .form-textarea:focus {
  outline: none;
  border-color: #4a90e2;
}

.dialog-actions {
  display: flex;
  gap: 1rem;
  margin-top: 1.5rem;
}

.save-button, .cancel-button {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
}

.save-button {
  background: #4a90e2;
  color: white;
}

.save-button:hover {
  background: #357abd;
}

.cancel-button {
  background: #f5f5f5;
  color: #333;
}

.cancel-button:hover {
  background: #e8e8e8;
}
</style>
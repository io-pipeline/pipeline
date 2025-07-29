<template>
  <v-card class="mb-4">
    <v-card-title class="d-flex justify-space-between align-center">
      <span>Configuration</span>
      <v-btn
        color="primary"
        @click="showNewConfig = true"
        prepend-icon="mdi-plus"
      >
        New Config
      </v-btn>
    </v-card-title>

    <v-card-text>
      <!-- Config List -->
      <v-list v-if="moduleConfigs.length > 0" lines="two">
        <v-list-item
          v-for="config in moduleConfigs"
          :key="config.id"
          :active="config.id === selectedConfigId"
          @click="selectConfig(config)"
          class="mb-2"
        >
          <template v-slot:prepend>
            <v-icon :color="config.id === selectedConfigId ? 'primary' : ''">
              mdi-file-cog-outline
            </v-icon>
          </template>

          <v-list-item-title>{{ config.name }}</v-list-item-title>
          <v-list-item-subtitle v-if="config.description">
            {{ config.description }}
          </v-list-item-subtitle>
          <v-list-item-subtitle>
            Updated {{ formatDate(config.updatedAt) }}
          </v-list-item-subtitle>

          <template v-slot:append>
            <v-btn
              icon="mdi-pencil"
              size="small"
              variant="text"
              @click.stop="editConfig(config)"
              title="Edit"
            />
            <v-btn
              icon="mdi-content-copy"
              size="small"
              variant="text"
              @click.stop="cloneConfig(config)"
              title="Clone"
            />
            <v-btn
              icon="mdi-delete"
              size="small"
              variant="text"
              @click.stop="deleteConfig(config)"
              title="Delete"
            />
          </template>
        </v-list-item>
      </v-list>

      <!-- Empty State -->
      <v-empty-state
        v-else
        icon="mdi-file-cog-outline"
        headline="No configurations yet"
        text="Create your first config!"
      />
    </v-card-text>

    <!-- New/Edit Config Dialog -->
    <v-dialog v-model="showConfigDialog" max-width="500px">
      <v-card>
        <v-card-title>
          {{ editingConfig ? 'Edit Configuration' : 'New Configuration' }}
        </v-card-title>
        
        <v-card-text>
          <v-text-field
            v-model="configForm.name"
            label="Name"
            placeholder="e.g., Default Parser Config"
            variant="outlined"
            density="comfortable"
            :rules="[v => !!v || 'Name is required']"
          />
          
          <v-textarea
            v-model="configForm.description"
            label="Description (optional)"
            placeholder="Describe this configuration..."
            variant="outlined"
            density="comfortable"
            rows="3"
          />
        </v-card-text>
        
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="cancelConfigForm">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            variant="flat"
            @click="saveConfigForm"
            :disabled="!configForm.name"
          >
            {{ editingConfig ? 'Update' : 'Create' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Clone Dialog -->
    <v-dialog v-model="showCloneDialog" max-width="400px">
      <v-card>
        <v-card-title>Clone Configuration</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="cloneName"
            label="Name for cloned configuration"
            variant="outlined"
            density="comfortable"
            :rules="[v => !!v || 'Name is required']"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showCloneDialog = false">Cancel</v-btn>
          <v-btn color="primary" variant="flat" @click="confirmClone" :disabled="!cloneName">
            Clone
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Delete Confirmation Dialog -->
    <v-dialog v-model="showDeleteDialog" max-width="400px">
      <v-card>
        <v-card-title>Delete Configuration</v-card-title>
        <v-card-text>
          Are you sure you want to delete "{{ configToDelete?.name }}"?
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showDeleteDialog = false">Cancel</v-btn>
          <v-btn color="error" variant="flat" @click="confirmDelete">Delete</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
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

const configStore = useConfigStore()

const moduleConfigs = computed(() => configStore.getConfigs(props.moduleAddress))
const selectedConfigId = ref<string>('')
const showNewConfig = ref(false)
const editingConfig = ref<any>(null)
const showConfigDialog = computed({
  get: () => showNewConfig.value || !!editingConfig.value,
  set: (val) => {
    if (!val) {
      showNewConfig.value = false
      editingConfig.value = null
    }
  }
})

// Clone dialog
const showCloneDialog = ref(false)
const cloneName = ref('')
const configToClone = ref<any>(null)

// Delete dialog
const showDeleteDialog = ref(false)
const configToDelete = ref<any>(null)

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
    const defaultConfig = configStore.saveConfig({
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
  configToClone.value = config
  cloneName.value = `${config.name} (Copy)`
  showCloneDialog.value = true
}

const confirmClone = () => {
  if (cloneName.value && configToClone.value) {
    const cloned = configStore.cloneConfig(configToClone.value.id, cloneName.value)
    if (cloned) {
      selectConfig(cloned)
    }
    showCloneDialog.value = false
    cloneName.value = ''
    configToClone.value = null
  }
}

const deleteConfig = (config: any) => {
  configToDelete.value = config
  showDeleteDialog.value = true
}

const confirmDelete = () => {
  if (configToDelete.value) {
    configStore.deleteConfig(configToDelete.value.id)
    if (selectedConfigId.value === configToDelete.value.id) {
      selectedConfigId.value = ''
      const remaining = moduleConfigs.value
      if (remaining.length > 0) {
        selectConfig(remaining[0])
      }
    }
    showDeleteDialog.value = false
    configToDelete.value = null
  }
}

const saveConfigForm = () => {
  if (!configForm.value.name) return
  
  if (editingConfig.value) {
    // Update existing
    configStore.saveConfig({
      ...editingConfig.value,
      name: configForm.value.name,
      description: configForm.value.description
    })
  } else {
    // Create new with current config data
    const newConfig = configStore.saveConfig({
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
      configStore.saveConfig({
        ...config,
        config: newConfig
      })
    }
  }
}

defineExpose({ updateCurrentConfig })
</script>

<style scoped>
/* Vuetify handles most styling */
</style>
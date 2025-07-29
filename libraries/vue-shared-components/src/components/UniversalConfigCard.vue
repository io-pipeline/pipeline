<template>
  <div class="universal-config-card">
    <h3>Configuration</h3>
    
    <!-- Schema-based rendering with JSONForms -->
    <div class="jsonforms-container" v-if="schema">
      <JsonForms
        :data="data"
        :schema="schema"
        :uischema="uischema"
        :renderers="renderers"
        @change="handleChange"
      />
    </div>
    
    <!-- Fallback key/value editor when no schema -->
    <div v-else class="kv-editor">
      <div class="kv-header">
        <p class="no-schema-message">No schema provided - using key/value editor</p>
        <button @click="addRow" class="add-button">+ Add Entry</button>
      </div>
      
      <div class="kv-grid">
        <div class="kv-row header">
          <div class="kv-key">Key</div>
          <div class="kv-value">Value</div>
          <div class="kv-actions"></div>
        </div>
        
        <div v-for="(entry, index) in kvEntries" :key="index" class="kv-row">
          <input 
            v-model="entry.key" 
            @input="updateKvData"
            placeholder="Enter key"
            class="kv-input"
          />
          <input 
            v-model="entry.value" 
            @input="updateKvData"
            placeholder="Enter value"
            class="kv-input"
          />
          <button @click="removeRow(index)" class="remove-button">×</button>
        </div>
      </div>
      
      <div v-if="kvEntries.length === 0" class="empty-state">
        No configuration entries. Click "Add Entry" to start.
      </div>
    </div>
    
    <div class="config-output">
      <h4>Current Configuration:</h4>
      <pre>{{ JSON.stringify(data, null, 2) }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'

interface KeyValueEntry {
  key: string
  value: string
}

const props = defineProps<{
  schema?: any
  initialData?: any
}>()

const emit = defineEmits<{
  'data-change': [data: any]
}>()

const data = ref(props.initialData || {})
const renderers = Object.freeze([...vanillaRenderers])
const kvEntries = ref<KeyValueEntry[]>([])

// Extract default values from schema
const getDefaultData = (schema: any): any => {
  if (!schema || !schema.properties) return {}
  
  const defaults: any = {}
  
  Object.entries(schema.properties).forEach(([key, prop]: [string, any]) => {
    if (prop.default !== undefined) {
      defaults[key] = prop.default
    } else if (prop.type === 'object' && prop.properties) {
      // Recursively get defaults for nested objects
      const nestedDefaults = getDefaultData(prop)
      if (Object.keys(nestedDefaults).length > 0) {
        defaults[key] = nestedDefaults
      }
    } else if (prop.type === 'array' && prop.default === undefined) {
      // Initialize arrays as empty if no default specified
      defaults[key] = []
    }
  })
  
  return defaults
}

// Generate a default UI schema if none provided
const uischema = computed(() => {
  if (!props.schema || !props.schema.properties) {
    return undefined
  }

  // Create a vertical layout with all properties
  const elements = Object.keys(props.schema.properties).map(key => ({
    type: 'Control',
    scope: `#/properties/${key}`
  }))

  return {
    type: 'VerticalLayout',
    elements
  }
})

// Initialize key/value entries from initial data when no schema
const initializeKvEntries = () => {
  if (!props.schema && props.initialData && Object.keys(props.initialData).length > 0) {
    kvEntries.value = Object.entries(props.initialData).map(([key, value]) => ({
      key,
      value: String(value)
    }))
  } else if (!props.schema && kvEntries.value.length === 0) {
    // Start with one empty row for better UX
    kvEntries.value = [{ key: '', value: '' }]
  }
}

// Watch for schema changes and reset data with defaults
watch(() => props.schema, (newSchema) => {
  if (newSchema) {
    const defaults = getDefaultData(newSchema)
    data.value = { ...defaults, ...(props.initialData || {}) }
  } else {
    data.value = props.initialData || {}
    initializeKvEntries()
  }
}, { immediate: true })

// Watch for external data changes
watch(() => props.initialData, (newData) => {
  if (!props.schema && newData) {
    kvEntries.value = Object.entries(newData).map(([key, value]) => ({
      key,
      value: String(value)
    }))
    data.value = newData
  }
})

onMounted(() => {
  if (!props.schema) {
    initializeKvEntries()
  }
})

const handleChange = (event: any) => {
  data.value = event.data
  emit('data-change', event.data)
}

// Key/Value editor methods
const addRow = () => {
  kvEntries.value.push({ key: '', value: '' })
}

const removeRow = (index: number) => {
  kvEntries.value.splice(index, 1)
  updateKvData()
}

const updateKvData = () => {
  // Convert entries to object, filtering out empty keys
  const kvData: Record<string, string> = {}
  kvEntries.value.forEach(entry => {
    if (entry.key.trim()) {
      kvData[entry.key] = entry.value
    }
  })
  data.value = kvData
  emit('data-change', kvData)
}
</script>

<style scoped>
.universal-config-card {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 2rem;
  margin-top: 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

h3 {
  margin-top: 0;
  margin-bottom: 1.5rem;
  color: #333;
}

.jsonforms-container {
  margin-bottom: 2rem;
}

.config-output {
  border-top: 1px solid #eee;
  padding-top: 1.5rem;
}

.config-output h4 {
  margin-top: 0;
  margin-bottom: 1rem;
  color: #666;
  font-size: 0.9rem;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

pre {
  background: #f5f5f5;
  padding: 1rem;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 0.9rem;
  line-height: 1.4;
}

/* JSONForms vanilla renderer styles */
:deep(.control) {
  margin-bottom: 1rem;
}

:deep(.control label) {
  display: block;
  margin-bottom: 0.25rem;
  font-weight: 500;
  color: #333;
}

:deep(.control input),
:deep(.control select),
:deep(.control textarea) {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

:deep(.control input:focus),
:deep(.control select:focus),
:deep(.control textarea:focus) {
  outline: none;
  border-color: #4a90e2;
}

:deep(.description) {
  font-size: 0.875rem;
  color: #666;
  margin-top: 0.25rem;
}

:deep(.error) {
  color: #c00;
  font-size: 0.875rem;
  margin-top: 0.25rem;
}

/* Key/Value editor styles */
.kv-editor {
  margin-bottom: 2rem;
}

.kv-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.no-schema-message {
  margin: 0;
  color: #666;
  font-style: italic;
}

.add-button {
  padding: 0.5rem 1rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.add-button:hover {
  background: #357abd;
}

.kv-grid {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.kv-row {
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 1rem;
  align-items: center;
}

.kv-row.header {
  font-weight: 600;
  color: #666;
  font-size: 0.9rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #eee;
}

.kv-input {
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.kv-input:focus {
  outline: none;
  border-color: #4a90e2;
}

.remove-button {
  width: 32px;
  height: 32px;
  border: 1px solid #ddd;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  font-size: 1.2rem;
  color: #999;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-button:hover {
  background: #fee;
  border-color: #fcc;
  color: #c00;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #999;
  font-style: italic;
}
</style>
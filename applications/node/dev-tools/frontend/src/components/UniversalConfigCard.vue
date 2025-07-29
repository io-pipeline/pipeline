<template>
  <v-card>
    <v-card-title class="d-flex justify-space-between align-center">
      <span>Configuration</span>
      <v-btn
        v-if="showJson"
        @click="showJson = false"
        icon="mdi-eye-off"
        size="small"
        variant="text"
        title="Hide JSON"
      />
      <v-btn
        v-else
        @click="showJson = true"
        icon="mdi-code-json"
        size="small"
        variant="text"
        title="Show JSON"
      />
    </v-card-title>
    
    <v-divider />
    
    <v-card-text>
      <!-- Schema-based rendering with JSONForms -->
      <div v-if="schema && !showJson" class="jsonforms-container compact-config">
        <JsonForms
          :data="data"
          :schema="schema"
          :uischema="uischema"
          :renderers="renderers"
          @change="handleChange"
        />
      </div>
    
      <!-- Fallback key/value editor when no schema -->
      <div v-else-if="!schema && !showJson" class="kv-editor">
      <div class="kv-header">
        <p class="no-schema-message">No schema provided - using key/value editor</p>
        <v-btn @click="addRow" color="primary" size="small">+ Add Entry</v-btn>
      </div>
      
      <div class="kv-grid">
        <div class="kv-row header">
          <div class="kv-key">Key</div>
          <div class="kv-value">Value</div>
          <div class="kv-actions"></div>
        </div>
        
        <div v-for="(entry, index) in kvEntries" :key="index" class="kv-row">
          <v-text-field
            v-model="entry.key" 
            @update:modelValue="updateKvData"
            placeholder="Enter key"
            density="compact"
            variant="outlined"
          />
          <v-text-field
            v-model="entry.value" 
            @update:modelValue="updateKvData"
            placeholder="Enter value"
            density="compact"
            variant="outlined"
          />
          <v-btn @click="removeRow(index)" icon size="small" variant="text">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </div>
      </div>
      
      <div v-if="kvEntries.length === 0" class="empty-state">
        No configuration entries. Click "Add Entry" to start.
      </div>
    </div>
    
      <!-- JSON View -->
      <v-expand-transition>
        <div v-if="showJson">
          <v-sheet 
            :color="$vuetify.theme.current.dark ? 'grey-darken-3' : 'grey-lighten-4'" 
            rounded 
            class="pa-3"
          >
            <pre class="config-json">{{ JSON.stringify(data, null, 2) }}</pre>
          </v-sheet>
        </div>
      </v-expand-transition>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'
import { generateUISchema, generateCategorizedUISchema } from '../utils/uiSchemaGenerator'
import { customRenderers } from '../renderers'

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
const showJson = ref(false)
// Use Vuetify renderers, then custom renderers, then vanilla fallbacks
const renderers = Object.freeze([...vuetifyRenderers, ...customRenderers, ...vanillaRenderers])
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

// Generate an enhanced UI schema
const uischema = computed(() => {
  if (!props.schema || !props.schema.properties) {
    return undefined
  }

  // Use our enhanced UI schema generator
  return generateUISchema(props.schema)
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
/* Compact JSONForms styling for Vuetify renderers */
.jsonforms-container :deep(.v-input) {
  margin-bottom: 0.5rem;
}

.jsonforms-container :deep(.v-messages) {
  min-height: auto;
}

/* Reduce padding on input fields */
.jsonforms-container :deep(.v-field__input) {
  padding-top: 6px;
  padding-bottom: 6px;
}

/* Tighten up text field density */
.jsonforms-container :deep(.v-text-field .v-field) {
  --v-field-padding-top: 4px;
  --v-field-padding-bottom: 4px;
}

/* Reduce gap between label and input */
.jsonforms-container :deep(.v-label) {
  margin-bottom: 2px;
}

/* Compact switches */
.jsonforms-container :deep(.v-switch) {
  margin-top: 0;
  margin-bottom: 0;
}

.jsonforms-container :deep(.v-switch .v-label) {
  margin-bottom: 0;
}

/* Compact config - reduce Vuetify layout spacing */
.compact-config :deep(.v-container) {
  padding: 0 !important;
}

.compact-config :deep(.v-row) {
  margin: 0 !important;
}

.compact-config :deep(.v-row + .v-row) {
  margin-top: 8px !important;
}

.compact-config :deep(.v-col) {
  padding: 0 !important;
}

/* Compact horizontal layout spacing */
.compact-config :deep(.horizontal-layout) {
  gap: 0.5rem;
}

/* Reduce spacing between form controls */
.compact-config :deep(.v-input + .v-input) {
  margin-top: 8px;
}

/* Vuetify will handle most control styling */
:deep(.control) {
  flex: 1 1 calc(50% - 1rem);
  min-width: 280px;
}

/* Custom renderer sizing */
:deep(.boolean-toggle-control) {
  flex: 0 1 calc(33.333% - 1.333rem);
  min-width: 220px;
  max-width: 350px;
}

:deep(.array-list-control) {
  flex: 1 1 100%;
  min-width: 100%;
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

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #999;
  font-style: italic;
}

/* Let Vuetify handle the rest of the styling */
</style>
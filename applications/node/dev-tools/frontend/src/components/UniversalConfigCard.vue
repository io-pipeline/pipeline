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
const renderers = Object.freeze([...customRenderers, ...vanillaRenderers])
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
.universal-config-card {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 12px;
  padding: 2rem;
  margin-top: 2rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

h3 {
  margin-top: 0;
  margin-bottom: 1.5rem;
  color: #333;
}

.jsonforms-container {
  margin-bottom: 2rem;
}

/* Debug: Add border to see structure */
:deep(.group > *) {
  /* border: 1px dashed red; */
}

/* Force any immediate child of group to be flex container */
:deep(.group > :first-child:not(.group-label)) {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 1rem 2rem;
  width: 100%;
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

/* JSONForms vanilla renderer styles - enhanced input design */
:deep(.control) {
  margin-bottom: 1rem;
  position: relative;
}

/* Material-UI style for TEXT inputs only */
:deep(.control:has(input[type="text"]),
      .control:has(select),
      .control:has(textarea)) {
  position: relative;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 1.25rem 1rem 0.75rem;
  transition: all 0.2s ease;
}

:deep(.control:has(input[type="text"]):hover,
      .control:has(select):hover,
      .control:has(textarea):hover) {
  border-color: #c0c0c0;
}

/* Different style for NUMBER inputs - integrated box design */
:deep(.control:has(input[type="number"])) {
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 0;
  overflow: hidden;
  transition: all 0.2s ease;
}

:deep(.control:has(input[type="number"]):hover) {
  border-color: #d0d0d0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

/* Label styling - floating for text, integrated for numbers */
:deep(.control:has(input[type="text"]) label,
      .control:has(select) label,
      .control:has(textarea) label) {
  display: block;
  position: absolute;
  top: 0.75rem;
  left: 1rem;
  font-size: 0.75rem;
  font-weight: 500;
  color: #666;
  background: white;
  padding: 0 0.25rem;
  margin: 0;
  transition: all 0.2s ease;
  pointer-events: none;
}

/* Number input label - header style */
:deep(.control:has(input[type="number"]) label) {
  display: block;
  padding: 0.75rem 1rem 0.5rem;
  font-weight: 600;
  color: #2c3e50;
  font-size: 0.875rem;
  background: #f8f9fa;
  border-bottom: 1px solid #e0e0e0;
  margin: 0;
}

/* Text input styling with underline */
:deep(.control input[type="text"]),
:deep(.control select),
:deep(.control textarea) {
  width: 100%;
  padding: 0.5rem 0;
  border: none;
  border-bottom: 1px solid #e0e0e0;
  font-size: 1rem;
  background: transparent;
  color: #333;
  transition: border-color 0.2s ease;
}

/* Number input styling - no underline */
:deep(.control input[type="number"]) {
  width: 100%;
  padding: 0.75rem 1rem;
  border: none;
  font-size: 0.95rem;
  background: transparent;
  color: #333;
}

/* Focus states for text inputs */
:deep(.control:has(input[type="text"]:focus),
      .control:has(select:focus),
      .control:has(textarea:focus)) {
  border-color: #4a90e2;
}

:deep(.control:has(input[type="text"]:focus) label,
      .control:has(select:focus) label,
      .control:has(textarea:focus) label) {
  color: #4a90e2;
}

:deep(.control input[type="text"]:focus),
:deep(.control select:focus),
:deep(.control textarea:focus) {
  outline: none;
  border-bottom: 2px solid #4a90e2;
  padding-bottom: calc(0.5rem - 1px);
}

/* Focus states for number inputs */
:deep(.control:has(input[type="number"]:focus)) {
  border-color: #4a90e2;
  box-shadow: 0 0 0 3px rgba(74, 144, 226, 0.1);
}

:deep(.control input[type="number"]:focus) {
  outline: none;
}

/* Placeholder styling */
:deep(.control input::placeholder),
:deep(.control select::placeholder),
:deep(.control textarea::placeholder) {
  color: #999;
  opacity: 0;
  transition: opacity 0.2s ease;
}

:deep(.control input:focus::placeholder),
:deep(.control select:focus::placeholder),
:deep(.control textarea:focus::placeholder) {
  opacity: 1;
}

/* Target any div that's a direct child of group */
:deep(.group > div:first-child) {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 1rem 2rem;
  width: 100%;
}

/* Also target if JSONForms uses custom classes */
:deep(.group [class*="vertical"]),
:deep(.group [class*="layout"]) {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 1rem 2rem;
}

/* Horizontal layout styles */
:deep(.horizontal-layout) {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 1rem 2rem;
  width: 100%;
}

/* Description text */
:deep(.description) {
  font-size: 0.8125rem;
  color: #6c757d;
  padding: 0 1rem 0.75rem;
  line-height: 1.4;
  margin: 0;
}

/* Error messages */
:deep(.error) {
  color: #dc3545;
  font-size: 0.8125rem;
  padding: 0.5rem 1rem;
  margin: 0;
  background: #fee;
  border-top: 1px solid #fcc;
}

/* Number inputs - remove max-width restriction in new design */
:deep(.control:has(input[type="number"])) {
  /* Let it follow the grid layout */
}

/* Checkbox controls - don't apply the box style */
:deep(.control:has(input[type="checkbox"])) {
  background: none;
  border: none;
  padding: 0.5rem 0;
}

:deep(.control input[type="checkbox"]) {
  width: auto;
  margin-right: 0.5rem;
}

/* Group/Section styling - each section is a distinct card */
:deep(.group) {
  margin-bottom: 1.5rem;
  padding: 1.75rem;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 10px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
  transition: box-shadow 0.2s ease;
}

:deep(.group:hover) {
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.06);
}

:deep(.group-label) {
  font-size: 1.2rem;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 1.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 2px solid #f0f0f0;
  letter-spacing: -0.02em;
}

/* Force all elements inside groups to use flexbox layout */
:deep(.group) {
  & > * {
    display: flex !important;
    flex-wrap: wrap !important;
    gap: 1rem 2rem;
    
    & > * {
      margin-bottom: 0 !important;
    }
  }
}

/* Each control takes appropriate width based on type */
:deep(.control) {
  flex: 1 1 calc(50% - 1rem);
  min-width: 280px;
}

/* Boolean controls are compact - 3 per row */
:deep(.boolean-toggle-control) {
  flex: 0 1 calc(33.333% - 1.333rem);
  min-width: 220px;
  max-width: 350px;
}

/* Number inputs can be smaller */
:deep(.control:has(input[type="number"])) {
  flex: 0 1 calc(33.333% - 1.333rem);
  min-width: 180px;
  max-width: 300px;
}

/* Text inputs take half width */
:deep(.control:has(input[type="text"]),
      .control:has(select)) {
  flex: 1 1 calc(50% - 1rem);
  min-width: 280px;
}

/* Arrays and nested groups take full width */
:deep(.array-list-control),
:deep(.group .group) {
  flex: 1 1 100%;
  min-width: 100%;
  margin-left: 0;
  margin-right: 0;
}

/* Main container keeps groups in single column */
:deep(.jsonforms-container > *) {
  & > .group {
    margin-bottom: 0;
  }
}

/* Nested groups */
:deep(.group .group) {
  background: white;
  border-color: #e8e8e8;
  margin-bottom: 1rem;
  box-shadow: none;
  
  /* Nested group content should also be multi-column */
  & > * {
    display: flex !important;
    flex-wrap: wrap !important;
    gap: 1rem 2rem;
  }
}

:deep(.group .group .group-label) {
  font-size: 1rem;
  font-weight: 500;
  border-bottom-width: 1px;
}

/* Boolean controls should fit nicely in grid */
:deep(.boolean-toggle-control) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.boolean-toggle-control .toggle-button) {
  margin-bottom: 0.5rem;
}

/* Array controls */
:deep(.array-list) {
  margin-top: 0.5rem;
}

:deep(.array-list-item) {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding: 0.5rem;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

:deep(.array-list-item-content) {
  flex: 1;
}

:deep(.array-list-item-buttons) {
  display: flex;
  gap: 0.25rem;
}

:deep(.array-list-add) {
  margin-top: 0.5rem;
  padding: 0.5rem 1rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

:deep(.array-list-add:hover) {
  background: #357abd;
}

/* Categorization/Tabs */
:deep(.categorization) {
  margin-top: 1rem;
}

:deep(.categorization-tabs) {
  display: flex;
  gap: 1rem;
  border-bottom: 2px solid #e0e0e0;
  margin-bottom: 1.5rem;
}

:deep(.categorization-tab) {
  padding: 0.75rem 1.5rem;
  background: none;
  border: none;
  border-bottom: 3px solid transparent;
  color: #666;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
}

:deep(.categorization-tab:hover) {
  color: #333;
}

:deep(.categorization-tab.selected) {
  color: #4a90e2;
  border-bottom-color: #4a90e2;
  font-weight: 600;
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
<template>
  <div class="universal-config-card">
    <!-- Schema Info Header -->
    <div class="schema-info">
      <h3>OpenAPI 3.1 Auto-Generated Configuration</h3>
      <div class="schema-stats">
        <span class="stat">
          <strong>Properties:</strong> {{ schemaPropertyCount }}
        </span>
        <span class="stat">
          <strong>Source:</strong> {{ schemaSource }}
        </span>
        <span class="stat" v-if="schemaTitle">
          <strong>Schema:</strong> {{ schemaTitle }}
        </span>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>Loading configuration schema...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error">
      <h4>Schema Loading Error</h4>
      <p>{{ error }}</p>
      <button @click="loadSchema" class="retry-btn">Retry</button>
    </div>

    <!-- Main Config Form -->
    <div v-else-if="filteredSchema" class="config-form">
      <!-- Example Text Input -->
      <div class="text-input-section">
        <h4>Text to Process</h4>
        <div v-if="availableExamples.length > 0" class="example-selection">
          <label for="example-select">Sample Text:</label>
          <select 
            id="example-select" 
            v-model="selectedExampleIndex" 
            @change="selectExample"
            class="example-dropdown"
          >
            <option v-for="(example, index) in availableExamples" :key="index" :value="index">
              {{ getExampleTitle(example, index) }}
            </option>
          </select>
        </div>
        <div v-else class="no-examples-note">
          <p><em>Note: This module processes file uploads, not text input. Use the Demo Documents tab for testing.</em></p>
        </div>
        <textarea 
          v-model="inputText" 
          class="text-input"
          :placeholder="textPlaceholder"
          rows="6"
        ></textarea>
      </div>

      <!-- JSONForms Rendered Configuration -->
      <div class="json-forms-container">
        <h4>Configuration</h4>
        <JsonForms
          :schema="filteredSchema"
          :data="formData"
          :renderers="renderers"
          @change="handleFormChange"
        />
      </div>

      <!-- Form Actions -->
      <div class="form-actions">
        <button 
          @click="submitForm" 
          :disabled="submitting || !inputText.trim()" 
          class="submit-btn"
        >
          {{ submitting ? 'Processing...' : submitButtonText }}
        </button>
        <button @click="resetForm" class="reset-btn">Reset</button>
      </div>

      <!-- Results Display -->
      <div v-if="result" class="result">
        <h4>Processing Results</h4>
        <div class="result-summary">
          <div class="result-stat">
            <span class="stat-label">Status:</span>
            <span :class="['stat-value', result.success ? 'success' : 'error']">
              {{ result.success ? 'Success' : 'Failed' }}
            </span>
          </div>
          <div class="result-stat" v-if="getResultMetrics().length > 0">
            <span class="stat-label">Results:</span>
            <span class="stat-value">{{ getResultSummary() }}</span>
          </div>
        </div>
        <details class="result-details">
          <summary>View Full Response</summary>
          <pre>{{ JSON.stringify(result, null, 2) }}</pre>
        </details>
      </div>
    </div>

    <!-- Debug Panel (Development Only) -->
    <details v-if="debugMode" class="debug-panel">
      <summary>Debug Information</summary>
      <div class="debug-content">
        <h5>Current Schema:</h5>
        <pre>{{ JSON.stringify(filteredSchema, null, 2) }}</pre>
        <h5>Form Data:</h5>
        <pre>{{ JSON.stringify(formData, null, 2) }}</pre>
        <h5>Request Payload:</h5>
        <pre>{{ JSON.stringify(getRequestPayload(), null, 2) }}</pre>
      </div>
    </details>
  </div>
</template>

<script>
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { markRaw } from 'vue'
import axios from 'axios'

export default {
  name: 'UniversalConfigCard',
  components: {
    JsonForms
  },
  props: {
    // Module configuration
    moduleName: {
      type: String,
      required: true
    },
    schemaEndpoint: {
      type: String,
      required: true
    },
    processingEndpoint: {
      type: String,
      required: true
    },
    
    // UI customization
    submitButtonText: {
      type: String,
      default: 'Process'
    },
    textPlaceholder: {
      type: String,
      default: 'Enter text to process...'
    },
    
    // Configuration
    debugMode: {
      type: Boolean,
      default: false
    }
  },
  emits: ['result', 'error', 'form-change'],
  data() {
    return {
      // Schema and form state
      loading: true,
      error: null,
      schema: null,
      filteredSchema: null,
      formData: {},
      
      // Form interaction
      inputText: '',
      submitting: false,
      result: null,
      
      // Examples handling
      availableExamples: [],
      selectedExampleIndex: 0,
      
      // JSONForms configuration
      renderers: markRaw(vanillaRenderers)
    }
  },
  computed: {
    schemaPropertyCount() {
      return this.filteredSchema?.properties ? Object.keys(this.filteredSchema.properties).length : 0
    },
    schemaSource() {
      return `${this.moduleName}Config.java annotations`
    },
    schemaTitle() {
      return this.schema?.title || this.schema?.name
    }
  },
  async mounted() {
    await this.loadSchema()
  },
  methods: {
    async loadSchema() {
      this.loading = true
      this.error = null
      
      try {
        console.log(`Loading schema from ${this.schemaEndpoint}`)
        const response = await axios.get(this.schemaEndpoint, {
          timeout: 10000
        })
        
        this.schema = response.data
        this.filteredSchema = this.filterSchemaForForm(this.schema)
        
        // Extract examples from schema - handle both text examples and config examples
        const allExamples = this.schema.examples || []
        console.log('Raw examples from schema:', allExamples)
        
        // Filter for text examples only (strings), ignore config object examples
        const textExamples = allExamples.filter(example => typeof example === 'string')
        console.log('Filtered text examples:', textExamples)
        
        this.availableExamples = textExamples
        
        if (this.availableExamples.length > 0) {
          this.inputText = this.availableExamples[0]
        } else {
          // Provide default text if no text examples available
          this.inputText = `Sample text to process with ${this.moduleName}...`
        }
        
        // Initialize form data based on schema defaults
        this.initializeFormData()
        
        console.log(`Schema loaded successfully: ${this.schemaPropertyCount} properties`)
        
      } catch (err) {
        console.error('Schema loading error:', err)
        if (err.code === 'ECONNABORTED') {
          this.error = `Schema loading timed out. Check if ${this.moduleName} service is running.`
        } else if (err.response) {
          this.error = `HTTP ${err.response.status}: ${err.response.statusText}`
        } else if (err.request) {
          this.error = `Network error: Could not reach ${this.schemaEndpoint}`
        } else {
          this.error = `Error: ${err.message}`
        }
      } finally {
        this.loading = false
      }
    },
    
    filterSchemaForForm(schema) {
      if (!schema || !schema.properties) return schema
      
      const filteredProperties = {}
      
      // Only include user-configurable fields (not readOnly or x-hidden)
      for (const [key, prop] of Object.entries(schema.properties)) {
        if (prop['x-hidden'] !== true && prop['x-hidden'] !== 'true' && prop.readOnly !== true) {
          filteredProperties[key] = prop
        }
      }
      
      return {
        ...schema,
        properties: filteredProperties,
        required: schema.required?.filter(field => filteredProperties[field]) || []
      }
    },
    
    initializeFormData() {
      const data = {}
      
      if (this.filteredSchema?.properties) {
        for (const [key, prop] of Object.entries(this.filteredSchema.properties)) {
          // Set default values based on schema
          if (prop.default !== undefined) {
            data[key] = prop.default
          } else if (prop.type === 'boolean') {
            data[key] = false
          } else if (prop.type === 'object') {
            data[key] = this.initializeNestedObject(prop)
          } else if (prop.type === 'array') {
            data[key] = []
          } else if (prop.type === 'number' || prop.type === 'integer') {
            data[key] = 0
          } else {
            data[key] = ''  
          }
        }
      }
      
      this.formData = data
      console.log('Initialized form data:', this.formData)
    },
    
    initializeNestedObject(prop) {
      const obj = {}
      
      if (prop.properties) {
        for (const [nestedKey, nestedProp] of Object.entries(prop.properties)) {
          if (nestedProp.default !== undefined) {
            obj[nestedKey] = nestedProp.default
          } else if (nestedProp.type === 'boolean') {
            obj[nestedKey] = false
          } else if (nestedProp.type === 'number' || nestedProp.type === 'integer') {
            obj[nestedKey] = 0
          } else {
            obj[nestedKey] = ''
          }
        }
      }
      
      return obj
    },
    
    handleFormChange(event) {
      // Update form data and emit change event (avoid reactive loops)
      Object.assign(this.formData, event.data)
      this.$emit('form-change', { ...this.formData })
    },
    
    getRequestPayload() {
      return {
        text: this.inputText,
        config: {
          configId: `config-${Date.now()}`,
          ...this.formData
        }
      }
    },
    
    async submitForm() {
      try {
        this.submitting = true
        this.result = null
        this.error = null
        
        const payload = this.getRequestPayload()
        
        console.log(`Submitting to ${this.processingEndpoint}:`, payload)
        
        const response = await axios.post(this.processingEndpoint, payload, {
          headers: {
            'Content-Type': 'application/json'
          },
          timeout: 30000
        })
        
        this.result = response.data
        this.$emit('result', this.result)
        
        console.log('Processing completed successfully')
        
      } catch (err) {
        const error = `Processing failed: ${err.message}`
        this.error = error
        this.$emit('error', error)
        console.error('Submit error:', err)
      } finally {
        this.submitting = false
      }
    },
    
    resetForm() {
      this.initializeFormData()
      this.result = null
      this.error = null
      this.inputText = this.availableExamples.length > 0 ? this.availableExamples[0] : ''
      this.selectedExampleIndex = 0
    },
    
    // Example handling
    selectExample() {
      if (this.availableExamples.length > this.selectedExampleIndex) {
        this.inputText = this.availableExamples[this.selectedExampleIndex]
      }
    },
    
    getExampleTitle(example, index) {
      // Safety check - ensure example is a string
      if (typeof example !== 'string') {
        return `Example ${index + 1}: Configuration Object`
      }
      
      const firstLine = example.split('\n')[0].trim()
      if (firstLine.length > 50) {
        return `Example ${index + 1}: ${firstLine.substring(0, 47)}...`
      }
      return `Example ${index + 1}: ${firstLine}`
    },
    
    // Results processing
    getResultMetrics() {
      if (!this.result) return []
      
      const metrics = []
      
      // Common result patterns
      if (this.result.chunks) {
        metrics.push(`${this.result.chunks.length} chunks`)
      }
      if (this.result.outputDoc) {
        if (this.result.outputDoc.title) {
          metrics.push(`title extracted`)
        }
        if (this.result.outputDoc.customData?.fields) {
          metrics.push(`${Object.keys(this.result.outputDoc.customData.fields).length} metadata fields`)
        }
      }
      if (this.result.metadata?.processingTimeMs) {
        metrics.push(`${this.result.metadata.processingTimeMs}ms`)
      }
      
      return metrics
    },
    
    getResultSummary() {
      const metrics = this.getResultMetrics()
      return metrics.length > 0 ? metrics.join(', ') : 'Completed'
    }
  }
}
</script>

<style scoped>
.universal-config-card {
  max-width: 1000px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Schema Info Header */
.schema-info {
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.schema-info h3 {
  margin: 0 0 1rem 0;
  color: #1e293b;
  font-size: 1.1rem;
  font-weight: 600;
}

.schema-stats {
  display: flex;
  gap: 2rem;
  flex-wrap: wrap;
}

.stat {
  color: #64748b;
  font-size: 0.9rem;
}

.stat strong {
  color: #334155;
}

/* Loading State */
.loading {
  text-align: center;
  padding: 2rem;
  color: #64748b;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  margin: 0 auto 1rem;
  border: 3px solid #e2e8f0;
  border-top: 3px solid #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

/* Error State */
.error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 8px;
  padding: 1.5rem;
  color: #dc2626;
  margin-bottom: 1.5rem;
}

.error h4 {
  margin: 0 0 0.5rem 0;
  color: #991b1b;
}

.retry-btn {
  background: #dc2626;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
  margin-top: 1rem;
}

.retry-btn:hover {
  background: #b91c1c;
}

/* Text Input Section */
.text-input-section {
  margin-bottom: 1.5rem;
}

.text-input-section h4 {
  margin: 0 0 1rem 0;
  color: #1e293b;
  font-size: 1rem;
  font-weight: 600;
}

.example-selection {
  margin-bottom: 1rem;
}

.example-selection label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.example-dropdown {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  background: white;
}

.text-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.9rem;
  font-family: monospace;
  resize: vertical;
  min-height: 120px;
}

.text-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}

/* JSONForms Container */
.json-forms-container {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.json-forms-container h4 {
  margin: 0 0 1rem 0;
  color: #1e293b;
  font-size: 1rem;
  font-weight: 600;
}

/* Form Actions */
.form-actions {
  display: flex;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.submit-btn, .reset-btn {
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.9rem;
}

.submit-btn {
  background: #3b82f6;
  color: white;
  border: none;
}

.submit-btn:hover:not(:disabled) {
  background: #2563eb;
}

.submit-btn:disabled {
  background: #9ca3af;
  cursor: not-allowed;
}

.reset-btn {
  background: #f8fafc;
  color: #64748b;
  border: 1px solid #d1d5db;
}

.reset-btn:hover {
  background: #f1f5f9;
}

/* Results Display */
.result {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.result h4 {
  margin: 0 0 1rem 0;
  color: #166534;
}

.result-summary {
  display: flex;
  gap: 2rem;
  margin-bottom: 1rem;
  flex-wrap: wrap;
}

.result-stat {
  display: flex;
  gap: 0.5rem;
}

.stat-label {
  font-weight: 500;
  color: #374151;
}

.stat-value.success {
  color: #166534;
  font-weight: 600;
}

.stat-value.error {
  color: #dc2626;
  font-weight: 600;
}

.result-details summary {
  cursor: pointer;
  color: #166534;
  font-weight: 500;
  margin-bottom: 1rem;
}

.result-details pre {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 1rem;
  overflow-x: auto;
  font-size: 0.8rem;
  color: #374151;
}

/* Debug Panel */
.debug-panel {
  background: #fefce8;
  border: 1px solid #fde047;
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1.5rem;
}

.debug-panel summary {
  cursor: pointer;
  color: #a16207;
  font-weight: 500;
  margin-bottom: 1rem;
}

.debug-content h5 {
  margin: 1rem 0 0.5rem 0;
  color: #92400e;
}

.debug-content pre {
  background: #fffbeb;
  border: 1px solid #fed7aa;
  border-radius: 4px;
  padding: 0.75rem;
  overflow-x: auto;
  font-size: 0.75rem;
  color: #92400e;
}

/* No examples note */
.no-examples-note {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 1rem;
  margin-bottom: 1rem;
}

.no-examples-note p {
  margin: 0;
  color: #64748b;
  font-style: italic;
}
</style>
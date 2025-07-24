<template>
  <div class="parser-config-form">
    <header class="dashboard-header">
      <div class="header-content">
        <h1 class="dashboard-title">{{ moduleName }} Dashboard</h1>
        <div class="module-info">
          <span class="module-type">Pipeline Engine Module</span>
          <span class="module-status" :class="statusClass">{{ moduleStatus }}</span>
        </div>
      </div>
    </header>

    <div v-if="loading" class="loading">
      Loading schema...
    </div>

    <div v-else-if="error" class="error">
      <h3>Error</h3>
      <p>{{ error }}</p>
    </div>

    <div v-else>
      <!-- Navigation tabs -->
      <div class="tabs">
        <button
            v-for="tab in tabs"
            :key="tab.id"
            @click="activeTab = tab.id"
            :class="['tab-button', { active: activeTab === tab.id }]"
        >
          {{ tab.name }}
        </button>
      </div>

      <!-- Tab Content -->
      <div class="tab-content">
        <!-- Config Card Tab -->
        <div v-if="activeTab === 'config'" class="tab-pane">
          <UniversalConfigCard
            module-name="Parser"
            schema-endpoint="/api/parser/service/config"
            processing-endpoint="/api/parser/service/parse-json"
            submit-button-text="Parse Document"
            text-placeholder="Enter document content to parse with Apache Tika..."
            :debug-mode="false"
            @result="handleConfigResult"
            @error="handleConfigError"
            @form-change="handleConfigFormChange"
          />
        </div>

        <!-- Demo Documents Tab -->
        <div v-if="activeTab === 'demo'" class="tab-pane">
          <div class="row">
            <div class="col-md-4">
              <div class="card">
                <div class="card-header">
                  <h5>Available Demo Documents</h5>
                </div>
                <div class="card-body">
                  <div v-if="loadingDemoDocuments" class="text-center">
                    Loading documents...
                  </div>
                  <div v-else-if="demoDocuments.length === 0" class="text-muted">
                    No demo documents available
                  </div>
                  <div v-else class="list-group">
                    <button 
                      v-for="doc in demoDocuments" 
                      :key="doc.filename"
                      @click="selectDemoDocument(doc)"
                      :class="['list-group-item', 'list-group-item-action', { active: selectedDemoDocument?.filename === doc.filename }]"
                    >
                      <div class="d-flex w-100 justify-content-between">
                        <h6 class="mb-1">{{ doc.title }}</h6>
                        <small class="text-muted">{{ doc.content_type }}</small>
                      </div>
                      <p class="mb-1 small">{{ doc.description }}</p>
                      <small class="text-muted">{{ doc.file_size }} bytes • {{ doc.category }}</small>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-md-8">
              <div v-if="selectedDemoDocument" class="card mb-4">
                <div class="card-header d-flex justify-content-between align-items-center">
                  <h5>Document Preview</h5>
                  <button class="btn btn-sm btn-outline-primary" @click="parseSelectedDemo">
                    Parse This Document
                  </button>
                </div>
                <div class="card-body">
                  <div class="demo-document-metadata mb-3">
                    <div class="row">
                      <div class="col-md-6">
                        <strong>Filename:</strong> {{ selectedDemoDocument.filename }}<br>
                        <strong>File Size:</strong> {{ formatFileSize(selectedDemoDocument.file_size) }}<br>
                        <strong>Content Type:</strong> {{ selectedDemoDocument.content_type }}
                      </div>
                      <div class="col-md-6">
                        <strong>Language:</strong> {{ selectedDemoDocument.language }}<br>
                        <strong>Category:</strong> {{ selectedDemoDocument.category }}<br>
                        <strong>Recommended Parser:</strong> {{ selectedDemoDocument.recommended_parser || 'Auto-detect' }}
                      </div>
                    </div>
                  </div>
                  <div class="demo-document-content">
                    <strong>Preview:</strong>
                    <div class="content-preview">{{ selectedDemoDocument.preview }}</div>
                  </div>
                </div>
              </div>
              
              <div v-if="selectedDemoDocument" class="card">
                <div class="card-header">
                  <h5>Parsing Options</h5>
                </div>
                <div class="card-body">
                  <div class="form-check mb-3">
                    <input class="form-check-input" type="checkbox" id="useRecommended" v-model="useRecommendedSettings">
                    <label class="form-check-label" for="useRecommended">
                      Use recommended settings from document metadata
                    </label>
                  </div>
                  
                  <div v-if="!useRecommendedSettings" class="custom-options">
                    <div class="row">
                      <div class="col-md-4">
                        <div class="mb-3">
                          <label for="demoExtractMetadata" class="form-label">Extract Metadata</label>
                          <select class="form-select" id="demoExtractMetadata" v-model="demoParsingOptions.extractMetadata">
                            <option value="true">Enabled</option>
                            <option value="false">Disabled</option>
                          </select>
                        </div>
                      </div>
                      <div class="col-md-4">
                        <div class="mb-3">
                          <label for="demoDisableEmfParser" class="form-label">Disable EMF Parser</label>
                          <select class="form-select" id="demoDisableEmfParser" v-model="demoParsingOptions.disableEmfParser">
                            <option value="true">Enabled</option>
                            <option value="false">Disabled</option>
                          </select>
                        </div>
                      </div>
                      <div class="col-md-4">
                        <div class="mb-3">
                          <label for="demoContentHandlers" class="form-label">Content Handlers</label>
                          <select class="form-select" id="demoContentHandlers" v-model="demoParsingOptions.contentHandlers">
                            <option value="default">Default</option>
                            <option value="xml">XML Only</option>
                            <option value="text">Text Only</option>
                          </select>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Metadata Dashboard Tab -->
        <div v-if="activeTab === 'metadata'" class="tab-pane">
          <div class="row">
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>Performance Metrics</h5>
                </div>
                <div class="card-body">
                  <div v-if="performanceMetrics" class="metrics-grid">
                    <div class="metric">
                      <span class="metric-label">Total Documents Parsed:</span>
                      <span class="metric-value">{{ performanceMetrics.totalDocuments || 0 }}</span>
                    </div>
                    <div class="metric">
                      <span class="metric-label">Average Processing Time:</span>
                      <span class="metric-value">{{ performanceMetrics.avgProcessingTime || 0 }}ms</span>
                    </div>
                    <div class="metric">
                      <span class="metric-label">Success Rate:</span>
                      <span class="metric-value">{{ performanceMetrics.successRate || 0 }}%</span>
                    </div>
                    <div class="metric">
                      <span class="metric-label">Total Metadata Fields:</span>
                      <span class="metric-value">{{ performanceMetrics.totalMetadataFields || 0 }}</span>
                    </div>
                  </div>
                  <div v-else class="text-muted">
                    No performance data available. Start parsing documents to see metrics.
                  </div>
                </div>
              </div>
            </div>
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>Recent Activity</h5>
                </div>
                <div class="card-body">
                  <div v-if="recentActivity.length > 0" class="activity-list">
                    <div v-for="activity in recentActivity" :key="activity.id" class="activity-item">
                      <div class="activity-header">
                        <strong>{{ activity.action }}</strong>
                        <span class="activity-time">{{ formatTime(activity.timestamp) }}</span>
                      </div>
                      <div class="activity-details">{{ activity.details }}</div>
                    </div>
                  </div>
                  <div v-else class="text-muted">
                    No recent activity. Start using the parser to see activity logs.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { markRaw } from 'vue'
import axios from 'axios'
import UniversalConfigCard from '@pipeline/shared-ui/components/UniversalConfigCard.vue'

export default {
  name: 'ParserConfigForm',
  components: {
    JsonForms,
    UniversalConfigCard
  },
  data() {
    return {
      loading: true,
      error: null,
      schema: null,
      filteredSchema: null,
      formData: {
        extractMetadata: true,
        disableEmfParser: true,
        contentHandlers: 'default',
        outputFormat: 'structured'
      },
      renderers: markRaw(vanillaRenderers),
      submitting: false,
      result: null,
      sampleText: '',
      selectedExampleIndex: 0,
      availableExamples: [],
      uploadedFile: null,
      activeTab: 'config',
      tabs: [
        { id: 'config', name: 'Config Card' },
        { id: 'demo', name: 'Demo Documents' },
        { id: 'metadata', name: 'Metadata Dashboard' }
      ],
      moduleName: 'Loading...',
      moduleDescription: '',
      moduleStatus: 'Connected',
      statusClass: 'status-healthy',
      schemaLoaded: false,
      // Demo documents data
      demoDocuments: [],
      selectedDemoDocument: null,
      loadingDemoDocuments: false,
      useRecommendedSettings: true,
      demoParsingOptions: {
        extractMetadata: true,
        disableEmfParser: true,
        contentHandlers: 'default'
      },
      // Metadata dashboard data
      performanceMetrics: null,
      recentActivity: []
    }
  },
  async mounted() {
    await this.loadModuleInfo()
    await this.loadSchema()
    await this.loadDemoDocuments()
  },
  methods: {
    async loadModuleInfo() {
      try {
        console.log('Loading module info from /api/parser/service/info')
        const response = await axios.get('/api/parser/service/info')
        console.log('Module info loaded:', response.data)
        
        this.moduleName = response.data.displayName || response.data.name || 'Parser'
        this.moduleDescription = response.data.description || ''
        
      } catch (error) {
        console.error('Failed to load module info:', error)
        this.moduleName = 'Parser' // fallback
      }
    },

    async loadSchema() {
      console.log('loadSchema called, schemaLoaded:', this.schemaLoaded)
      // Prevent recursive calls
      if (this.schemaLoaded) return

      try {
        console.log('Setting loading = true')
        this.loading = true
        this.error = null

        // Add timeout and better error handling
        const response = await axios.get('/api/parser/service/config', {
          timeout: 5000  // 5 second timeout
        })
        console.log('API response received:', response.data)
        this.schema = response.data

        // Filter out readOnly and x-hidden fields for the form
        this.filteredSchema = this.filterSchemaForForm(this.schema)
        console.log('Filtered schema:', this.filteredSchema)

        // Extract examples from schema for generic dropdown
        this.availableExamples = this.schema.examples || []
        console.log('Available examples:', this.availableExamples.length)
        if (this.availableExamples.length > 0) {
          this.sampleText = this.availableExamples[0]
        }

        console.log('Schema loading completed successfully')

      } catch (err) {
        console.log('Full error object:', err)
        if (err.code === 'ECONNABORTED') {
          this.error = `Schema loading timed out after 5 seconds. API endpoint may be hanging.`
        } else if (err.response) {
          this.error = `HTTP ${err.response.status}: ${err.response.statusText} - ${err.response.data || 'No response data'}`
        } else if (err.request) {
          this.error = `Network error: No response received from /api/parser/service/config`
        } else {
          this.error = `Error: ${err.message}`
        }
        console.error('Schema loading error:', err)
      } finally {
        this.loading = false
        this.schemaLoaded = true
      }
    },

    filterSchemaForForm(schema) {
      if (!schema || !schema.properties) return schema

      const filteredProperties = {}

      // Only include user-configurable fields (not readOnly or x-hidden)
      for (const [key, prop] of Object.entries(schema.properties)) {
        if (prop['x-hidden'] !== 'true' && prop['x-hidden'] !== true && prop.readOnly !== true) {
          filteredProperties[key] = prop
        }
      }

      return {
        ...schema,
        properties: filteredProperties,
        required: schema.required?.filter(field => filteredProperties[field])
      }
    },

    handleFormChange(event) {
      // Simple assignment without reactive effects
      Object.assign(this.formData, event.data)
    },

    handleFileUpload(event) {
      const file = event.target.files[0]
      if (file) {
        this.uploadedFile = file
        console.log('File selected:', file.name, file.size, 'bytes')
      }
    },

    async submitForm() {
      try {
        this.submitting = true
        this.result = null
        this.error = null

        console.log('Submitting form with data:', this.formData)

        // Create pure JSON request matching OpenAPI schema
        const jsonRequest = {
          text: this.sampleText,
          config: {
            configId: `config-${Date.now()}`,
            parsingOptions: {
              extractMetadata: this.formData.extractMetadata !== false,
              maxContentLength: 1000000
            },
            advancedOptions: {
              disableEmfParser: this.formData.disableEmfParser !== false,
              enableGeoTopicParser: false
            },
            outputOptions: {
              contentHandlers: this.formData.contentHandlers || 'default',
              outputFormat: this.formData.outputFormat || 'structured'
            }
          }
        }
        
        console.log('Sending JSON request to /api/parser/service/parse-json')
        console.log('JSON data:', JSON.stringify(jsonRequest, null, 2))

        const response = await axios.post('/api/parser/service/parse-json', jsonRequest, {
          headers: {
            'Content-Type': 'application/json'
          }
        })

        console.log('Response received:', response.data)
        this.result = response.data
        
        // Update metrics and activity
        this.updatePerformanceMetrics(response.data)
        this.addActivity('Config Card Processing', `Processed text with pure JSON config using Tika parser`)

      } catch (err) {
        this.error = `Parsing failed: ${err.message}`
        console.error('Submission error:', err)
      } finally {
        this.submitting = false
      }
    },

    resetForm() {
      this.formData = {
        extractMetadata: true,
        disableEmfParser: true,
        contentHandlers: 'default',
        outputFormat: 'structured'
      }
      this.result = null
      this.error = null
      this.uploadedFile = null
    },

    // Generic methods for example handling
    selectExample() {
      if (this.availableExamples.length > this.selectedExampleIndex) {
        this.sampleText = this.availableExamples[this.selectedExampleIndex]
      }
    },

    getExampleTitle(example, index) {
      // Extract title from first line of example text
      const firstLine = example.split('\n')[0].trim()
      if (firstLine.length > 40) {
        return `Example ${index + 1}: ${firstLine.substring(0, 37)}...`
      }
      return `Example ${index + 1}: ${firstLine}`
    },

    getExamplePreview(text) {
      return text.length > 100 ? text.substring(0, 100) + '...' : text
    },
    
    // Demo documents methods
    async loadDemoDocuments() {
      try {
        this.loadingDemoDocuments = true
        const response = await axios.get('/api/parser/service/demo/documents')
        this.demoDocuments = response.data.documents || []
        console.log('Loaded demo documents:', this.demoDocuments.length)
      } catch (error) {
        console.error('Error loading demo documents:', error)
      } finally {
        this.loadingDemoDocuments = false
      }
    },
    
    selectDemoDocument(document) {
      this.selectedDemoDocument = document
      console.log('Selected demo document:', document.title)
      
      // Set recommended settings
      this.demoParsingOptions = {
        extractMetadata: document.recommended_extract_metadata !== false,
        disableEmfParser: document.recommended_disable_emf !== false,
        contentHandlers: document.recommended_content_handlers || 'default'
      }
    },

    async parseSelectedDemo() {
      if (!this.selectedDemoDocument) return
      
      try {
        this.submitting = true
        this.result = null
        this.error = null
        
        // Use recommended settings if enabled
        const options = this.useRecommendedSettings ? {
          extractMetadata: this.selectedDemoDocument.recommended_extract_metadata !== false,
          disableEmfParser: this.selectedDemoDocument.recommended_disable_emf !== false,
          contentHandlers: this.selectedDemoDocument.recommended_content_handlers || 'default'
        } : this.demoParsingOptions
        
        const formData = new URLSearchParams()
        formData.append('filename', this.selectedDemoDocument.filename)
        formData.append('extractMetadata', options.extractMetadata)
        formData.append('disableEmfParser', options.disableEmfParser)
        formData.append('contentHandlers', options.contentHandlers)
        
        console.log('Parsing demo document:', this.selectedDemoDocument.filename)
        console.log('Options:', options)
        
        const response = await axios.post('/api/parser/service/demo/parse', formData, {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        })
        
        this.result = response.data
        this.activeTab = 'config' // Switch to config tab to show results
        
        // Update metrics and activity
        this.updatePerformanceMetrics(response.data)
        this.addActivity('Demo Document Parsing', `Processed ${this.selectedDemoDocument.title}`)
        
      } catch (error) {
        this.error = `Demo parsing failed: ${error.message}`
        console.error('Demo parsing error:', error)
      } finally {
        this.submitting = false
      }
    },

    // Utility methods
    formatFileSize(bytes) {
      if (bytes === 0) return '0 Bytes'
      const k = 1024
      const sizes = ['Bytes', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    },

    formatTime(timestamp) {
      return new Date(timestamp).toLocaleTimeString()
    },

    updatePerformanceMetrics(result) {
      if (!this.performanceMetrics) {
        this.performanceMetrics = {
          totalDocuments: 0,
          avgProcessingTime: 0,
          successRate: 0,
          totalMetadataFields: 0
        }
      }
      
      this.performanceMetrics.totalDocuments++
      if (result.success) {
        this.performanceMetrics.successRate = ((this.performanceMetrics.successRate * (this.performanceMetrics.totalDocuments - 1)) + 100) / this.performanceMetrics.totalDocuments
      }
      
      // Add metadata field count if available
      if (result.outputDoc?.customData?.fields) {
        this.performanceMetrics.totalMetadataFields += Object.keys(result.outputDoc.customData.fields).length
      }
    },

    addActivity(action, details) {
      this.recentActivity.unshift({
        id: Date.now(),
        action,
        details,
        timestamp: new Date()
      })
      
      // Keep only last 10 activities
      if (this.recentActivity.length > 10) {
        this.recentActivity = this.recentActivity.slice(0, 10)
      }
    },

    // UniversalConfigCard event handlers
    handleConfigResult(result) {
      this.result = result
      this.updatePerformanceMetrics(result)
      this.addActivity('Universal Config Card', 'Processed document using schema-driven form')
    },

    handleConfigError(error) {
      this.error = error
      console.error('Config card error:', error)
    },

    handleConfigFormChange(formData) {
      console.log('Config form data changed:', formData)
      // Store the form data if needed for integration with other tabs (avoid reactive loops)
      Object.assign(this.formData, formData)
    }
  }
}
</script>

<style scoped>
/* Main container styling */
.parser-config-form {
  max-width: 1200px;
  margin: 0 auto;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
}

/* Header styling */
.dashboard-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 2rem;
  border-radius: 12px;
  margin-bottom: 2rem;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dashboard-title {
  margin: 0;
  font-size: 2rem;
  font-weight: 700;
}

.module-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.5rem;
}

.module-type {
  font-size: 0.9rem;
  opacity: 0.9;
}

.module-status {
  padding: 0.25rem 0.75rem;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
}

.status-healthy {
  background-color: rgba(34, 197, 94, 0.2);
  color: #16a34a;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

/* Loading and error states */
.loading, .error {
  text-align: center;
  padding: 2rem;
  border-radius: 8px;
  margin: 1rem 0;
}

.loading {
  background-color: #f3f4f6;
  color: #6b7280;
}

.error {
  background-color: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

/* Tab navigation */
.tabs {
  display: flex;
  border-bottom: 2px solid #e5e7eb;
  margin-bottom: 2rem;
}

.tab-button {
  padding: 0.75rem 1.5rem;
  border: none;
  background: none;
  font-size: 1rem;
  font-weight: 500;
  color: #6b7280;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  transition: all 0.2s;
}

.tab-button:hover {
  color: #4f46e5;
  background-color: #f8fafc;
}

.tab-button.active {
  color: #4f46e5;
  border-bottom-color: #4f46e5;
  background-color: #f8fafc;
}

/* Schema info */
.schema-info {
  background-color: #f8fafc;
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  border-left: 4px solid #4f46e5;
}

.schema-info h3 {
  margin: 0 0 1rem 0;
  color: #1f2937;
}

.schema-info p {
  margin: 0.5rem 0;
  color: #6b7280;
}

/* Example selection */
.example-selection {
  background-color: #f9fafb;
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  border: 1px solid #e5e7eb;
}

.example-selection h4 {
  margin: 0 0 1rem 0;
  color: #1f2937;
}

.example-dropdown {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  background-color: white;
  margin-bottom: 1rem;
}

.example-preview {
  font-size: 0.875rem;
  color: #6b7280;
  background-color: white;
  padding: 1rem;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

/* File upload section */
.file-upload-section {
  background-color: #f0f9ff;
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
  border: 2px dashed #0ea5e9;
}

.file-upload-section h4 {
  margin: 0 0 1rem 0;
  color: #0c4a6e;
}

.file-input {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #0ea5e9;
  border-radius: 6px;
  background-color: white;
}

.file-preview {
  margin-top: 1rem;
  font-size: 0.875rem;
  color: #0c4a6e;
  background-color: white;
  padding: 0.75rem;
  border-radius: 6px;
  border: 1px solid #bae6fd;
}

/* Form container */
.form-container {
  background-color: white;
  padding: 2rem;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 2rem;
}

/* Form actions */
.form-actions {
  display: flex;
  gap: 1rem;
  justify-content: flex-start;
  margin-bottom: 2rem;
}

.submit-btn, .reset-btn {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 6px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.submit-btn {
  background-color: #4f46e5;
  color: white;
}

.submit-btn:hover:not(:disabled) {
  background-color: #4338ca;
}

.submit-btn:disabled {
  background-color: #9ca3af;
  cursor: not-allowed;
}

.reset-btn {
  background-color: #f3f4f6;
  color: #6b7280;
  border: 1px solid #d1d5db;
}

.reset-btn:hover {
  background-color: #e5e7eb;
}

/* Results section */
.result {
  background-color: #f0fdf4;
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid #bbf7d0;
}

.result h3 {
  margin: 0 0 1rem 0;
  color: #166534;
}

.result-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}

.result-stats p {
  margin: 0;
  padding: 0.75rem;
  background-color: white;
  border-radius: 6px;
  border: 1px solid #dcfce7;
  font-size: 0.875rem;
}

/* Demo documents and metadata dashboard styles */
.row {
  display: flex;
  gap: 1rem;
  margin: -0.5rem;
}

.col-md-4, .col-md-6, .col-md-8 {
  flex: 1;
  padding: 0.5rem;
}

.col-md-4 {
  flex: 0 0 33.333333%;
}

.col-md-6 {
  flex: 0 0 50%;
}

.col-md-8 {
  flex: 0 0 66.666667%;
}

.card {
  background-color: white;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 1rem;
}

.card-header {
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  background-color: #f9fafb;
  border-radius: 8px 8px 0 0;
}

.card-header h5 {
  margin: 0;
  color: #1f2937;
  font-weight: 600;
}

.card-body {
  padding: 1.5rem;
}

.list-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.list-group-item {
  padding: 1rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background-color: white;
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}

.list-group-item:hover {
  background-color: #f3f4f6;
}

.list-group-item.active {
  background-color: #eff6ff;
  border-color: #3b82f6;
}

.d-flex {
  display: flex;
}

.w-100 {
  width: 100%;
}

.justify-content-between {
  justify-content: space-between;
}

.mb-1 {
  margin-bottom: 0.25rem;
}

.mb-3 {
  margin-bottom: 1rem;
}

.small {
  font-size: 0.875rem;
}

.text-muted {
  color: #6b7280;
}

.text-center {
  text-align: center;
}

.btn {
  padding: 0.5rem 1rem;
  border: 1px solid transparent;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
  transition: all 0.2s;
}

.btn-sm {
  padding: 0.25rem 0.75rem;
  font-size: 0.875rem;
}

.btn-outline-primary {
  color: #3b82f6;
  border-color: #3b82f6;
  background-color: transparent;
}

.btn-outline-primary:hover {
  color: white;
  background-color: #3b82f6;
}

.form-check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.form-check-input {
  margin: 0;
}

.form-check-label {
  margin: 0;
  font-weight: 500;
  color: #374151;
}

.form-label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #374151;
}

.form-select {
  width: 100%;
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background-color: white;
  font-size: 0.875rem;
}

.custom-options {
  margin-top: 1rem;
}

.content-preview {
  background-color: #f9fafb;
  padding: 1rem;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  font-family: monospace;
  font-size: 0.875rem;
  color: #374151;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}

.metric {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  background-color: #f9fafb;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.metric-label {
  font-size: 0.875rem;
  color: #6b7280;
}

.metric-value {
  font-weight: 600;
  color: #1f2937;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.activity-item {
  padding: 1rem;
  background-color: #f9fafb;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.activity-time {
  font-size: 0.75rem;
  color: #9ca3af;
}

.activity-details {
  font-size: 0.875rem;
  color: #6b7280;
}
</style>
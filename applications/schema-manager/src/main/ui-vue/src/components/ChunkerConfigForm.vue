<template>
  <div class="chunker-config-form">
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
            module-name="Chunker"
            schema-endpoint="/api/chunker/service/config"
            processing-endpoint="/api/chunker/service/process-json"
            submit-button-text="Chunk Text"
            text-placeholder="Enter text to chunk into smaller pieces..."
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
                        <small class="text-muted">{{ doc.estimated_chunks }} chunks</small>
                      </div>
                      <p class="mb-1 small">{{ doc.description }}</p>
                      <small class="text-muted">by {{ doc.author }} • {{ doc.category }}</small>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="col-md-8">
              <div v-if="selectedDemoDocument" class="card mb-4">
                <div class="card-header d-flex justify-content-between align-items-center">
                  <h5>Document Preview</h5>
                  <button class="btn btn-sm btn-outline-primary" @click="chunkSelectedDemo">
                    Chunk This Document
                  </button>
                </div>
                <div class="card-body">
                  <div class="demo-document-metadata mb-3">
                    <div class="row">
                      <div class="col-md-6">
                        <strong>Filename:</strong> {{ selectedDemoDocument.filename }}<br>
                        <strong>File Size:</strong> {{ formatFileSize(selectedDemoDocument.file_size) }}<br>
                        <strong>Recommended Algorithm:</strong> {{ selectedDemoDocument.recommended_algorithm }}
                      </div>
                      <div class="col-md-6">
                        <strong>Recommended Chunk Size:</strong> {{ selectedDemoDocument.recommended_chunk_size }}<br>
                        <strong>Estimated Chunks:</strong> {{ selectedDemoDocument.estimated_chunks }}<br>
                        <strong>Category:</strong> {{ selectedDemoDocument.category }}
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
                  <h5>Chunking Options</h5>
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
                          <label for="demoAlgorithm" class="form-label">Algorithm</label>
                          <select class="form-select" id="demoAlgorithm" v-model="demoChunkingOptions.algorithm">
                            <option value="character">Character</option>
                            <option value="token">Token</option>
                            <option value="sentence">Sentence</option>
                            <option value="semantic">Semantic</option>
                          </select>
                        </div>
                      </div>
                      <div class="col-md-4">
                        <div class="mb-3">
                          <label for="demoChunkSize" class="form-label">Chunk Size</label>
                          <input type="number" class="form-control" id="demoChunkSize" 
                                 v-model="demoChunkingOptions.chunkSize" min="50" max="10000">
                        </div>
                      </div>
                      <div class="col-md-4">
                        <div class="mb-3">
                          <label for="demoChunkOverlap" class="form-label">Overlap</label>
                          <input type="number" class="form-control" id="demoChunkOverlap" 
                                 v-model="demoChunkingOptions.chunkOverlap" min="0" max="5000">
                        </div>
                      </div>
                    </div>
                    
                    <div class="form-check">
                      <input class="form-check-input" type="checkbox" id="demoPreserveUrls" v-model="demoChunkingOptions.preserveUrls">
                      <label class="form-check-label" for="demoPreserveUrls">
                        Preserve URLs
                      </label>
                    </div>
                  </div>
                  
                  <button type="button" class="btn btn-primary mt-3" @click="chunkDemoDocument" :disabled="submitting">
                    {{ submitting ? 'Processing...' : 'Process Document' }}
                  </button>
                </div>
              </div>
              
              <!-- Demo Results -->
              <div v-if="result" class="card">
                <div class="card-header">
                  <h5>Chunking Results</h5>
                </div>
                <div class="card-body">
                  <div class="result-stats mb-3">
                    <div class="row">
                      <div class="col-md-4">
                        <strong>Chunks Generated:</strong><br>
                        <span class="badge bg-success">{{ result.chunks?.length || 0 }}</span>
                      </div>
                      <div class="col-md-4">
                        <strong>Processing Time:</strong><br>
                        <span class="badge bg-info">{{ result.metadata?.processingTimeMs || 0 }}ms</span>
                      </div>
                      <div class="col-md-4">
                        <strong>Success:</strong><br>
                        <span :class="['badge', result.success ? 'bg-success' : 'bg-danger']">
                          {{ result.success ? 'Yes' : 'No' }}
                        </span>
                      </div>
                    </div>
                  </div>
                  <details>
                    <summary class="btn btn-outline-secondary btn-sm">View Full Response</summary>
                    <pre class="mt-3">{{ JSON.stringify(result, null, 2) }}</pre>
                  </details>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Metadata Dashboard Tab -->
        <div v-if="activeTab === 'metadata'" class="tab-pane">
          <div class="row">
            <!-- System Status Card -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>System Status</h5>
                </div>
                <div class="card-body">
                  <div class="status-grid">
                    <div class="status-item">
                      <div class="status-label">Module Status</div>
                      <div :class="['status-value', statusClass]">{{ moduleStatus }}</div>
                    </div>
                    <div class="status-item">
                      <div class="status-label">Schema Status</div>
                      <div :class="['status-value', schemaLoaded ? 'status-healthy' : 'status-error']">
                        {{ schemaLoaded ? 'Loaded' : 'Loading' }}
                      </div>
                    </div>
                    <div class="status-item">
                      <div class="status-label">Demo Documents</div>
                      <div class="status-value status-healthy">{{ demoDocuments.length }} Available</div>
                    </div>
                    <div class="status-item">
                      <div class="status-label">Configuration</div>
                      <div class="status-value status-healthy">{{ filteredSchema ? Object.keys(filteredSchema.properties || {}).length : 0 }} Properties</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Performance Metrics Card -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>Performance Metrics</h5>
                </div>
                <div class="card-body">
                  <div v-if="performanceMetrics">
                    <div class="metric-item">
                      <div class="metric-label">Last Processing Time</div>
                      <div class="metric-value">{{ performanceMetrics.lastProcessingTime || 'N/A' }}</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-label">Total Chunks Processed</div>
                      <div class="metric-value">{{ performanceMetrics.totalChunks || 0 }}</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-label">Average Chunk Size</div>
                      <div class="metric-value">{{ performanceMetrics.avgChunkSize || 'N/A' }}</div>
                    </div>
                    <div class="metric-item">
                      <div class="metric-label">Success Rate</div>
                      <div class="metric-value">{{ performanceMetrics.successRate || 'N/A' }}</div>
                    </div>
                  </div>
                  <div v-else class="text-muted">
                    No performance data available yet. Process some documents to see metrics.
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="row">
            <!-- Schema Information Card -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>OpenAPI Schema Details</h5>
                </div>
                <div class="card-body">
                  <div v-if="schema">
                    <div class="schema-detail">
                      <strong>Schema Type:</strong> {{ schema.type || 'object' }}
                    </div>
                    <div class="schema-detail">
                      <strong>Required Fields:</strong> {{ schema.required?.length || 0 }}
                    </div>
                    <div class="schema-detail">
                      <strong>Total Properties:</strong> {{ Object.keys(schema.properties || {}).length }}
                    </div>
                    <div class="schema-detail">
                      <strong>Examples Available:</strong> {{ schema.examples?.length || 0 }}
                    </div>
                    <div class="schema-detail">
                      <strong>Description:</strong> {{ schema.description || 'N/A' }}
                    </div>
                    <details class="mt-3">
                      <summary class="btn btn-outline-secondary btn-sm">View Full Schema</summary>
                      <pre class="mt-3">{{ JSON.stringify(schema, null, 2) }}</pre>
                    </details>
                  </div>
                  <div v-else class="text-muted">
                    Schema not loaded
                  </div>
                </div>
              </div>
            </div>

            <!-- Module Configuration Card -->
            <div class="col-md-6">
              <div class="card">
                <div class="card-header">
                  <h5>Module Configuration</h5>
                </div>
                <div class="card-body">
                  <div class="config-grid">
                    <div class="config-item">
                      <div class="config-label">Module Name</div>
                      <div class="config-value">{{ moduleName }}</div>
                    </div>
                    <div class="config-item">
                      <div class="config-label">Module Type</div>
                      <div class="config-value">Pipeline Engine Module</div>
                    </div>
                    <div class="config-item">
                      <div class="config-label">Port</div>
                      <div class="config-value">39102 (Development)</div>
                    </div>
                    <div class="config-item">
                      <div class="config-label">Framework</div>
                      <div class="config-value">Vue.js 3 + JSON Schema Forms</div>
                    </div>
                    <div class="config-item">
                      <div class="config-label">API Version</div>
                      <div class="config-value">OpenAPI 3.1</div>
                    </div>
                    <div class="config-item">
                      <div class="config-label">Build Tool</div>
                      <div class="config-value">Quinoa + Vite</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Recent Activity Card -->
          <div class="row">
            <div class="col-md-12">
              <div class="card">
                <div class="card-header">
                  <h5>Recent Activity</h5>
                </div>
                <div class="card-body">
                  <div v-if="recentActivity.length > 0">
                    <div class="activity-list">
                      <div v-for="(activity, index) in recentActivity" :key="index" class="activity-item">
                        <div class="activity-time">{{ activity.timestamp }}</div>
                        <div class="activity-description">{{ activity.description }}</div>
                        <div v-if="activity.details" class="activity-details">{{ activity.details }}</div>
                      </div>
                    </div>
                  </div>
                  <div v-else class="text-muted">
                    No recent activity. Start using the chunker to see activity logs.
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
import UniversalConfigCard from './UniversalConfigCard.vue'

export default {
  name: 'ChunkerConfigForm',
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
        algorithm: 'sentence',
        sourceField: 'body',
        chunkSize: 50,
        chunkOverlap: 10,
        preserveUrls: true,
        cleanText: true
      },
      renderers: markRaw(vanillaRenderers),
      submitting: false,
      result: null,
      sampleText: '',
      selectedExampleIndex: 0,
      availableExamples: [],
      activeTab: 'config',
      tabs: [
        { id: 'config', name: 'Config Card' },
        { id: 'demo', name: 'Demo Documents' },
        { id: 'metadata', name: 'Metadata Dashboard' }
      ],
      moduleName: 'Chunker',
      moduleStatus: 'Connected',
      statusClass: 'status-healthy',
      schemaLoaded: false,
      // Demo documents data
      demoDocuments: [],
      selectedDemoDocument: null,
      loadingDemoDocuments: false,
      useRecommendedSettings: true,
      demoChunkingOptions: {
        algorithm: 'token',
        chunkSize: 500,
        chunkOverlap: 50,
        preserveUrls: true
      },
      // Metadata dashboard data
      performanceMetrics: null,
      recentActivity: []
    }
  },
  async mounted() {
    await this.loadSchema()
    await this.loadDemoDocuments()
  },
  methods: {
    async loadSchema() {
      console.log('loadSchema called, schemaLoaded:', this.schemaLoaded)
      // Prevent recursive calls
      if (this.schemaLoaded) return

      try {
        console.log('Setting loading = true')
        this.loading = true
        this.error = null

        // Add timeout and better error handling
        const response = await axios.get('/api/chunker/service/config', {
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
          this.error = `Network error: No response received from /api/chunker/service/config`
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
            algorithm: this.formData.algorithm || 'token',
            sourceField: this.formData.sourceField || 'body',
            chunkSize: this.formData.chunkSize || 500,
            chunkOverlap: this.formData.chunkOverlap || 50,
            preserveUrls: this.formData.preserveUrls !== false,
            cleanText: this.formData.cleanText !== false
          }
        }

        console.log('Sending JSON request to /api/chunker/service/process-json')
        console.log('JSON data:', JSON.stringify(jsonRequest, null, 2))

        const response = await axios.post('/api/chunker/service/process-json', jsonRequest, {
          headers: {
            'Content-Type': 'application/json'
          }
        })

        console.log('Response received:', response.data)
        this.result = response.data
        
        // Update metrics and activity
        this.updatePerformanceMetrics(response.data)
        this.addActivity('Config Card Processing', `Processed text with pure JSON config using ${this.formData.algorithm} algorithm`)

      } catch (err) {
        this.error = `Chunking failed: ${err.message}`
        console.error('Submission error:', err)
      } finally {
        this.submitting = false
      }
    },

    resetForm() {
      this.formData = {
        algorithm: 'token',
        sourceField: 'body',
        chunkSize: 500,
        chunkOverlap: 50,
        preserveUrls: true,
        cleanText: true
      }
      this.result = null
      this.error = null
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
        const response = await axios.get('/api/chunker/service/demo/documents')
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
      this.demoChunkingOptions = {
        algorithm: document.recommended_algorithm || 'token',
        chunkSize: document.recommended_chunk_size || 500,
        chunkOverlap: Math.floor((document.recommended_chunk_size || 500) / 10),
        preserveUrls: true
      }
    },
    
    async chunkDemoDocument() {
      if (!this.selectedDemoDocument) {
        alert('Please select a demo document first')
        return
      }
      
      try {
        this.submitting = true
        this.result = null
        this.error = null
        
        const formData = new URLSearchParams()
        formData.append('useRecommended', this.useRecommendedSettings)
        formData.append('preserveUrls', this.demoChunkingOptions.preserveUrls)
        
        if (!this.useRecommendedSettings) {
          formData.append('algorithm', this.demoChunkingOptions.algorithm)
          formData.append('chunkSize', this.demoChunkingOptions.chunkSize)
          formData.append('chunkOverlap', this.demoChunkingOptions.chunkOverlap)
        }
        
        const response = await axios.post(`/api/chunker/service/demo/chunk/${this.selectedDemoDocument.filename}`, formData, {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          }
        })
        
        this.result = response.data
        console.log('Demo chunking completed:', response.data)
        
        // Update metrics and activity
        this.updatePerformanceMetrics(response.data)
        this.addActivity('Demo Document Chunking', `Processed "${this.selectedDemoDocument.title}" with ${this.useRecommendedSettings ? 'recommended' : 'custom'} settings`)
        
      } catch (error) {
        this.error = `Demo chunking failed: ${error.message}`
        console.error('Demo chunking error:', error)
      } finally {
        this.submitting = false
      }
    },
    
    chunkSelectedDemo() {
      this.useRecommendedSettings = true
      this.chunkDemoDocument()
    },
    
    formatFileSize(bytes) {
      if (bytes === 0) return '0 Bytes'
      const k = 1024
      const sizes = ['Bytes', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    },
    
    // Metadata dashboard methods
    updatePerformanceMetrics(result) {
      if (!result || !result.chunks) return
      
      const processingTime = result.metadata?.processingTimeMs || 0
      const chunksCount = result.chunks.length
      const avgChunkLength = chunksCount > 0 ? 
        Math.round(result.chunks.reduce((sum, chunk) => sum + (chunk.content?.length || 0), 0) / chunksCount) : 0
      
      if (!this.performanceMetrics) {
        this.performanceMetrics = {
          totalChunks: 0,
          totalProcessingTime: 0,
          processingSessions: 0,
          avgChunkSize: 0,
          successRate: '100%'
        }
      }
      
      this.performanceMetrics.lastProcessingTime = `${processingTime}ms`
      this.performanceMetrics.totalChunks += chunksCount
      this.performanceMetrics.totalProcessingTime += processingTime
      this.performanceMetrics.processingSessions += 1
      this.performanceMetrics.avgChunkSize = `${avgChunkLength} chars`
      
      // Calculate success rate (simplified - assuming all tracked sessions are successful)
      this.performanceMetrics.successRate = '100%'
    },
    
    addActivity(type, description) {
      const activity = {
        timestamp: new Date().toLocaleTimeString(),
        description: `${type}: ${description}`,
        details: this.result ? `${this.result.chunks?.length || 0} chunks generated` : null
      }
      
      this.recentActivity.unshift(activity)
      
      // Keep only last 10 activities
      if (this.recentActivity.length > 10) {
        this.recentActivity = this.recentActivity.slice(0, 10)
      }
    },

    // UniversalConfigCard event handlers
    handleConfigResult(result) {
      this.result = result
      this.updatePerformanceMetrics(result)
      this.addActivity('Universal Config Card', 'Processed text using schema-driven chunking form')
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
.chunker-config-form {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.dashboard-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 32px 40px;
  margin: -20px -20px 30px -20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
}

.dashboard-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.module-info {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  opacity: 0.9;
}

.module-type {
  background: rgba(255, 255, 255, 0.2);
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 500;
}

.module-status {
  padding: 4px 12px;
  border-radius: 12px;
  font-weight: 600;
  font-size: 13px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-healthy {
  background: #10b981;
  color: white;
}

.status-error {
  background: #ef4444;
  color: white;
}

.status-loading {
  background: #f59e0b;
  color: white;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e1e4e8;
  margin: 0 20px 20px 20px;
}

.tab-button {
  background: none;
  border: none;
  padding: 12px 24px;
  cursor: pointer;
  font-size: 14px;
  color: #656d76;
  border-bottom: 2px solid transparent;
  transition: all 0.2s;
}

.tab-button:hover {
  color: #24292e;
  background-color: #f6f8fa;
}

.tab-button.active {
  color: #0366d6;
  border-bottom-color: #0366d6;
  font-weight: 600;
}

.tab-content {
  min-height: 400px;
  padding: 0 20px 20px 20px;
}

.tab-pane {
  animation: fadeIn 0.3s ease-in;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.loading, .error {
  text-align: center;
  padding: 20px;
}

.error {
  color: #d73a49;
  background-color: #ffeaea;
  border: 1px solid #d73a49;
  border-radius: 4px;
}

.schema-info {
  background-color: #f6f8fa;
  padding: 15px;
  border-radius: 6px;
  margin-bottom: 20px;
}

.schema-info h3 {
  margin: 0 0 10px 0;
  color: #24292e;
}

.example-selection {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 6px;
  margin-bottom: 20px;
  border: 1px solid #e1e4e8;
}

.example-selection h4 {
  margin: 0 0 10px 0;
  color: #24292e;
  font-size: 16px;
}

.example-dropdown {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  background-color: white;
  font-size: 14px;
  margin-bottom: 10px;
}

.example-dropdown:focus {
  outline: none;
  border-color: #0366d6;
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.example-preview {
  font-size: 13px;
  color: #656d76;
  background-color: white;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #d0d7de;
}

.form-container {
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 20px;
  margin-bottom: 20px;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.submit-btn {
  background-color: #0366d6;
  color: white;
  border: none;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.submit-btn:hover:not(:disabled) {
  background-color: #0256cc;
}

.submit-btn:disabled {
  background-color: #94a3b8;
  cursor: not-allowed;
}

.reset-btn {
  background-color: #f6f8fa;
  color: #24292e;
  border: 1px solid #d0d7de;
  padding: 8px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.reset-btn:hover {
  background-color: #f3f4f6;
}

.result {
  border: 1px solid #28a745;
  border-radius: 6px;
  padding: 20px;
  background-color: #f0fff4;
}

.result h3 {
  margin: 0 0 15px 0;
  color: #28a745;
}

.result-stats {
  margin-bottom: 15px;
}

.result-stats p {
  margin: 5px 0;
}

details {
  margin-top: 15px;
}

pre {
  background-color: #f6f8fa;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
}

/* Demo Documents Styles */
.row {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -10px;
}

.col-md-4, .col-md-6, .col-md-8 {
  padding: 0 10px;
  flex: 1;
  min-width: 0;
}

.col-md-4 { flex: 0 0 33.333333%; }
.col-md-6 { flex: 0 0 50%; }
.col-md-8 { flex: 0 0 66.666667%; }

.card {
  background-color: white;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  margin-bottom: 20px;
}

.card-header {
  background-color: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  padding: 15px 20px;
  border-radius: 6px 6px 0 0;
}

.card-header h5 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.card-body {
  padding: 20px;
}

.list-group {
  border-radius: 6px;
  overflow: hidden;
}

.list-group-item {
  background: white;
  border: 1px solid #e1e4e8;
  border-bottom: none;
  padding: 12px 16px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.list-group-item:last-child {
  border-bottom: 1px solid #e1e4e8;
}

.list-group-item:hover {
  background-color: #f6f8fa;
}

.list-group-item.active {
  background-color: #0366d6;
  color: white;
  border-color: #0366d6;
}

.content-preview {
  background-color: #f6f8fa;
  padding: 10px;
  border-radius: 4px;
  font-family: Monaco, 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
}

.demo-document-metadata {
  background-color: #f8f9fa;
  padding: 15px;
  border-radius: 6px;
  border: 1px solid #e1e4e8;
}

.form-label {
  display: block;
  margin-bottom: 5px;
  font-weight: 600;
  color: #24292e;
}

.form-control, .form-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d0d7de;
  border-radius: 6px;
  font-size: 14px;
}

.form-control:focus, .form-select:focus {
  outline: none;
  border-color: #0366d6;
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.form-check {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.form-check-input {
  margin-right: 8px;
}

.btn {
  display: inline-block;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 6px;
  border: 1px solid;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.2s;
}

.btn-primary {
  background-color: #0366d6;
  border-color: #0366d6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background-color: #0256cc;
  border-color: #0256cc;
}

.btn-primary:disabled {
  background-color: #94a3b8;
  border-color: #94a3b8;
  cursor: not-allowed;
}

.btn-outline-primary {
  background-color: transparent;
  border-color: #0366d6;
  color: #0366d6;
}

.btn-outline-primary:hover {
  background-color: #0366d6;
  color: white;
}

.btn-sm {
  padding: 6px 12px;
  font-size: 12px;
}

.d-flex {
  display: flex;
}

.justify-content-between {
  justify-content: space-between;
}

.align-items-center {
  align-items: center;
}

.w-100 {
  width: 100%;
}

.mb-1, .mb-3 {
  margin-bottom: 0.75rem;
}

.mb-4 {
  margin-bottom: 1.5rem;
}

.mt-3 {
  margin-top: 1rem;
}

.text-center {
  text-align: center;
}

.text-muted {
  color: #656d76;
}

.small {
  font-size: 0.875em;
}

.badge {
  display: inline-block;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 600;
  border-radius: 12px;
  color: white;
}

.bg-success {
  background-color: #28a745;
}

.bg-info {
  background-color: #17a2b8;
}

.bg-danger {
  background-color: #dc3545;
}

.btn-outline-secondary {
  background-color: transparent;
  border-color: #6c757d;
  color: #6c757d;
}

.btn-outline-secondary:hover {
  background-color: #6c757d;
  color: white;
}
</style>
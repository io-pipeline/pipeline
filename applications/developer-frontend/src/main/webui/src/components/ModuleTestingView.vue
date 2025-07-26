<template>
  <div class="module-testing-view">
    <div class="testing-header">
      <div class="module-info">
        <h1>{{ moduleConfig.moduleType }} Module Testing</h1>
        <div class="connection-info">
          <span class="connection-badge">
            <span class="status-dot connected"></span>
            Connected to {{ connectionString }}
          </span>
        </div>
      </div>
      
      <div class="testing-actions">
        <button @click="goBackToConfig" class="btn btn-secondary">
          ← Back to Configuration
        </button>
        <button @click="testConnection" class="btn btn-primary" :disabled="isTestingConnection">
          {{ isTestingConnection ? 'Testing...' : 'Test Connection' }}
        </button>
      </div>
    </div>

    <div class="testing-content">
      <div class="config-card-container">
        <div class="card-header">
          <h2>Module Configuration & Testing</h2>
          <p>Configure and test the {{ moduleConfig.moduleType }} module with real data</p>
        </div>
        
        <div class="universal-config-wrapper">
          <!-- This will hold the UniversalConfigCard when we integrate it -->
          <div class="placeholder-config-card">
            <div class="placeholder-content">
              <span class="placeholder-icon">⚙️</span>
              <h3>UniversalConfigCard Integration</h3>
              <p>This is where the UniversalConfigCard will be integrated to provide schema-driven testing for the {{ moduleConfig.moduleType }} module.</p>
              
              <div class="connection-details">
                <h4>Connection Details:</h4>
                <div class="detail-grid">
                  <div class="detail-item">
                    <strong>Module Type:</strong> {{ moduleConfig.moduleType }}
                  </div>
                  <div class="detail-item">
                    <strong>Host:</strong> {{ moduleConfig.host }}
                  </div>
                  <div class="detail-item">
                    <strong>Port:</strong> {{ moduleConfig.port }}
                  </div>
                  <div class="detail-item">
                    <strong>Service Name:</strong> {{ moduleConfig.serviceName || 'Auto-detected' }}
                  </div>
                  <div class="detail-item">
                    <strong>TLS:</strong> {{ moduleConfig.useTLS ? 'Enabled' : 'Disabled' }}
                  </div>
                  <div class="detail-item">
                    <strong>Timeout:</strong> {{ moduleConfig.timeout }}s
                  </div>
                </div>
              </div>
              
              <div class="next-steps">
                <h4>Next Steps:</h4>
                <ul>
                  <li>Integrate UniversalConfigCard component</li>
                  <li>Fetch module OpenAPI schema</li>
                  <li>Generate dynamic form with JSONForms</li>
                  <li>Enable real module testing</li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ModuleTestingView',
  data() {
    return {
      moduleConfig: {},
      isTestingConnection: false
    }
  },
  computed: {
    connectionString() {
      if (!this.moduleConfig.host || !this.moduleConfig.port) return 'Unknown'
      const protocol = this.moduleConfig.useTLS ? 'grpcs' : 'grpc'
      return `${protocol}://${this.moduleConfig.host}:${this.moduleConfig.port}`
    }
  },
  mounted() {
    this.loadModuleConfig()
  },
  methods: {
    loadModuleConfig() {
      // Load the saved module configuration
      const saved = localStorage.getItem('pipelineClientConfig')
      if (saved) {
        try {
          const savedConfig = JSON.parse(saved)
          this.moduleConfig = savedConfig.config || savedConfig
        } catch (error) {
          console.warn('Failed to load module configuration:', error)
          this.goBackToConfig()
        }
      } else {
        // No config found, go back to configuration
        this.goBackToConfig()
      }
    },
    
    goBackToConfig() {
      this.$emit('navigate', 'configure')
    },
    
    async testConnection() {
      this.isTestingConnection = true
      
      try {
        // Perform real gRPC health check
        const { checkModuleHealth } = await import('../services/grpcHealthCheck.js')
        const result = await checkModuleHealth(
          this.moduleConfig.host,
          this.moduleConfig.port,
          this.moduleConfig.useTLS,
          this.moduleConfig.timeout ? this.moduleConfig.timeout * 1000 : 5000
        )
        
        if (result.status === 'connected') {
          console.log('gRPC health check successful for:', this.connectionString)
        } else {
          console.error('gRPC health check failed:', result.message)
        }
        
      } catch (error) {
        console.error('gRPC health check failed:', error)
      } finally {
        this.isTestingConnection = false
      }
    }
  }
}
</script>

<style scoped>
.module-testing-view {
  min-height: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.testing-header {
  background: white;
  border-bottom: 1px solid #e2e8f0;
  padding: 2rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
}

.module-info h1 {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.connection-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.connection-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #dcfce7;
  color: #166534;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #dc2626;
}

.status-dot.connected {
  background-color: #16a34a;
}

.testing-actions {
  display: flex;
  gap: 1rem;
  align-items: center;
}

.btn {
  padding: 0.75rem 1.5rem;
  border-radius: 6px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  font-size: 0.875rem;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.btn-secondary {
  background: #f8fafc;
  color: #374151;
  border: 1px solid #d1d5db;
}

.btn-secondary:hover:not(:disabled) {
  background: #f1f5f9;
}

.testing-content {
  padding: 2rem;
}

.config-card-container {
  max-width: 1200px;
  margin: 0 auto;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.card-header {
  padding: 2rem;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.card-header h2 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.card-header p {
  color: #64748b;
  font-size: 1rem;
}

.universal-config-wrapper {
  padding: 2rem;
}

.placeholder-config-card {
  text-align: center;
  padding: 3rem 2rem;
}

.placeholder-content {
  max-width: 600px;
  margin: 0 auto;
}

.placeholder-icon {
  font-size: 4rem;
  display: block;
  margin-bottom: 1.5rem;
}

.placeholder-content h3 {
  font-size: 1.5rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 1rem;
}

.placeholder-content p {
  color: #64748b;
  line-height: 1.6;
  margin-bottom: 2rem;
}

.connection-details {
  text-align: left;
  background: #f8fafc;
  padding: 1.5rem;
  border-radius: 8px;
  margin-bottom: 2rem;
}

.connection-details h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 1rem;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 0.75rem;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  padding: 0.5rem 0;
  border-bottom: 1px solid #e2e8f0;
  font-size: 0.875rem;
}

.detail-item:last-child {
  border-bottom: none;
}

.detail-item strong {
  color: #374151;
}

.next-steps {
  text-align: left;
  background: #eff6ff;
  padding: 1.5rem;
  border-radius: 8px;
  border: 1px solid #bfdbfe;
}

.next-steps h4 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1e40af;
  margin-bottom: 1rem;
}

.next-steps ul {
  list-style-type: disc;
  padding-left: 1.5rem;
  color: #1e40af;
}

.next-steps li {
  margin-bottom: 0.5rem;
  line-height: 1.5;
}

@media (max-width: 768px) {
  .testing-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .testing-actions {
    justify-content: center;
  }
  
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
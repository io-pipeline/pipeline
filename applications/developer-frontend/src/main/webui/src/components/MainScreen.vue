<template>
  <div class="main-screen">
    <div class="hero-section">
      <div class="hero-content">
        <h1 class="hero-title">Pipeline Developer Frontend</h1>
        <p class="hero-subtitle">
          Direct gRPC connection tool for testing and developing pipeline modules
        </p>
        <div class="hero-features">
          <div class="feature-card clickable" @click="navigateToConfig">
            <span class="feature-icon">🔗</span>
            <h3>Direct Module Connection</h3>
            <p>Connect directly to any pipeline module via gRPC for testing and development</p>
            <div class="feature-action">
              <span class="action-text">Set up connection →</span>
            </div>
          </div>
          <div class="feature-card clickable" @click="navigateToTesting">
            <span class="feature-icon">⚙️</span>
            <h3>Schema-Driven Forms</h3>
            <p>Automatically generate configuration forms from module OpenAPI schemas</p>
            <div class="feature-action">
              <span class="action-text">{{ hasConnectedModules ? 'Try it now' : 'Connect first' }} →</span>
            </div>
          </div>
          <div class="feature-card clickable" @click="navigateToTesting">
            <span class="feature-icon">🧪</span>
            <h3>Live Testing</h3>
            <p>Test module functionality with real data and see immediate results</p>
            <div class="feature-action">
              <span class="action-text">{{ hasConnectedModules ? 'Start testing' : 'Connect first' }} →</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="status-section">
      <div class="status-container">
        <h2>Quick Start</h2>
        <div class="status-grid">
          <div class="status-card">
            <div class="status-header">
              <span class="status-icon">🔧</span>
              <h3>Module Connection Manager</h3>
            </div>
            <p>Set up and manage connections to pipeline modules (Parser, Chunker, etc.)</p>
            <button class="action-btn" @click="navigateToConfig">
              {{ connectedModules.length > 0 ? 'Manage Connections' : 'Add First Module' }} →
            </button>
          </div>
          
          <!-- Show Connected Modules if any exist, otherwise show Direct Connection info -->
          <div v-if="connectedModules.length > 0" class="status-card">
            <div class="status-header">
              <span class="status-icon">📊</span>
              <h3>Connected Modules ({{ connectedModules.length }})</h3>
              <button @click="refreshAllConnections" :disabled="isRefreshing" class="refresh-btn">
                <span :class="{ 'spinning': isRefreshing }">🔄</span>
              </button>
            </div>
            <div class="module-status-list">
              <div 
                v-for="module in connectedModules" 
                :key="module.id"
                class="module-status-item"
                @click="navigateToModule(module)"
              >
                <div class="module-info">
                  <div class="module-details">
                    <strong class="module-name">{{ module.moduleType }}</strong>
                    <span class="module-endpoint">{{ module.host }}:{{ module.port }}</span>
                  </div>
                  <div class="connection-status">
                    <span class="status-dot" :class="getStatusClass(module.status)"></span>
                    <span class="status-text">{{ getStatusText(module.status) }}</span>
                    <span v-if="module.lastChecked" class="last-checked">
                      Last checked: {{ formatTime(module.lastChecked) }}
                    </span>
                  </div>
                </div>
                <span class="navigate-arrow">→</span>
              </div>
            </div>
            <div class="polling-info">
              <small>🔄 Auto-refreshing every 10 minutes</small>
            </div>
          </div>
          
          <div v-else class="status-card">
            <div class="status-header">
              <span class="status-icon">🔌</span>
              <h3>Direct Connection</h3>
            </div>
            <p>Connect directly to any pipeline module for development and testing.</p>
            <div class="direct-mode-info">
              <div class="info-item">
                <span class="info-icon">✅</span>
                <span>Connect to any module via host:port</span>
              </div>
              <div class="info-item">
                <span class="info-icon">⚡</span>
                <span>No infrastructure required</span>
              </div>
              <div class="info-item">
                <span class="info-icon">🎯</span>
                <span>Perfect for development workflow</span>
              </div>
            </div>
            <button class="action-btn" @click="navigateToConfig">
              Add Module Connection →
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="info-section">
      <div class="info-container">
        <div class="info-content">
          <h2>About This Tool</h2>
          <p>
            This developer frontend is part of the Multi-Frontend Architecture for the Pipeline Engine ecosystem.
            It provides a standalone way to connect to and test pipeline modules during development.
          </p>
          <div class="architecture-info">
            <h3>Architecture Overview</h3>
            <ul>
              <li><strong>Direct gRPC Communication:</strong> No proxy layers, connect straight to modules</li>
              <li><strong>Schema-Driven UI:</strong> Forms generated automatically from OpenAPI specifications</li>
              <li><strong>Language Agnostic:</strong> Works with modules written in any language</li>
              <li><strong>Development Focused:</strong> Built for rapid testing and iteration</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'MainScreen',
  data() {
    return {
      connectedModules: [],
      isRefreshing: false,
      pollingInterval: null
    }
  },
  computed: {
    hasConnectedModules() {
      return this.connectedModules.length > 0
    }
  },
  mounted() {
    this.loadConnectedModules()
    this.startPolling()
  },
  
  beforeUnmount() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval)
    }
  },
  methods: {
    navigateToConfig() {
      this.$emit('navigate', 'configure')
    },
    
    navigateToTesting() {
      if (this.hasConnectedModules) {
        // Navigate to testing with the most recent module
        const mostRecent = this.connectedModules[this.connectedModules.length - 1]
        localStorage.setItem('currentModuleConfig', JSON.stringify(mostRecent))
        this.$emit('navigate', 'testing')
      } else {
        // No modules connected, go to connection manager first
        this.navigateToConfig()
      }
    },
    
    navigateToModule(module) {
      // Save the specific module config and navigate to testing view
      localStorage.setItem('pipelineClientConfig', JSON.stringify(module))
      this.$emit('navigate', 'testing')
    },
    
    loadConnectedModules() {
      // Load all saved module configurations from localStorage
      const savedModules = this.getSavedModules()
      this.connectedModules = savedModules.map(config => ({
        id: this.generateModuleId(config),
        ...config,
        status: 'checking', // 'connected', 'disconnected', 'checking'
        lastChecked: null
      }))
      
      // Check initial connection status
      if (this.connectedModules.length > 0) {
        this.checkAllConnections()
      }
    },
    
    getSavedModules() {
      const saved = localStorage.getItem('pipelineClientConfig')
      if (!saved) return []
      
      try {
        const config = JSON.parse(saved)
        // Handle both single config and array of configs
        if (Array.isArray(config)) {
          return config
        } else if (config.moduleType && config.host && config.port) {
          return [config]
        }
        return []
      } catch (error) {
        console.warn('Failed to load saved modules:', error)
        return []
      }
    },
    
    generateModuleId(config) {
      return `${config.moduleType}-${config.host}-${config.port}`
    },
    
    async checkAllConnections() {
      for (const module of this.connectedModules) {
        await this.checkModuleConnection(module)
      }
    },
    
    async refreshAllConnections() {
      this.isRefreshing = true
      await this.checkAllConnections()
      this.isRefreshing = false
    },
    
    async checkModuleConnection(module) {
      module.status = 'checking'
      
      try {
        // Perform real gRPC health check
        const { checkModuleHealth } = await import('../services/grpcHealthCheck.js')
        const result = await checkModuleHealth(
          module.host,
          module.port,
          module.useTLS,
          module.timeout ? module.timeout * 1000 : 5000
        )
        
        module.status = result.status
        module.healthMessage = result.message
        
      } catch (error) {
        module.status = 'disconnected'
        module.healthMessage = `Health check error: ${error.message}`
        console.log(`gRPC health check failed for ${module.moduleType} at ${module.host}:${module.port}:`, error.message)
      } finally {
        module.lastChecked = new Date()
      }
    },
    
    startPolling() {
      // Poll every 10 minutes (600,000 ms)
      this.pollingInterval = setInterval(() => {
        if (this.connectedModules.length > 0) {
          this.checkAllConnections()
        }
      }, 600000) // 10 minutes
    },
    
    getStatusClass(status) {
      return {
        'connected': status === 'connected',
        'checking': status === 'checking',
        'disconnected': status === 'disconnected'
      }
    },
    
    getStatusText(status) {
      const statusMap = {
        'connected': 'Connected',
        'disconnected': 'Disconnected', 
        'checking': 'Checking...'
      }
      return statusMap[status] || 'Unknown'
    },
    
    formatTime(date) {
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  }
}
</script>

<style scoped>
.main-screen {
  min-height: 100%;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
}

.hero-section {
  padding: 3rem 2rem;
  text-align: center;
  background: white;
  border-bottom: 1px solid #e2e8f0;
}

.hero-content {
  max-width: 1200px;
  margin: 0 auto;
}

.hero-title {
  font-size: 3rem;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 1.25rem;
  color: #64748b;
  margin-bottom: 3rem;
  max-width: 600px;
  margin-left: auto;
  margin-right: auto;
}

.hero-features {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 2rem;
  margin-top: 2rem;
}

.feature-card {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  border: 1px solid #e2e8f0;
  transition: transform 0.2s, box-shadow 0.2s;
  position: relative;
}

.feature-card.clickable {
  cursor: pointer;
}

.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0,0,0,0.1);
}

.feature-card.clickable:hover {
  border-color: #667eea;
}

.feature-action {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #f1f5f9;
  opacity: 0;
  transition: opacity 0.2s;
}

.feature-card.clickable:hover .feature-action {
  opacity: 1;
}

.action-text {
  color: #667eea;
  font-weight: 500;
  font-size: 0.875rem;
}

.feature-icon {
  font-size: 2.5rem;
  display: block;
  margin-bottom: 1rem;
}

.feature-card h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.feature-card p {
  color: #64748b;
  line-height: 1.6;
}

.status-section {
  padding: 3rem 2rem;
}

.status-container {
  max-width: 1200px;
  margin: 0 auto;
}

.status-section h2 {
  font-size: 2rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 2rem;
  text-align: center;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(400px, 1fr));
  gap: 2rem;
}

.status-card {
  background: white;
  padding: 2rem;
  border-radius: 12px;
  box-shadow: 0 4px 6px rgba(0,0,0,0.05);
  border: 1px solid #e2e8f0;
}

.status-header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.status-icon {
  font-size: 1.5rem;
}

.status-card h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.status-card p {
  color: #64748b;
  margin-bottom: 1.5rem;
  line-height: 1.6;
}

.action-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.module-status {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #dc2626;
  transition: background-color 0.2s;
}

.status-dot.active {
  background-color: #16a34a;
}

.status-text {
  margin-left: auto;
  font-size: 0.875rem;
  color: #64748b;
}

.refresh-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.refresh-btn:hover:not(:disabled) {
  background-color: #f3f4f6;
}

.refresh-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.module-status-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.module-status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.module-status-item:hover {
  background: #f1f5f9;
  border-color: #667eea;
  transform: translateY(-1px);
}

.module-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.module-details {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.module-name {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  text-transform: capitalize;
}

.module-endpoint {
  font-family: monospace;
  font-size: 0.875rem;
  color: #64748b;
  background: #e2e8f0;
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.875rem;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: background-color 0.2s;
}

.status-dot.connected {
  background-color: #16a34a;
}

.status-dot.disconnected {
  background-color: #dc2626;
}

.status-dot.checking {
  background-color: #f59e0b;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.status-text {
  font-weight: 500;
  color: #374151;
}

.last-checked {
  color: #6b7280;
  font-size: 0.75rem;
}

.navigate-arrow {
  color: #9ca3af;
  font-size: 1.25rem;
  transition: transform 0.2s;
}

.module-status-item:hover .navigate-arrow {
  transform: translateX(2px);
  color: #667eea;
}

.polling-info {
  text-align: center;
  color: #6b7280;
  font-size: 0.75rem;
  padding-top: 0.75rem;
  border-top: 1px solid #e2e8f0;
}

.direct-mode-info {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-top: 1rem;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.875rem;
  color: #64748b;
}

.info-icon {
  font-size: 1rem;
}

.info-section {
  padding: 3rem 2rem;
  background: white;
  border-top: 1px solid #e2e8f0;
}

.info-container {
  max-width: 800px;
  margin: 0 auto;
}

.info-content h2 {
  font-size: 1.75rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 1rem;
}

.info-content p {
  color: #64748b;
  line-height: 1.7;
  margin-bottom: 2rem;
}

.architecture-info h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 1rem;
}

.architecture-info ul {
  list-style: none;
  padding: 0;
}

.architecture-info li {
  padding: 0.5rem 0;
  color: #64748b;
  line-height: 1.6;
}

.architecture-info strong {
  color: #1e293b;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 2rem;
  }
  
  .hero-features {
    grid-template-columns: 1fr;
  }
  
  .status-grid {
    grid-template-columns: 1fr;
  }
}
</style>
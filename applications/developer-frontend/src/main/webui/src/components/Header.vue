<template>
  <header class="header">
    <button class="hamburger-btn" @click="$emit('toggle-menu')" aria-label="Toggle menu">
      <span class="hamburger-line"></span>
      <span class="hamburger-line"></span>
      <span class="hamburger-line"></span>
    </button>
    <h1 class="title">Pipeline Developer Frontend</h1>
    <div class="header-actions">
      <span class="status-indicator" :class="statusClass">
        {{ statusText }}
      </span>
    </div>
  </header>
</template>

<script>
export default {
  name: 'Header',
  emits: ['toggle-menu'],
  data() {
    return {
      connectedModules: [],
      pollingInterval: null
    }
  },
  computed: {
    connectionSummary() {
      const total = this.connectedModules.length
      if (total === 0) {
        return { type: 'setup', connected: 0, total: 0 }
      }
      
      const connected = this.connectedModules.filter(m => m.status === 'connected').length
      
      if (connected === total) {
        return { type: 'connected', connected, total }
      } else if (connected === 0) {
        return { type: 'disconnected', connected, total }
      } else {
        return { type: 'partial', connected, total }
      }
    },
    
    statusText() {
      switch (this.connectionSummary.type) {
        case 'setup':
          return 'Setup Module'
        case 'connected':
          return 'Connected'
        case 'disconnected':
          return 'Disconnected'
        case 'partial':
          return `${this.connectionSummary.connected}/${this.connectionSummary.total} Connected`
        default:
          return 'Unknown'
      }
    },
    
    statusClass() {
      return {
        'status-setup': this.connectionSummary.type === 'setup',
        'status-connected': this.connectionSummary.type === 'connected',
        'status-disconnected': this.connectionSummary.type === 'disconnected',
        'status-partial': this.connectionSummary.type === 'partial'
      }
    }
  },
  
  mounted() {
    this.loadModuleStatus()
    this.startPolling()
  },
  
  beforeUnmount() {
    if (this.pollingInterval) {
      clearInterval(this.pollingInterval)
    }
  },
  
  methods: {
    loadModuleStatus() {
      // Load saved modules and their connection status
      const saved = localStorage.getItem('pipelineClientConfig')
      if (!saved) {
        this.connectedModules = []
        return
      }
      
      try {
        const config = JSON.parse(saved)
        // Handle both single config and array of configs
        let modules = []
        if (Array.isArray(config)) {
          modules = config
        } else if (config.moduleType && config.host && config.port) {
          modules = [config]
        }
        
        // Initialize modules with status if not present
        this.connectedModules = modules.map(module => ({
          ...module,
          status: module.status || 'disconnected',
          lastChecked: module.lastChecked || null
        }))
        
        // Check initial status
        this.checkAllConnections()
      } catch (error) {
        console.warn('Failed to load module status:', error)
        this.connectedModules = []
      }
    },
    
    async checkAllConnections() {
      for (const module of this.connectedModules) {
        await this.checkModuleConnection(module)
      }
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
        console.error('gRPC health check failed:', error)
        module.status = 'disconnected'
        module.healthMessage = `Health check error: ${error.message}`
      } finally {
        module.lastChecked = new Date()
      }
    },
    
    startPolling() {
      // Check status every 30 seconds (more frequent than MainScreen's 10 minutes)
      this.pollingInterval = setInterval(() => {
        if (this.connectedModules.length > 0) {
          this.checkAllConnections()
        } else {
          this.loadModuleStatus() // Reload in case modules were added
        }
      }, 30000) // 30 seconds
    }
  }
}
</script>

<style scoped>
.header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 1rem 1.5rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 2px 10px rgba(0,0,0,0.1);
  z-index: 1000;
}

.hamburger-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.5rem;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.hamburger-btn:hover {
  background-color: rgba(255,255,255,0.1);
}

.hamburger-line {
  width: 24px;
  height: 3px;
  background-color: white;
  border-radius: 2px;
  transition: all 0.3s ease;
}

.title {
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  flex: 1;
  text-align: center;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.status-indicator {
  padding: 0.25rem 0.75rem;
  border-radius: 12px;
  font-size: 0.875rem;
  font-weight: 500;
  transition: all 0.3s ease;
  cursor: pointer;
}

/* Setup Module - Neutral gray */
.status-indicator.status-setup {
  background-color: rgba(107, 114, 128, 0.2);
  border: 1px solid rgba(107, 114, 128, 0.5);
  color: #f3f4f6;
}

/* All Connected - Green */
.status-indicator.status-connected {
  background-color: rgba(34, 197, 94, 0.2);
  border: 1px solid rgba(34, 197, 94, 0.5);
  color: #dcfce7;
}

/* Partial Connected - Yellow */
.status-indicator.status-partial {
  background-color: rgba(245, 158, 11, 0.2);
  border: 1px solid rgba(245, 158, 11, 0.5);
  color: #fef3c7;
}

/* All Disconnected - Red */
.status-indicator.status-disconnected {
  background-color: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.5);
  color: #fecaca;
}

.status-indicator:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

@media (max-width: 768px) {
  .title {
    font-size: 1.25rem;
  }
  
  .header {
    padding: 1rem;
  }
}
</style>
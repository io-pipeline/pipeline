<template>
  <div class="connection-details">
    <div class="details-layout">
      <!-- Left Panel: Connection List -->
      <div class="list-panel">
        <ConnectionList
          :connections="connections"
          :selected-connection-id="selectedConnectionId"
          @select-connection="selectConnection"
          @test-connection="testConnection"
          @remove-connection="removeConnection"
          @add-new="startAddingNew"
        />
      </div>
      
      <!-- Right Panel: Details -->
      <div class="details-panel">
        <!-- Preview Card -->
        <ConnectionPreviewCard
          :connection="selectedConnection"
          :is-testing="isTestingConnection"
          @test-connection="testSelectedConnection"
          @navigate-to-testing="navigateToTesting"
        />
        
        <!-- Form -->
        <ConnectionForm
          :connection="isEditing ? selectedConnection : null"
          :is-editing="isEditing"
          @submit="saveConnection"
          @cancel="cancelEditing"
          @test="testFormConnection"
          ref="connectionForm"
        />
      </div>
    </div>
  </div>
</template>

<script>
import ConnectionList from './ConnectionList.vue'
import ConnectionPreviewCard from './ConnectionPreviewCard.vue'
import ConnectionForm from './ConnectionForm.vue'
import { testGrpcHealthCheck } from '../testGrpcHealth.js'

export default {
  name: 'ConnectionDetails',
  components: {
    ConnectionList,
    ConnectionPreviewCard,
    ConnectionForm
  },
  data() {
    return {
      connections: [],
      selectedConnectionId: null,
      isEditing: false,
      isAddingNew: false,
      isTestingConnection: false,
      testResults: new Map() // Store test results by connection ID
    }
  },
  computed: {
    selectedConnection() {
      if (!this.selectedConnectionId) return null
      return this.connections.find(conn => 
        this.generateConnectionId(conn) === this.selectedConnectionId
      )
    }
  },
  mounted() {
    this.loadConnections()
    // Auto-select first connection if any exist
    if (this.connections.length > 0) {
      this.selectConnection(this.connections[0], this.generateConnectionId(this.connections[0]))
    }
  },
  methods: {
    loadConnections() {
      const saved = localStorage.getItem('pipelineClientConfig')
      if (!saved) {
        this.connections = []
        return
      }
      
      try {
        const config = JSON.parse(saved)
        if (Array.isArray(config)) {
          this.connections = config.map(conn => ({
            ...conn,
            status: conn.status || 'disconnected',
            lastChecked: conn.lastChecked ? new Date(conn.lastChecked) : null,
            testing: false
          }))
        } else if (config.moduleType && config.host && config.port) {
          this.connections = [{
            ...config,
            status: config.status || 'disconnected',
            lastChecked: config.lastChecked ? new Date(config.lastChecked) : null,
            testing: false
          }]
        } else {
          this.connections = []
        }
      } catch (error) {
        console.warn('Failed to load connections:', error)
        this.connections = []
      }
    },
    
    saveConnections() {
      localStorage.setItem('pipelineClientConfig', JSON.stringify(this.connections))
      
      // Also emit to parent to update other components
      this.$emit('connections-updated', this.connections)
    },
    
    generateConnectionId(connection) {
      return `${connection.moduleType}-${connection.host}-${connection.port}`
    },
    
    selectConnection(connection, connectionId) {
      this.selectedConnectionId = connectionId
      this.isEditing = false
      this.isAddingNew = false
    },
    
    startAddingNew() {
      this.selectedConnectionId = null
      this.isEditing = false
      this.isAddingNew = true
    },
    
    saveConnection(connectionData) {
      const connectionId = this.generateConnectionId(connectionData)
      
      // Check if connection already exists
      const existingIndex = this.connections.findIndex(conn => 
        this.generateConnectionId(conn) === connectionId
      )
      
      if (existingIndex >= 0) {
        // Update existing connection
        this.connections[existingIndex] = { ...this.connections[existingIndex], ...connectionData }
      } else {
        // Add new connection
        this.connections.push(connectionData)
      }
      
      this.saveConnections()
      
      // Select the saved connection
      this.selectedConnectionId = connectionId
      this.isEditing = false
      this.isAddingNew = false
      
      // Auto-test the connection
      this.testConnection(connectionData)
      
      // Emit navigation event for successful save
      this.$emit('navigate', 'testing')
    },
    
    removeConnection(connection) {
      const connectionId = this.generateConnectionId(connection)
      this.connections = this.connections.filter(conn => 
        this.generateConnectionId(conn) !== connectionId
      )
      
      this.saveConnections()
      
      // Clear selection if this was the selected connection
      if (this.selectedConnectionId === connectionId) {
        this.selectedConnectionId = null
        this.isEditing = false
      }
      
      // Auto-select first connection if any remain
      if (this.connections.length > 0 && !this.selectedConnectionId) {
        this.selectConnection(this.connections[0], this.generateConnectionId(this.connections[0]))
      }
    },
    
    async testConnection(connection) {
      const connectionId = this.generateConnectionId(connection)
      
      // Set testing state
      connection.testing = true
      connection.status = 'checking'
      
      try {
        const result = await testGrpcHealthCheck(connection.host, connection.port)
        
        // Update connection status
        connection.status = result.isHealthy ? 'connected' : 'disconnected'
        connection.lastChecked = new Date()
        
        // Store test result
        this.testResults.set(connectionId, {
          type: result.isHealthy ? 'success' : 'error',
          message: result.message
        })
        
        // Update form test result if this connection is being edited
        if (this.isEditing && this.selectedConnectionId === connectionId) {
          this.$refs.connectionForm?.setTestResult(this.testResults.get(connectionId))
        }
        
      } catch (error) {
        connection.status = 'disconnected'
        connection.lastChecked = new Date()
        
        this.testResults.set(connectionId, {
          type: 'error',
          message: `Connection failed: ${error.message}`
        })
        
        if (this.isEditing && this.selectedConnectionId === connectionId) {
          this.$refs.connectionForm?.setTestResult(this.testResults.get(connectionId))
        }
      } finally {
        connection.testing = false
        this.saveConnections()
      }
    },
    
    async testSelectedConnection() {
      if (this.selectedConnection) {
        this.isTestingConnection = true
        await this.testConnection(this.selectedConnection)
        this.isTestingConnection = false
      }
    },
    
    async testFormConnection(formData) {
      // Create a temporary connection for testing
      const tempConnection = { ...formData, testing: false }
      await this.testConnection(tempConnection)
      
      // Return test result to form
      const connectionId = this.generateConnectionId(formData)
      return this.testResults.get(connectionId)
    },
    
    cancelEditing() {
      this.isEditing = false
      this.isAddingNew = false
    },
    
    navigateToTesting() {
      if (this.selectedConnection) {
        // Save current connection for testing view
        localStorage.setItem('currentModuleConfig', JSON.stringify(this.selectedConnection))
        this.$emit('navigate', 'testing')
      }
    }
  }
}
</script>

<style scoped>
.connection-details {
  height: 100%;
  background: #f8fafc;
}

.details-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  height: 100vh;
  max-height: calc(100vh - 80px); /* Account for header */
}

.list-panel {
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
  overflow: hidden;
}

.details-panel {
  background: white;
  padding: 2rem;
  overflow-y: auto;
}

/* Responsive design */
@media (max-width: 1200px) {
  .details-layout {
    grid-template-columns: 250px 1fr;
  }
}

@media (max-width: 900px) {
  .details-layout {
    grid-template-columns: 1fr;
    grid-template-rows: 300px 1fr;
  }
  
  .list-panel {
    border-right: none;
    border-bottom: 1px solid #e2e8f0;
  }
}

@media (max-width: 600px) {
  .details-panel {
    padding: 1rem;
  }
  
  .details-layout {
    grid-template-rows: 250px 1fr;
  }
}

/* Scrollbar styling for details panel */
.details-panel::-webkit-scrollbar {
  width: 8px;
}

.details-panel::-webkit-scrollbar-track {
  background: #f1f5f9;
}

.details-panel::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.details-panel::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
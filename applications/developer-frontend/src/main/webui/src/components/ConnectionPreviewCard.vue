<template>
  <div class="connection-preview-card" v-if="connection">
    <div class="card-header">
      <div class="module-info">
        <span class="module-icon">🔗</span>
        <div class="module-details">
          <h3 class="module-name">{{ connection.moduleType }} Module</h3>
          <span class="module-endpoint">{{ connectionString }}</span>
        </div>
      </div>
      <div class="status-badge" :class="statusClass">
        <span class="status-dot"></span>
        <span class="status-text">{{ statusText }}</span>
      </div>
    </div>
    
    <div class="card-content">
      <div class="connection-details">
        <div class="detail-row">
          <span class="label">Service Name:</span>
          <span class="value">{{ connection.serviceName || 'Auto-detected' }}</span>
        </div>
        <div class="detail-row">
          <span class="label">TLS:</span>
          <span class="value">{{ connection.useTLS ? 'Enabled' : 'Disabled' }}</span>
        </div>
        <div class="detail-row">
          <span class="label">Timeout:</span>
          <span class="value">{{ connection.timeout }}s</span>
        </div>
        <div class="detail-row" v-if="connection.lastChecked">
          <span class="label">Last checked:</span>
          <span class="value">{{ formatTime(connection.lastChecked) }}</span>
        </div>
      </div>
      
      <div class="card-actions">
        <button @click="$emit('test-connection')" :disabled="isTesting" class="test-btn">
          <span v-if="isTesting">⏳ Testing...</span>
          <span v-else>🔄 Test Connection</span>
        </button>
        <button @click="$emit('navigate-to-testing')" :disabled="connection.status !== 'connected'" class="navigate-btn">
          🧪 Test Module →
        </button>
      </div>
    </div>
  </div>
  
  <div class="no-selection" v-else>
    <div class="no-selection-content">
      <span class="no-selection-icon">📡</span>
      <h3>No Connection Selected</h3>
      <p>Select a connection from the list to view details, or add a new connection to get started.</p>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ConnectionPreviewCard',
  props: {
    connection: {
      type: Object,
      default: null
    },
    isTesting: {
      type: Boolean,
      default: false
    }
  },
  emits: ['test-connection', 'navigate-to-testing'],
  computed: {
    connectionString() {
      if (!this.connection) return ''
      const protocol = this.connection.useTLS ? 'grpcs' : 'grpc'
      return `${protocol}://${this.connection.host}:${this.connection.port}`
    },
    
    statusClass() {
      if (!this.connection) return ''
      return {
        'status-connected': this.connection.status === 'connected',
        'status-disconnected': this.connection.status === 'disconnected',
        'status-checking': this.connection.status === 'checking'
      }
    },
    
    statusText() {
      if (!this.connection) return ''
      const statusMap = {
        'connected': 'Connected',
        'disconnected': 'Disconnected',
        'checking': 'Checking...'
      }
      return statusMap[this.connection.status] || 'Unknown'
    }
  },
  methods: {
    formatTime(date) {
      if (!date) return 'Never'
      const dateObj = typeof date === 'string' ? new Date(date) : date
      return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  }
}
</script>

<style scoped>
.connection-preview-card {
  background: white;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  margin-bottom: 1.5rem;
}

.card-header {
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  padding: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.module-info {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.module-icon {
  font-size: 2rem;
}

.module-details h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 0.25rem 0;
  text-transform: capitalize;
}

.module-endpoint {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 0.875rem;
  color: #64748b;
  background: rgba(255,255,255,0.7);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 1rem;
  border-radius: 20px;
  font-size: 0.875rem;
  font-weight: 500;
}

.status-badge.status-connected {
  background: #dcfce7;
  color: #166534;
}

.status-badge.status-disconnected {
  background: #fee2e2;
  color: #991b1b;
}

.status-badge.status-checking {
  background: #fef3c7;
  color: #92400e;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.status-checking .status-dot {
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.card-content {
  padding: 1.5rem;
}

.connection-details {
  margin-bottom: 1.5rem;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid #f1f5f9;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row .label {
  font-weight: 500;
  color: #374151;
}

.detail-row .value {
  color: #64748b;
  font-family: monospace;
  font-size: 0.875rem;
}

.card-actions {
  display: flex;
  gap: 1rem;
}

.test-btn, .navigate-btn {
  flex: 1;
  padding: 0.75rem 1rem;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 0.875rem;
}

.test-btn {
  background: #f8fafc;
  color: #374151;
  border: 1px solid #d1d5db;
}

.test-btn:hover:not(:disabled) {
  background: #f1f5f9;
  transform: translateY(-1px);
}

.test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.navigate-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.navigate-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.navigate-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #e5e7eb;
  color: #9ca3af;
}

.no-selection {
  background: white;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 3rem 2rem;
  text-align: center;
  margin-bottom: 1.5rem;
}

.no-selection-content {
  max-width: 300px;
  margin: 0 auto;
}

.no-selection-icon {
  font-size: 3rem;
  display: block;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.no-selection h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 0.5rem;
}

.no-selection p {
  color: #64748b;
  line-height: 1.5;
}
</style>
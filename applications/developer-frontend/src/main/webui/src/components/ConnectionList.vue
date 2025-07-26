<template>
  <div class="connection-list">
    <div class="list-header">
      <h3>Connections</h3>
      <span class="connection-count" v-if="connections.length > 0">
        {{ connections.length }}
      </span>
    </div>
    
    <div class="list-content" v-if="connections.length > 0">
      <ConnectionListItem
        v-for="connection in connections"
        :key="generateConnectionId(connection)"
        :connection="connection"
        :is-selected="selectedConnectionId === generateConnectionId(connection)"
        @select="selectConnection(connection)"
        @test="testConnection(connection)"
        @remove="removeConnection(connection)"
      />
    </div>
    
    <div class="empty-state" v-else>
      <div class="empty-content">
        <span class="empty-icon">📡</span>
        <p>No connections yet</p>
        <small>Add your first module connection to get started</small>
      </div>
    </div>
    
    <div class="list-footer">
      <button @click="$emit('add-new')" class="add-new-btn">
        <span class="add-icon">+</span>
        <span>Add New Connection</span>
      </button>
    </div>
  </div>
</template>

<script>
import ConnectionListItem from './ConnectionListItem.vue'

export default {
  name: 'ConnectionList',
  components: {
    ConnectionListItem
  },
  props: {
    connections: {
      type: Array,
      default: () => []
    },
    selectedConnectionId: {
      type: String,
      default: null
    }
  },
  emits: ['select-connection', 'test-connection', 'remove-connection', 'add-new'],
  methods: {
    generateConnectionId(connection) {
      return `${connection.moduleType}-${connection.host}-${connection.port}`
    },
    
    selectConnection(connection) {
      const connectionId = this.generateConnectionId(connection)
      this.$emit('select-connection', connection, connectionId)
    },
    
    testConnection(connection) {
      this.$emit('test-connection', connection)
    },
    
    removeConnection(connection) {
      if (confirm(`Remove connection to ${connection.moduleType} at ${connection.host}:${connection.port}?`)) {
        this.$emit('remove-connection', connection)
      }
    }
  }
}
</script>

<style scoped>
.connection-list {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border-right: 1px solid #e2e8f0;
}

.list-header {
  padding: 1.5rem 1rem 1rem 1rem;
  border-bottom: 1px solid #e2e8f0;
  background: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-header h3 {
  font-size: 1.125rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.connection-count {
  background: #667eea;
  color: white;
  font-size: 0.75rem;
  font-weight: 500;
  padding: 0.25rem 0.5rem;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.list-content {
  flex: 1;
  padding: 1rem;
  overflow-y: auto;
  min-height: 0;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem 1rem;
}

.empty-content {
  text-align: center;
  max-width: 200px;
}

.empty-icon {
  font-size: 2.5rem;
  display: block;
  margin-bottom: 1rem;
  opacity: 0.5;
}

.empty-content p {
  font-weight: 500;
  color: #374151;
  margin: 0 0 0.25rem 0;
}

.empty-content small {
  color: #6b7280;
  line-height: 1.4;
}

.list-footer {
  padding: 1rem;
  border-top: 1px solid #e2e8f0;
  background: white;
}

.add-new-btn {
  width: 100%;
  padding: 0.75rem 1rem;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
}

.add-new-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.add-icon {
  font-size: 1.125rem;
  font-weight: 300;
}

/* Scrollbar styling */
.list-content::-webkit-scrollbar {
  width: 6px;
}

.list-content::-webkit-scrollbar-track {
  background: transparent;
}

.list-content::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.list-content::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
</style>
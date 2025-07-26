<template>
  <div 
    class="connection-list-item" 
    :class="{ 'selected': isSelected, 'testing': connection.testing }"
    @click="$emit('select')"
  >
    <div class="item-content">
      <div class="connection-info">
        <div class="connection-header">
          <span class="status-dot" :class="statusClass"></span>
          <span class="module-name">{{ connection.moduleType }}</span>
        </div>
        <div class="connection-endpoint">
          {{ connection.host }}:{{ connection.port }}
        </div>
      </div>
      
      <div class="item-actions">
        <button 
          @click.stop="$emit('test')" 
          :disabled="connection.testing"
          class="quick-test-btn"
          :title="connection.testing ? 'Testing...' : 'Quick test'"
        >
          <span v-if="connection.testing">⏳</span>
          <span v-else>🔄</span>
        </button>
        <button 
          @click.stop="$emit('remove')" 
          class="remove-btn"
          title="Remove connection"
        >
          ×
        </button>
      </div>
    </div>
    
    <div class="last-checked" v-if="connection.lastChecked">
      {{ formatTime(connection.lastChecked) }}
    </div>
  </div>
</template>

<script>
export default {
  name: 'ConnectionListItem',
  props: {
    connection: {
      type: Object,
      required: true
    },
    isSelected: {
      type: Boolean,
      default: false
    }
  },
  emits: ['select', 'test', 'remove'],
  computed: {
    statusClass() {
      return {
        'status-connected': this.connection.status === 'connected',
        'status-disconnected': this.connection.status === 'disconnected',
        'status-checking': this.connection.status === 'checking'
      }
    }
  },
  methods: {
    formatTime(date) {
      if (!date) return ''
      const dateObj = typeof date === 'string' ? new Date(date) : date
      return dateObj.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  }
}
</script>

<style scoped>
.connection-list-item {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.connection-list-item:hover {
  border-color: #667eea;
  background: #f8fafc;
  transform: translateX(2px);
}

.connection-list-item.selected {
  border-color: #667eea;
  background: #f0f4ff;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.15);
}

.connection-list-item.testing {
  border-color: #f59e0b;
  background: #fffbeb;
}

.item-content {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 0.5rem;
}

.connection-info {
  flex: 1;
  min-width: 0;
}

.connection-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.status-connected {
  background-color: #16a34a;
}

.status-dot.status-disconnected {
  background-color: #dc2626;
}

.status-dot.status-checking {
  background-color: #f59e0b;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.module-name {
  font-weight: 600;
  font-size: 0.875rem;
  color: #1e293b;
  text-transform: capitalize;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.connection-endpoint {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 0.75rem;
  color: #64748b;
  background: rgba(241, 245, 249, 0.8);
  padding: 0.125rem 0.375rem;
  border-radius: 3px;
  width: fit-content;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.item-actions {
  display: flex;
  gap: 0.25rem;
  flex-shrink: 0;
}

.quick-test-btn, .remove-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  transition: all 0.2s;
  background: transparent;
}

.quick-test-btn {
  color: #667eea;
}

.quick-test-btn:hover:not(:disabled) {
  background: #e0e7ff;
}

.quick-test-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.remove-btn {
  color: #dc2626;
  font-size: 1rem;
}

.remove-btn:hover {
  background: #fee2e2;
}

.last-checked {
  font-size: 0.625rem;
  color: #9ca3af;
  text-align: right;
  margin-top: 0.25rem;
}

/* Compact mode for narrow sidebar */
@media (max-width: 1200px) {
  .connection-list-item {
    padding: 0.75rem;
  }
  
  .module-name {
    font-size: 0.8rem;
  }
  
  .connection-endpoint {
    font-size: 0.7rem;
  }
}
</style>
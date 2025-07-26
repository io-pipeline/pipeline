<template>
  <div class="connection-form">
    <div class="form-header">
      <h3>{{ isEditing ? 'Edit Connection' : 'Add New Connection' }}</h3>
      <button v-if="isEditing" @click="$emit('cancel')" class="cancel-btn">
        Cancel
      </button>
    </div>
    
    <form @submit.prevent="handleSubmit" class="form-content">
      <div class="form-section">
        <h4>Module Configuration</h4>
        
        <div class="form-group">
          <label for="moduleType">Module Type</label>
          <select 
            id="moduleType" 
            v-model="formData.moduleType" 
            @change="updateDefaultPort"
            class="form-input"
            required
          >
            <option value="">Select Module Type</option>
            <option value="parser">Parser Module</option>
            <option value="chunker">Chunker Module</option>
            <option value="embedder">Embedder Module</option>
            <option value="tika">Tika Parser Module</option>
            <option value="custom">Custom Module</option>
          </select>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label for="host">Host</label>
            <input 
              id="host"
              type="text" 
              v-model="formData.host" 
              placeholder="localhost"
              class="form-input"
              required
            >
          </div>
          
          <div class="form-group">
            <label for="port">Port</label>
            <input 
              id="port"
              type="number" 
              v-model.number="formData.port" 
              placeholder="39101"
              class="form-input"
              min="1"
              max="65535"
              required
            >
          </div>
        </div>

        <div class="form-group">
          <label for="serviceName">Service Name (Optional)</label>
          <input 
            id="serviceName"
            type="text" 
            v-model="formData.serviceName" 
            placeholder="Auto-detected from module"
            class="form-input"
          >
          <small class="form-help">
            Leave empty to auto-detect from module OpenAPI specification
          </small>
        </div>
      </div>

      <div class="form-section">
        <h4>Connection Options</h4>
        
        <div class="form-group">
          <label class="checkbox-label">
            <input 
              type="checkbox" 
              v-model="formData.useTLS" 
              class="form-checkbox"
            >
            Use TLS/SSL
          </label>
        </div>

        <div class="form-group">
          <label for="timeout">Timeout (seconds)</label>
          <input 
            id="timeout"
            type="number" 
            v-model.number="formData.timeout" 
            min="1" 
            max="300"
            class="form-input"
            required
          >
        </div>
      </div>

      <div class="form-actions">
        <button 
          type="button"
          @click="testConnection" 
          :disabled="!isFormValid || isTesting"
          class="btn btn-secondary"
        >
          {{ isTesting ? 'Testing...' : 'Test Connection' }}
        </button>
        
        <button 
          type="submit"
          :disabled="!isFormValid"
          class="btn btn-primary"
        >
          {{ isEditing ? 'Update Connection' : 'Save & Connect' }}
        </button>
      </div>
      
      <!-- Test Result -->
      <div v-if="testResult" class="test-result" :class="testResult.type">
        <div class="result-content">
          <span class="result-icon">
            {{ testResult.type === 'success' ? '✅' : testResult.type === 'error' ? '❌' : 'ℹ️' }}
          </span>
          <span class="result-message">{{ testResult.message }}</span>
        </div>
      </div>
    </form>
  </div>
</template>

<script>
export default {
  name: 'ConnectionForm',
  props: {
    connection: {
      type: Object,
      default: null
    },
    isEditing: {
      type: Boolean,
      default: false
    }
  },
  emits: ['submit', 'cancel', 'test'],
  data() {
    return {
      formData: {
        moduleType: '',
        host: 'localhost',
        port: null,
        serviceName: '',
        useTLS: false,
        timeout: 30
      },
      isTesting: false,
      testResult: null,
      moduleDefaults: {
        parser: { port: 39101, serviceName: 'parser' },
        chunker: { port: 39102, serviceName: 'chunker' },
        embedder: { port: 39103, serviceName: 'embedder' },
        tika: { port: 39104, serviceName: 'tika-parser' }
      }
    }
  },
  computed: {
    isFormValid() {
      return this.formData.moduleType && 
             this.formData.host && 
             this.formData.port && 
             this.formData.port > 0 && 
             this.formData.port <= 65535 &&
             this.formData.timeout > 0
    }
  },
  watch: {
    connection: {
      immediate: true,
      handler(newConnection) {
        if (newConnection) {
          this.formData = { ...newConnection }
        } else {
          this.resetForm()
        }
      }
    }
  },
  methods: {
    updateDefaultPort() {
      if (this.formData.moduleType && this.moduleDefaults[this.formData.moduleType]) {
        const defaults = this.moduleDefaults[this.formData.moduleType]
        if (!this.formData.port) {
          this.formData.port = defaults.port
        }
        if (!this.formData.serviceName) {
          this.formData.serviceName = defaults.serviceName
        }
      }
    },
    
    async testConnection() {
      if (!this.isFormValid) return
      
      this.isTesting = true
      this.testResult = null
      
      try {
        await this.$emit('test', this.formData)
        // The parent component will handle the actual test result
      } catch (error) {
        this.testResult = {
          type: 'error',
          message: `Test failed: ${error.message}`
        }
      } finally {
        this.isTesting = false
      }
    },
    
    handleSubmit() {
      if (!this.isFormValid) return
      
      const connectionData = {
        ...this.formData,
        id: this.generateConnectionId(this.formData),
        status: 'disconnected',
        lastChecked: null
      }
      
      this.$emit('submit', connectionData)
    },
    
    resetForm() {
      this.formData = {
        moduleType: '',
        host: 'localhost',
        port: null,
        serviceName: '',
        useTLS: false,
        timeout: 30
      }
      this.testResult = null
    },
    
    generateConnectionId(config) {
      return `${config.moduleType}-${config.host}-${config.port}`
    },
    
    setTestResult(result) {
      this.testResult = result
    }
  }
}
</script>

<style scoped>
.connection-form {
  background: white;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
}

.form-header {
  background: #f8fafc;
  padding: 1.5rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-header h3 {
  font-size: 1.25rem;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.cancel-btn {
  background: none;
  border: 1px solid #d1d5db;
  color: #374151;
  padding: 0.5rem 1rem;
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.875rem;
  transition: all 0.2s;
}

.cancel-btn:hover {
  background: #f3f4f6;
}

.form-content {
  padding: 1.5rem;
}

.form-section {
  margin-bottom: 2rem;
}

.form-section:last-of-type {
  margin-bottom: 1rem;
}

.form-section h4 {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid #f1f5f9;
}

.form-group {
  margin-bottom: 1.5rem;
}

.form-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 1rem;
}

label {
  display: block;
  font-weight: 500;
  color: #374151;
  margin-bottom: 0.5rem;
  font-size: 0.875rem;
}

.form-input {
  width: 100%;
  padding: 0.75rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 0.875rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-input:invalid {
  border-color: #dc2626;
}

.form-help {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.75rem;
  color: #6b7280;
  line-height: 1.4;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  font-weight: normal;
}

.form-checkbox {
  width: auto;
  margin: 0;
}

.form-actions {
  display: flex;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #e2e8f0;
}

.btn {
  flex: 1;
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

.test-result {
  margin-top: 1rem;
  padding: 1rem;
  border-radius: 8px;
  border: 1px solid;
}

.test-result.success {
  background: #dcfce7;
  color: #166534;
  border-color: #bbf7d0;
}

.test-result.error {
  background: #fee2e2;
  color: #991b1b;
  border-color: #fecaca;
}

.test-result.info {
  background: #dbeafe;
  color: #1e40af;
  border-color: #bfdbfe;
}

.result-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.result-icon {
  flex-shrink: 0;
}

.result-message {
  font-size: 0.875rem;
  line-height: 1.4;
}

@media (max-width: 768px) {
  .form-row {
    grid-template-columns: 1fr;
  }
  
  .form-actions {
    flex-direction: column;
  }
}
</style>
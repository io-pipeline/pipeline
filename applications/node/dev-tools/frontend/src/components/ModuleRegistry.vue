<template>
  <div class="module-registry">
    <div class="header">
      <h3>Module Registry</h3>
      <button @click="showAddModule = true" class="add-button">+ Add Module</button>
    </div>

    <!-- Add Module Form -->
    <div v-if="showAddModule" class="add-module-form">
      <input 
        v-model="newModuleAddress" 
        placeholder="localhost:39101"
        @keyup.enter="connectModule"
        class="address-input"
      />
      <button @click="connectModule" :disabled="!newModuleAddress || connecting">
        {{ connecting ? 'Connecting...' : 'Connect' }}
      </button>
      <button @click="cancelAdd" class="cancel-button">Cancel</button>
    </div>

    <!-- Connected Modules List -->
    <div class="modules-list">
      <div 
        v-for="module in connectedModules" 
        :key="module.address"
        class="module-card"
        :class="{ 
          active: module.address === activeModuleAddress,
          serving: module.healthStatus === 'SERVING',
          'not-serving': module.healthStatus === 'NOT_SERVING',
          unknown: !module.healthStatus || module.healthStatus === 'UNKNOWN'
        }"
        @click="configureModule(module)"
      >
        <div class="module-content">
          <span class="module-name" :class="{ 'disconnected-text': module.healthStatus === 'NOT_SERVING' }">
            {{ module.name }}
          </span>
          <span class="module-address">({{ module.address }})</span>
        </div>
        
        <div class="module-actions">
          <button @click.stop="refreshModule(module.address)" class="mini-icon-button" title="Refresh">
            🔄
          </button>
          <button @click.stop="removeModule(module.address)" class="mini-icon-button" title="Remove">
            ×
          </button>
        </div>
      </div>

      <div v-if="connectedModules.length === 0" class="empty-state">
        No modules connected. Click "Add Module" to get started.
      </div>
    </div>

    <!-- Quick Connect Buttons -->
    <div class="quick-connect">
      <h4>Quick Connect:</h4>
      <div class="quick-buttons">
        <button @click="quickConnect('localhost:39101')" class="quick-button">
          Parser (39101)
        </button>
        <button @click="quickConnect('localhost:39100')" class="quick-button">
          Echo (39100)
        </button>
        <button @click="quickConnect('localhost:39102')" class="quick-button">
          Chunker (39102)
        </button>
        <button @click="quickConnect('localhost:39103')" class="quick-button">
          Embedder (39103)
        </button>
        <button @click="quickConnect('localhost:39104')" class="quick-button">
          OpenSearch Sink (39104)
        </button>
      </div>
    </div>

    <div v-if="error" class="error-message">
      {{ error }}
    </div>
    
    <!-- Navigation button -->
    <div v-if="activeModuleAddress" class="navigation-buttons">
      <button @click="emit('navigate-to-config')" class="next-button">
        Next: Configure {{ activeModule?.name }} →
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useModuleStore } from '../stores/moduleStore'

const emit = defineEmits<{
  'navigate-to-config': []
}>()

const { 
  connectedModules, 
  activeModuleAddress,
  activeModule,
  addModule, 
  removeModule, 
  setActiveModule,
  refreshModule 
} = useModuleStore()

const showAddModule = ref(false)
const newModuleAddress = ref('')
const connecting = ref(false)
const error = ref('')

const connectModule = async () => {
  if (!newModuleAddress.value) return
  
  connecting.value = true
  error.value = ''
  
  try {
    await addModule(newModuleAddress.value)
    newModuleAddress.value = ''
    showAddModule.value = false
  } catch (err: any) {
    error.value = `Failed to connect to ${newModuleAddress.value}: ${err.message}`
  } finally {
    connecting.value = false
  }
}

const quickConnect = async (address: string) => {
  newModuleAddress.value = address
  await connectModule()
}

const cancelAdd = () => {
  showAddModule.value = false
  newModuleAddress.value = ''
  error.value = ''
}

const configureModule = (module: any) => {
  setActiveModule(module.address)
  emit('navigate-to-config')
}
</script>

<style scoped>
.module-registry {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1.5rem;
  margin-bottom: 1.5rem;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.header h3 {
  margin: 0;
  color: #333;
}

.add-button {
  padding: 0.5rem 1rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.9rem;
}

.add-button:hover {
  background: #357abd;
}

.add-module-form {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1rem;
  padding: 1rem;
  background: #f5f5f5;
  border-radius: 4px;
}

.address-input {
  flex: 1;
  padding: 0.5rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

.cancel-button {
  padding: 0.5rem 1rem;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
}

.modules-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.module-card {
  border: 2px solid #dee2e6;
  border-radius: 6px;
  padding: 0.75rem 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
}

.module-card:hover {
  border-color: #4a90e2;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.module-card.active {
  border-color: #4a90e2;
  background: #f0f7ff;
}

/* Health status-based styling */
.module-card.serving {
  border-color: #28a745;
}

.module-card.not-serving {
  border-color: #dc3545;
  background: #fff5f5;
}

.module-card.unknown {
  border-color: #ffc107;
  background: #fffbf0;
}

.module-content {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.module-name {
  font-weight: 600;
  color: #333;
  font-size: 1rem;
}

.module-name.disconnected-text {
  color: #6c757d;
  text-decoration: line-through;
}

.module-address {
  font-size: 0.85rem;
  color: #6c757d;
  font-family: monospace;
}

.module-actions {
  display: flex;
  gap: 0.25rem;
}

.mini-icon-button {
  width: 24px;
  height: 24px;
  border: 1px solid #dee2e6;
  background: white;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  color: #6c757d;
  transition: all 0.2s ease;
}

.mini-icon-button:hover {
  background: #f8f9fa;
  border-color: #adb5bd;
  color: #495057;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: #999;
  font-style: italic;
}

.quick-connect {
  border-top: 1px solid #eee;
  padding-top: 1rem;
}

.quick-connect h4 {
  margin: 0 0 0.5rem 0;
  color: #666;
  font-size: 0.9rem;
}

.quick-buttons {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 0.5rem;
}

.quick-button {
  padding: 0.5rem;
  background: #f5f5f5;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
}

.quick-button:hover {
  background: #e8e8e8;
}

.error-message {
  margin-top: 1rem;
  padding: 0.5rem;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c00;
  font-size: 0.9rem;
}

/* Navigation */
.navigation-buttons {
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid #eee;
  display: flex;
  justify-content: flex-end;
}

.next-button {
  padding: 0.75rem 1.5rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.next-button:hover {
  background: #357abd;
}
</style>
<template>
  <div class="app">
    <header class="app-header">
      <div class="header-content">
        <div class="header-left">
          <h1>Pipeline Developer Tools</h1>
          <p class="tagline">Design, test, and prototype document processing pipelines</p>
        </div>
        
        <div v-if="activeModule" class="active-status-card">
          <div class="status-item">
            <span class="status-label">Active Module:</span>
            <span class="status-value">{{ activeModule.name }}</span>
          </div>
          <div class="status-item">
            <span class="status-label">Status:</span>
            <span class="status-value" :class="getStatusClass(activeModule.healthStatus)">
              {{ getStatusText(activeModule.healthStatus) }}
            </span>
          </div>
          <div v-if="currentConfigId" class="status-item">
            <span class="status-label">Config:</span>
            <span class="status-value">{{ getConfigName(currentConfigId) }}</span>
          </div>
        </div>
      </div>
    </header>
    
    <nav class="tabs">
      <button 
        v-for="tab in tabs" 
        :key="tab.id"
        @click="activeTab = tab.id"
        :class="{ active: activeTab === tab.id }"
        class="tab-button"
      >
        {{ tab.label }}
      </button>
    </nav>
    
    <main>
      <!-- Module Registry Tab -->
      <div v-if="activeTab === 'registry'" class="tab-content">
        <ModuleRegistry @navigate-to-config="activeTab = 'config'" />
      </div>
      
      <!-- Module Config Tab (Original functionality) -->
      <div v-if="activeTab === 'config'" class="tab-content">
        <div v-if="!activeModule" class="empty-state">
          <p>No module selected. Go to Module Registry to connect a module.</p>
          <button @click="activeTab = 'registry'" class="primary-button">
            Go to Registry
          </button>
        </div>
        
        <div v-else>
          <!-- Config Selector -->
          <ConfigSelector
            ref="configSelectorRef"
            :module-address="activeModule.address"
            :module-name="activeModule.name"
            :current-config="currentConfig"
            @config-selected="handleConfigSelected"
            @config-changed="handleConfigIdChanged"
          />
      
          <!-- UniversalConfigCard handles both schema and no-schema cases -->
          <UniversalConfigCard 
            v-if="activeModule.schema"
            :schema="activeModule.schema"
            :initial-data="currentConfig"
            @data-change="handleConfigChange"
          />
          
          <!-- Navigation button -->
          <div class="navigation-buttons">
            <button @click="activeTab = 'data'" class="next-button">
              Next: Data Seeding →
            </button>
          </div>
        </div>
      </div>
      
      <!-- Data Seeding Tab -->
      <div v-if="activeTab === 'data'" class="tab-content">
        <h2>Data Seeding</h2>
        <p>Create test data for pipeline processing</p>
        
        <div v-if="!activeModule" class="empty-state">
          <p>No module selected. Go to Module Registry to connect a module.</p>
          <button @click="activeTab = 'registry'" class="primary-button">
            Go to Registry
          </button>
        </div>
        
        <div v-else>
          <!-- Seed Data Builder -->
          <SeedDataBuilder 
            :current-config="currentConfig"
            @request-created="handleRequestCreated"
          />
      
          <!-- Request Executor -->
          <RequestExecutor
            v-if="currentRequest"
            :request="currentRequest"
            :module-address="activeModule.address"
            :config-data="currentConfig"
            @response-received="handleResponseReceived"
          />
          
          <!-- Navigation buttons -->
          <div class="navigation-buttons">
            <button @click="activeTab = 'config'" class="prev-button">
              ← Back: Module Config
            </button>
            <button @click="activeTab = 'pipeline'" class="next-button">
              Next: Pipeline →
            </button>
          </div>
        </div>
      </div>
      
      <!-- Pipeline Tab -->
      <div v-if="activeTab === 'pipeline'" class="tab-content">
        <h2>Simple Pipeline</h2>
        <p>Chain modules together for sequential processing</p>
        
        <div v-if="connectedModules.length < 2" class="empty-state">
          <p>You need at least 2 connected modules to create a pipeline.</p>
          <button @click="activeTab = 'registry'" class="primary-button">
            Go to Registry
          </button>
        </div>
        
        <div v-else>
          <!-- TODO: Add simple pipeline chaining -->
          <p>Pipeline builder coming soon...</p>
          
          <!-- Navigation button -->
          <div class="navigation-buttons">
            <button @click="activeTab = 'data'" class="prev-button">
              ← Back: Data Seeding
            </button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import UniversalConfigCard from './components/UniversalConfigCard.vue'
import SeedDataBuilder from './components/SeedDataBuilder.vue'
import RequestExecutor from './components/RequestExecutor.vue'
import ModuleRegistry from './components/ModuleRegistry.vue'
import ConfigSelector from './components/ConfigSelector.vue'
import { useModuleStore } from './stores/moduleStore'
import { useConfigStore } from './stores/configStore'

interface ModuleData {
  module_name: string
  version: string
  description?: string
  module_type?: string
  schema: any
  raw_schema: string
}

// Module store
const moduleStore = useModuleStore()
const { getConfig } = useConfigStore()

// Get activeModule as computed to ensure reactivity
const activeModule = computed(() => moduleStore.activeModule.value)
const connectedModules = computed(() => moduleStore.connectedModules.value)

// Local state
const currentRequest = ref<any>(null)
const currentConfig = ref<any>({})
const currentConfigId = ref<string>('')
const configSelectorRef = ref<any>(null)

// Debug logging
watch(activeModule, (newModule) => {
  console.log('Active module changed:', newModule)
  if (newModule) {
    console.log('Module has schema:', !!newModule.schema)
  }
}, { immediate: true })

// Tab management
const activeTab = ref('registry')
const tabs = [
  { id: 'registry', label: 'Module Registry' },
  { id: 'config', label: 'Module Config' },
  { id: 'data', label: 'Data Seeding' },
  { id: 'pipeline', label: 'Pipeline' }
]

// Handle config selection
const handleConfigSelected = (config: any) => {
  currentConfig.value = config
  console.log('Config selected:', config)
}

const handleConfigIdChanged = (configId: string) => {
  currentConfigId.value = configId
}

const handleConfigChange = (data: any) => {
  currentConfig.value = data
  // Update the config in the store via ConfigSelector
  if (configSelectorRef.value) {
    configSelectorRef.value.updateCurrentConfig(data)
  }
  console.log('Config changed:', data)
}

const handleRequestCreated = (request: any) => {
  currentRequest.value = request
  console.log('Request created:', request)
}

const handleResponseReceived = (response: any) => {
  console.log('Response received:', response)
  // TODO: Add functionality to save response as .bin or use as input for next module
}

// Helper to get config name
const getConfigName = (configId: string) => {
  const config = getConfig(configId).value
  return config?.name || 'Unknown Config'
}

// Helper to get status text
const getStatusText = (healthStatus?: string) => {
  switch (healthStatus) {
    case 'SERVING':
      return 'Connected'
    case 'NOT_SERVING':
      return 'Disconnected'
    default:
      return 'Unknown'
  }
}

// Helper to get status class
const getStatusClass = (healthStatus?: string) => {
  switch (healthStatus) {
    case 'SERVING':
      return 'status-connected'
    case 'NOT_SERVING':
      return 'status-disconnected'
    default:
      return 'status-unknown'
  }
}
</script>

<style scoped>
.app {
  min-height: 100vh;
  background: #f8f9fa;
}

/* Header */
.app-header {
  background: white;
  border-bottom: 2px solid #e9ecef;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 1.5rem 2rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.header-left h1 {
  margin: 0 0 0.25rem 0;
  color: #333;
  font-size: 1.75rem;
}

.tagline {
  margin: 0;
  color: #666;
  font-size: 0.95rem;
}

/* Active Status Card */
.active-status-card {
  background: #f8f9fa;
  border: 1px solid #dee2e6;
  border-radius: 8px;
  padding: 1rem 1.5rem;
  min-width: 250px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.status-item:last-child {
  margin-bottom: 0;
}

.status-label {
  font-size: 0.85rem;
  color: #6c757d;
  font-weight: 500;
}

.status-value {
  font-size: 0.95rem;
  color: #333;
  font-weight: 600;
}

/* Status colors */
.status-connected {
  color: #28a745;
}

.status-disconnected {
  color: #dc3545;
}

.status-unknown {
  color: #ffc107;
}

/* Main content */
main {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 2rem 2rem;
}

.module-info {
  background: #f5f5f5;
  padding: 1.5rem;
  border-radius: 8px;
  margin: 2rem 0;
}

.module-info h2 {
  margin: 0 0 0.5rem 0;
  color: #333;
}

.module-info p {
  margin: 0.25rem 0;
  color: #666;
}

.version {
  font-size: 0.9rem;
  font-style: italic;
}

/* Tab Navigation */
.tabs {
  max-width: 1200px;
  margin: 2rem auto 1.5rem;
  padding: 0 2rem;
  display: flex;
  gap: 1rem;
  border-bottom: 2px solid #e9ecef;
}

.tab-button {
  padding: 0.75rem 1.5rem;
  background: none;
  border: none;
  border-bottom: 3px solid transparent;
  color: #666;
  font-size: 1rem;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: -2px;
}

.tab-button:hover {
  color: #333;
}

.tab-button.active {
  color: #4a90e2;
  border-bottom-color: #4a90e2;
  font-weight: 600;
}

.tab-content {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Empty state */
.empty-state {
  text-align: center;
  padding: 3rem;
  color: #666;
}

.empty-state p {
  margin-bottom: 1.5rem;
  font-size: 1.1rem;
}

.primary-button {
  padding: 0.75rem 1.5rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.primary-button:hover {
  background: #357abd;
}


/* Navigation buttons */
.navigation-buttons {
  display: flex;
  justify-content: space-between;
  margin-top: 2rem;
  padding-top: 2rem;
  border-top: 1px solid #eee;
}

.next-button, .prev-button {
  padding: 0.75rem 1.5rem;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.next-button {
  background: #4a90e2;
  color: white;
  margin-left: auto;
}

.next-button:hover {
  background: #357abd;
}

.prev-button {
  background: #f5f5f5;
  color: #333;
}

.prev-button:hover {
  background: #e8e8e8;
}
</style>
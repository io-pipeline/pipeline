<template>
  <div class="app">
    <header>
      <h1>Pipeline Developer Tools</h1>
      <p>Connect to your module and preview its configuration UI</p>
    </header>
    
    <main>
      <ModuleConnector @schema-loaded="handleSchemaLoaded" />
      
      <div v-if="moduleData" class="module-info">
        <h2>{{ moduleData.module_name }}</h2>
        <p v-if="moduleData.description">{{ moduleData.description }}</p>
        <p class="version">Version: {{ moduleData.version || 'Unknown' }}</p>
      </div>
      
      <!-- UniversalConfigCard handles both schema and no-schema cases -->
      <UniversalConfigCard 
        v-if="moduleData"
        :schema="schema"
        :initial-data="configData"
        @data-change="handleDataChange"
      />
      
      <!-- Seed Data Builder -->
      <SeedDataBuilder 
        v-if="moduleData"
        :current-config="configData"
        @request-created="handleRequestCreated"
      />
      
      <!-- Request Executor -->
      <RequestExecutor
        v-if="currentRequest && moduleData"
        :request="currentRequest"
        :module-address="moduleAddress"
        :config-data="configData"
        @response-received="handleResponseReceived"
      />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ModuleConnector from './components/ModuleConnector.vue'
import UniversalConfigCard from './components/UniversalConfigCard.vue'
import SeedDataBuilder from './components/SeedDataBuilder.vue'
import RequestExecutor from './components/RequestExecutor.vue'

interface ModuleData {
  module_name: string
  version: string
  description?: string
  module_type?: string
  schema: any
  raw_schema: string
}

const moduleData = ref<ModuleData | null>(null)
const schema = ref<any>(null)
const configData = ref<any>({})
const currentRequest = ref<any>(null)
const moduleAddress = ref<string>('')

const handleSchemaLoaded = (data: ModuleData & { address?: string }) => {
  moduleData.value = data
  schema.value = data.schema
  moduleAddress.value = data.address || ''
  // Reset config data when loading a new module
  configData.value = {}
  console.log('Schema loaded:', data)
}

const handleDataChange = (data: any) => {
  configData.value = data
  console.log('Config data changed:', data)
}

const handleRequestCreated = (request: any) => {
  currentRequest.value = request
  console.log('Request created:', request)
}

const handleResponseReceived = (response: any) => {
  console.log('Response received:', response)
  // TODO: Add functionality to save response as .bin or use as input for next module
}
</script>

<style scoped>
.app {
  max-width: 1200px;
  margin: 0 auto;
  padding: 2rem;
}

header {
  text-align: center;
  margin-bottom: 3rem;
}

header h1 {
  color: #333;
  margin-bottom: 0.5rem;
}

header p {
  color: #666;
  font-size: 1.1rem;
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
</style>
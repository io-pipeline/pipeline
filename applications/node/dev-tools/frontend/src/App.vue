<template>
  <v-app>
    <v-app-bar color="primary" dark>
      <v-container fluid class="d-flex align-center justify-space-between">
        <div>
          <v-app-bar-title class="text-white">Pipeline Developer Tools</v-app-bar-title>
          <div class="text-caption text-white">Design, test, and prototype document processing pipelines</div>
        </div>
        
        <!-- Status Indicators -->
        <div class="d-flex align-center">
          <!-- Repository Service Status -->
          <v-tooltip location="bottom">
            <template v-slot:activator="{ props }">
              <v-btn
                v-bind="props"
                :icon="getRepositoryIcon()"
                :color="getRepositoryStatusColor()"
                variant="text"
                size="small"
                @click="checkRepositoryHealth"
                class="mr-2"
              />
            </template>
            <span>{{ getRepositoryStatusText() }}</span>
          </v-tooltip>
          
          <v-btn
            :icon="theme.global.name.value === 'dark' ? 'mdi-weather-sunny' : 'mdi-weather-night'"
            @click="toggleTheme"
            color="white"
          />
        </div>
      </v-container>
    </v-app-bar>
    
    <v-main>
      <v-container fluid>
        <v-sheet elevation="2" rounded="lg" class="mb-4">
          <v-tabs 
            v-model="activeTab" 
            color="primary"
            align-tabs="center"
            height="64"
            show-arrows
          >
            <v-tab value="registry" prepend-icon="mdi-puzzle-outline">
              Module Registry
            </v-tab>
            <v-tab value="config" prepend-icon="mdi-cog-outline">
              Module Config
            </v-tab>
            <v-tab v-if="!databaseStore.isConnected" value="data" prepend-icon="mdi-database-outline">
              Data Seeding
            </v-tab>
            <v-tab v-else value="data-enhanced" prepend-icon="mdi-database-plus-outline">
              Data Seeding
            </v-tab>
            <v-tab value="process" prepend-icon="mdi-file-document-outline">
              Process Document
            </v-tab>
            <v-tab value="admin" prepend-icon="mdi-shield-crown-outline">
              Admin
            </v-tab>
          </v-tabs>
        </v-sheet>
        
        <v-tabs-window v-model="activeTab" :key="activeTab">
          <!-- Module Registry Tab -->
          <v-tabs-window-item value="registry">
            <ModuleRegistry @navigate-to-config="() => nextTick(() => activeTab = 'config')" />
          </v-tabs-window-item>
          
          <!-- Module Config Tab -->
          <v-tabs-window-item value="config">
            <div v-if="moduleStore.activeModule">
              <ConfigSelector
                :module-address="moduleStore.activeModuleAddress"
                :module-name="moduleStore.activeModule.name"
                :current-config="currentConfig"
                @config-selected="handleConfigSelected"
                @config-changed="handleConfigChanged"
                ref="configSelector"
              />
              
              <UniversalConfigCard
                v-if="moduleStore.activeModule.schema"
                :schema="moduleStore.activeModule.schema"
                v-model="currentConfig"
                @update:modelValue="handleConfigUpdate"
              />
            </div>
            <v-empty-state
              v-else
              icon="mdi-cog-outline"
              headline="No module selected"
              text="Select a module from the registry to configure it"
            />
          </v-tabs-window-item>
          
          <!-- Data Seeding Tab (Basic) -->
          <v-tabs-window-item value="data">
            <div v-if="moduleStore.activeModule">
              <v-alert
                type="info"
                variant="tonal"
                class="mb-4"
              >
                <v-alert-title>Test Data Creation</v-alert-title>
                <div>Create seed data to test {{ moduleStore.activeModule.name }} with your current configuration</div>
              </v-alert>
              
              <SeedDataBuilder
                :current-config="currentConfig"
                @request-created="handleRequestCreated"
              />
            </div>
            <v-empty-state
              v-else
              icon="mdi-database-outline"
              headline="No module selected"
              text="Select a module from the registry to create test data"
            />
          </v-tabs-window-item>
          
          <!-- Data Seeding Tab (Enhanced with MongoDB) -->
          <v-tabs-window-item value="data-enhanced">
            <DataSeedingEnhanced />
          </v-tabs-window-item>
          
          <!-- Process Document Tab -->
          <v-tabs-window-item value="process">
            <div v-if="moduleStore.activeModule">
              <ProcessDocument 
                :current-config="currentConfig"
                :created-request="createdRequest"
                @request-updated="handleRequestUpdated"
              />
            </div>
            <v-empty-state
              v-else
              icon="mdi-file-document-outline"
              headline="No module selected"
              text="Select a module from the registry to process documents"
            />
          </v-tabs-window-item>
          
          <!-- Admin Tab -->
          <v-tabs-window-item value="admin">
            <v-container fluid>
              <!-- Storage Management -->
              <v-card>
                <v-card-title>Storage Management</v-card-title>
                <v-card-subtitle>Administrative functions and debugging tools</v-card-subtitle>
                
                <v-divider />
                
                <v-card-text>
                  <h3 class="text-h6 mb-2">Local Storage Management</h3>
                  <p class="text-body-2 mb-4">Clear locally stored data to reset the application state.</p>
                  
                  <v-row>
                    <v-col cols="12" sm="4">
                      <v-btn
                        color="error"
                        variant="flat"
                        block
                        @click="clearAllStorage"
                        prepend-icon="mdi-delete-alert"
                      >
                        Clear All Storage
                      </v-btn>
                    </v-col>
                    <v-col cols="12" sm="4">
                      <v-btn
                        color="warning"
                        variant="flat"
                        block
                        @click="clearModuleStorage"
                        prepend-icon="mdi-delete"
                      >
                        Clear Module Storage Only
                      </v-btn>
                    </v-col>
                    <v-col cols="12" sm="4">
                      <v-btn
                        color="warning"
                        variant="flat"
                        block
                        @click="clearConfigStorage"
                        prepend-icon="mdi-delete"
                      >
                        Clear Config Storage Only
                      </v-btn>
                    </v-col>
                  </v-row>
                  
                  <v-alert
                    v-if="storageCleared"
                    type="success"
                    variant="tonal"
                    closable
                    @click:close="storageCleared = ''"
                    class="mt-4"
                  >
                    {{ storageCleared }}
                  </v-alert>
                </v-card-text>
              </v-card>
              
              <!-- MongoDB Filesystem Browser -->
              <v-card class="mt-4">
                <v-card-title>MongoDB Filesystem Browser</v-card-title>
                <v-card-subtitle>Browse and manage documents in a virtual filesystem</v-card-subtitle>
                <v-divider />
                <v-card-text>
                  <!-- Placeholder for filesystem browser -->
                  <v-alert type="info" variant="tonal">
                    Filesystem browser coming soon...
                  </v-alert>
                </v-card-text>
              </v-card>
            </v-container>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useTheme } from 'vuetify'
import ModuleRegistry from './components/ModuleRegistry.vue'
import ConfigSelector from './components/ConfigSelector.vue'
import UniversalConfigCard from './components/UniversalConfigCard.vue'
import SeedDataBuilder from './components/SeedDataBuilder.vue'
import ProcessDocument from './components/ProcessDocument.vue'
import DataSeedingEnhanced from './components/DataSeedingEnhanced.vue'
import { useModuleStore } from './stores/moduleStore'
import { useConfigStore } from './stores/configStore'
import { useDatabaseStore } from './stores/databaseStore'

const theme = useTheme()
const moduleStore = useModuleStore()
const configStore = useConfigStore()
const databaseStore = useDatabaseStore()

// Toggle theme
const toggleTheme = () => {
  const newTheme = theme.global.name.value === 'dark' ? 'light' : 'dark'
  theme.change(newTheme)
}

// Initialize stores
onMounted(() => {
  moduleStore.init()
  configStore.init()
  databaseStore.init()
  checkRepositoryHealth()
  // Check repository health periodically
  setInterval(checkRepositoryHealth, 30000) // every 30 seconds
})

const activeTab = ref('registry')
const currentConfig = ref<any>({})
const configSelector = ref<any>(null)
const createdRequest = ref<any>(null)
const storageCleared = ref('')
const repositoryHealthy = ref(false)
const repositoryChecking = ref(false)

// Handle config selection
const handleConfigSelected = (config: any) => {
  currentConfig.value = config
}

// Handle config ID change
const handleConfigChanged = (configId: string) => {
  moduleStore.setCurrentConfigId(configId)
}

// Handle config updates
const handleConfigUpdate = (newConfig: any) => {
  currentConfig.value = newConfig
  if (configSelector.value) {
    configSelector.value.updateCurrentConfig(newConfig)
  }
}

// Handle seed data request creation
const handleRequestCreated = (request: any) => {
  createdRequest.value = request
  // Switch to Process Document tab with proper timing
  nextTick(() => {
    activeTab.value = 'process'
  })
}

// Handle request update from Process Document tab
const handleRequestUpdated = (request: any) => {
  createdRequest.value = request
}

// Navigate to admin tab
const navigateToAdmin = () => {
  // Use nextTick to ensure proper component lifecycle
  nextTick(() => {
    activeTab.value = 'admin'
  })
}

// Repository Service health check
const checkRepositoryHealth = async () => {
  repositoryChecking.value = true
  try {
    const response = await fetch('http://localhost:3000/api/repository-health')
    const data = await response.json()
    repositoryHealthy.value = data.healthy
  } catch (error) {
    repositoryHealthy.value = false
  } finally {
    repositoryChecking.value = false
  }
}

// Repository status helpers
const getRepositoryIcon = () => {
  if (repositoryChecking.value) return 'mdi-loading'
  if (repositoryHealthy.value) return 'mdi-server-network'
  return 'mdi-server-network-off'
}

const getRepositoryStatusColor = () => {
  if (repositoryChecking.value) return 'blue'
  if (repositoryHealthy.value) return 'green'
  return 'red'
}

const getRepositoryStatusText = () => {
  if (repositoryChecking.value) return 'Checking repository service...'
  if (repositoryHealthy.value) return 'Repository Service Connected'
  return 'Repository Service Offline'
}


// Admin functions
const clearAllStorage = () => {
  localStorage.clear()
  storageCleared.value = 'All local storage cleared. Reloading page...'
  setTimeout(() => {
    window.location.reload()
  }, 1500)
}

const clearModuleStorage = () => {
  localStorage.removeItem('pipeline-dev-tools-state')
  storageCleared.value = 'Module storage cleared. Reloading page...'
  setTimeout(() => {
    window.location.reload()
  }, 1500)
}

const clearConfigStorage = () => {
  localStorage.removeItem('pipeline-module-configs')
  storageCleared.value = 'Config storage cleared.'
  setTimeout(() => {
    storageCleared.value = ''
  }, 3000)
}
</script>

<style scoped>
/* Rotating animation for launching state */
.rotate-animation :deep(.v-icon) {
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
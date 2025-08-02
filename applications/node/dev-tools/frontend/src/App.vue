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
          <RepositoryConnectionStatus 
            @click="showRepositoryConfig = true"
            class="mr-3"
          />
          
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
            <v-tab value="pipedoc" prepend-icon="mdi-file-document-plus-outline">
              PipeDoc Generator
            </v-tab>
            <v-tab v-if="repositoryStore.isConnected" value="data-seeding" prepend-icon="mdi-database-plus-outline">
              Data Seeding
            </v-tab>
            <v-tab value="process" prepend-icon="mdi-file-document-outline">
              Process Document
            </v-tab>
            <v-tab value="protobuf" prepend-icon="mdi-file-code-outline">
              Protobuf Browser
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
          
          <!-- PipeDoc Generator Tab -->
          <v-tabs-window-item value="pipedoc">
            <div v-if="moduleStore.activeModule">
              <v-alert
                type="info"
                variant="tonal"
                class="mb-4"
              >
                <v-alert-title>PipeDoc Generator</v-alert-title>
                <div>Create test documents to test {{ moduleStore.activeModule.name }} with your current configuration</div>
              </v-alert>
              
              <SeedDataBuilder
                :current-config="currentConfig"
                @request-created="handleRequestCreated"
                @process-document="handleProcessDocument"
              />
            </div>
            <v-empty-state
              v-else
              icon="mdi-file-document-plus-outline"
              headline="No module selected"
              text="Select a module from the registry to generate test documents"
            />
          </v-tabs-window-item>
          
          <!-- Data Seeding Tab (Repository Filesystem Browser) -->
          <v-tabs-window-item value="data-seeding">
            <RepositoryDataSeeding />
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
          
          <!-- Protobuf Browser Tab -->
          <v-tabs-window-item value="protobuf">
            <ProtobufBrowser />
          </v-tabs-window-item>
          
          <!-- Admin Tab -->
          <v-tabs-window-item value="admin">
            <v-container fluid>
              <!-- Repository Configuration -->
              <v-card>
                <v-card-title>Repository Service Configuration</v-card-title>
                <v-card-subtitle>Configure connection to the document repository</v-card-subtitle>
                <v-divider />
                <v-card-text>
                  <div class="d-flex align-center mb-4">
                    <RepositoryConnectionStatus 
                      :show-address="true"
                      @click="showRepositoryConfig = true"
                      class="mr-3"
                    />
                    <v-btn
                      color="primary"
                      variant="tonal"
                      @click="showRepositoryConfig = true"
                    >
                      Configure Connection
                    </v-btn>
                  </div>
                  <v-alert
                    type="info"
                    variant="tonal"
                    density="compact"
                  >
                    The repository service manages document storage. Connection will automatically retry if disconnected.
                  </v-alert>
                </v-card-text>
              </v-card>
              
              <!-- Storage Management -->
              <v-card class="mt-4">
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
              
              <!-- Repository Management -->
              <v-card class="mt-4">
                <v-card-title>Repository Management</v-card-title>
                <v-card-subtitle>Manage repository storage</v-card-subtitle>
                
                <v-divider />
                
                <v-card-text>
                  <h3 class="text-h6 mb-2">Data Storage Management</h3>
                  <p class="text-body-2 mb-4">Permanently delete data from the repository. These operations cannot be undone.</p>
                  
                  <v-row>
                    <v-col cols="12" md="6">
                      <v-card variant="outlined">
                        <v-card-title class="text-h6">Format Filesystem</v-card-title>
                        <v-card-text>
                          <p class="text-body-2 mb-3">
                            Deletes all nodes and folders from the virtual filesystem. This includes all stored files, 
                            folders, and their metadata.
                          </p>
                          <v-btn
                            color="error"
                            variant="flat"
                            block
                            @click="showFormatFilesystemDialog = true"
                            prepend-icon="mdi-folder-remove"
                            :loading="clearingFilesystem"
                          >
                            Format Filesystem
                          </v-btn>
                        </v-card-text>
                      </v-card>
                    </v-col>
                    
                    <v-col cols="12" md="6">
                      <v-card variant="outlined">
                        <v-card-title class="text-h6">Format Repository</v-card-title>
                        <v-card-text>
                          <p class="text-body-2 mb-3">
                            Deletes all PipeDoc documents and process requests from the repository. This clears 
                            all test data and saved requests.
                          </p>
                          <v-btn
                            color="error"
                            variant="flat"
                            block
                            @click="showFormatRepositoryDialog = true"
                            prepend-icon="mdi-database-remove"
                            :loading="clearingPipeDocRepository"
                          >
                            Format Repository
                          </v-btn>
                        </v-card-text>
                      </v-card>
                    </v-col>
                  </v-row>
                  
                  <v-alert
                    v-if="repositoryCleared"
                    :type="repositoryCleared.type"
                    variant="tonal"
                    closable
                    @click:close="repositoryCleared = null"
                    class="mt-4"
                  >
                    {{ repositoryCleared.message }}
                  </v-alert>
                </v-card-text>
              </v-card>
              
              <!-- Connect Integration Test -->
              <ConnectHealthCheck class="mt-4" />
            </v-container>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-container>
    </v-main>
    
    <!-- Repository Configuration Dialog -->
    <RepositoryConfigDialog v-model="showRepositoryConfig" />
    
    <!-- Format Filesystem Confirmation Dialog -->
    <v-dialog v-model="showFormatFilesystemDialog" max-width="600">
      <v-card>
        <v-card-title class="text-h5">Format Filesystem - Confirmation Required</v-card-title>
        <v-card-text>
          <v-alert type="warning" variant="tonal" class="mb-4">
            This operation will permanently delete all nodes and folders from the virtual filesystem.
            This action cannot be undone.
          </v-alert>
          
          <p class="mb-4">
            To confirm this operation, please type <strong>DELETE_FILESYSTEM_DATA</strong> below:
          </p>
          
          <v-text-field
            v-model="filesystemConfirmation"
            label="Confirmation"
            placeholder="Type DELETE_FILESYSTEM_DATA"
            variant="outlined"
            :error-messages="filesystemConfirmationError"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="closeFormatFilesystemDialog">Cancel</v-btn>
          <v-btn 
            color="error" 
            variant="flat" 
            @click="executeFormatFilesystem"
            :disabled="filesystemConfirmation !== 'DELETE_FILESYSTEM_DATA'"
            :loading="clearingFilesystem"
          >
            Format Filesystem
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    
    <!-- Format Repository Confirmation Dialog -->
    <v-dialog v-model="showFormatRepositoryDialog" max-width="600">
      <v-card>
        <v-card-title class="text-h5">Format Repository - Confirmation Required</v-card-title>
        <v-card-text>
          <v-alert type="warning" variant="tonal" class="mb-4">
            This operation will permanently delete all PipeDoc documents and process requests.
            This action cannot be undone.
          </v-alert>
          
          <p class="mb-4">
            To confirm this operation, please type <strong>DELETE_REPOSITORY_DATA</strong> below:
          </p>
          
          <v-text-field
            v-model="repositoryConfirmation"
            label="Confirmation"
            placeholder="Type DELETE_REPOSITORY_DATA"
            variant="outlined"
            :error-messages="repositoryConfirmationError"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="closeFormatRepositoryDialog">Cancel</v-btn>
          <v-btn 
            color="error" 
            variant="flat" 
            @click="executeFormatRepository"
            :disabled="repositoryConfirmation !== 'DELETE_REPOSITORY_DATA'"
            :loading="clearingPipeDocRepository"
          >
            Format Repository
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    
    <!-- Global Snackbar -->
    <v-snackbar
      v-model="snackbar.show"
      :timeout="snackbar.timeout"
      :color="snackbar.color"
      :location="snackbar.location"
    >
      {{ snackbar.message }}
      <template v-slot:actions>
        <v-btn
          variant="text"
          @click="snackbar.show = false"
        >
          Close
        </v-btn>
      </template>
    </v-snackbar>
  </v-app>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useTheme } from 'vuetify'
import ModuleRegistry from './components/ModuleRegistry.vue'
import ConfigSelector from './components/ConfigSelector.vue'
import UniversalConfigCard from './components/UniversalConfigCard.vue'
import SeedDataBuilder from './components/SeedDataBuilder.vue'
import ProcessDocument from './components/ProcessDocument.vue'
import RepositoryDataSeeding from './components/RepositoryDataSeeding.vue'
import ConnectHealthCheck from './components/ConnectHealthCheck.vue'
import RepositoryConnectionStatus from './components/RepositoryConnectionStatus.vue'
import RepositoryConfigDialog from './components/RepositoryConfigDialog.vue'
import ProtobufBrowser from './components/ProtobufFileBrowser.vue'
import { useModuleStore } from './stores/moduleStore'
import { useConfigStore } from './stores/configStore'
import { useDatabaseStore } from './stores/databaseStore'
import { useRepositoryStore } from './stores/repositoryStore'
import { useSnackbar } from './composables/useSnackbar'
import { filesystemService, pipeDocRepositoryService } from './services/connectService'
import { create } from '@bufbuild/protobuf'
import { FormatFilesystemRequestSchema } from '@/gen/filesystem_service_pb'
import { FormatRepositoryRequestSchema as PipeDocFormatRepositoryRequestSchema } from '@/gen/pipedoc_repository_pb'

const theme = useTheme()
const moduleStore = useModuleStore()
const configStore = useConfigStore()
const databaseStore = useDatabaseStore()
const repositoryStore = useRepositoryStore()
const { snackbar } = useSnackbar()

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
})

const activeTab = ref('registry')
const currentConfig = ref<any>({})
const configSelector = ref<any>(null)
const createdRequest = ref<any>(null)
const storageCleared = ref('')
const showRepositoryConfig = ref(false)

// Format operation states
const clearingFilesystem = ref(false)
const clearingPipeDocRepository = ref(false)
const repositoryCleared = ref<{ type: 'success' | 'error'; message: string } | null>(null)

// Format dialogs
const showFormatFilesystemDialog = ref(false)
const showFormatRepositoryDialog = ref(false)
const filesystemConfirmation = ref('')
const repositoryConfirmation = ref('')
const filesystemConfirmationError = ref('')
const repositoryConfirmationError = ref('')

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
  // Don't automatically switch tabs - let the user choose what to do
  // They can click "Process Document" if they want to switch
}

// Handle process document request
const handleProcessDocument = (request: any) => {
  createdRequest.value = request
  // Switch to Process Document tab
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

// Format filesystem dialog handlers
const closeFormatFilesystemDialog = () => {
  showFormatFilesystemDialog.value = false
  filesystemConfirmation.value = ''
  filesystemConfirmationError.value = ''
}

const executeFormatFilesystem = async () => {
  if (filesystemConfirmation.value !== 'DELETE_FILESYSTEM_DATA') {
    filesystemConfirmationError.value = 'Confirmation text does not match'
    return
  }
  
  clearingFilesystem.value = true
  repositoryCleared.value = null
  
  try {
    const request = create(FormatFilesystemRequestSchema, {
      drive: 'default',
      confirmation: filesystemConfirmation.value,
      dryRun: false
    })
    
    const result = await filesystemService.formatFilesystem(request)
    
    repositoryCleared.value = {
      type: 'success',
      message: `Successfully formatted filesystem. Deleted ${result.nodesDeleted} nodes and ${result.foldersDeleted} folders.`
    }
    
    closeFormatFilesystemDialog()
  } catch (error) {
    console.error('Failed to format filesystem:', error)
    repositoryCleared.value = {
      type: 'error',
      message: `Failed to format filesystem: ${error instanceof Error ? error.message : 'Unknown error'}`
    }
  } finally {
    clearingFilesystem.value = false
  }
}

// Format repository dialog handlers
const closeFormatRepositoryDialog = () => {
  showFormatRepositoryDialog.value = false
  repositoryConfirmation.value = ''
  repositoryConfirmationError.value = ''
}

const executeFormatRepository = async () => {
  if (repositoryConfirmation.value !== 'DELETE_REPOSITORY_DATA') {
    repositoryConfirmationError.value = 'Confirmation text does not match'
    return
  }
  
  clearingPipeDocRepository.value = true
  repositoryCleared.value = null
  
  try {
    const request = create(PipeDocFormatRepositoryRequestSchema, {
      confirmation: repositoryConfirmation.value,
      includeDocuments: true,
      includeRequests: true,
      dryRun: false
    })
    
    const result = await pipeDocRepositoryService.formatRepository(request)
    
    repositoryCleared.value = {
      type: 'success',
      message: `Successfully formatted repository. Deleted ${result.documentsDeleted} documents and ${result.requestsDeleted} requests.`
    }
    
    closeFormatRepositoryDialog()
  } catch (error) {
    console.error('Failed to format repository:', error)
    repositoryCleared.value = {
      type: 'error',
      message: `Failed to format repository: ${error instanceof Error ? error.message : 'Unknown error'}`
    }
  } finally {
    clearingPipeDocRepository.value = false
  }
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
<template>
  <v-container fluid class="pa-0">
    <!-- Add Module Button - Subtle placement -->
    <div class="d-flex justify-end mb-3">
      <v-btn
        variant="text"
        size="small"
        @click="showAddModule = true"
        prepend-icon="mdi-plus"
      >
        Add Custom Module
      </v-btn>
    </div>

        <!-- Add Module Dialog -->
        <v-dialog v-model="showAddModule" max-width="500px">
          <v-card>
            <v-card-title>Connect to Module</v-card-title>
            <v-card-text>
              <v-text-field
                v-model="newModuleAddress"
                label="Module Address"
                placeholder="localhost:39101"
                @keyup.enter="connectModule"
                variant="outlined"
                density="comfortable"
                hide-details
              />
            </v-card-text>
            <v-card-actions>
              <v-spacer />
              <v-btn variant="text" @click="cancelAdd">Cancel</v-btn>
              <v-btn
                color="primary"
                variant="flat"
                @click="connectModule"
                :loading="connecting"
                :disabled="!newModuleAddress"
              >
                Connect
              </v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>

        <!-- Error Alert -->
        <v-alert
          v-if="error"
          type="error"
          closable
          @click:close="error = ''"
          class="mb-4"
        >
          {{ error }}
        </v-alert>

        <!-- Connected Modules Grid -->
        <v-row v-if="moduleStore.connectedModules.length > 0">
          <v-col
            v-for="module in moduleStore.connectedModules"
            :key="module.address"
            cols="12"
            sm="6"
            md="4"
          >
            <v-card
              :variant="module.address === moduleStore.activeModuleAddress ? 'elevated' : 'outlined'"
              @click="configureModule(module)"
              class="module-card"
              :class="{ 'active-module': module.address === moduleStore.activeModuleAddress }"
            >
              <v-card-item>
                <template v-slot:prepend>
                  <v-avatar
                    :color="getModuleCardColor(module)"
                    size="56"
                  >
                    <v-icon size="large">mdi-puzzle</v-icon>
                  </v-avatar>
                </template>

                <v-card-title :class="{ 'text-decoration-line-through': module.healthStatus === 'NOT_SERVING' }">
                  {{ module.name }}
                </v-card-title>
                
                <v-card-subtitle>
                  <div>{{ module.address }}</div>
                  <div v-if="module.description" class="text-caption">{{ module.description }}</div>
                </v-card-subtitle>

                <template v-slot:append>
                  <v-chip
                    :color="getHealthColor(module.healthStatus)"
                    size="small"
                    label
                    class="mb-2"
                  >
                    <v-icon start size="x-small">
                      {{ getHealthIcon(module.healthStatus) }}
                    </v-icon>
                    {{ getHealthLabel(module.healthStatus) }}
                  </v-chip>
                </template>
              </v-card-item>

              <v-card-text v-if="module.version">
                <div class="text-caption">
                  <v-icon size="x-small">mdi-tag</v-icon>
                  Version: {{ module.version }}
                </div>
              </v-card-text>

              <v-divider />

              <v-card-actions>
                <v-btn
                  variant="text"
                  color="primary"
                  @click.stop="configureModule(module)"
                >
                  Configure
                </v-btn>
                <v-spacer />
                <v-btn
                  icon="mdi-refresh"
                  size="small"
                  variant="text"
                  @click.stop="refreshModule(module.address)"
                  title="Refresh"
                />
                <v-btn
                  icon="mdi-delete"
                  size="small"
                  variant="text"
                  color="error"
                  @click.stop="removeModule(module.address)"
                  title="Remove"
                />
              </v-card-actions>
            </v-card>
          </v-col>
        </v-row>

        <!-- Empty State -->
        <v-empty-state
          v-else
          icon="mdi-server-network-off"
          headline="No modules connected"
          text="Click 'Add Module' to get started"
        />

        <!-- Quick Connect Section -->
        <v-card variant="outlined" class="mt-4">
          <v-card-title class="text-h6 d-flex align-center">
            <v-icon class="mr-2">mdi-lightning-bolt</v-icon>
            Quick Connect
          </v-card-title>
          
          <v-card-text>
            <v-row>
              <v-col
                v-for="quick in quickConnectOptions"
                :key="quick.address"
                cols="12"
                sm="6"
                md="3"
              >
                <v-btn
                  block
                  :variant="isModuleConnected(quick.address) ? 'tonal' : 'outlined'"
                  :color="isModuleConnected(quick.address) ? 'grey' : 'primary'"
                  :disabled="isModuleConnected(quick.address)"
                  @click="quickConnect(quick.address)"
                  class="text-none"
                >
                  <v-icon start>{{ quick.icon || 'mdi-server' }}</v-icon>
                  <div>
                    <div>{{ quick.name }}</div>
                    <div class="text-caption">{{ quick.port }}</div>
                  </div>
                  <v-icon v-if="isModuleConnected(quick.address)" end>
                    mdi-check-circle
                  </v-icon>
                </v-btn>
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <!-- Navigation -->
        <v-divider v-if="moduleStore.activeModuleAddress" class="mt-4" />
        <div v-if="moduleStore.activeModuleAddress" class="d-flex justify-end mt-4">
          <v-btn
            color="primary"
            @click="emit('navigate-to-config')"
            append-icon="mdi-arrow-right"
          >
            Next: Configure {{ moduleStore.activeModule?.name }}
          </v-btn>
    </div>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useModuleStore } from '../stores/moduleStore'

const emit = defineEmits<{
  'navigate-to-config': []
}>()

const moduleStore = useModuleStore()

const showAddModule = ref(false)
const newModuleAddress = ref('')
const connecting = ref(false)
const error = ref('')

const quickConnectOptions = [
  { 
    name: 'Parser',
    port: '39101',
    address: 'localhost:39101',
    icon: 'mdi-file-document-outline'
  },
  { 
    name: 'Chunker',
    port: '39102', 
    address: 'localhost:39102',
    icon: 'mdi-scissors-cutting'
  },
  { 
    name: 'Embedder',
    port: '39103',
    address: 'localhost:39103',
    icon: 'mdi-vector-combine'
  },
  { 
    name: 'OpenSearch',
    port: '39104',
    address: 'localhost:39104',
    icon: 'mdi-magnify'
  }
]

const connectModule = async () => {
  if (!newModuleAddress.value) return
  
  connecting.value = true
  error.value = ''
  
  try {
    await moduleStore.connectModule(newModuleAddress.value)
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
  moduleStore.setActiveModule(module.address)
  emit('navigate-to-config')
}

const refreshModule = async (address: string) => {
  await moduleStore.checkModuleHealth(address)
}

const removeModule = (address: string) => {
  moduleStore.removeModule(address)
}

const getModuleCardColor = (module: any) => {
  if (module.healthStatus === 'SERVING') return 'success'
  if (module.healthStatus === 'NOT_SERVING') return 'error'
  return 'warning'
}

const getHealthColor = (status: string) => {
  if (status === 'SERVING') return 'success'
  if (status === 'NOT_SERVING') return 'error'
  return 'warning'
}

const getHealthLabel = (status: string) => {
  if (status === 'SERVING') return 'Online'
  if (status === 'NOT_SERVING') return 'Offline'
  return 'Unknown'
}

const getHealthIcon = (status: string) => {
  if (status === 'SERVING') return 'mdi-check-circle'
  if (status === 'NOT_SERVING') return 'mdi-alert-circle'
  return 'mdi-help-circle'
}

const isModuleConnected = (address: string) => {
  return moduleStore.connectedModules.some(m => m.address === address)
}
</script>

<style scoped>
.module-card {
  transition: all 0.2s ease;
  cursor: pointer;
}

.module-card:hover {
  transform: translateY(-2px);
}

.module-card.active-module {
  border-color: rgb(var(--v-theme-primary));
  border-width: 2px;
}

.text-mono {
  font-family: monospace;
}
</style>
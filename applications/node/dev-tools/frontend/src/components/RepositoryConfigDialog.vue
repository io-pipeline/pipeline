<template>
  <v-dialog
    v-model="dialog"
    max-width="600"
    :persistent="isConnecting"
  >
    <v-card>
      <v-card-title>
        <v-icon start>mdi-database-cog</v-icon>
        Repository Configuration
      </v-card-title>
      
      <v-card-text>
        <v-alert
          v-if="error"
          type="error"
          variant="tonal"
          closable
          @click:close="error = ''"
          class="mb-4"
        >
          {{ error }}
        </v-alert>
        
        <v-form ref="form" @submit.prevent="saveConfiguration">
          <v-text-field
            v-model="address"
            label="Repository Address"
            placeholder="localhost:38002"
            variant="outlined"
            density="compact"
            :rules="addressRules"
            :disabled="isConnecting"
            prepend-inner-icon="mdi-server"
            hint="Enter the gRPC server address (host:port)"
            persistent-hint
            class="mb-4"
          />
          
          <v-divider class="mb-4" />
          
          <div class="d-flex align-center mb-2">
            <v-icon :color="statusColor" class="mr-2">{{ statusIcon }}</v-icon>
            <div>
              <div class="text-subtitle-2">Connection Status</div>
              <div class="text-caption text-medium-emphasis">{{ statusText }}</div>
            </div>
            <v-spacer />
            <v-btn
              v-if="connectionStatus === 'connected'"
              variant="text"
              size="small"
              @click="disconnect"
            >
              Disconnect
            </v-btn>
            <v-btn
              v-else-if="connectionStatus === 'disconnected' || connectionStatus === 'error'"
              variant="tonal"
              size="small"
              color="primary"
              @click="testConnection"
              :loading="isConnecting"
            >
              Test Connection
            </v-btn>
          </div>
          
          <v-expand-transition>
            <div v-if="lastError" class="mt-2">
              <v-alert
                type="error"
                variant="tonal"
                density="compact"
              >
                {{ lastError }}
              </v-alert>
            </div>
          </v-expand-transition>
          
          <v-divider class="mt-4 mb-4" />
          
          <div class="text-caption text-medium-emphasis mb-2">
            <v-icon size="small" class="mr-1">mdi-information</v-icon>
            The repository service manages document storage and retrieval. It should be running on the same server as your Pipeline services.
          </div>
        </v-form>
      </v-card-text>
      
      <v-card-actions>
        <v-btn
          variant="text"
          @click="cancel"
          :disabled="isConnecting"
        >
          Cancel
        </v-btn>
        <v-spacer />
        <v-btn
          color="primary"
          variant="flat"
          @click="saveConfiguration"
          :disabled="!isValid || isConnecting"
        >
          Save Configuration
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRepositoryStore } from '../stores/repositoryStore'
import { storeToRefs } from 'pinia'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

// Store
const repositoryStore = useRepositoryStore()
const { 
  connectionStatus, 
  selectedAddress, 
  isConnecting,
  lastError 
} = storeToRefs(repositoryStore)

// Local state
const dialog = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const form = ref<any>(null)
const address = ref(selectedAddress.value || 'localhost:38002')
const error = ref('')

// Validation rules
const addressRules = [
  (v: string) => !!v || 'Repository address is required',
  (v: string) => {
    // Basic host:port validation
    const pattern = /^[a-zA-Z0-9.-]+:[0-9]+$/
    return pattern.test(v) || 'Invalid address format. Use host:port'
  }
]

// Computed
const isValid = computed(() => {
  return addressRules.every(rule => rule(address.value) === true)
})

const statusColor = computed(() => {
  switch (connectionStatus.value) {
    case 'connected':
      return 'success'
    case 'disconnected':
      return 'grey'
    case 'error':
      return 'error'
    default:
      return 'grey'
  }
})

const statusIcon = computed(() => {
  switch (connectionStatus.value) {
    case 'connected':
      return 'mdi-check-circle'
    case 'disconnected':
      return 'mdi-circle-outline'
    case 'error':
      return 'mdi-alert-circle'
    default:
      return 'mdi-help-circle'
  }
})

const statusText = computed(() => {
  switch (connectionStatus.value) {
    case 'connected':
      return 'Connected to repository service'
    case 'disconnected':
      return 'Not connected'
    case 'error':
      return 'Connection error'
    default:
      return 'Unknown status'
  }
})

// Methods
async function testConnection() {
  error.value = ''
  repositoryStore.setSelectedAddress(address.value)
  await repositoryStore.connect()
}

function disconnect() {
  repositoryStore.disconnect()
}

async function saveConfiguration() {
  const { valid } = await form.value.validate()
  if (!valid) return
  
  // Save the address
  repositoryStore.setSelectedAddress(address.value)
  
  // Connect if not already connected
  if (connectionStatus.value !== 'connected') {
    await repositoryStore.connect()
  }
  
  // Close dialog
  dialog.value = false
}

function cancel() {
  // Reset to current saved address
  address.value = selectedAddress.value || 'localhost:38002'
  error.value = ''
  dialog.value = false
}

// Watch for dialog open
watch(dialog, (isOpen) => {
  if (isOpen) {
    // Reset form when dialog opens
    address.value = selectedAddress.value || 'localhost:38002'
    error.value = ''
  }
})
</script>

<style scoped>
.v-card-title {
  font-size: 1.1rem;
  font-weight: 500;
}
</style>
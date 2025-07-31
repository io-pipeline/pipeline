<template>
  <v-tooltip :text="tooltipText">
    <template v-slot:activator="{ props }">
      <v-chip
        v-bind="props"
        :color="chipColor"
        :prepend-icon="statusIcon"
        @click="handleClick"
        class="repository-status-chip"
        :class="{ 'cursor-pointer': !isConnecting }"
      >
        <span v-if="showAddress">{{ displayAddress }}</span>
        <span v-else>Repository</span>
        <v-progress-circular
          v-if="isConnecting"
          :size="16"
          :width="2"
          indeterminate
          class="ml-2"
        />
      </v-chip>
    </template>
  </v-tooltip>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRepositoryStore } from '../stores/repositoryStore'
import { storeToRefs } from 'pinia'

interface Props {
  showAddress?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showAddress: true
})

const repositoryStore = useRepositoryStore()
const { 
  connectionStatus, 
  selectedAddress, 
  isConnecting,
  lastHealthCheckTime 
} = storeToRefs(repositoryStore)

const chipColor = computed(() => {
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
      return 'mdi-database-check'
    case 'disconnected':
      return 'mdi-database-off'
    case 'error':
      return 'mdi-database-alert'
    default:
      return 'mdi-database'
  }
})

const displayAddress = computed(() => {
  if (!selectedAddress.value) return 'Not configured'
  // Show just host:port
  return selectedAddress.value.replace('http://', '').replace('https://', '')
})

const tooltipText = computed(() => {
  if (isConnecting.value) {
    return 'Connecting to repository...'
  }
  
  switch (connectionStatus.value) {
    case 'connected':
      return `Connected to ${displayAddress.value}\nClick to view details`
    case 'disconnected':
      return `Disconnected from ${displayAddress.value}\nClick to reconnect`
    case 'error':
      return `Connection error\nClick to retry`
    default:
      return 'Repository not configured'
  }
})

const handleClick = () => {
  if (isConnecting.value) return
  
  if (connectionStatus.value === 'disconnected' || connectionStatus.value === 'error') {
    // Reconnect
    repositoryStore.connect()
  } else {
    // Show connection dialog
    emit('click')
  }
}

// Emit events
const emit = defineEmits<{
  click: []
}>()

// Auto-check health every 30 seconds
let healthCheckInterval: NodeJS.Timer | null = null

onMounted(() => {
  // Initial connection check
  if (selectedAddress.value) {
    repositoryStore.checkHealth()
  }
  
  // Set up periodic health checks
  healthCheckInterval = setInterval(() => {
    if (selectedAddress.value && connectionStatus.value === 'connected') {
      repositoryStore.checkHealth()
    }
  }, 30000) // Check every 30 seconds
})

onUnmounted(() => {
  if (healthCheckInterval) {
    clearInterval(healthCheckInterval)
    healthCheckInterval = null
  }
})

// Watch for connection timeout (2 minutes)
watch(lastHealthCheckTime, (newTime) => {
  if (!newTime) return
  
  const checkTimeout = () => {
    const now = Date.now()
    const timeSinceLastCheck = now - newTime
    
    if (timeSinceLastCheck > 120000 && connectionStatus.value === 'connected') {
      // More than 2 minutes since last successful check
      repositoryStore.setConnectionStatus('disconnected')
    }
  }
  
  // Check immediately
  checkTimeout()
  
  // Set up a timer to check again in 2 minutes
  const timeoutChecker = setTimeout(checkTimeout, 120000)
  
  // Clean up on next update
  return () => clearTimeout(timeoutChecker)
})
</script>

<style scoped>
.repository-status-chip {
  transition: all 0.3s ease;
}

.repository-status-chip.cursor-pointer:hover {
  transform: scale(1.05);
  cursor: pointer;
}

.repository-status-chip :deep(.v-chip__content) {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
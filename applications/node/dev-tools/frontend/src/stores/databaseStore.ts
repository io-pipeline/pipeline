import { defineStore } from 'pinia'
import { computed } from 'vue'
import { useRepositoryStore } from './repositoryStore'

export const useDatabaseStore = defineStore('database', () => {
  const repositoryStore = useRepositoryStore()
  
  // Default mode is localStorage, transforms to repository when connected
  const isConnected = computed(() => repositoryStore.isConnected)
  const isMongoConnected = computed(() => repositoryStore.isConnected)
  const storageType = computed(() => repositoryStore.isConnected ? 'repository' : 'localStorage')
  
  // For backwards compatibility with components
  const repositoryHealthy = computed(() => repositoryStore.isConnected)
  const repositoryChecking = computed(() => repositoryStore.isConnecting)
  
  async function checkRepositoryHealth() {
    await repositoryStore.checkHealth()
  }
  
  async function init() {
    // Don't auto-connect to repository - keep local storage as default
    // Repository connection is opt-in through admin panel
  }
  
  return {
    // State (computed for backwards compatibility)
    repositoryHealthy,
    repositoryChecking,
    
    // Computed
    isConnected,
    isMongoConnected,
    storageType,
    
    // Actions
    checkRepositoryHealth,
    init
  }
})
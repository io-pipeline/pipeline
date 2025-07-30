import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useDatabaseStore = defineStore('database', () => {
  // State
  const repositoryHealthy = ref(false)
  const repositoryChecking = ref(false)
  
  // Computed - simplified to use repository service health
  const isConnected = computed(() => repositoryHealthy.value)
  const isMongoConnected = computed(() => repositoryHealthy.value)
  const storageType = computed(() => repositoryHealthy.value ? 'mongodb' : 'localStorage')
  
  async function checkRepositoryHealth() {
    try {
      const response = await fetch('http://localhost:3000/api/repository-health')
      const data = await response.json()
      repositoryHealthy.value = data.healthy
    } catch (error) {
      repositoryHealthy.value = false
    }
  }
  
  async function init() {
    await checkRepositoryHealth()
    // Poll repository health every 5 seconds
    setInterval(checkRepositoryHealth, 5000)
  }
  
  return {
    // State
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
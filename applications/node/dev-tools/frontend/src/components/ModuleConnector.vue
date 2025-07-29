<template>
  <div class="module-connector">
    <form @submit.prevent="connectToModule">
      <div class="form-group">
        <label for="module-address">Module gRPC Address:</label>
        <input 
          id="module-address"
          v-model="moduleAddress" 
          type="text" 
          placeholder="localhost:39101"
          :disabled="loading"
        />
        <button type="submit" :disabled="loading || !moduleAddress">
          {{ loading ? 'Connecting...' : 'Connect' }}
        </button>
      </div>
    </form>
    
    <div v-if="error" class="error">
      {{ error }}
    </div>
    
    <div v-if="loading" class="loading">
      Connecting to module...
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { moduleService } from '../services/moduleService'

const emit = defineEmits<{
  'schema-loaded': [data: any]
}>()

const moduleAddress = ref('localhost:39101')
const loading = ref(false)
const error = ref('')

const connectToModule = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const data = await moduleService.getModuleSchema(moduleAddress.value)
    emit('schema-loaded', data)
  } catch (err: any) {
    error.value = err.response?.data?.details || err.message || 'Failed to connect to module'
    console.error('Connection error:', err)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.module-connector {
  background: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 2rem;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.form-group {
  display: flex;
  gap: 1rem;
  align-items: flex-end;
}

label {
  display: block;
  margin-bottom: 0.5rem;
  font-weight: 500;
  color: #333;
}

input {
  flex: 1;
  padding: 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 1rem;
}

input:focus {
  outline: none;
  border-color: #4a90e2;
}

button {
  padding: 0.75rem 1.5rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 1rem;
  cursor: pointer;
  transition: background 0.2s;
}

button:hover:not(:disabled) {
  background: #357abd;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.error {
  margin-top: 1rem;
  padding: 0.75rem;
  background: #fee;
  border: 1px solid #fcc;
  border-radius: 4px;
  color: #c00;
}

.loading {
  margin-top: 1rem;
  color: #666;
  font-style: italic;
}
</style>
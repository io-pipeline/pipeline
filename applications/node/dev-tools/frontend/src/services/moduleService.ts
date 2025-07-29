import axios from 'axios'

// Configure axios to use the backend server
const API_BASE_URL = 'http://localhost:3000'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  }
})

export interface ModuleSchemaResponse {
  module_name: string
  version: string
  description?: string
  module_type?: string
  schema: any
  raw_schema: string
}

export const moduleService = {
  async getModuleSchema(address: string): Promise<ModuleSchemaResponse> {
    const response = await apiClient.post<ModuleSchemaResponse>('/api/module-schema', {
      address
    })
    return response.data
  },

  async checkHealth(): Promise<any> {
    const response = await apiClient.get('/health')
    return response.data
  }
}
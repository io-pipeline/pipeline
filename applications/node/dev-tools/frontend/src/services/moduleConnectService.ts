import { create } from '@bufbuild/protobuf'
import { createModuleProcessorClient, createHealthClient } from './connectService'
import { 
  RegistrationRequestSchema,
  ModuleProcessRequestSchema,
  CapabilityType
} from '@/gen/pipe_step_processor_service_pb'
import { 
  HealthCheckRequestSchema,
  HealthCheckResponse_ServingStatus
} from '@/gen/health/v1/health_pb'

export interface ModuleSchemaResponse {
  module_name: string
  version: string
  description?: string
  module_type?: string
  schema: any
  raw_schema: string
  capabilities?: CapabilityType[]
  display_name?: string
  tags?: string[]
}

export interface ModuleProcessResponse {
  success: boolean
  processor_logs: string[]
  output_doc?: any
  error_details?: any
}

// Schema transformation function (simplified for frontend)
async function transformSchemaForUI(rawSchema: string): Promise<any> {
  try {
    const parsed = JSON.parse(rawSchema)
    
    // Extract the actual schema - it might be nested in an OpenAPI structure
    let schema = parsed
    let configKey: string | undefined
    
    if (parsed.components && parsed.components.schemas) {
      const schemas = parsed.components.schemas
      
      // Find the main schema (usually ends with 'Config')
      configKey = Object.keys(schemas).find(key => key.endsWith('Config'))
      
      if (configKey) {
        schema = schemas[configKey]
      } else {
        // If no Config schema, look for any object schema
        configKey = Object.keys(schemas).find(key => 
          schemas[key].type === 'object' && schemas[key].properties
        )
        if (configKey) {
          schema = schemas[configKey]
        }
      }
    }
    
    // If it's an OpenAPI schema with a single component, extract it
    if (!schema.properties && parsed.components && parsed.components.schemas) {
      const schemaKeys = Object.keys(parsed.components.schemas)
      if (schemaKeys.length === 1) {
        schema = parsed.components.schemas[schemaKeys[0]]
      }
    }
    
    // Simple reference resolution for common cases
    if (configKey && parsed.components && parsed.components.schemas) {
      schema = resolveReferences(schema, parsed.components.schemas)
    }
    
    return enhanceSchema(schema)
  } catch (error) {
    console.error('Error transforming schema:', error)
    throw error
  }
}

// Simple reference resolver for $ref
function resolveReferences(schema: any, schemas: any): any {
  if (!schema || typeof schema !== 'object') return schema
  
  // Handle $ref
  if (schema.$ref && typeof schema.$ref === 'string') {
    const refName = schema.$ref.split('/').pop()
    if (refName && schemas[refName]) {
      return resolveReferences(schemas[refName], schemas)
    }
  }
  
  // Recursively resolve in properties
  if (schema.properties) {
    const resolved: any = { ...schema, properties: {} }
    for (const [key, prop] of Object.entries(schema.properties)) {
      resolved.properties[key] = resolveReferences(prop, schemas)
    }
    return resolved
  }
  
  // Handle arrays
  if (schema.items) {
    return {
      ...schema,
      items: resolveReferences(schema.items, schemas)
    }
  }
  
  return schema
}

function enhanceSchema(schema: any): any {
  // Add UI hints based on field types
  if (schema.properties) {
    Object.entries(schema.properties).forEach(([key, prop]: [string, any]) => {
      if (prop.type === 'boolean') {
        prop['x-ui-widget'] = 'switch'
      } else if (prop.enum) {
        prop['x-ui-widget'] = 'select'
      }
      
      // Recursively enhance nested objects
      if (prop.type === 'object' && prop.properties) {
        enhanceSchema(prop)
      }
      
      // Handle arrays - preserve x-suggestions and other custom properties
      if (prop.type === 'array' && prop.items) {
        // Preserve x-suggestions if found
        if (prop.items['x-suggestions']) {
          // x-suggestions preserved for autocomplete functionality
        }
        
        // Recursively enhance array items if they're objects
        if (prop.items.type === 'object' && prop.items.properties) {
          enhanceSchema(prop.items)
        }
      }
    })
  }
  return schema
}

// Store active health watchers and their last known status
const healthWatchers = new Map<string, { controller: AbortController, lastStatus?: string }>()

export const moduleConnectService = {
  async getModuleSchema(address: string): Promise<ModuleSchemaResponse> {
    try {
      const client = createModuleProcessorClient(address)
      
      // Create empty registration request
      const request = create(RegistrationRequestSchema, {})
      
      // Call GetServiceRegistration
      const response = await client.getServiceRegistration(request)
      
      if (!response.jsonConfigSchema) {
        throw new Error('Module does not provide a configuration schema')
      }
      
      // Transform the schema for UI
      const transformedSchema = await transformSchemaForUI(response.jsonConfigSchema)
      
      return {
        module_name: response.moduleName,
        version: response.version,
        description: response.description,
        schema: transformedSchema,
        raw_schema: response.jsonConfigSchema,
        capabilities: response.capabilities?.types || [],
        display_name: response.displayName,
        tags: response.tags || []
      }
    } catch (error: any) {
      console.error('Error getting module schema:', error)
      throw error
    }
  },

  async checkHealth(): Promise<any> {
    // This is for the backend health check, not module health
    const response = await fetch(`${window.location.protocol}//${window.location.hostname}:3000/health`)
    return response.json()
  },
  
  async checkModuleHealth(address: string): Promise<{ status: string; serving: boolean }> {
    try {
      const client = createHealthClient(address)
      
      // Create health check request with empty service name
      const request = create(HealthCheckRequestSchema, {
        service: ''
      })
      
      const response = await client.check(request)
      
      // Convert enum to string
      let statusString = 'UNKNOWN'
      if (response.status === HealthCheckResponse_ServingStatus.SERVING) {
        statusString = 'SERVING'
      } else if (response.status === HealthCheckResponse_ServingStatus.NOT_SERVING) {
        statusString = 'NOT_SERVING'
      } else if (response.status === HealthCheckResponse_ServingStatus.SERVICE_UNKNOWN) {
        statusString = 'SERVICE_UNKNOWN'
      }
      
      return {
        status: statusString,
        serving: statusString === 'SERVING'
      }
    } catch (error) {
      console.error('Module health check error:', error)
      return { status: 'NOT_SERVING', serving: false }
    }
  },
  
  async executeRequest(address: string, request: any): Promise<ModuleProcessResponse> {
    try {
      const client = createModuleProcessorClient(address)
      
      // Create the module process request
      const moduleRequest = create(ModuleProcessRequestSchema, request)
      
      const response = await client.processData(moduleRequest)
      
      return {
        success: response.success,
        processor_logs: response.processorLogs || [],
        output_doc: response.outputDoc,
        error_details: response.errorDetails
      }
    } catch (error: any) {
      console.error('Error executing request:', error)
      throw error
    }
  },
  
  // Start watching health status with streaming updates
  watchModuleHealth(address: string, onStatusChange: (status: string, serving: boolean) => void): () => void {
    // Cancel any existing watcher for this address
    const existingWatcher = healthWatchers.get(address)
    if (existingWatcher) {
      existingWatcher.controller.abort()
    }
    
    // Create new abort controller for this watcher
    const abortController = new AbortController()
    const watcherInfo = { controller: abortController, lastStatus: undefined }
    healthWatchers.set(address, watcherInfo)
    
    const client = createHealthClient(address)
    const request = create(HealthCheckRequestSchema, {
      service: ''
    })
    
    // Start the streaming watch
    ;(async () => {
      try {
        // First try a single check to get immediate status
        try {
          const checkResponse = await client.check(request)
          let statusString = 'UNKNOWN'
          if (checkResponse.status === HealthCheckResponse_ServingStatus.SERVING) {
            statusString = 'SERVING'
          } else if (checkResponse.status === HealthCheckResponse_ServingStatus.NOT_SERVING) {
            statusString = 'NOT_SERVING'
          } else if (checkResponse.status === HealthCheckResponse_ServingStatus.SERVICE_UNKNOWN) {
            statusString = 'SERVICE_UNKNOWN'
          }
          // Log initial status
          console.log(`[Frontend] Initial health status for ${address}: ${statusString}`)
          watcherInfo.lastStatus = statusString
          onStatusChange(statusString, statusString === 'SERVING')
        } catch (checkError) {
          console.error(`[Frontend] Health check failed for ${address}:`, checkError)
          // Connection failed - service is not reachable
          onStatusChange('NOT_SERVING', false)
        }
        
        // Now try streaming with binary format
        for await (const response of client.watch(request, {
          signal: abortController.signal
        })) {
          // Convert enum to string
          let statusString = 'UNKNOWN'
          if (response.status === HealthCheckResponse_ServingStatus.SERVING) {
            statusString = 'SERVING'
          } else if (response.status === HealthCheckResponse_ServingStatus.NOT_SERVING) {
            statusString = 'NOT_SERVING'
          } else if (response.status === HealthCheckResponse_ServingStatus.SERVICE_UNKNOWN) {
            statusString = 'SERVICE_UNKNOWN'
          }
          
          // Only log status changes, not every update
          if (watcherInfo.lastStatus !== statusString) {
            console.log(`[Frontend] Health status changed for ${address}: ${watcherInfo.lastStatus || 'UNKNOWN'} → ${statusString}`)
            watcherInfo.lastStatus = statusString
          }
          onStatusChange(statusString, statusString === 'SERVING')
        }
      } catch (error: any) {
        if (error.name !== 'AbortError') {
          console.error('Health watch error for', address, ':', error)
          // On error, report as not serving
          onStatusChange('NOT_SERVING', false)
          
          // Retry after 10 seconds if the service is down
          setTimeout(() => {
            // Only retry if this watcher is still active
            const currentWatcher = healthWatchers.get(address)
            if (currentWatcher && currentWatcher.controller === abortController) {
              console.log(`[Frontend] Retrying health check for ${address}...`)
              // Recursively call the watch function
              moduleConnectService.watchModuleHealth(address, onStatusChange)
            }
          }, 10000) // 10 second delay
        }
      } finally {
        // Only delete if this is still the active watcher
        const currentWatcher = healthWatchers.get(address)
        if (currentWatcher && currentWatcher.controller === abortController) {
          healthWatchers.delete(address)
        }
      }
    })()
    
    // Return cleanup function
    return () => {
      abortController.abort()
      healthWatchers.delete(address)
    }
  },
  
  // Stop watching a specific module's health
  stopHealthWatch(address: string) {
    const watcher = healthWatchers.get(address)
    if (watcher) {
      watcher.controller.abort()
      healthWatchers.delete(address)
    }
  },
  
  // Stop all health watchers
  stopAllHealthWatches() {
    healthWatchers.forEach(watcher => watcher.controller.abort())
    healthWatchers.clear()
  }
}
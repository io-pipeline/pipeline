import * as grpc from '@grpc/grpc-js'
import * as protoLoader from '@grpc/proto-loader'
import { fileURLToPath } from 'url'
import { dirname, join } from 'path'

/**
 * Single function to test gRPC health check
 * Using the standard grpc.health.v1.Health service
 */
export async function testGrpcHealthCheck(host = 'localhost', port = 39101) {
  // Load the health proto from grpc-health-check package
  const __filename = fileURLToPath(import.meta.url)
  const __dirname = dirname(__filename)
  
  // Try to find the health proto file in node_modules
  let healthProtoPath
  try {
    // This works in Node.js environment
    healthProtoPath = join(__dirname, '../node_modules/grpc-health-check/proto/health/v1/health.proto')
  } catch (error) {
    throw new Error('Cannot find health.proto file')
  }
  
  const packageDefinition = protoLoader.loadSync(healthProtoPath, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
  })
  
  const healthProto = grpc.loadPackageDefinition(packageDefinition)
  
  // Create client
  const client = new healthProto.grpc.health.v1.Health(
    `${host}:${port}`,
    grpc.credentials.createInsecure()
  )
  
  return new Promise((resolve, reject) => {
    // Call health check
    client.Check(
      { service: '' }, // Empty string checks overall server health
      { deadline: Date.now() + 5000 }, // 5 second timeout
      (error, response) => {
        client.close()
        
        if (error) {
          reject(new Error(`Health check failed: ${error.message}`))
        } else {
          // Status can be a string 'SERVING' or number 1
          const isServing = response.status === 'SERVING' || response.status === 1
          resolve({
            isHealthy: isServing,
            status: response.status,
            message: isServing ? 'Service is healthy' : `Service is not serving (status: ${response.status})`
          })
        }
      }
    )
  })
}
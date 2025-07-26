import * as health_grpc_pb from './generated/health_grpc_pb.js'
import * as health_pb from './generated/health_pb.js'
import { credentials } from '@grpc/grpc-js'

export interface HealthCheckResult {
  isHealthy: boolean
  status: string
  message: string
}

export async function testGrpcHealthCheck(host: string = 'localhost', port: number = 39101): Promise<HealthCheckResult> {
  return new Promise((resolve, reject) => {
    // Create gRPC client
    const client = new health_grpc_pb.HealthClient(`${host}:${port}`, credentials.createInsecure())
    
    // Create health check request
    const request = new health_pb.HealthCheckRequest()
    request.setService('') // Empty string checks overall server health
    
    console.log(`Testing health check for ${host}:${port}...`)
    
    // Call health check with timeout
    client.check(request, { deadline: Date.now() + 5000 }, (error, response) => {
      
      if (error) {
        console.error(`Health check failed: ${error.message}`)
        resolve({
          isHealthy: false,
          status: 'ERROR',
          message: `Health check failed: ${error.message}`
        })
      } else if (response) {
        const status = response.getStatus()
        const isServing = status === 1 // SERVING = 1
        const statusName = isServing ? 'SERVING' : 'NOT_SERVING'
        const message = isServing ? 'Service is healthy' : `Service is not serving (status: ${status})`
        
        console.log(`Health check result: ${statusName} - ${message}`)
        resolve({
          isHealthy: isServing,
          status: statusName,
          message
        })
      } else {
        resolve({
          isHealthy: false,
          status: 'UNKNOWN',
          message: 'No response received'
        })
      }
    })
  })
}

// Direct test execution if this file is run directly
if (import.meta.url === `file://${process.argv[1]}`) {
  const host = process.argv[2] || 'localhost'
  const port = parseInt(process.argv[3]) || 39101
  
  testGrpcHealthCheck(host, port)
    .then(result => {
      console.log('✅ Health check completed:', result)
      process.exit(result.isHealthy ? 0 : 1)
    })
    .catch(error => {
      console.error('❌ Health check error:', error)
      process.exit(1)
    })
}
// Simple CommonJS test for health check
const { HealthClient } = require('./src/generated/proto/health_grpc_pb.js')
const { HealthCheckRequest } = require('./src/generated/proto/health_pb.js')
const { credentials } = require('@grpc/grpc-js')

async function testHealthCheck(host = 'localhost', port = 39101) {
  return new Promise((resolve, reject) => {
    const client = new HealthClient(`${host}:${port}`, credentials.createInsecure())
    const request = new HealthCheckRequest()
    request.setService('')
    
    console.log(`Testing health check for ${host}:${port}...`)
    
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
        const isServing = status === 1
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

// Run the test
const host = process.argv[2] || 'localhost'
const port = parseInt(process.argv[3]) || 39101

testHealthCheck(host, port)
  .then(result => {
    console.log('✅ Health check completed:', result)
    process.exit(result.isHealthy ? 0 : 1)
  })
  .catch(error => {
    console.error('❌ Health check error:', error)
    process.exit(1)
  })
// Test the gRPC health check function directly
import { testGrpcHealthCheck } from './src/testGrpcHealth.js'

async function runTest() {
  try {
    console.log('Testing gRPC health check function...')
    
    // Test against a non-existent service (should fail gracefully)
    const result = await testGrpcHealthCheck('localhost', 39999)
    console.log('✅ Health check result:', result)
  } catch (error) {
    console.log('❌ Expected error (no service running):', error.message)
  }
}

runTest()
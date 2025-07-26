// Test script to verify Node.js polyfills and gRPC imports work
import grpc from '@grpc/grpc-js'
import protoLoader from '@grpc/proto-loader'
import healthCheck from 'grpc-health-check'

try {
  console.log('Testing Node.js globals...')
  console.log('process:', typeof process)
  console.log('Buffer:', typeof Buffer)
  
  console.log('Testing gRPC imports...')
  console.log('grpc:', typeof grpc)
  console.log('protoLoader:', typeof protoLoader) 
  console.log('healthCheck:', typeof healthCheck)
  
  console.log('✅ All imports successful!')
} catch (error) {
  console.error('❌ Import error:', error.message)
  process.exit(1)
}
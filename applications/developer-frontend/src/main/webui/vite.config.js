import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  base: '/',
  server: {
    port: 5175,
    host: '127.0.0.1',
    strictPort: true  // Fail if port is occupied instead of finding next available
  },
  build: {
    outDir: 'dist',
    rollupOptions: {
      external: ['@grpc/grpc-js', '@grpc/proto-loader', 'grpc-health-check']
    }
  },
  define: {
    global: 'globalThis',
    'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV || 'development')
  },
  ssr: {
    external: ['@grpc/grpc-js', '@grpc/proto-loader', 'grpc-health-check']
  },
  optimizeDeps: {
    exclude: ['@grpc/grpc-js', '@grpc/proto-loader', 'grpc-health-check']
  }
})
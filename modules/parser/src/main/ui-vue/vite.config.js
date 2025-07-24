import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@pipeline/shared-ui': resolve(__dirname, '../../../../../libraries/shared-ui/src')
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets'
  },
  server: {
    port: 5174, // Different port from chunker (5173)
    proxy: {
      '/api': {
        target: 'http://localhost:39101',
        changeOrigin: true
      }
    }
  }
})
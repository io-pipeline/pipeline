import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
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
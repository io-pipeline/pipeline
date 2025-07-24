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
    port: 5173, // Chunker uses default Vite port
    proxy: {
      '/api': {
        target: 'http://localhost:39102',
        changeOrigin: true
      }
    }
  }
})
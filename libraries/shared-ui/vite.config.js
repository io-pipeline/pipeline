import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.js'),
      name: 'PipelineSharedUI',
      fileName: (format) => `index.${format === 'es' ? 'es.js' : 'js'}`
    },
    rollupOptions: {
      external: ['vue', '@jsonforms/vue', '@jsonforms/vue-vanilla', 'axios'],
      output: {
        globals: {
          vue: 'Vue',
          axios: 'axios',
          '@jsonforms/vue': 'JsonFormsVue',
          '@jsonforms/vue-vanilla': 'JsonFormsVueVanilla'
        }
      }
    }
  }
})
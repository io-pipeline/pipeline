import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import dts from 'vite-plugin-dts'
import { resolve } from 'path'

export default defineConfig({
  plugins: [
    vue(),
    dts({
      insertTypesEntry: true,
    })
  ],
  build: {
    lib: {
      entry: resolve(__dirname, 'src/index.ts'),
      name: 'UniversalConfigCard',
      formats: ['es', 'umd'],
      fileName: (format) => `index.${format}.js`
    },
    rollupOptions: {
      external: ['vue', '@jsonforms/vue', '@jsonforms/vue-vanilla'],
      output: {
        globals: {
          vue: 'Vue',
          '@jsonforms/vue': 'JsonFormsVue',
          '@jsonforms/vue-vanilla': 'JsonFormsVueVanilla'
        }
      }
    }
  }
})
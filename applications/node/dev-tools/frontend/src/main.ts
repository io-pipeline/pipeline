/**
 * main.ts
 *
 * Bootstraps Vuetify and other plugins then mounts the App`
 */

// Plugins
import { registerPlugins } from '@/plugins'

// Components
import App from './App.vue'
// import App from './App.test.vue'
// import App from './App.simple.vue'

// Composables
import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Styles
import './style.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
registerPlugins(app)

app.mount('#app')

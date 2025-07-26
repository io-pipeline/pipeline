<template>
  <div id="app">
    <Header @toggle-menu="toggleMenu" />
    <SideMenu :isOpen="menuOpen" :currentView="currentView" @close="closeMenu" @navigate="navigate" />
    <main class="main-content" :class="{ 'menu-open': menuOpen }">
      <component :is="currentComponent" @navigate="navigate" />
    </main>
  </div>
</template>

<script>
import Header from './components/Header.vue'
import SideMenu from './components/SideMenu.vue'
import MainScreen from './components/MainScreen.vue'
import ConnectionDetails from './components/ConnectionDetails.vue'
import ModuleTestingView from './components/ModuleTestingView.vue'

export default {
  name: 'App',
  components: {
    Header,
    SideMenu,
    MainScreen,
    ConnectionDetails,
    ModuleTestingView
  },
  data() {
    return {
      menuOpen: false,
      currentView: 'main'
    }
  },
  computed: {
    currentComponent() {
      const components = {
        main: MainScreen,
        configure: ConnectionDetails,
        testing: ModuleTestingView
      }
      return components[this.currentView] || MainScreen
    }
  },
  methods: {
    toggleMenu() {
      this.menuOpen = !this.menuOpen
    },
    closeMenu() {
      this.menuOpen = false
    },
    navigate(view) {
      this.currentView = view
      this.closeMenu()
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #f5f5f5;
}

#app {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.main-content {
  flex: 1;
  overflow-y: auto;
}

/* Removed margin-left shifts to prevent content jumping when menu opens */
</style>
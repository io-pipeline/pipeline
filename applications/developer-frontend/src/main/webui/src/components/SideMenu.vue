<template>
  <div class="side-menu-overlay" :class="{ 'open': isOpen }" @click="$emit('close')">
    <nav class="side-menu" :class="{ 'open': isOpen }" @click.stop>
      <div class="menu-header">
        <h2>Developer Tools</h2>
        <button class="close-btn" @click="$emit('close')" aria-label="Close menu">
          ×
        </button>
      </div>
      
      <ul class="menu-items">
        <li>
          <button 
            class="menu-item" 
            :class="{ 'active': currentView === 'main' }"
            @click="navigate('main')"
          >
            <span class="menu-icon">🏠</span>
            <span>Main Dashboard</span>
          </button>
        </li>
        
        <li>
          <button 
            class="menu-item" 
            :class="{ 'active': currentView === 'configure' }"
            @click="navigate('configure')"
          >
            <span class="menu-icon">⚙️</span>
            <span>Connection Manager</span>
          </button>
        </li>
        
        <li>
          <button 
            class="menu-item" 
            :class="{ 'active': currentView === 'testing' }"
            @click="navigate('testing')"
            :disabled="!hasValidConfig"
          >
            <span class="menu-icon">🧪</span>
            <span>Module Testing</span>
            <span v-if="!hasValidConfig" class="config-required">Config Required</span>
          </button>
        </li>
        
        <li class="menu-separator"></li>
        
        <li>
          <button class="menu-item" disabled>
            <span class="menu-icon">🔗</span>
            <span>Module Explorer</span>
            <span class="coming-soon">Soon</span>
          </button>
        </li>
        
        <li>
          <button class="menu-item" disabled>
            <span class="menu-icon">📊</span>
            <span>Testing Tools</span>
            <span class="coming-soon">Soon</span>
          </button>
        </li>
      </ul>
      
      <div class="menu-footer">
        <div class="version-info">
          <small>Developer Frontend v1.0.0</small>
          <small>Phase 1 - Reference Implementation</small>
        </div>
      </div>
    </nav>
  </div>
</template>

<script>
export default {
  name: 'SideMenu',
  props: {
    isOpen: {
      type: Boolean,
      default: false
    },
    currentView: {
      type: String,
      default: 'main'
    }
  },
  computed: {
    hasValidConfig() {
      const saved = localStorage.getItem('pipelineClientConfig')
      if (!saved) return false
      
      try {
        const config = JSON.parse(saved)
        const moduleConfig = config.config || config
        return moduleConfig.host && moduleConfig.port && moduleConfig.moduleType
      } catch {
        return false
      }
    }
  },
  emits: ['close', 'navigate'],
  methods: {
    navigate(view) {
      this.$emit('navigate', view)
    }
  }
}
</script>

<style scoped>
.side-menu-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  z-index: 1001;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.3s ease, visibility 0.3s ease;
}

.side-menu-overlay.open {
  opacity: 1;
  visibility: visible;
}

.side-menu {
  position: fixed;
  top: 0;
  left: -250px;
  width: 250px;
  height: 100%;
  background-color: white;
  box-shadow: 2px 0 10px rgba(0,0,0,0.1);
  transition: left 0.3s ease;
  display: flex;
  flex-direction: column;
  z-index: 1002;
}

.side-menu.open {
  left: 0;
}

.menu-header {
  padding: 1.5rem;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.menu-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: #374151;
}

.close-btn {
  background: none;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
  color: #6b7280;
  transition: background-color 0.2s;
}

.close-btn:hover {
  background-color: #f3f4f6;
}

.menu-items {
  flex: 1;
  list-style: none;
  padding: 1rem 0;
  margin: 0;
}

.menu-item {
  width: 100%;
  padding: 0.75rem 1.5rem;
  border: none;
  background: none;
  text-align: left;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  color: #374151;
  transition: background-color 0.2s;
  font-size: 0.875rem;
}

.menu-item:hover:not(:disabled) {
  background-color: #f3f4f6;
}

.menu-item.active {
  background-color: #e0e7ff;
  color: #3730a3;
  font-weight: 500;
}

.menu-item:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.menu-icon {
  font-size: 1rem;
  width: 20px;
  text-align: center;
}

.coming-soon {
  margin-left: auto;
  background-color: #fbbf24;
  color: white;
  font-size: 0.625rem;
  padding: 0.125rem 0.375rem;
  border-radius: 8px;
  font-weight: 500;
}

.config-required {
  margin-left: auto;
  background-color: #dc2626;
  color: white;
  font-size: 0.625rem;
  padding: 0.125rem 0.375rem;
  border-radius: 8px;
  font-weight: 500;
}

.menu-separator {
  height: 1px;
  background-color: #e5e7eb;
  margin: 0.5rem 1.5rem;
}

.menu-footer {
  padding: 1rem 1.5rem;
  border-top: 1px solid #e5e7eb;
  background-color: #f9fafb;
}

.version-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.version-info small {
  color: #6b7280;
  font-size: 0.75rem;
}

/* Removed desktop-specific styles to keep consistent overlay behavior across all screen sizes */
</style>
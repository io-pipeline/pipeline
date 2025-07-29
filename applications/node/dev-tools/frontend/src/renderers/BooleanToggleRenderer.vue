<template>
  <div v-if="control" class="boolean-toggle-control">
    <button
      type="button"
      class="toggle-button"
      :class="{ active: control.data }"
      @click="toggle"
    >
      <span class="toggle-label">{{ control.label }}</span>
      <span class="toggle-switch">
        <span class="toggle-slider"></span>
      </span>
    </button>
    <div v-if="control.description" class="toggle-description">
      {{ control.description }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ControlElement } from '@jsonforms/core'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'

const props = defineProps(rendererProps<ControlElement>())
const { control: controlRef, handleChange } = useJsonFormsControl(props)

// Control is a computed ref, we need to access its value
const control = computed(() => controlRef.value)

const toggle = () => {
  if (!control.value) return
  
  const currentValue = control.value.data ?? false
  const newValue = !currentValue
  console.log('Toggle clicked', control.value.path, 'from', currentValue, 'to', newValue)
  // handleChange needs both path and value
  handleChange(control.value.path, newValue)
}
</script>

<style scoped>
.boolean-toggle-control {
  margin-bottom: 1rem;
}

.toggle-button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  background: white;
  color: #666;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  width: 100%;
  user-select: none;
}

.toggle-label {
  flex: 1;
  text-align: left;
  margin-right: 0.5rem;
}

.toggle-switch {
  display: inline-block;
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: #ccc;
  position: relative;
  transition: background 0.15s ease;
  flex-shrink: 0;
}

.toggle-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.15s ease;
}

.toggle-button:hover {
  border-color: #90caf9;
  background: #fafafa;
  color: #333;
}

.toggle-button.active {
  background: #e3f2fd;
  border-color: #2196f3;
  color: #1565c0;
}

.toggle-button.active .toggle-switch {
  background: #2196f3;
}

.toggle-button.active .toggle-slider {
  transform: translateX(16px);
}

.toggle-description {
  margin-top: 0.5rem;
  font-size: 0.875rem;
  color: #666;
  line-height: 1.4;
  padding-left: 0.5rem;
}
</style>
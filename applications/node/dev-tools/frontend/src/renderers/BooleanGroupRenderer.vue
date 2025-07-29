<template>
  <div class="boolean-group">
    <h4 v-if="groupLabel" class="group-title">{{ groupLabel }}</h4>
    <div class="toggle-grid">
      <button
        v-for="prop in booleanProperties"
        :key="prop.path"
        type="button"
        class="toggle-button"
        :class="{ active: data[prop.key] }"
        @click="handleToggle(prop.key)"
      >
        {{ prop.label }}
        <span v-if="prop.description" class="toggle-hint">{{ prop.description }}</span>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { GroupElement } from '@jsonforms/core'
import { rendererProps, useJsonFormsGroup } from '@jsonforms/vue'

const props = defineProps(rendererProps<GroupElement>())
const { control, data } = useJsonFormsGroup(props)

const groupLabel = computed(() => control.value.label)

const booleanProperties = computed(() => {
  const elements = control.value.uischema.elements || []
  return elements
    .filter((el: any) => el.type === 'Control')
    .filter((el: any) => {
      const path = el.scope.split('/').pop()
      const prop = control.value.schema.properties?.[path]
      return prop?.type === 'boolean'
    })
    .map((el: any) => {
      const key = el.scope.split('/').pop()
      const prop = control.value.schema.properties?.[key]
      return {
        key,
        path: el.scope,
        label: el.label || key,
        description: prop?.description
      }
    })
})

const handleToggle = (key: string) => {
  data.value[key] = !data.value[key]
}
</script>

<style scoped>
.boolean-group {
  margin-bottom: 1.5rem;
}

.group-title {
  margin: 0 0 1rem 0;
  color: #333;
  font-size: 1.1rem;
  font-weight: 600;
}

.toggle-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 0.75rem;
}

.toggle-button {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 1rem;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  background: white;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}

.toggle-button:hover {
  border-color: #4a90e2;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.toggle-button.active {
  background: #e3f2fd;
  border-color: #4a90e2;
  color: #1976d2;
}

.toggle-hint {
  display: block;
  margin-top: 0.25rem;
  font-size: 0.8rem;
  color: #666;
  font-weight: normal;
  line-height: 1.3;
}

.toggle-button.active .toggle-hint {
  color: #555;
}
</style>
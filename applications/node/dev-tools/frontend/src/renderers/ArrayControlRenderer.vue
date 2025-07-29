<template>
  <div v-if="control" class="array-control">
    <div class="array-header">
      <label class="array-label">{{ control.label }}</label>
      <div class="array-actions">
        <span class="array-count">{{ items.length }} {{ items.length === 1 ? 'item' : 'items' }}</span>
        <button 
          type="button" 
          class="add-button"
          @click="addItem"
          :disabled="!control.enabled"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 2a.5.5 0 0 1 .5.5v5h5a.5.5 0 0 1 0 1h-5v5a.5.5 0 0 1-1 0v-5h-5a.5.5 0 0 1 0-1h5v-5A.5.5 0 0 1 8 2Z"/>
          </svg>
          Add
        </button>
      </div>
    </div>
    
    <div v-if="control.description" class="array-description">
      {{ control.description }}
    </div>
    
    <div v-if="items.length > 0" class="array-items">
      <div 
        v-for="(item, index) in items" 
        :key="`${control.path}-${index}`"
        class="array-item"
        :class="{ 'dragging': dragIndex === index }"
        draggable="true"
        @dragstart="startDrag(index)"
        @dragover.prevent
        @drop="handleDrop(index)"
        @dragend="dragIndex = -1"
      >
        <div class="item-handle">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M10 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm0-4a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm0-4a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm-4 4a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm0 4a1 1 0 1 0 0-2 1 1 0 0 0 0 2zm0-8a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"/>
          </svg>
        </div>
        
        <input 
          v-if="isStringArray"
          type="text"
          class="item-input"
          :value="item"
          @input="updateItem(index, $event)"
          :placeholder="`Item ${index + 1}`"
          :disabled="!control.enabled"
        />
        
        <div v-else class="item-content">
          <dispatch-renderer
            :schema="itemSchema"
            :uischema="itemUiSchema"
            :path="`${control.path}.${index}`"
            :enabled="control.enabled"
            :renderers="control.renderers"
            :cells="control.cells"
          />
        </div>
        
        <button 
          type="button" 
          class="remove-button"
          @click="removeItem(index)"
          :disabled="!control.enabled"
          :title="'Remove item ' + (index + 1)"
        >
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M2.146 2.854a.5.5 0 1 1 .708-.708L8 7.293l5.146-5.147a.5.5 0 0 1 .708.708L8.707 8l5.147 5.146a.5.5 0 0 1-.708.708L8 8.707l-5.146 5.147a.5.5 0 0 1-.708-.708L7.293 8 2.146 2.854Z"/>
          </svg>
        </button>
      </div>
    </div>
    
  </div>
</template>

<script lang="ts">
import { computed, ref, defineComponent } from 'vue'
import { ArrayControlProps, composePaths } from '@jsonforms/core'
import { rendererProps, useJsonFormsArrayControl, DispatchRenderer } from '@jsonforms/vue'

export default defineComponent({
  name: 'ArrayControlRenderer',
  components: {
    DispatchRenderer
  },
  props: rendererProps<ArrayControlProps>(),
  setup(props) {
    const arrayControl = useJsonFormsArrayControl(props)

    const control = computed(() => arrayControl.control.value)
    const dragIndex = ref(-1)

    // Get the schema for array items
    const itemSchema = computed(() => {
      // The control schema might already be the items schema in some cases
      if (control.value?.schema?.type !== 'array' && control.value?.schema?.type) {
        // This is already the item schema
        return control.value?.schema || {}
      }
      return control.value?.schema?.items || {}
    })

    // Check if this is a simple string array
    const isStringArray = computed(() => {
      // Check if the schema is already resolved to string type
      if (control.value?.schema?.type === 'string') {
        return true
      }
      // Or check the items type
      return itemSchema.value?.type === 'string'
    })

    // Generate UI schema for array items
    const itemUiSchema = computed(() => {
      // For non-object items, just return a control
      if (itemSchema.value?.type !== 'object') {
        return {
          type: 'Control',
          scope: '#'
        }
      }
      // For object items, generate a vertical layout
      return {
        type: 'VerticalLayout',
        elements: Object.keys(itemSchema.value.properties || {}).map(key => ({
          type: 'Control',
          scope: `#/properties/${key}`
        }))
      }
    })

    // Get array items
    const items = computed(() => {
      return control.value?.data || []
    })

    // Common MIME type suggestions
    const mimeTypeSuggestions = [
      'application/pdf',
      'application/json',
      'application/xml',
      'text/plain',
      'text/html',
      'text/csv',
      'image/jpeg',
      'image/png',
      'image/gif',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
      'application/msword',
      'application/vnd.ms-excel',
      'application/vnd.ms-powerpoint'
    ]

    // Get suggestions based on schema first, then fall back to heuristics
    const suggestions = computed(() => {
      if (!isStringArray.value) return []
      
      // First priority: Check schema for enum values (most specific)
      if (itemSchema.value?.enum && Array.isArray(itemSchema.value.enum)) {
        return itemSchema.value.enum
      }
      
      // Second priority: Check schema for examples
      if (itemSchema.value?.examples && Array.isArray(itemSchema.value.examples)) {
        return itemSchema.value.examples
      }
      
      // Third priority: Check for custom suggestions in schema extensions
      if (itemSchema.value?.['x-suggestions'] && Array.isArray(itemSchema.value['x-suggestions'])) {
        return itemSchema.value['x-suggestions']
      }
      
      // Last resort: Use heuristics based on field name (only for MIME types)
      const label = control.value?.label?.toLowerCase() || ''
      const path = control.value?.path?.toLowerCase() || ''
      
      if (label.includes('mime') || path.includes('mime') || label.includes('content type')) {
        // Only return a subset of the most common MIME types
        return [
          'application/pdf',
          'application/json',
          'text/plain',
          'text/html',
          'image/jpeg',
          'image/png'
        ]
      }
      
      return []
    })

    // Add item
    const addItem = () => {
      if (isStringArray.value) {
        if (arrayControl.addItem) {
          arrayControl.addItem(control.value.path, '')()
        }
      } else {
        if (arrayControl.addItem) {
          arrayControl.addItem(control.value.path, {})()
        }
      }
    }

    // Remove item
    const removeItem = (index: number) => {
      if (arrayControl.removeItem) {
        arrayControl.removeItem(control.value.path, index)()
      }
    }

    // Update string item
    const updateItem = (index: number, event: Event) => {
      const target = event.target as HTMLInputElement
      const newData = [...items.value]
      newData[index] = target.value
      // Use the handleChange from the control hook
      if (arrayControl.control.value.handleChange) {
        arrayControl.control.value.handleChange(arrayControl.control.value.path, newData)
      }
    }

    // Add suggestion
    const addSuggestion = (suggestion: string) => {
      if (!items.value.includes(suggestion)) {
        // Just add the item using the provided addItem function
        if (arrayControl.addItem) {
          arrayControl.addItem(control.value.path, suggestion)()
        }
      }
    }

    // Drag and drop
    const startDrag = (index: number) => {
      dragIndex.value = index
    }

    const handleDrop = (dropIndex: number) => {
      if (dragIndex.value !== -1 && dragIndex.value !== dropIndex) {
        const newData = [...items.value]
        const [removed] = newData.splice(dragIndex.value, 1)
        newData.splice(dropIndex, 0, removed)
        // Use the handleChange from the control hook
        if (arrayControl.control.value.handleChange) {
          arrayControl.control.value.handleChange(arrayControl.control.value.path, newData)
        }
      }
      dragIndex.value = -1
    }

    return {
      control,
      itemSchema,
      itemUiSchema,
      isStringArray,
      items,
      suggestions,
      addItem,
      removeItem,
      updateItem,
      addSuggestion,
      startDrag,
      handleDrop,
      dragIndex
    }
  }
})
</script>

<style scoped>
.array-control {
  margin-bottom: 1.5rem;
}

.array-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 0.5rem;
}

.array-label {
  font-weight: 500;
  color: #333;
  font-size: 0.95rem;
}

.array-actions {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.array-count {
  font-size: 0.875rem;
  color: #666;
}

.add-button {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease;
}

.add-button:hover:not(:disabled) {
  background: #357abd;
}

.add-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.array-description {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 1rem;
  line-height: 1.4;
}

.empty-state {
  text-align: center;
  padding: 2rem;
  background: #f8f9fa;
  border: 2px dashed #dee2e6;
  border-radius: 8px;
}

.empty-state p {
  margin: 0 0 1rem 0;
  color: #6c757d;
}

.add-first-button {
  padding: 0.5rem 1.5rem;
  background: #4a90e2;
  color: white;
  border: none;
  border-radius: 6px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s ease;
}

.add-first-button:hover:not(:disabled) {
  background: #357abd;
}

.array-items {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.array-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.array-item:hover {
  border-color: #d0d0d0;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.array-item.dragging {
  opacity: 0.5;
}

.item-handle {
  cursor: move;
  color: #999;
  display: flex;
  align-items: center;
}

.item-handle:hover {
  color: #666;
}

.item-input {
  flex: 1;
  padding: 0.5rem 0.75rem;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 0.9rem;
}

.item-input:focus {
  outline: none;
  border-color: #4a90e2;
}

.item-content {
  flex: 1;
}

.remove-button {
  padding: 0.375rem;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  color: #999;
  cursor: pointer;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

.remove-button:hover:not(:disabled) {
  background: #fee;
  border-color: #fcc;
  color: #c00;
}

.remove-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.suggestions {
  margin-top: 1rem;
  padding-top: 1rem;
  border-top: 1px solid #e0e0e0;
}

.suggestions-header {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 0.5rem;
  font-weight: 500;
}

.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.suggestion-chip {
  padding: 0.375rem 0.75rem;
  background: #f0f4f8;
  border: 1px solid #d0dae4;
  border-radius: 16px;
  font-size: 0.875rem;
  color: #495057;
  cursor: pointer;
  transition: all 0.15s ease;
}

.suggestion-chip:hover:not(:disabled) {
  background: #e1e8f0;
  border-color: #b0c4db;
}

.suggestion-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: #e9ecef;
}
</style>
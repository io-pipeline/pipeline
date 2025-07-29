<template>
  <div v-if="control" class="array-autocomplete-control">
    <div class="array-header">
      <label class="array-label">{{ control.label }}</label>
      <span class="array-count">{{ items.length }} {{ items.length === 1 ? 'item' : 'items' }}</span>
    </div>
    
    <div v-if="control.description" class="array-description">
      {{ control.description }}
    </div>
    
    <!-- Autocomplete input -->
    <div class="autocomplete-wrapper">
      <v-combobox
        v-model="newItem"
        :items="filteredSuggestions"
        :label="`Add ${control.label || 'item'}`"
        :disabled="!control.enabled"
        @keydown.enter.prevent="addCurrentItem"
        clearable
        hide-details
        density="compact"
        variant="outlined"
        :menu-props="{ maxHeight: 300 }"
        :return-object="false"
      >
        <template v-slot:append>
          <v-btn
            icon
            size="small"
            @click="addCurrentItem"
            :disabled="!newItem || !control.enabled"
          >
            <v-icon>mdi-plus</v-icon>
          </v-btn>
        </template>
      </v-combobox>
    </div>
    
    <!-- Array items -->
    <div v-if="items.length > 0" class="array-items">
      <v-chip
        v-for="(item, index) in items"
        :key="`${control.path}-${index}`"
        closable
        @click:close="removeItem(index)"
        class="array-chip"
        :disabled="!control.enabled"
      >
        {{ item }}
      </v-chip>
    </div>
    
  </div>
</template>

<script lang="ts">
import { computed, ref, defineComponent } from 'vue'
import { ArrayControlProps } from '@jsonforms/core'
import { rendererProps, useJsonFormsArrayControl } from '@jsonforms/vue'

export default defineComponent({
  name: 'ArrayAutoCompleteRenderer',
  props: rendererProps<ArrayControlProps>(),
  setup(props) {
    const arrayControl = useJsonFormsArrayControl(props)
    const control = computed(() => arrayControl.control.value)
    const newItem = ref('')

    // Get the schema for array items
    const itemSchema = computed(() => {
      if (control.value?.schema?.type !== 'array' && control.value?.schema?.type) {
        return control.value?.schema || {}
      }
      return control.value?.schema?.items || {}
    })

    // Check if this is a simple string array
    const isStringArray = computed(() => {
      if (control.value?.schema?.type === 'string') {
        return true
      }
      return itemSchema.value?.type === 'string'
    })

    // Get array items
    const items = computed(() => {
      return control.value?.data || []
    })

    // Get all available suggestions from schema
    const allSuggestions = computed(() => {
      if (!isStringArray.value) return []
      
      // Priority 1: enum values (most restrictive)
      if (itemSchema.value?.enum && Array.isArray(itemSchema.value.enum)) {
        return itemSchema.value.enum
      }
      
      // Priority 2: x-suggestions (custom extension for autocomplete)
      if (itemSchema.value?.['x-suggestions'] && Array.isArray(itemSchema.value['x-suggestions'])) {
        return itemSchema.value['x-suggestions']
      }
      
      // Priority 3: examples from schema
      if (itemSchema.value?.examples && Array.isArray(itemSchema.value.examples)) {
        return itemSchema.value.examples
      }
      
      // Priority 4: Check parent schema for suggestions
      if (control.value?.schema?.['x-suggestions'] && Array.isArray(control.value.schema['x-suggestions'])) {
        return control.value.schema['x-suggestions']
      }
      
      return []
    })

    // Filter suggestions based on current input and exclude already added items
    const filteredSuggestions = computed(() => {
      const searchTerm = (newItem.value || '').toLowerCase()
      return allSuggestions.value
        .filter(suggestion => !items.value.includes(suggestion))
        .filter(suggestion => suggestion.toLowerCase().includes(searchTerm))
        .sort((a, b) => {
          // Sort by relevance: exact matches first, then starts with, then contains
          const aLower = a.toLowerCase()
          const bLower = b.toLowerCase()
          
          if (aLower === searchTerm) return -1
          if (bLower === searchTerm) return 1
          if (aLower.startsWith(searchTerm)) return -1
          if (bLower.startsWith(searchTerm)) return 1
          return a.localeCompare(b)
        })
        .slice(0, 50) // Limit to top 50 results for performance
    })

    // Add current item
    const addCurrentItem = () => {
      if (newItem.value && !items.value.includes(newItem.value)) {
        if (arrayControl.addItem) {
          arrayControl.addItem(control.value.path, newItem.value)()
          newItem.value = ''
        }
      }
    }


    // Remove item
    const removeItem = (index: number) => {
      if (arrayControl.removeItem) {
        arrayControl.removeItem(control.value.path, index)()
      }
    }

    return {
      control,
      items,
      newItem,
      filteredSuggestions,
      addCurrentItem,
      removeItem
    }
  }
})
</script>

<style scoped>
.array-autocomplete-control {
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

.array-count {
  font-size: 0.875rem;
  color: #666;
}

.array-description {
  font-size: 0.875rem;
  color: #666;
  margin-bottom: 1rem;
  line-height: 1.4;
}

.autocomplete-wrapper {
  margin-bottom: 1rem;
}

.array-items {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 1rem;
}

.array-chip {
  margin: 0;
}

</style>
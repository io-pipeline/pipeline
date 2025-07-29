<template>
  <v-card>
    <v-card-title>
      Configuration
    </v-card-title>
    
    <v-divider />
    
    <v-card-text>
      <JsonForms
        :data="data"
        :schema="schema"
        :renderers="renderers"
        @change="onChange"
      />
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'

const props = defineProps<{
  schema?: any
  initialData?: any
}>()

const emit = defineEmits<{
  'data-change': [data: any]
}>()

const data = ref(props.initialData || {})
const renderers = Object.freeze(vuetifyRenderers)

// Watch for schema changes
watch(() => props.schema, (newSchema) => {
  if (newSchema && newSchema.properties) {
    // Initialize with defaults from schema if no initial data
    if (!props.initialData) {
      const defaults: any = {}
      Object.entries(newSchema.properties).forEach(([key, prop]: [string, any]) => {
        if (prop.default !== undefined) {
          defaults[key] = prop.default
        }
      })
      data.value = defaults
    }
  }
}, { immediate: true })

const onChange = (event: any) => {
  data.value = event.data
  emit('data-change', event.data)
}
</script>
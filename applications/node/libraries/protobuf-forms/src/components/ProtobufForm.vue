<template>
  <div class="protobuf-form">
    <v-progress-circular
      v-if="loading"
      indeterminate
      color="primary"
    />
    
    <v-alert
      v-else-if="error"
      type="error"
      variant="tonal"
    >
      {{ error }}
    </v-alert>
    
    <json-forms
      v-else-if="schema"
      :data="modelValue"
      :schema="schema"
      :uischema="uischema"
      :renderers="renderers"
      @change="handleChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vuetify'
import type { JsonSchema } from '../converter'
import { ProtobufSchemaLoader } from '../loader'

interface Props {
  // The protobuf message type to render (e.g., 'PipeDoc', 'ModuleRequest')
  messageType: string
  // The data to edit
  modelValue: any
  // Path to the proto file (optional if using protoContent)
  protoPath?: string
  // Proto content as string (optional if using protoPath)
  protoContent?: string
  // Custom UI schema
  uischema?: any
  // Additional options for schema generation
  options?: {
    addUiHints?: boolean
    includeComments?: boolean
    fieldTransformers?: Record<string, any>
  }
}

const props = defineProps<Props>()
const emit = defineEmits(['update:modelValue', 'error', 'schemaLoaded'])

const loading = ref(false)
const error = ref<string | null>(null)
const schema = ref<JsonSchema | null>(null)
const loader = ref<ProtobufSchemaLoader | null>(null)

// Use Vuetify renderers
const renderers = computed(() => vanillaRenderers)

const loadSchema = async () => {
  loading.value = true
  error.value = null
  
  try {
    loader.value = new ProtobufSchemaLoader(props.options || {})
    
    if (props.protoPath) {
      await loader.value.loadProtoFile(props.protoPath)
    } else if (props.protoContent) {
      loader.value.loadProtoString(props.protoContent)
    } else {
      throw new Error('Either protoPath or protoContent must be provided')
    }
    
    schema.value = loader.value.getMessageSchema(props.messageType)
    emit('schemaLoaded', schema.value)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schema'
    emit('error', error.value)
  } finally {
    loading.value = false
  }
}

const handleChange = (event: any) => {
  emit('update:modelValue', event.data)
}

// Reload schema when props change
watch(() => [props.messageType, props.protoPath, props.protoContent], () => {
  loadSchema()
})

onMounted(() => {
  loadSchema()
})
</script>

<style scoped>
.protobuf-form {
  width: 100%;
}
</style>
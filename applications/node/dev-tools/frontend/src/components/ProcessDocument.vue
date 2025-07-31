<template>
  <v-container fluid>
    <!-- Document Input Display -->
    <v-card class="mb-4">
      <v-card-title>Document Input</v-card-title>
      
      <v-divider />
      
      <v-card-text>
        <!-- File upload for .bin documents -->
        <v-file-input
          v-if="!localRequest"
          v-model="binFile"
          @update:model-value="handleBinFileUpload"
          label="Load .bin document"
          prepend-icon="mdi-file-import"
          accept=".bin"
          variant="outlined"
          hint="Upload a previously saved .bin document"
          persistent-hint
          class="mb-4"
        />
        
        <!-- Error alert for file loading -->
        <v-alert
          v-if="processingError && !localRequest"
          type="error"
          variant="tonal"
          closable
          @click:close="processingError = ''"
          class="mb-4"
        >
          {{ processingError }}
        </v-alert>
        
        <div v-if="localRequest">
          <CodeBlock
            :code="JSON.stringify(localRequest, null, 2)"
            language="json"
            title="Request JSON"
            :show-save="true"
            filename="module-process-request.json"
          />
          
          <v-btn
            variant="text"
            size="small"
            class="mt-2"
            @click="clearDocument"
          >
            Clear Document
          </v-btn>
        </div>
        <v-empty-state
          v-else
          icon="mdi-file-document-outline"
          headline="No document loaded"
          text="Create a document in the Data Seeding tab or upload a .bin file"
        />
      </v-card-text>
      
      <v-card-actions v-if="localRequest">
        <v-spacer />
        <v-btn
          color="primary"
          variant="flat"
          size="large"
          prepend-icon="mdi-send"
          @click="processDocument"
          :loading="processing"
          :disabled="!moduleStore.activeModule"
        >
          Send Document to Module
        </v-btn>
      </v-card-actions>
    </v-card>

    <!-- Processing Result -->
    <v-card v-if="processingResult">
      <v-card-title>Processing Result</v-card-title>
      
      <v-divider />
      
      <v-card-text>
        <v-alert
          v-if="processingError"
          type="error"
          variant="tonal"
          closable
          @click:close="processingError = ''"
          class="mb-4"
        >
          {{ processingError }}
        </v-alert>
        
        <CodeBlock
          v-else
          :code="JSON.stringify(processingResult, null, 2)"
          language="json"
          title="Result JSON"
          :show-save="true"
          filename="processing-result.bin"
        />
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import SeedDataBuilder from './SeedDataBuilder.vue'
import CodeBlock from './CodeBlock.vue'
import { useModuleStore } from '../stores/moduleStore'
import { moduleConnectService } from '../services/moduleConnectService'

const props = defineProps<{
  currentConfig?: any
  createdRequest?: any
}>()

const emit = defineEmits<{
  'request-updated': [request: any]
}>()

const moduleStore = useModuleStore()
const localRequest = ref<any>(null)
const processing = ref(false)
const processingResult = ref<any>(null)
const processingError = ref('')
const binFile = ref<File | null>(null)

// Watch for external request updates
watch(() => props.createdRequest, (newRequest) => {
  console.log('ProcessDocument received createdRequest:', newRequest)
  if (newRequest) {
    localRequest.value = newRequest
  }
}, { immediate: true })

const handleRequestCreated = (request: any) => {
  localRequest.value = request
  emit('request-updated', request)
}

const handleBinFileUpload = async (file: File | null) => {
  if (!file) return
  
  try {
    // Read the .bin file as binary
    const arrayBuffer = await file.arrayBuffer()
    const bytes = new Uint8Array(arrayBuffer)
    
    // Import protobuf schemas
    const { fromBinary } = await import('@bufbuild/protobuf')
    const { ModuleProcessRequestSchema } = await import('../gen/pipe_step_processor_service_pb')
    
    // Decode protobuf
    const message = fromBinary(ModuleProcessRequestSchema, bytes)
    
    // Convert to plain object
    const parsed = JSON.parse(JSON.stringify(message))
    
    // Validate it's a ModuleProcessRequest
    if (!parsed.document || !parsed.metadata) {
      throw new Error('Invalid document format. Expected ModuleProcessRequest with document and metadata.')
    }
    
    localRequest.value = parsed
    emit('request-updated', parsed)
    
    // Clear the file input after successful load
    binFile.value = null
  } catch (err) {
    processingError.value = 'Error loading .bin file: ' + (err as Error).message
    binFile.value = null
  }
}

const clearDocument = () => {
  localRequest.value = null
  processingResult.value = null
  processingError.value = ''
  binFile.value = null
  emit('request-updated', null)
}

const processDocument = async () => {
  if (!localRequest.value || !moduleStore.activeModule) return
  
  processing.value = true
  processingError.value = ''
  processingResult.value = null
  
  try {
    // Use Connect service to execute the request
    const response = await moduleConnectService.executeRequest(
      moduleStore.activeModuleAddress,
      localRequest.value
    )
    
    processingResult.value = response
  } catch (err) {
    processingError.value = 'Error processing document: ' + (err as Error).message
  } finally {
    processing.value = false
  }
}

</script>

<style scoped>
.request-preview,
.result-preview {
  margin: 0;
  font-size: 0.875rem;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 400px;
  overflow-y: auto;
}
</style>
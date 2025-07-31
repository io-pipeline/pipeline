<template>
  <control-wrapper
    v-bind="controlWrapper"
    :styles="styles"
    :isFocused="isFocused"
    :appliedOptions="appliedOptions"
  >
    <v-card elevation="0" class="blob-upload-card">
      <v-card-title class="text-subtitle-2 pa-2">
        {{ computedLabel }}
      </v-card-title>
      <v-card-text class="pa-2">
        <!-- File Upload Section -->
        <v-file-input
          v-model="selectedFile"
          label="Upload file"
          prepend-icon="mdi-paperclip"
          density="compact"
          variant="outlined"
          :accept="acceptedFileTypes"
          @change="handleFileSelect"
          class="mb-2"
        />
        
        <!-- Blob Fields -->
        <div v-if="blobData" class="blob-fields">
          <v-text-field
            v-model="blobData.blobId"
            label="Blob ID"
            density="compact"
            variant="outlined"
            readonly
            class="mb-2"
          />
          
          <v-textarea
            v-model="blobData.data"
            label="Data (Base64)"
            density="compact"
            variant="outlined"
            rows="2"
            :hint="`${dataSize} bytes`"
            persistent-hint
            class="mb-2"
          />
          
          <v-text-field
            v-model="blobData.mimeType"
            label="MIME Type"
            density="compact"
            variant="outlined"
            class="mb-2"
          />
          
          <v-text-field
            v-model="blobData.filename"
            label="Filename"
            density="compact"
            variant="outlined"
            class="mb-2"
          />
          
          <v-text-field
            v-model="blobData.encoding"
            label="Encoding"
            density="compact"
            variant="outlined"
            placeholder="base64"
            class="mb-2"
          />
          
          <!-- Metadata as key-value pairs -->
          <div class="text-caption mb-1">Metadata</div>
          <v-card elevation="0" variant="outlined" class="pa-2">
            <div v-for="(item, index) in metadata" :key="index" class="d-flex align-center mb-1">
              <v-text-field
                v-model="item.key"
                label="Key"
                density="compact"
                variant="outlined"
                hide-details
                class="mr-2"
                @input="updateMetadata"
              />
              <v-text-field
                v-model="item.value"
                label="Value"
                density="compact"
                variant="outlined"
                hide-details
                class="mr-2"
                @input="updateMetadata"
              />
              <v-btn
                icon="mdi-delete"
                size="small"
                variant="text"
                @click="removeMetadataItem(index)"
              />
            </div>
            <v-btn
              size="small"
              variant="text"
              prepend-icon="mdi-plus"
              @click="addMetadataItem"
              class="mt-1"
            >
              Add metadata
            </v-btn>
          </v-card>
        </div>
      </v-card-text>
    </v-card>
  </control-wrapper>
</template>

<script lang="ts">
import { defineComponent, ref, computed, watch } from 'vue'
import { rendererProps, useJsonFormsControl } from '@jsonforms/vue'
import { ControlElement } from '@jsonforms/core'
import { useVuetifyControl } from '../renderers/vue-vuetify/util'
import { VCard, VCardTitle, VCardText, VFileInput, VTextField, VTextarea, VBtn } from 'vuetify/components'
import ControlWrapper from '../renderers/vue-vuetify/controls/ControlWrapper.vue'

interface BlobData {
  blobId?: string
  data?: string
  mimeType?: string
  filename?: string
  encoding?: string
  metadata?: Record<string, string>
}

interface MetadataItem {
  key: string
  value: string
}

export default defineComponent({
  name: 'blob-upload-renderer',
  components: {
    ControlWrapper,
    VCard,
    VCardTitle,
    VCardText,
    VFileInput,
    VTextField,
    VTextarea,
    VBtn
  },
  props: {
    ...rendererProps<ControlElement>()
  },
  setup(props) {
    const control = useVuetifyControl(useJsonFormsControl(props), adaptValue)
    const selectedFile = ref<File | null>(null)
    
    // Initialize blob data from control
    const blobData = ref<BlobData>(control.control.value.data || {
      blobId: `blob-${Date.now()}`,
      data: '',
      mimeType: '',
      filename: '',
      encoding: 'base64',
      metadata: {}
    })
    
    // Convert metadata object to array for editing
    const metadata = ref<MetadataItem[]>([])
    
    watch(() => control.control.value.data, (newData) => {
      if (newData) {
        blobData.value = { ...newData }
        updateMetadataArray()
      }
    }, { immediate: true })
    
    function updateMetadataArray() {
      if (blobData.value.metadata) {
        metadata.value = Object.entries(blobData.value.metadata).map(([key, value]) => ({
          key,
          value
        }))
      }
    }
    
    function adaptValue(value: any) {
      return value || {}
    }
    
    const dataSize = computed(() => {
      if (!blobData.value.data) return 0
      // Base64 string to byte size calculation
      const base64 = blobData.value.data.replace(/[^A-Za-z0-9+/]/g, '')
      return Math.floor(base64.length * 0.75)
    })
    
    const acceptedFileTypes = computed(() => {
      // You can customize this based on your needs
      return '*/*'
    })
    
    async function handleFileSelect(event: Event) {
      const file = selectedFile.value
      if (!file) return
      
      try {
        // Read file as base64
        const reader = new FileReader()
        reader.onload = (e) => {
          const base64Data = e.target?.result as string
          // Remove data URL prefix
          const base64Content = base64Data.split(',')[1]
          
          // Update blob data
          blobData.value = {
            ...blobData.value,
            data: base64Content,
            mimeType: file.type || 'application/octet-stream',
            filename: file.name,
            encoding: 'base64',
            metadata: {
              ...blobData.value.metadata,
              'original-size': file.size.toString(),
              'last-modified': file.lastModified.toString(),
              'upload-date': new Date().toISOString()
            }
          }
          
          updateMetadataArray()
          emitChange()
        }
        
        reader.readAsDataURL(file)
      } catch (error) {
        console.error('Error reading file:', error)
      }
    }
    
    function updateMetadata() {
      const metadataObj: Record<string, string> = {}
      metadata.value.forEach(item => {
        if (item.key) {
          metadataObj[item.key] = item.value
        }
      })
      blobData.value.metadata = metadataObj
      emitChange()
    }
    
    function addMetadataItem() {
      metadata.value.push({ key: '', value: '' })
    }
    
    function removeMetadataItem(index: number) {
      metadata.value.splice(index, 1)
      updateMetadata()
    }
    
    function emitChange() {
      control.onChange(control.control.value.path, blobData.value)
    }
    
    // Watch for changes in blob data fields
    watch(blobData, (newValue) => {
      emitChange()
    }, { deep: true })
    
    return {
      ...control,
      selectedFile,
      blobData,
      metadata,
      dataSize,
      acceptedFileTypes,
      handleFileSelect,
      updateMetadata,
      addMetadataItem,
      removeMetadataItem
    }
  }
})
</script>

<style scoped>
.blob-upload-card {
  border: 1px solid rgba(0, 0, 0, 0.12);
}

.blob-fields {
  margin-top: 8px;
}

:deep(.v-input) {
  font-size: 12px;
}

:deep(.v-field__input) {
  font-size: 12px;
}
</style>
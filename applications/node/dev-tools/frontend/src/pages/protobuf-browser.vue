<template>
  <v-container fluid>
    <v-row>
      <v-col>
        <h1 class="text-h4 mb-4">Protobuf File Browser</h1>
        <p class="text-body-1 mb-6">
          Browse and manage protobuf files across different drives. You can create drives to organize your data,
          upload protobuf files, and view their contents.
        </p>
      </v-col>
    </v-row>
    
    <v-row>
      <v-col>
        <v-card>
          <ProtobufFileBrowser
            ref="fileBrowser"
            :allow-drive-selection="true"
            initial-drive="default"
            @file-selected="onFileSelected"
            @drive-changed="onDriveChanged"
          />
        </v-card>
      </v-col>
    </v-row>
    
    <!-- Upload Section -->
    <v-row class="mt-4">
      <v-col>
        <v-card>
          <v-card-title>
            <v-icon class="mr-2">mdi-upload</v-icon>
            Upload Protobuf
          </v-card-title>
          <v-card-text>
            <v-file-input
              v-model="uploadFile"
              label="Select protobuf file"
              accept=".pb,.protobuf,.bin"
              prepend-icon="mdi-file"
              show-size
              @change="onFileChange"
            />
            <v-select
              v-model="uploadType"
              :items="protobufTypes"
              label="Protobuf Type"
              hint="Select the type of protobuf message"
              persistent-hint
            />
            <v-text-field
              v-model="uploadName"
              label="File Name (optional)"
              hint="Leave empty to use original filename"
            />
          </v-card-text>
          <v-card-actions>
            <v-spacer />
            <v-btn
              color="primary"
              @click="uploadProtobuf"
              :disabled="!uploadFile || !uploadType"
              :loading="uploading"
            >
              Upload
            </v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
    
    <!-- Selected File Info -->
    <v-row class="mt-4" v-if="selectedFile">
      <v-col>
        <v-card>
          <v-card-title>
            <v-icon class="mr-2">mdi-information</v-icon>
            Selected File Info
          </v-card-title>
          <v-card-text>
            <v-list density="compact">
              <v-list-item>
                <v-list-item-title>Name</v-list-item-title>
                <v-list-item-subtitle>{{ selectedFile.name }}</v-list-item-subtitle>
              </v-list-item>
              <v-list-item v-if="selectedFile.payloadType">
                <v-list-item-title>Type</v-list-item-title>
                <v-list-item-subtitle>{{ selectedFile.payloadType }}</v-list-item-subtitle>
              </v-list-item>
              <v-list-item v-if="selectedFile.size">
                <v-list-item-title>Size</v-list-item-title>
                <v-list-item-subtitle>{{ formatFileSize(selectedFile.size) }}</v-list-item-subtitle>
              </v-list-item>
              <v-list-item v-if="selectedFile.updatedAt">
                <v-list-item-title>Last Modified</v-list-item-title>
                <v-list-item-subtitle>{{ formatDate(selectedFile.updatedAt) }}</v-list-item-subtitle>
              </v-list-item>
            </v-list>
          </v-card-text>
          <v-card-actions>
            <v-btn color="primary" @click="viewSelectedFile">View Content</v-btn>
            <v-btn color="secondary" @click="downloadSelectedFile">Download</v-btn>
          </v-card-actions>
        </v-card>
      </v-col>
    </v-row>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ProtobufFileBrowser from '../components/ProtobufFileBrowser.vue'
import { filesystemService } from '../services/connectService'
import { useSnackbar } from '../composables/useSnackbar'
import type { Node, CreateNodeRequest, GetNodeRequest } from '../gen/filesystem_service_pb'
import { Node_NodeType } from '../gen/filesystem_service_pb'
import { Any } from '@bufbuild/protobuf/wkt'

const { showSuccess, showError } = useSnackbar()

// Refs
const fileBrowser = ref<InstanceType<typeof ProtobufFileBrowser>>()
const selectedFile = ref<Node | null>(null)
const uploadFile = ref<File | null>(null)
const uploadType = ref('')
const uploadName = ref('')
const uploading = ref(false)

// Available protobuf types for upload
const protobufTypes = [
  'io.pipeline.core.PipeDoc',
  'io.pipeline.process.ModuleProcessRequest',
  'io.pipeline.process.ModuleProcessResponse',
  'io.pipeline.process.ServiceRegistrationResponse',
  'io.pipeline.core.PipeStream',
  'io.pipeline.core.StepExecutionRecord',
  'io.pipeline.core.SemanticProcessingResult'
]

// Event handlers
const onFileSelected = (node: Node) => {
  selectedFile.value = node
}

const onDriveChanged = (drive: string) => {
  console.log('Drive changed to:', drive)
  selectedFile.value = null
}

const onFileChange = (file: File | null) => {
  if (file && !uploadName.value) {
    uploadName.value = file.name
  }
}

const uploadProtobuf = async () => {
  if (!uploadFile.value || !uploadType.value) return
  
  uploading.value = true
  try {
    // Read file as binary
    const arrayBuffer = await uploadFile.value.arrayBuffer()
    const uint8Array = new Uint8Array(arrayBuffer)
    
    // Create Any message with the binary data
    const anyPayload = Any.pack({
      typeUrl: `type.googleapis.com/${uploadType.value}`,
      value: uint8Array
    })
    
    // Get current drive and folder from file browser
    const currentDrive = fileBrowser.value?.getCurrentDrive() || 'default'
    const currentFolder = fileBrowser.value?.currentNodeId || ''
    
    // Create the file node
    const request: CreateNodeRequest = {
      drive: currentDrive,
      parentId: currentFolder,
      name: uploadName.value || uploadFile.value.name,
      type: Node_NodeType.FILE,
      payload: anyPayload,
      metadata: {
        'uploadedAt': new Date().toISOString(),
        'originalName': uploadFile.value.name,
        'size': uploadFile.value.size.toString()
      }
    }
    
    await filesystemService.createNode(request)
    
    showSuccess('File uploaded successfully')
    
    // Refresh the file browser
    fileBrowser.value?.refresh()
    
    // Reset upload form
    uploadFile.value = null
    uploadType.value = ''
    uploadName.value = ''
  } catch (error) {
    showError(`Failed to upload file: ${error}`)
  } finally {
    uploading.value = false
  }
}

const viewSelectedFile = async () => {
  if (!selectedFile.value) return
  
  try {
    const drive = fileBrowser.value?.getCurrentDrive() || 'default'
    const request: GetNodeRequest = {
      drive,
      id: selectedFile.value.id
    }
    const node = await filesystemService.getNode(request)
    
    if (node.payload) {
      // The ProtobufFileBrowser component handles viewing
      // This is just to demonstrate the integration
      console.log('File payload:', node.payload)
    }
  } catch (error) {
    showError(`Failed to view file: ${error}`)
  }
}

const downloadSelectedFile = () => {
  // The ProtobufFileBrowser component handles downloading
  // This is just to demonstrate the integration
  if (selectedFile.value) {
    console.log('Download file:', selectedFile.value.name)
  }
}

// Utility functions
const formatFileSize = (bytes: number) => {
  if (!bytes) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

const formatDate = (timestamp: any) => {
  if (!timestamp) return ''
  const date = timestamp.toDate ? timestamp.toDate() : new Date(timestamp)
  return date.toLocaleString()
}
</script>
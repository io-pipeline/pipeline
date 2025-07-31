<template>
  <v-card>
    <v-card-title>Connect Integration Status</v-card-title>
    <v-card-subtitle>Testing Connect protocol with type registry</v-card-subtitle>
    
    <v-divider />
    
    <v-card-text>
      <v-row>
        <v-col cols="12" md="4">
          <v-btn
            color="primary"
            @click="testFilesystemConnection"
            :loading="loading"
            prepend-icon="mdi-connection"
            block
          >
            Test Filesystem Service
          </v-btn>
        </v-col>
        <v-col cols="12" md="4">
          <v-btn
            color="success"
            @click="showCreateDialog = true"
            prepend-icon="mdi-folder-plus"
            block
          >
            Create Test Folder
          </v-btn>
        </v-col>
        <v-col cols="12" md="4">
          <v-btn
            color="info"
            @click="createSampleData"
            :loading="creatingData"
            prepend-icon="mdi-database-plus"
            block
          >
            Create Sample Data
          </v-btn>
        </v-col>
      </v-row>
      
      <v-alert
        v-if="testResult"
        :type="testResult.success ? 'success' : 'error'"
        class="mt-4"
      >
        <v-alert-title>{{ testResult.title }}</v-alert-title>
        <div v-if="testResult.message">{{ testResult.message }}</div>
        <pre v-if="testResult.data" class="mt-2 text-caption">{{ JSON.stringify(testResult.data, null, 2) }}</pre>
      </v-alert>
    </v-card-text>
    
    <!-- Create Folder Dialog -->
    <v-dialog v-model="showCreateDialog" max-width="500">
      <v-card>
        <v-card-title>Create Test Folder</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="newFolderName"
            label="Folder Name"
            prepend-icon="mdi-folder"
            @keyup.enter="createFolder"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreateDialog = false">Cancel</v-btn>
          <v-btn color="primary" @click="createFolder" :loading="loading">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { createFilesystemClient } from '@/services/connectService'
import { create, toJson } from '@bufbuild/protobuf'
import { 
  GetChildrenRequestSchema, 
  CreateNodeRequestSchema,
  Node_NodeType
} from '@/gen/filesystem_service_pb'
import { StructSchema } from '@bufbuild/protobuf/wkt'

const loading = ref(false)
const creatingData = ref(false)
const showCreateDialog = ref(false)
const newFolderName = ref('')
const testResult = ref<{
  success: boolean
  title: string
  message?: string
  data?: any
} | null>(null)

async function testFilesystemConnection() {
  loading.value = true
  testResult.value = null
  
  try {
    const client = createFilesystemClient()
    
    // Create request to get root children
    const request = create(GetChildrenRequestSchema, {
      parentId: "",
      pageSize: 10
    })
    
    const response = await client.getChildren(request)
    
    testResult.value = {
      success: true,
      title: 'Connect Integration Working!',
      message: `Successfully retrieved ${response.nodes.length} nodes from filesystem`,
      data: {
        totalCount: response.totalCount,
        nodes: response.nodes.map(node => ({
          id: node.id,
          name: node.name,
          type: node.type === 1 ? 'FOLDER' : 'FILE',
          hasPayload: node.payload.case !== undefined
        }))
      }
    }
  } catch (error: any) {
    testResult.value = {
      success: false,
      title: 'Connect Integration Failed',
      message: error.message,
      data: {
        code: error.code,
        details: error.details
      }
    }
  } finally {
    loading.value = false
  }
}

async function createFolder() {
  if (!newFolderName.value.trim()) {
    return
  }
  
  loading.value = true
  testResult.value = null
  
  try {
    const client = createFilesystemClient()
    
    // Create request for a new folder
    const request = create(CreateNodeRequestSchema, {
      parentId: "", // Root level
      name: newFolderName.value,
      type: Node_NodeType.FOLDER,
      metadata: {
        createdBy: 'Connect Test',
        createdAt: new Date().toISOString()
      }
    })
    
    const response = await client.createNode(request)
    
    testResult.value = {
      success: true,
      title: 'Folder Created Successfully!',
      message: `Created folder "${response.name}" with ID: ${response.id}`,
      data: {
        id: response.id,
        name: response.name,
        path: response.path,
        type: 'FOLDER'
      }
    }
    
    showCreateDialog.value = false
    newFolderName.value = ''
  } catch (error: any) {
    testResult.value = {
      success: false,
      title: 'Failed to Create Folder',
      message: error.message,
      data: {
        code: error.code,
        details: error.details
      }
    }
  } finally {
    loading.value = false
  }
}

async function createSampleData() {
  creatingData.value = true
  testResult.value = null
  
  try {
    const client = createFilesystemClient()
    
    // Create a sample folder structure
    const folders = [
      { name: 'Documents', parent: '' },
      { name: 'Processors', parent: '' },
      { name: 'Test Data', parent: '' }
    ]
    
    const createdFolders: any[] = []
    
    for (const folder of folders) {
      const request = create(CreateNodeRequestSchema, {
        parentId: folder.parent,
        name: folder.name,
        type: Node_NodeType.FOLDER,
        metadata: {
          createdBy: 'Sample Data Generator',
          createdAt: new Date().toISOString()
        }
      })
      
      const response = await client.createNode(request)
      createdFolders.push({
        id: response.id,
        name: response.name
      })
    }
    
    // Create a sample file with Struct data
    const structData = create(StructSchema, {
      fields: {
        title: { kind: { case: "stringValue", value: "Sample Document" } },
        content: { kind: { case: "stringValue", value: "This is a test document created via Connect" } },
        version: { kind: { case: "numberValue", value: 1.0 } },
        metadata: { 
          kind: { 
            case: "structValue", 
            value: create(StructSchema, {
              fields: {
                author: { kind: { case: "stringValue", value: "Connect Test" } },
                timestamp: { kind: { case: "stringValue", value: new Date().toISOString() } }
              }
            })
          } 
        }
      }
    })
    
    const fileRequest = create(CreateNodeRequestSchema, {
      parentId: createdFolders[0].id, // Put in Documents folder
      name: "sample-document.json",
      type: Node_NodeType.FILE,
      structData: structData,
      metadata: {
        mimeType: 'application/json',
        size: JSON.stringify(toJson(StructSchema, structData)).length.toString()
      }
    })
    
    const fileResponse = await client.createNode(fileRequest)
    
    testResult.value = {
      success: true,
      title: 'Sample Data Created Successfully!',
      message: `Created ${createdFolders.length} folders and 1 file`,
      data: {
        folders: createdFolders,
        file: {
          id: fileResponse.id,
          name: fileResponse.name,
          parentId: fileResponse.parentId
        }
      }
    }
  } catch (error: any) {
    testResult.value = {
      success: false,
      title: 'Failed to Create Sample Data',
      message: error.message,
      data: {
        code: error.code,
        details: error.details
      }
    }
  } finally {
    creatingData.value = false
  }
}
</script>
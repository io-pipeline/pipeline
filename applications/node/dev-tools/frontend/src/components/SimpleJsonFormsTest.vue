<template>
  <v-card>
    <v-card-title>
      <v-icon start>mdi-test-tube</v-icon>
      Simple JSON Forms Test (Vuetify Renderers)
    </v-card-title>
    
    <v-card-text>
      <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
        {{ error }}
      </v-alert>
      
      <v-tabs v-model="tab" class="mb-4">
        <v-tab value="form">Form</v-tab>
        <v-tab value="json">JSON View</v-tab>
        <v-tab value="schema">Schema View</v-tab>
      </v-tabs>
      
      <v-tabs-window v-model="tab">
        <v-tabs-window-item value="form">
          <json-forms
            :data="formData"
            :schema="schema"
            :renderers="renderers"
            @change="handleChange"
          />
        </v-tabs-window-item>
        
        <v-tabs-window-item value="json">
          <pre class="json-view">{{ JSON.stringify(formData, null, 2) }}</pre>
        </v-tabs-window-item>
        
        <v-tabs-window-item value="schema">
          <pre class="json-view">{{ JSON.stringify(schema, null, 2) }}</pre>
        </v-tabs-window-item>
      </v-tabs-window>
    </v-card-text>
    
    <v-card-actions>
      <v-spacer />
      <v-btn @click="resetForm">Reset</v-btn>
      <v-btn color="primary" @click="logData">Log Data</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'
import { customRenderers } from '../renderers'

// State
const tab = ref('form')
const error = ref<string | null>(null)

// Use the same renderer setup as UniversalConfigCard
const renderers = Object.freeze([...vuetifyRenderers, ...customRenderers, ...vanillaRenderers])

// Simple test schema that includes number inputs and time
const schema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
      title: 'Name',
      description: 'Enter your full name'
    },
    age: {
      type: 'integer',
      title: 'Age',
      description: 'Your age (uses VNumberInput)',
      minimum: 0,
      maximum: 150
    },
    salary: {
      type: 'number',
      title: 'Salary',
      description: 'Annual salary (uses VNumberInput)',
      minimum: 0
    },
    startTime: {
      type: 'string',
      format: 'time',
      title: 'Start Time',
      description: 'Work start time (uses VTimePicker)'
    },
    isActive: {
      type: 'boolean',
      title: 'Active Employee'
    },
    department: {
      type: 'string',
      title: 'Department',
      enum: ['Engineering', 'Sales', 'Marketing', 'HR', 'Finance']
    },
    skills: {
      type: 'array',
      title: 'Skills',
      items: {
        type: 'string'
      }
    }
  },
  required: ['name', 'age']
}

// Form data
const formData = ref({
  name: 'John Doe',
  age: 30,
  salary: 75000,
  startTime: '09:00',
  isActive: true,
  department: 'Engineering',
  skills: ['JavaScript', 'TypeScript', 'Vue']
})

// Handle form changes
const handleChange = (event: any) => {
  formData.value = event.data
  console.log('Form data changed:', event.data)
}

// Reset form
const resetForm = () => {
  formData.value = {
    name: '',
    age: 25,
    salary: 50000,
    startTime: '09:00',
    isActive: true,
    department: 'Engineering',
    skills: []
  }
}

// Log current data
const logData = () => {
  console.log('Current form data:', formData.value)
  console.log('Data is valid:', validateData())
}

// Simple validation
const validateData = () => {
  return formData.value.name && formData.value.age >= 0
}
</script>

<style scoped>
.json-view {
  background-color: #f5f5f5;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 14px;
}

.jsonforms-container {
  min-height: 400px;
}
</style>
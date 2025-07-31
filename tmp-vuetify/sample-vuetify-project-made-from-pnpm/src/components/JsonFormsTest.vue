<template>
  <v-container>
    <v-card>
      <v-card-title>JSON Forms with Vuetify Test</v-card-title>
      <v-card-text>
        <v-tabs v-model="tab">
          <v-tab value="form">Form</v-tab>
          <v-tab value="json">JSON Data</v-tab>
          <v-tab value="schema">Schema</v-tab>
        </v-tabs>
        
        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="form">
            <json-forms
              :data="data"
              :schema="schema"
              :renderers="renderers"
              @change="handleChange"
            />
          </v-tabs-window-item>
          
          <v-tabs-window-item value="json">
            <pre>{{ JSON.stringify(data, null, 2) }}</pre>
          </v-tabs-window-item>
          
          <v-tabs-window-item value="schema">
            <pre>{{ JSON.stringify(schema, null, 2) }}</pre>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vuetifyRenderers } from '@jsonforms/vue-vuetify'

const tab = ref('form')

// Test schema that includes a number field
const schema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
      title: 'Name'
    },
    age: {
      type: 'number',
      title: 'Age',
      minimum: 0,
      maximum: 150
    },
    salary: {
      type: 'number',
      title: 'Salary',
      description: 'Annual salary in USD'
    },
    startTime: {
      type: 'string',
      format: 'time',
      title: 'Start Time'
    },
    isActive: {
      type: 'boolean',
      title: 'Active'
    }
  },
  required: ['name', 'age']
}

const data = ref({
  name: 'John Doe',
  age: 30,
  salary: 50000,
  startTime: '09:00',
  isActive: true
})

const renderers = [...vuetifyRenderers]

const handleChange = (event: any) => {
  data.value = event.data
}
</script>
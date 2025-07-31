<template>
  <v-container>
    <v-card>
      <v-card-title>Vuetify Labs Components Test</v-card-title>
      <v-card-text>
        <v-row>
          <v-col cols="12" md="6">
            <h3>VNumberInput Test</h3>
            <v-number-input
              v-model="numberValue"
              label="Number Input"
              :min="0"
              :max="100"
              :step="5"
            />
            <p>Value: {{ numberValue }}</p>
          </v-col>
          
          <v-col cols="12" md="6">
            <h3>VTimePicker Test</h3>
            <v-time-picker
              v-model="timeValue"
              label="Time Picker"
            />
            <p>Time: {{ timeValue }}</p>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </v-container>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const numberValue = ref(25)
const timeValue = ref('12:30')
</script>
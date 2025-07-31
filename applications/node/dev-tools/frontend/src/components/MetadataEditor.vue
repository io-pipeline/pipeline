<template>
  <div class="metadata-editor">
    <div v-for="(item, index) in items" :key="index" class="d-flex align-center mb-2">
      <v-text-field
        v-model="item.key"
        label="Key"
        density="compact"
        variant="outlined"
        hide-details
        class="mr-2"
        @input="updateValue"
      />
      <v-text-field
        v-model="item.value"
        label="Value"
        density="compact"
        variant="outlined"
        hide-details
        class="mr-2"
        @input="updateValue"
      />
      <v-btn
        icon="mdi-delete"
        size="small"
        variant="text"
        @click="removeItem(index)"
      />
    </div>
    
    <v-btn
      size="small"
      variant="text"
      prepend-icon="mdi-plus"
      @click="addItem"
      class="mt-1"
    >
      Add metadata
    </v-btn>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface MetadataItem {
  key: string
  value: string
}

const props = defineProps<{
  modelValue: MetadataItem[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: MetadataItem[]]
}>()

const items = ref<MetadataItem[]>(props.modelValue || [])

watch(() => props.modelValue, (newValue) => {
  items.value = newValue || []
})

function addItem() {
  items.value.push({ key: '', value: '' })
  updateValue()
}

function removeItem(index: number) {
  items.value.splice(index, 1)
  updateValue()
}

function updateValue() {
  emit('update:modelValue', [...items.value])
}
</script>

<style scoped>
.metadata-editor {
  width: 100%;
}
</style>
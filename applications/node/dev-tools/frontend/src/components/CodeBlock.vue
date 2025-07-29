<template>
  <v-card class="code-block" variant="outlined" elevation="0">
    <v-toolbar density="compact" :color="$vuetify.theme.current.dark ? 'grey-darken-4' : 'grey-lighten-4'">
      <v-toolbar-title class="text-caption">{{ title || language.toUpperCase() }}</v-toolbar-title>
      <v-spacer></v-spacer>
      <v-btn
        icon="mdi-content-copy"
        variant="text"
        size="small"
        @click="copyCode"
        title="Copy to clipboard"
      />
      <v-btn
        v-if="showSave"
        icon="mdi-download"
        variant="text"
        size="small"
        @click="saveCode"
        title="Save as .bin"
      />
    </v-toolbar>

    <v-divider></v-divider>

    <v-card-text class="pa-0">
      <pre :class="`language-${language}`" class="rounded-0 ma-0"><code ref="codeElement">{{ code }}</code></pre>
    </v-card-text>

    <v-snackbar v-model="snackbar" :timeout="2000" location="bottom">
      {{ snackbarText }}
      <template v-slot:actions>
        <v-btn variant="text" @click="snackbar = false">Close</v-btn>
      </template>
    </v-snackbar>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue';
import Prism from 'prismjs';
import 'prismjs/themes/prism-tomorrow.css';
import 'prismjs/components/prism-json';

const props = defineProps<{
  code: string;
  language?: string;
  title?: string;
  showSave?: boolean;
  filename?: string;
}>();

const emit = defineEmits<{
  save: [filename: string];
}>();

const codeElement = ref<HTMLElement>();
const snackbar = ref(false);
const snackbarText = ref('');

// Highlight code when mounted or when code changes
const highlightCode = async () => {
  await nextTick();
  if (codeElement.value) {
    Prism.highlightElement(codeElement.value);
  }
};

onMounted(() => {
  highlightCode();
});

watch(() => props.code, () => {
  highlightCode();
});

const copyCode = async () => {
  try {
    await navigator.clipboard.writeText(props.code);
    snackbarText.value = 'Copied to clipboard!';
    snackbar.value = true;
  } catch (err) {
    console.error('Failed to copy text: ', err);
    snackbarText.value = 'Failed to copy';
    snackbar.value = true;
  }
};

const saveCode = () => {
  // Convert to binary format
  const blob = new Blob([props.code], { type: 'application/octet-stream' });
  
  // Create download link
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = props.filename || `output-${Date.now()}.bin`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  
  snackbarText.value = 'Downloaded as .bin file';
  snackbar.value = true;
};
</script>

<style scoped>
.code-block {
  overflow: hidden;
}

/* Override Prism's default styles to work with Vuetify themes */
:deep(pre[class*="language-"]) {
  margin: 0;
  padding: 1rem;
  overflow: auto;
  max-height: 400px;
  background: transparent !important;
}

:deep(pre[class*="language-"] code) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 0.875rem;
  line-height: 1.5;
}

/* Light theme adjustments */
.v-theme--light :deep(pre[class*="language-"]) {
  background-color: #f5f5f5 !important;
}

.v-theme--light :deep(.token.property),
.v-theme--light :deep(.token.string) {
  color: #0d7377;
}

.v-theme--light :deep(.token.boolean),
.v-theme--light :deep(.token.number) {
  color: #d63031;
}

/* Dark theme adjustments */
.v-theme--dark :deep(pre[class*="language-"]) {
  background-color: #1e1e1e !important;
}
</style>
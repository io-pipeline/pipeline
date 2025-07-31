<template>
  <v-card>
    <v-card-title>
      <v-icon start>mdi-file-document-edit</v-icon>
      PipeDoc Editor (Protobuf Forms Demo)
    </v-card-title>
    
    <v-card-text>
      <v-alert v-if="error" type="error" variant="tonal" class="mb-4">
        {{ error }}
      </v-alert>
      
      <v-tabs v-model="tab" class="mb-4">
        <v-tab value="form">Form Editor</v-tab>
        <v-tab value="json">JSON View</v-tab>
        <v-tab value="schema">Schema View</v-tab>
      </v-tabs>
      
      <v-tabs-window v-model="tab">
        <v-tabs-window-item value="form">
          <div v-if="schema" class="compact-form">
            <json-forms
              :data="pipeDoc"
              :schema="schema"
              :renderers="renderers"
              @change="handleChange"
            />
          </div>
          <v-progress-circular v-else indeterminate />
        </v-tabs-window-item>
        
        <v-tabs-window-item value="json">
          <pre class="json-view">{{ JSON.stringify(pipeDoc, null, 2) }}</pre>
        </v-tabs-window-item>
        
        <v-tabs-window-item value="schema">
          <pre class="json-view">{{ JSON.stringify(schema, null, 2) }}</pre>
        </v-tabs-window-item>
      </v-tabs-window>
    </v-card-text>
    
    <v-card-actions>
      <v-spacer />
      <v-btn @click="resetForm">Reset</v-btn>
      <v-btn color="primary" @click="saveDocument">Save</v-btn>
    </v-card-actions>
  </v-card>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { JsonForms } from '@jsonforms/vue'
import { vanillaRenderers } from '@jsonforms/vue-vanilla'
// For now, keep using our custom renderers until the package is built
import { vuetifyRenderers } from '../renderers/vue-vuetify/renderers'
import { customRenderers } from '../renderers'
// Import the generated types
import { 
  type PipeDoc 
} from '@pipeline/protobuf-forms'
import type { JsonSchema } from '@pipeline/protobuf-forms'
import { schemaConnectService } from '../services/schemaConnectService'

// State
const tab = ref('form')
const schema = ref<JsonSchema | null>(null)
const error = ref<string | null>(null)

// Use the same renderer setup as UniversalConfigCard
const renderers = Object.freeze([...vuetifyRenderers, ...customRenderers, ...vanillaRenderers])

// Initial PipeDoc data
const pipeDoc = ref<Partial<PipeDoc>>({
  id: `doc-${Date.now()}`,
  title: 'Test Document',
  sourceUri: 'file://test.txt',
  sourceMimeType: 'text/plain',
  documentType: 'test'
  // Don't initialize metadata - let user add entries as needed
})

// Load schema using Connect service
onMounted(async () => {
  try {
    schema.value = await schemaConnectService.getMessageSchema('PipeDoc')
    console.log('Loaded PipeDoc schema via Connect')
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load schema'
  }
})

// Handle form changes
const handleChange = (event: any) => {
  pipeDoc.value = event.data
}

// Debug: Check rendered DOM after schema loads
const checkFormSpacing = () => {
  setTimeout(() => {
    const formInputs = document.querySelectorAll('.compact-form .v-input')
    console.log('Form inputs found:', formInputs.length)
    
    if (formInputs.length > 1) {
      // Check spacing between first two inputs
      const firstInput = formInputs[0] as HTMLElement
      const secondInput = formInputs[1] as HTMLElement
      
      const firstRect = firstInput.getBoundingClientRect()
      const secondRect = secondInput.getBoundingClientRect()
      
      console.log('Spacing analysis:')
      console.log('- First input bottom:', firstRect.bottom)
      console.log('- Second input top:', secondRect.top)
      console.log('- Gap between inputs:', secondRect.top - firstRect.bottom, 'px')
      
      // Check all parent containers
      let parent = firstInput.parentElement
      let level = 0
      while (parent && parent !== document.body && level < 5) {
        const parentStyles = window.getComputedStyle(parent)
        console.log(`Parent level ${level}:`, parent.tagName, parent.className)
        console.log(`  - margin: ${parentStyles.margin}`)
        console.log(`  - padding: ${parentStyles.padding}`)
        console.log(`  - display: ${parentStyles.display}`)
        console.log(`  - gap: ${parentStyles.gap}`)
        parent = parent.parentElement
        level++
      }
      
      // Check array controls - look for cards or other containers
      const allCards = document.querySelectorAll('.compact-form .v-card')
      const expansions = document.querySelectorAll('.compact-form .v-expansion-panels')
      console.log('\nArray containers:')
      console.log('- Cards found:', allCards.length)
      console.log('- Expansion panels found:', expansions.length)
      
      // Look for the Metadata card specifically
      const metadataCard = Array.from(allCards).find(card => {
        const title = card.querySelector('.v-card-title')
        return title?.textContent?.trim() === 'Metadata'
      }) as HTMLElement
      
      if (metadataCard) {
        console.log('\nMetadata card analysis:')
        console.log('- Card classes:', metadataCard.className)
        const titleEl = metadataCard.querySelector('.v-card-title') as HTMLElement
        if (titleEl) {
          const titleStyles = window.getComputedStyle(titleEl)
          console.log('- Title padding:', titleStyles.padding)
          console.log('- Title font-size:', titleStyles.fontSize)
          console.log('- Title height:', titleEl.getBoundingClientRect().height)
        }
      }
      const blobCards: HTMLElement[] = []
      
      allCards.forEach(card => {
        const title = card.querySelector('.v-card-title')
        if (title?.textContent?.includes('Blob')) {
          blobCards.push(card as HTMLElement)
        }
      })
      
      console.log('Number of cards with "Blob":', blobCards.length)
      
      // Use the last one found (in case there are nested cards)
      const blobCard = blobCards[blobCards.length - 1]
      
      if (blobCard) {
        console.log('\nBlob card analysis:')
        const blobRect = blobCard.getBoundingClientRect()
        console.log('- Card height:', blobRect.height)
        
        // Check if it's actually the Blob field or maybe a parent
        console.log('- Card classes:', blobCard.className)
        
        // Look for expansion panels INSIDE the blob card
        const expansionPanels = blobCard.querySelectorAll('.v-expansion-panels')
        console.log('- Expansion panels in Blob:', expansionPanels.length)
        
        if (expansionPanels.length > 0) {
          const panel = expansionPanels[0] as HTMLElement
          const panelRect = panel.getBoundingClientRect()
          console.log('- Expansion panel height:', panelRect.height)
          
          // Check expansion panel items
          const panelItems = panel.querySelectorAll('.v-expansion-panel')
          console.log('- Number of expansion items:', panelItems.length)
        }
        
        // Look at the card's internal structure
        const cardText = blobCard.querySelector('.v-card-text')
        if (cardText) {
          const textStyles = window.getComputedStyle(cardText)
          console.log('- Card-text padding:', textStyles.padding)
          
          // The card text has only 1 child but is very tall - check why
          console.log('- Card text children:', cardText.children.length)
          const cardTextRect = cardText.getBoundingClientRect()
          console.log('- Card text actual height:', cardTextRect.height)
          
          // Check if there's a v-container with wrong padding
          const allContainers = blobCard.querySelectorAll('.v-container')
          console.log('- Total v-containers in Blob:', allContainers.length)
          
          allContainers.forEach((container, idx) => {
            const containerStyles = window.getComputedStyle(container)
            console.log(`  Container ${idx} padding:`, containerStyles.padding)
            console.log(`  Container ${idx} height:`, container.getBoundingClientRect().height)
          })
          
          // Check what's actually inside - maybe there's a nested structure
          console.log('\nChecking full Blob structure:')
          
          // Get ALL v-cards inside this card (nested cards)
          const nestedCards = blobCard.querySelectorAll('.v-card')
          console.log('- Nested cards inside Blob:', nestedCards.length)
          
          // Get all labels to see what fields are actually there
          const allLabels = blobCard.querySelectorAll('label')
          console.log('- All field labels in Blob:')
          allLabels.forEach((label, idx) => {
            console.log(`  ${idx}: ${label.textContent?.trim()}`)
          })
          
          // Look for any vertical-layout inside the card
          const vertLayout = cardText.querySelector('.vertical-layout')
          if (vertLayout) {
            console.log('- Found vertical-layout inside Blob card')
            const vertContainer = vertLayout.querySelector('.v-container')
            if (vertContainer) {
              const containerStyles = window.getComputedStyle(vertContainer)
              console.log('- Nested container padding:', containerStyles.padding)
            }
          }
          
          // Count all control divs
          const controls = cardText.querySelectorAll('.control')
          console.log('- Number of control divs:', controls.length)
          
          if (controls.length > 1) {
            const first = controls[0] as HTMLElement
            const second = controls[1] as HTMLElement
            const gap = second.getBoundingClientRect().top - first.getBoundingClientRect().bottom
            console.log('- Gap between controls:', gap, 'px')
          }
        }
      }
      
      // Check what's making the title so tall
      if (metadataCard) {
        const titleContent = metadataCard.querySelector('.v-card-title')
        if (titleContent) {
          // Check for any child elements that might be adding height
          const children = titleContent.children
          console.log('- Title has', children.length, 'child elements')
          for (let i = 0; i < children.length; i++) {
            const child = children[i] as HTMLElement
            console.log(`  Child ${i}:`, child.tagName, child.getBoundingClientRect().height, 'px')
            
            // Check what's inside the header
            if (child.tagName === 'HEADER') {
              const headerStyles = window.getComputedStyle(child)
              console.log('  Header padding:', headerStyles.padding)
              console.log('  Header margin:', headerStyles.margin)
              console.log('  Header min-height:', headerStyles.minHeight)
              
              // Check header children
              const headerChildren = child.children
              console.log('  Header has', headerChildren.length, 'children:')
              for (let j = 0; j < headerChildren.length; j++) {
                const headerChild = headerChildren[j] as HTMLElement
                console.log(`    ${j}:`, headerChild.tagName, headerChild.className, headerChild.getBoundingClientRect().height, 'px')
              }
            }
          }
        }
      }
    }
  }, 500) // Wait for rendering
}

// Watch for schema changes to check spacing
watch(schema, (newSchema) => {
  if (newSchema) {
    nextTick(() => {
      checkFormSpacing()
    })
  }
})

// Reset form
const resetForm = () => {
  pipeDoc.value = {
    id: `doc-${Date.now()}`,
    title: 'Test Document',
    sourceUri: 'file://test.txt',
    sourceMimeType: 'text/plain',
    documentType: 'test',
    metadata: {
      created: new Date().toISOString()
    }
  }
}

// Save document (just log for now)
const saveDocument = () => {
  console.log('Saving PipeDoc:', pipeDoc.value)
  emit('document-ready', pipeDoc.value)
}

// Define emits
const emit = defineEmits<{
  'document-ready': [data: any]
}>()

// Expose methods and data for parent component
defineExpose({
  pipeDoc,
  resetForm,
  getDocumentData: () => pipeDoc.value
})
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

:deep(.v-tabs) {
  margin-bottom: 16px;
}

/* Reduce spacing between form fields */
.compact-form :deep(.v-input) {
  margin-bottom: 4px !important;
}

.compact-form :deep(.v-messages) {
  min-height: 14px !important;
  font-size: 11px !important;
}

/* Reduce v-col padding for vertical layouts - even tighter */
.compact-form :deep(.vertical-layout-item.v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

/* Adjust first and last items */
.compact-form :deep(.vertical-layout-item.v-col:first-child) {
  padding-top: 0 !important;
}

.compact-form :deep(.vertical-layout-item.v-col:last-child) {
  padding-bottom: 0 !important;
}

/* Compact array controls (Keywords, Embedding, etc.) */
.compact-form :deep(.array-list.v-card) {
  margin-bottom: 4px !important;
  border: 1px solid rgba(0, 0, 0, 0.12) !important;
}

/* Add border in dark mode */
.v-theme--dark .compact-form :deep(.array-list.v-card) {
  border-color: rgba(255, 255, 255, 0.12) !important;
}

.compact-form :deep(.array-list .v-card-title) {
  padding: 4px 12px !important;
  font-size: 12px !important;
  line-height: 1.2 !important;
  min-height: 28px !important;
  background-color: rgba(0, 0, 0, 0.03);
}

/* Even more specific for array card titles */
.compact-form :deep(.v-card.array-list > .v-card-title) {
  padding: 4px 12px !important;
  font-size: 12px !important;
  font-weight: 500 !important;
  min-height: 28px !important;
  height: auto !important;
  display: flex !important;
  align-items: center !important;
}

/* Ensure title content doesn't expand */
.compact-form :deep(.array-list .v-card-title > *) {
  margin: 0 !important;
  padding: 0 !important;
  line-height: 1.2 !important;
}

/* Target the toolbar inside title if it exists */
.compact-form :deep(.array-list .v-card-title .array-list-toolbar) {
  padding: 0 !important;
  margin: 0 !important;
}

/* Target the header element inside array card titles */
.compact-form :deep(.array-list .v-card-title header) {
  min-height: auto !important;
  padding: 0 !important;
  margin: 0 !important;
}

/* Target v-toolbar with auto height */
.compact-form :deep(.array-list .v-card-title .v-toolbar) {
  min-height: auto !important;
  height: auto !important;
  padding: 4px 8px !important;
}

/* Remove v-toolbar__content padding */
.compact-form :deep(.array-list .v-card-title .v-toolbar__content) {
  height: auto !important;
  padding: 0 !important;
}

/* Target the title text inside the header */
.compact-form :deep(.array-list .v-card-title header .array-list-title) {
  font-size: 12px !important;
  line-height: 1.2 !important;
  padding: 0 !important;
  margin: 0 !important;
}

/* Target any text elements in the array title */
.compact-form :deep(.array-list .v-card-title span),
.compact-form :deep(.array-list .v-card-title div.array-list-title) {
  font-size: 12px !important;
}

/* Target the specific title text in v-toolbar */
.compact-form :deep(.array-list .v-toolbar__title) {
  font-size: 12px !important;
  line-height: 1.2 !important;
}

/* Force all text in array card headers to be small */
.compact-form :deep(.array-list header *),
.compact-form :deep(.array-list .v-toolbar *),
.compact-form :deep(.array-list .v-toolbar__content *) {
  font-size: 12px !important;
}

/* Specifically target the title text that might be deeply nested */
.compact-form :deep(.array-list .v-card-title .v-toolbar__content > div),
.compact-form :deep(.array-list .v-card-title .v-toolbar__content > span) {
  font-size: 12px !important;
  font-weight: 500 !important;
}

/* Title background in dark mode */
.v-theme--dark .compact-form :deep(.array-list .v-card-title) {
  background-color: rgba(255, 255, 255, 0.03);
}

.compact-form :deep(.array-list .v-card-text) {
  padding: 4px 12px !important;
}

/* Make array tables more compact */
.compact-form :deep(.array-list .v-table) {
  font-size: 12px !important;
}

.compact-form :deep(.array-list .v-table th) {
  padding: 4px 8px !important;
  height: 32px !important;
  font-size: 11px !important;
  font-weight: 600 !important;
}

.compact-form :deep(.array-list .v-table td) {
  padding: 4px 8px !important;
  height: 36px !important;
}

/* Compact inputs inside array tables */
.compact-form :deep(.array-list .v-table .v-input) {
  margin: 0 !important;
  font-size: 12px !important;
}

.compact-form :deep(.array-list .v-table .v-field__input) {
  padding: 4px 8px !important;
  min-height: 28px !important;
  font-size: 12px !important;
}

/* Compact expansion panels too */
.compact-form :deep(.v-expansion-panels) {
  margin-bottom: 4px !important;
}

.compact-form :deep(.v-expansion-panel) {
  margin-bottom: 0 !important;
}

.compact-form :deep(.v-expansion-panel-title) {
  min-height: 32px !important;
  padding: 6px 12px !important;
  font-size: 13px !important;
}

.compact-form :deep(.v-expansion-panel-text__wrapper) {
  padding: 4px 8px !important;
}

/* Expansion panels inside cards need even tighter spacing */
.compact-form :deep(.v-card .v-expansion-panels) {
  margin: 0 !important;
}

.compact-form :deep(.v-card .v-expansion-panel + .v-expansion-panel) {
  margin-top: 2px !important;
}

/* Nested vertical layouts inside expansion panels */
.compact-form :deep(.v-expansion-panel .vertical-layout .v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

/* Reduce spacing in array item lists */
.compact-form :deep(.array-list-item) {
  padding: 2px 0 !important;
}

/* Make array item content more compact */
.compact-form :deep(.array-list-item-content) {
  padding: 4px !important;
}

/* Compact the toolbar in arrays */
.compact-form :deep(.array-list-toolbar) {
  padding: 2px 0 !important;
}

/* Make the add button smaller */
.compact-form :deep(.array-list-toolbar .v-btn) {
  height: 28px !important;
  font-size: 12px !important;
}

/* Hide empty state message */
.compact-form :deep(.array-list-no-data) {
  display: none !important;
}

/* Hide table headers when no data */
.compact-form :deep(.array-list .v-table:has(tbody:empty)) thead {
  display: none !important;
}

/* Also hide the table itself if it only contains headers */
.compact-form :deep(.array-list .v-table:has(tbody:empty)) {
  display: none !important;
}

/* Make the card more compact when empty */
.compact-form :deep(.array-list:has(.array-list-no-data)) .v-card-text {
  padding-bottom: 4px !important;
}

/* Make array item labels smaller */
.compact-form :deep(.array-list-item-label) {
  font-size: 12px !important;
  padding: 2px 0 !important;
}

/* Fix group cards that have excessive height */
.compact-form :deep(.group.v-card .v-card-text) {
  padding: 4px 12px !important;
}

/* If there's a container inside group cards */
.compact-form :deep(.group.v-card .v-container) {
  padding: 0 !important;
  min-height: auto !important;
}

/* Make card titles smaller */
.compact-form :deep(.v-card-title) {
  padding: 8px 16px !important;
  font-size: 14px !important;
  line-height: 1.2 !important;
}

/* Consistent font sizes for all labels */
.compact-form :deep(.v-label) {
  font-size: 12px !important;
}

.compact-form :deep(.v-field-label) {
  font-size: 12px !important;
}

/* Make input text consistent */
.compact-form :deep(.v-field__input) {
  font-size: 14px !important;
}

/* Reduce space between nested cards */
.compact-form :deep(.v-card .v-card) {
  margin-top: 4px !important;
  margin-bottom: 0 !important;
}

/* Even tighter for v-col inside group cards */
.compact-form :deep(.group.v-card .v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

/* Handle nested v-containers and v-rows inside cards */
.compact-form :deep(.v-card .v-container) {
  padding: 4px !important;
}

.compact-form :deep(.v-card .v-row) {
  margin: -2px !important;
}

.compact-form :deep(.v-card .v-col) {
  padding: 2px !important;
}

/* Even tighter for nested structures in cards */
.compact-form :deep(.v-card .v-card-text .v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

/* Specifically target nested array items */
.compact-form :deep(.v-card .array-list-item .v-col) {
  padding-top: 2px !important;
  padding-bottom: 2px !important;
}

/* Compact nested inputs inside cards */
.compact-form :deep(.v-card .v-input) {
  margin-bottom: 4px !important;
}

/* Handle textareas (like for Blob) */
.compact-form :deep(.v-textarea textarea) {
  min-height: 60px !important;
  max-height: 120px !important;
}

.compact-form :deep(.v-textarea .v-field__input) {
  padding: 8px !important;
  min-height: 60px !important;
}

/* Specifically target the Blob card content */
.compact-form :deep(.v-card-text > .v-textarea:only-child) {
  margin: 0 !important;
}

/* If Blob is using a specific component */
.compact-form :deep(.v-textarea.v-input--density-default) {
  --v-input-control-height: 80px !important;
}
</style>
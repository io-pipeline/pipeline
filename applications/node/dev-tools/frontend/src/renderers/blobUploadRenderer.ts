import { rankWith, and, schemaMatches, uiTypeIs } from '@jsonforms/core'
import type { JsonFormsRendererRegistryEntry } from '@jsonforms/vue'
import BlobUploadRenderer from './BlobUploadRenderer.vue'

// Match objects with specific blob-like properties
const isBlobSchema = schemaMatches((schema) => {
  if (schema.type !== 'object' || !schema.properties) {
    return false
  }
  
  const props = schema.properties
  // Check if it has blob-specific properties
  return (
    props.blobId?.type === 'string' &&
    props.data?.type === 'string' &&
    props.mimeType?.type === 'string' &&
    props.filename?.type === 'string'
  )
})

export const blobUploadTester = rankWith(
  10, // High priority
  and(
    uiTypeIs('Control'),
    isBlobSchema
  )
)

export const blobUploadRenderer: JsonFormsRendererRegistryEntry = {
  renderer: BlobUploadRenderer,
  tester: blobUploadTester
}
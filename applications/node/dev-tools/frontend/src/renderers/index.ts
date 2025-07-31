import ArrayAutoCompleteRenderer from './ArrayAutoCompleteRenderer.vue'
import { arrayAutoCompleteTester } from './arrayAutoCompleteTester'
import { blobUploadRenderer } from './blobUploadRenderer'

export const customRenderers = [
  {
    tester: arrayAutoCompleteTester,
    renderer: ArrayAutoCompleteRenderer
  },
  blobUploadRenderer
]
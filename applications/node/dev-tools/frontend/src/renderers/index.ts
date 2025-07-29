import ArrayAutoCompleteRenderer from './ArrayAutoCompleteRenderer.vue'
import { arrayAutoCompleteTester } from './arrayAutoCompleteTester'

export const customRenderers = [
  {
    tester: arrayAutoCompleteTester,
    renderer: ArrayAutoCompleteRenderer
  }
]
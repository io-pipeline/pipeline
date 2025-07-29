import BooleanToggleRenderer from './BooleanToggleRenderer.vue'
import { booleanToggleTester } from './booleanToggleTester'
import ArrayControlRenderer from './ArrayControlRenderer.vue'
import { arrayControlTester } from './arrayControlTester'
import ArrayAutoCompleteRenderer from './ArrayAutoCompleteRenderer.vue'
import { arrayAutoCompleteTester } from './arrayAutoCompleteTester'
export const customRenderers = [
  {
    tester: booleanToggleTester,
    renderer: BooleanToggleRenderer
  },
  {
    tester: arrayAutoCompleteTester,
    renderer: ArrayAutoCompleteRenderer
  },
  {
    tester: arrayControlTester,
    renderer: ArrayControlRenderer
  }
]
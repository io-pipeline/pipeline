import BooleanToggleRenderer from './BooleanToggleRenderer.vue'
import { booleanToggleTester } from './booleanToggleTester'
import ArrayControlRenderer from './ArrayControlRenderer.vue'
import { arrayControlTester } from './arrayControlTester'

export const customRenderers = [
  {
    tester: booleanToggleTester,
    renderer: BooleanToggleRenderer
  },
  {
    tester: arrayControlTester,
    renderer: ArrayControlRenderer
  }
]
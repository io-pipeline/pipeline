import { rankWith } from '@jsonforms/core'

// Debug tester to log all array controls
export const debugArrayTester = rankWith(-1, (uischema, schema, context) => {
  if (schema?.type === 'array') {
  }
  return false // Never actually use this renderer
})
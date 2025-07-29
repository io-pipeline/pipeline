import { rankWith, and, uiTypeIs, schemaTypeIs, or, isPrimitiveArrayControl, isObjectArrayControl } from '@jsonforms/core'

// Test for any array control (both primitive and object arrays)
export const arrayControlTester = rankWith(
  10, // High priority to override default
  or(isPrimitiveArrayControl, isObjectArrayControl)
)
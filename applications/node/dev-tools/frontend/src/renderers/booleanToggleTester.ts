import { rankWith, isBooleanControl, JsonSchema } from '@jsonforms/core'

// Test if this is a boolean control that should use toggle renderer
export const booleanToggleTester = rankWith(
  10, // High priority to override default boolean renderer
  isBooleanControl
)
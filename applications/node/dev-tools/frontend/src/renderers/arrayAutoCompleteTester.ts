import { rankWith, and, schemaMatches, or, isPrimitiveArrayControl, isObjectArrayControl, resolveSchema } from '@jsonforms/core'

// Higher priority for string arrays that have suggestions (Vuetify uses 3)
export const arrayAutoCompleteTester = rankWith(20, (uischema, schema, context) => {
  // First check if it's an array control
  const isArray = isPrimitiveArrayControl(uischema, schema, context) || 
                  isObjectArrayControl(uischema, schema, context)
  
  if (!isArray) {
    return false
  }
  
  // Resolve the actual schema from the scope
  let resolvedSchema = schema
  if (uischema?.scope && context?.rootSchema) {
    const resolved = resolveSchema(context.rootSchema, uischema.scope, context.rootSchema)
    if (resolved) {
      resolvedSchema = resolved
    }
  }
  
  
  // Check if this is a string array
  const itemType = resolvedSchema?.items?.type
  
  if (itemType !== 'string') {
    return false
  }
  
  // Check if we have suggestions in various places
  const hasSuggestions = !!(
    resolvedSchema.items?.enum || 
    resolvedSchema.items?.['x-suggestions'] || 
    resolvedSchema['x-suggestions'] ||
    resolvedSchema.items?.examples
  )
  
  const result = isArray && hasSuggestions
  
  return result
})
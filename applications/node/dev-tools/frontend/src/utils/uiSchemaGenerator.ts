/**
 * Enhanced UI Schema Generator for JSONForms
 * Creates hierarchical layouts with better visual organization
 */

interface SchemaProperty {
  type: string
  description?: string
  properties?: Record<string, any>
  items?: any
  default?: any
  examples?: any[]
  format?: string
  minimum?: number
  maximum?: number
  'x-ui-widget'?: string
  [key: string]: any
}

interface UISchemaElement {
  type: string
  label?: string
  scope?: string
  elements?: UISchemaElement[]
  options?: Record<string, any>
  rule?: {
    effect: string
    condition: {
      scope: string
      schema: any
    }
  }
}

// Convert property name to human-readable label
function propertyToLabel(propertyName: string): string {
  return propertyName
    .replace(/([A-Z])/g, ' $1') // Add space before capitals
    .replace(/^./, (str) => str.toUpperCase()) // Capitalize first letter
    .replace(/_/g, ' ') // Replace underscores with spaces
    .trim()
}

// Determine if a property should be in advanced section
function isAdvancedProperty(key: string, prop: SchemaProperty): boolean {
  const advancedKeywords = ['advanced', 'timeout', 'max', 'recursion', 'fallback']
  return advancedKeywords.some(keyword => 
    key.toLowerCase().includes(keyword) || 
    prop.description?.toLowerCase().includes(keyword)
  )
}

// Generate control options based on property schema
function generateControlOptions(prop: SchemaProperty): Record<string, any> {
  const options: Record<string, any> = {}
  
  // Add description as help text
  if (prop.description) {
    options.description = prop.description
  }
  
  // Handle specific widgets
  if (prop['x-ui-widget']) {
    options.widget = prop['x-ui-widget']
  }
  
  // Boolean specific - use toggle renderer
  if (prop.type === 'boolean') {
    options.toggle = true
    // Pass description to the renderer
    if (prop.description) {
      options.detail = prop.description
    }
  }
  
  // Add examples to dropdown if available
  if (prop.examples && prop.examples.length > 0) {
    options.suggestions = prop.examples
  }
  
  // Format specific options
  if (prop.type === 'integer' || prop.type === 'number') {
    if (prop.minimum !== undefined) options.min = prop.minimum
    if (prop.maximum !== undefined) options.max = prop.maximum
    
    // Use slider for bounded numbers
    if (prop.minimum !== undefined && prop.maximum !== undefined) {
      options.slider = true
    }
  }
  
  // Array specific options
  if (prop.type === 'array') {
    options.showSortButtons = true
  }
  
  return options
}

// Generate a control element for a property
function generateControl(key: string, prop: SchemaProperty, parentScope = '#'): UISchemaElement {
  const control: UISchemaElement = {
    type: 'Control',
    scope: `${parentScope}/properties/${key}`,
    label: propertyToLabel(key)
  }
  
  const options = generateControlOptions(prop)
  if (Object.keys(options).length > 0) {
    control.options = options
  }
  
  return control
}

// Generate a group/card for nested objects
function generateGroup(key: string, prop: SchemaProperty, parentScope = '#'): UISchemaElement {
  const elements: UISchemaElement[] = []
  
  if (prop.properties) {
    // Define field order priority
    const fieldOrder: Record<string, number> = {
      // Boolean fields typically come first
      'enableTitleExtraction': 1,
      'fallbackToFilename': 2,
      // Lists/arrays come after
      'supportedMimeTypes': 3,
      // Everything else
    }
    
    // Sort properties with custom order
    const sortedKeys = Object.keys(prop.properties).sort((a, b) => {
      const aProp = prop.properties[a]
      const bProp = prop.properties[b]
      
      // First check for x-ui-order property
      const aOrder = aProp['x-ui-order'] ?? fieldOrder[a] ?? 100
      const bOrder = bProp['x-ui-order'] ?? fieldOrder[b] ?? 100
      
      if (aOrder !== bOrder) {
        return aOrder - bOrder
      }
      
      // Then by advanced property
      const aAdvanced = isAdvancedProperty(a, aProp)
      const bAdvanced = isAdvancedProperty(b, bProp)
      
      if (aAdvanced && !bAdvanced) return 1
      if (!aAdvanced && bAdvanced) return -1
      
      // Finally alphabetical
      return a.localeCompare(b)
    })
    
    sortedKeys.forEach(subKey => {
      const subProp = prop.properties[subKey]
      
      // Skip hidden fields and description fields
      if (subProp['x-ui-hidden'] === true || subKey.toLowerCase().endsWith('description')) {
        return
      }
      
      if (subProp.type === 'object' && subProp.properties) {
        elements.push(generateGroup(subKey, subProp, `${parentScope}/properties/${key}`))
      } else {
        elements.push(generateControl(subKey, subProp, `${parentScope}/properties/${key}`))
      }
    })
  }
  
  // Instead of putting elements directly in the group, wrap them in a horizontal layout
  const layoutElement: UISchemaElement = {
    type: 'HorizontalLayout',
    elements: elements
  }
  
  return {
    type: 'Group',
    label: propertyToLabel(key),
    elements: [layoutElement],
    options: {
      style: {
        padding: '0.75rem',
        marginBottom: '0.5rem',
        border: '1px solid #e0e0e0',
        borderRadius: '4px',
        backgroundColor: '#f9f9f9'
      }
    }
  }
}

/**
 * Generate an enhanced UI schema from a JSON schema
 */
export function generateUISchema(schema: any): UISchemaElement | undefined {
  if (!schema || !schema.properties) {
    return undefined
  }
  
  const mainElements: UISchemaElement[] = []
  const advancedElements: UISchemaElement[] = []
  
  // Process top-level properties with custom ordering
  const topLevelKeys = Object.keys(schema.properties).sort((a, b) => {
    const aProp = schema.properties[a]
    const bProp = schema.properties[b]
    
    // First check for x-ui-order property
    const aOrder = aProp['x-ui-order'] ?? 100
    const bOrder = bProp['x-ui-order'] ?? 100
    
    if (aOrder !== bOrder) {
      return aOrder - bOrder
    }
    
    // Then by type (booleans first, then others)
    const aIsBool = aProp.type === 'boolean'
    const bIsBool = bProp.type === 'boolean'
    if (aIsBool && !bIsBool) return -1
    if (!aIsBool && bIsBool) return 1
    
    // Finally alphabetical
    return a.localeCompare(b)
  })
  
  topLevelKeys.forEach(key => {
    const prop = schema.properties[key]
    
    // Skip hidden fields and description fields
    if (prop['x-ui-hidden'] === true || key.toLowerCase().endsWith('description')) {
      return
    }
    
    // Generate appropriate element
    let element: UISchemaElement
    if (prop.type === 'object' && prop.properties) {
      element = generateGroup(key, prop)
    } else {
      element = generateControl(key, prop)
    }
    
    // Separate advanced properties
    if (key.toLowerCase().includes('advanced')) {
      advancedElements.push(element)
    } else {
      mainElements.push(element)
    }
  })
  
  // Create the final layout
  const elements: UISchemaElement[] = [...mainElements]
  
  // Add advanced section if there are advanced properties
  if (advancedElements.length > 0) {
    elements.push({
      type: 'Group',
      label: 'Advanced Settings',
      elements: advancedElements,
      options: {
        collapsible: true,
        collapsed: true,
        style: {
          marginTop: '1rem',
          padding: '0.75rem',
          border: '1px solid #d0d0d0',
          borderRadius: '4px',
          backgroundColor: '#f5f5f5'
        }
      }
    })
  }
  
  // Group related fields horizontally
  const finalElements: UISchemaElement[] = []
  
  // Group boolean fields horizontally
  const booleanFields = mainElements.filter(el => {
    const key = el.scope?.split('/').pop()
    const prop = schema.properties[key]
    return prop?.type === 'boolean'
  })
  
  const nonBooleanFields = mainElements.filter(el => {
    const key = el.scope?.split('/').pop()
    const prop = schema.properties[key]
    return prop?.type !== 'boolean'
  })
  
  // Add boolean fields in horizontal groups (2-3 per row)
  if (booleanFields.length > 0) {
    for (let i = 0; i < booleanFields.length; i += 3) {
      finalElements.push({
        type: 'HorizontalLayout',
        elements: booleanFields.slice(i, i + 3)
      })
    }
  }
  
  // Add non-boolean fields
  finalElements.push(...nonBooleanFields)
  
  // Add advanced section if needed
  if (advancedElements.length > 0) {
    finalElements.push({
      type: 'Group',
      label: 'Advanced Settings',
      elements: advancedElements,
      options: {
        collapsible: true,
        collapsed: true,
        style: {
          marginTop: '1rem',
          padding: '0.75rem',
          border: '1px solid #d0d0d0',
          borderRadius: '4px',
          backgroundColor: '#f5f5f5'
        }
      }
    })
  }
  
  return {
    type: 'VerticalLayout',
    elements: finalElements
  }
}

/**
 * Generate categorized UI schema with tabs for better organization
 */
export function generateCategorizedUISchema(schema: any): UISchemaElement | undefined {
  if (!schema || !schema.properties) {
    return generateUISchema(schema)
  }
  
  // If there are more than 4 top-level properties, consider using tabs
  const topLevelCount = Object.keys(schema.properties).length
  if (topLevelCount <= 4) {
    return generateUISchema(schema)
  }
  
  // Categorize properties
  const categories: Record<string, UISchemaElement[]> = {
    'General': [],
    'Options': [],
    'Advanced': [],
    'Error Handling': []
  }
  
  Object.entries(schema.properties).forEach(([key, prop]: [string, any]) => {
    let element: UISchemaElement
    if (prop.type === 'object' && prop.properties) {
      element = generateGroup(key, prop)
    } else {
      element = generateControl(key, prop)
    }
    
    // Categorize based on key name
    if (key === 'config_id' || key.includes('name') || key.includes('description')) {
      categories['General'].push(element)
    } else if (key.toLowerCase().includes('error') || key.toLowerCase().includes('handling')) {
      categories['Error Handling'].push(element)
    } else if (key.toLowerCase().includes('advanced')) {
      categories['Advanced'].push(element)
    } else {
      categories['Options'].push(element)
    }
  })
  
  // Remove empty categories
  Object.keys(categories).forEach(cat => {
    if (categories[cat].length === 0) {
      delete categories[cat]
    }
  })
  
  // If only one category, return simple layout
  if (Object.keys(categories).length === 1) {
    return generateUISchema(schema)
  }
  
  // Create tab layout
  const tabs = Object.entries(categories).map(([label, elements]) => ({
    type: 'Category',
    label,
    elements
  }))
  
  return {
    type: 'Categorization',
    elements: tabs
  }
}
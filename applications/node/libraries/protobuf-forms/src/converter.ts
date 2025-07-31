import * as protobuf from 'protobufjs'

export interface JsonSchema {
  type?: string
  properties?: Record<string, JsonSchema>
  items?: JsonSchema
  enum?: any[]
  required?: string[]
  title?: string
  description?: string
  default?: any
  minimum?: number
  maximum?: number
  [key: string]: any
}

export interface ConversionOptions {
  // Add UI hints for better form rendering
  addUiHints?: boolean
  // Include field descriptions from proto comments
  includeComments?: boolean
  // Custom field transformers
  fieldTransformers?: Record<string, (field: protobuf.Field) => JsonSchema>
}

export class ProtobufToJsonSchemaConverter {
  private options: ConversionOptions

  constructor(options: ConversionOptions = {}) {
    this.options = {
      addUiHints: true,
      includeComments: true,
      ...options
    }
  }

  /**
   * Convert a protobuf Type to JSON Schema
   */
  convertType(type: protobuf.Type): JsonSchema {
    const schema: JsonSchema = {
      type: 'object',
      title: type.name,
      properties: {},
      required: []
    }

    // Convert each field
    for (const [fieldName, field] of Object.entries(type.fields)) {
      schema.properties![fieldName] = this.convertField(field)
      
      // Add to required if not optional
      if (field.required) {
        schema.required!.push(fieldName)
      }
    }

    return schema
  }

  /**
   * Convert a protobuf Field to JSON Schema property
   */
  private convertField(field: protobuf.Field): JsonSchema {
    let schema: JsonSchema = {}

    // Check for custom transformer
    if (this.options.fieldTransformers?.[field.name]) {
      return this.options.fieldTransformers[field.name](field)
    }
    
    // Check if this is a map field
    if ((field as any).map) {
      return this.convertMapField(field as any)
    }
    
    // Add title from field name
    if (this.options.addUiHints) {
      schema.title = this.camelCaseToTitle(field.name)
    }

    // Handle repeated fields (arrays)
    if (field.repeated) {
      schema.type = 'array'
      schema.items = this.getFieldTypeSchema(field)
      
      if (this.options.addUiHints) {
        // Add UI hints for common array types
        if (field.type === 'string' && field.name.toLowerCase().includes('mime')) {
          schema.items['x-suggestions'] = this.getMimeTypeSuggestions()
        }
      }
    } else {
      schema = this.getFieldTypeSchema(field)
    }

    // Add field metadata
    if (field.comment && this.options.includeComments) {
      schema.description = field.comment
    }

    // Add default value if specified
    if (field.defaultValue !== undefined) {
      schema.default = field.defaultValue
    }

    return schema
  }

  /**
   * Get JSON Schema for a specific field type
   */
  private getFieldTypeSchema(field: protobuf.Field): JsonSchema {
    const schema: JsonSchema = {}

    switch (field.type) {
      // String types
      case 'string':
        schema.type = 'string'
        if (this.options.addUiHints) {
          // Add UI hints based on field name
          if (field.name.toLowerCase().includes('url') || field.name.toLowerCase().includes('uri')) {
            schema.format = 'uri'
          } else if (field.name.toLowerCase().includes('date')) {
            schema.format = 'date-time'
          } else if (field.name.toLowerCase().includes('description')) {
            schema['x-ui-widget'] = 'textarea'
          }
        }
        break

      // Numeric types
      case 'double':
      case 'float':
        schema.type = 'number'
        break
      
      case 'int32':
      case 'uint32':
      case 'sint32':
      case 'fixed32':
      case 'sfixed32':
        schema.type = 'integer'
        schema.minimum = field.type.startsWith('u') ? 0 : undefined
        break
      
      case 'int64':
      case 'uint64':
      case 'sint64':
      case 'fixed64':
      case 'sfixed64':
        // JSON Schema doesn't have a specific int64 type
        schema.type = 'string'
        schema.pattern = '^-?[0-9]+$'
        schema.description = (schema.description || '') + ' (64-bit integer)'
        break

      // Boolean
      case 'bool':
        schema.type = 'boolean'
        if (this.options.addUiHints) {
          schema['x-ui-widget'] = 'switch'
        }
        break

      // Bytes
      case 'bytes':
        schema.type = 'string'
        schema.contentEncoding = 'base64'
        schema.description = (schema.description || '') + ' (base64 encoded)'
        break

      // Message type (nested object)
      default:
        if (field.resolvedType) {
          if (field.resolvedType instanceof protobuf.Type) {
            // Check for well-known types
            const fullName = field.resolvedType.fullName
            if (fullName === '.google.protobuf.Struct') {
              // Struct is arbitrary JSON
              return {
                type: 'object',
                additionalProperties: true,
                description: 'Arbitrary JSON object'
              }
            } else if (fullName === '.google.protobuf.Value') {
              // Value can be any JSON value
              return {
                description: 'Any JSON value'
              }
            } else if (fullName === '.google.protobuf.ListValue') {
              // ListValue is an array of any values
              return {
                type: 'array',
                items: {},
                description: 'Array of any JSON values'
              }
            } else if (fullName === '.google.protobuf.Timestamp') {
              // Timestamp should be a date-time string
              return {
                type: 'string',
                format: 'date-time',
                title: this.camelCaseToTitle(field.name),
                description: 'Date and time'
              }
            }
            
            // Regular nested message
            return this.convertType(field.resolvedType)
          } else if (field.resolvedType instanceof protobuf.Enum) {
            // Enum type
            schema.type = 'string'
            schema.enum = Object.keys(field.resolvedType.values)
            if (this.options.addUiHints) {
              schema['x-ui-widget'] = 'select'
            }
          }
        }
        break
    }

    return schema
  }

  /**
   * Get MIME type suggestions for autocomplete
   */
  private getMimeTypeSuggestions(): string[] {
    return [
      'text/plain',
      'text/html',
      'text/csv',
      'text/xml',
      'application/json',
      'application/xml',
      'application/pdf',
      'application/zip',
      'application/octet-stream',
      'image/jpeg',
      'image/png',
      'image/gif',
      'image/svg+xml',
      'audio/mpeg',
      'audio/wav',
      'video/mp4',
      'video/webm'
    ]
  }

  /**
   * Convert protobuf Enum to JSON Schema
   */
  convertEnum(enumType: protobuf.Enum): JsonSchema {
    return {
      type: 'string',
      title: enumType.name,
      enum: Object.keys(enumType.values),
      'x-ui-widget': 'select'
    }
  }

  /**
   * Convert a map field to JSON Schema
   */
  private convertMapField(field: any): JsonSchema {
    // For better UI support, convert maps to arrays of key-value objects
    if (field.keyType === 'string' && field.type === 'string') {
      return {
        type: 'array',
        title: this.camelCaseToTitle(field.name),
        description: 'Key-value pairs',
        items: {
          type: 'object',
          properties: {
            key: {
              type: 'string',
              title: 'Key'
            },
            value: {
              type: 'string', 
              title: 'Value'
            }
          },
          required: ['key', 'value']
        }
      }
    }
    
    // For other map types, use object with additionalProperties
    const schema: JsonSchema = {
      type: 'object',
      title: this.camelCaseToTitle(field.name),
      additionalProperties: true,
      description: `Map<${field.keyType}, ${field.type}>`
    }
    
    return schema
  }

  /**
   * Convert camelCase to Title Case
   */
  private camelCaseToTitle(str: string): string {
    // Handle special cases
    const result = str
      // Insert space before uppercase letters
      .replace(/([A-Z])/g, ' $1')
      // Insert space before numbers
      .replace(/([0-9]+)/g, ' $1')
      // Handle common abbreviations
      .replace(/Id$/g, ' ID')
      .replace(/Url$/g, ' URL')
      .replace(/Uri$/g, ' URI')
      .replace(/Api$/g, ' API')
      .replace(/Xml$/g, ' XML')
      .replace(/Json$/g, ' JSON')
      .replace(/Html$/g, ' HTML')
      .replace(/Http$/g, ' HTTP')
      .replace(/Sql$/g, ' SQL')
      .replace(/Cpu$/g, ' CPU')
      .replace(/Gpu$/g, ' GPU')
      // Trim and capitalize first letter
      .trim()
    
    return result.charAt(0).toUpperCase() + result.slice(1)
  }
}
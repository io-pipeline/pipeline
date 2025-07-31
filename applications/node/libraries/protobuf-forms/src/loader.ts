import * as protobuf from 'protobufjs'
import { ProtobufToJsonSchemaConverter, JsonSchema, ConversionOptions } from './converter'

export interface LoaderOptions extends ConversionOptions {
  // Additional proto paths for imports
  includePaths?: string[]
}

export class ProtobufSchemaLoader {
  private converter: ProtobufToJsonSchemaConverter
  private root: protobuf.Root | null = null
  private options: LoaderOptions

  constructor(options: LoaderOptions = {}) {
    this.options = options
    this.converter = new ProtobufToJsonSchemaConverter(options)
  }

  /**
   * Load a proto file and prepare for schema generation
   */
  async loadProtoFile(protoPath: string): Promise<void> {
    this.root = new protobuf.Root()
    
    // Add include paths if specified
    if (this.options.includePaths) {
      this.options.includePaths.forEach(path => {
        this.root!.resolvePath = (origin, target) => {
          // Custom path resolution logic
          return target
        }
      })
    }

    await this.root.load(protoPath)
  }

  /**
   * Load proto definition from string
   */
  loadProtoString(protoContent: string, filename = 'schema.proto'): void {
    this.root = protobuf.parse(protoContent).root
  }

  /**
   * Get JSON Schema for a specific message type
   */
  getMessageSchema(messageName: string): JsonSchema {
    if (!this.root) {
      throw new Error('No proto file loaded. Call loadProtoFile or loadProtoString first.')
    }

    const type = this.root.lookupType(messageName)
    return this.converter.convertType(type)
  }

  /**
   * Get all message schemas from the loaded proto
   */
  getAllMessageSchemas(): Record<string, JsonSchema> {
    if (!this.root) {
      throw new Error('No proto file loaded. Call loadProtoFile or loadProtoString first.')
    }

    const schemas: Record<string, JsonSchema> = {}
    
    // Recursively find all message types
    const findTypes = (obj: any) => {
      if (obj instanceof protobuf.Type) {
        schemas[obj.fullName.slice(1)] = this.converter.convertType(obj)
      }
      
      if (obj.nested) {
        Object.values(obj.nested).forEach(findTypes)
      }
    }

    findTypes(this.root)
    return schemas
  }

  /**
   * Get the loaded protobuf root for advanced usage
   */
  getRoot(): protobuf.Root | null {
    return this.root
  }
}
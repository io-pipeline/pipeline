// Core exports
export { ProtobufToJsonSchemaConverter } from './converter'
export type { JsonSchema, ConversionOptions } from './converter'

export { ProtobufSchemaLoader } from './loader'
export type { LoaderOptions } from './loader'

// Import for the utility function
import { ProtobufSchemaLoader } from './loader'
import type { ConversionOptions, JsonSchema } from './converter'

// Utility function for quick schema generation
export async function generateSchema(
  protoPath: string,
  messageType: string,
  options?: ConversionOptions
): Promise<JsonSchema> {
  const loader = new ProtobufSchemaLoader(options)
  await loader.loadProtoFile(protoPath)
  return loader.getMessageSchema(messageType)
}

// Re-export generated types (will be created by ts-proto)
export * from './generated'
import { createClient } from "@connectrpc/connect";
import { SchemaService } from "@/gen/schema_service_pb";
import { getConnectTransport } from "./connectService";
import type { JsonSchema } from '@pipeline/protobuf-forms';

// Cache for loaded schemas
const schemaCache = new Map<string, JsonSchema>();

export const schemaConnectService = {
  // Create the schema client
  schemaClient: createClient(SchemaService, getConnectTransport()),

  // Get schema for a protobuf message type
  async getMessageSchema(
    messageType: string,
    options?: {
      addUiHints?: boolean;
      includeComments?: boolean;
    }
  ): Promise<JsonSchema> {
    // Check cache first
    const cacheKey = `${messageType}-${options?.addUiHints ?? true}-${options?.includeComments ?? true}`;
    if (schemaCache.has(cacheKey)) {
      return schemaCache.get(cacheKey)!;
    }

    try {
      const response = await this.schemaClient.getMessageSchema({
        messageType,
        addUiHints: options?.addUiHints ?? true,
        includeComments: options?.includeComments ?? true
      });

      // The schema field is already a JsonObject (google.protobuf.Struct is mapped to JsonObject)
      if (!response.schema) {
        throw new Error('No schema returned from server');
      }
      
      // The schema is already a plain JavaScript object
      const jsonSchema = response.schema as JsonSchema;
      
      // Cache it
      schemaCache.set(cacheKey, jsonSchema);
      
      return jsonSchema;
    } catch (error) {
      console.error(`Failed to load schema for ${messageType}:`, error);
      throw error;
    }
  },

  // Get all available message types
  async getAllMessageTypes(): Promise<string[]> {
    try {
      const response = await this.schemaClient.getAllMessageTypes({});
      return response.messageTypes;
    } catch (error) {
      console.error('Failed to load message types:', error);
      throw error;
    }
  },

  // Clear the cache if needed
  clearCache() {
    schemaCache.clear();
  }
};
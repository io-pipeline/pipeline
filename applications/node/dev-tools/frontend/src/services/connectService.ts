import { createRegistry } from "@bufbuild/protobuf";
import { StructSchema } from "@bufbuild/protobuf/wkt";
import { createConnectTransport } from "@connectrpc/connect-web";
import { createClient } from "@connectrpc/connect";
import type { Transport } from "@connectrpc/connect";

// Import generated schemas
import {
  PipeDocSchema,
  PipeStreamSchema,
  StepExecutionRecordSchema,
  ErrorDataSchema,
  SemanticProcessingResultSchema,
  BlobSchema,
  BatchInfoSchema,
  EmbeddingSchema,
  ChunkEmbeddingSchema,
  SemanticChunkSchema,
  FailedStepInputStateSchema
} from "@/gen/pipeline_core_types_pb";
import {
  ModuleProcessRequestSchema,
  ModuleProcessResponseSchema,
  ServiceRegistrationResponseSchema,
  RegistrationRequestSchema
} from "@/gen/pipe_step_processor_service_pb";
import {
  FilesystemService,
  FormatFilesystemRequestSchema,
  FormatFilesystemResponseSchema
} from "@/gen/filesystem_service_pb";
import { PipeStepProcessor } from "@/gen/pipe_step_processor_service_pb";
import { Health } from "@/gen/health/v1/health_pb";
import {
  PipeDocRepository,
  FormatRepositoryRequestSchema,
  FormatRepositoryResponseSchema
} from "@/gen/pipedoc_repository_pb";
import {
  GetMessageSchemaRequestSchema,
  GetMessageSchemaResponseSchema,
  GetAllMessageTypesRequestSchema,
  GetAllMessageTypesResponseSchema
} from "@/gen/schema_service_pb";

// Create a registry with all types that might be in Any fields
const typeRegistry = createRegistry(
  // Google types
  StructSchema,

  // Pipeline core types
  PipeDocSchema,
  PipeStreamSchema,
  StepExecutionRecordSchema,
  ErrorDataSchema,
  SemanticProcessingResultSchema,
  BlobSchema,
  BatchInfoSchema,
  EmbeddingSchema,
  ChunkEmbeddingSchema,
  SemanticChunkSchema,
  FailedStepInputStateSchema,

  // Module types
  ModuleProcessRequestSchema,
  ModuleProcessResponseSchema,
  ServiceRegistrationResponseSchema,
  RegistrationRequestSchema,

  // Schema service types
  GetMessageSchemaRequestSchema,
  GetMessageSchemaResponseSchema,
  GetAllMessageTypesRequestSchema,
  GetAllMessageTypesResponseSchema,

  // Format operation types
  FormatFilesystemRequestSchema,
  FormatFilesystemResponseSchema,
  FormatRepositoryRequestSchema,
  FormatRepositoryResponseSchema
);

// Create the transport with type registry
let transport: Transport | null = null;

export function getConnectTransport(): Transport {
  if (!transport) {
    transport = createConnectTransport({
      baseUrl: `${window.location.protocol}//${window.location.hostname}:3000/connect`,
      jsonOptions: {
        registry: typeRegistry,
      },
      // Force binary for streaming support
      useBinaryFormat: true,
    });
  }
  return transport;
}

// Create service clients
export function createFilesystemClient() {
  return createClient(FilesystemService, getConnectTransport());
}

export function createPipeDocRepositoryClient() {
  return createClient(PipeDocRepository, getConnectTransport());
}

// Create module processor client with custom headers
export function createModuleProcessorClient(moduleAddress: string) {
  const transport = createConnectTransport({
    baseUrl: `${window.location.protocol}//${window.location.hostname}:3000/connect`,
    jsonOptions: {
      registry: typeRegistry,
    },
    // Use binary for efficiency
    useBinaryFormat: true,
    interceptors: [
      (next) => async (req) => {
        // Add module address as a header
        req.header.set("x-module-address", moduleAddress);
        return await next(req);
      },
    ],
  });

  return createClient(PipeStepProcessor, transport);
}

// Create health check client with custom headers
export function createHealthClient(moduleAddress: string) {
  const transport = createConnectTransport({
    baseUrl: `${window.location.protocol}//${window.location.hostname}:3000/connect`,
    jsonOptions: {
      registry: typeRegistry,
    },
    // Force binary for streaming support
    useBinaryFormat: true,
    interceptors: [
      (next) => async (req) => {
        // Add module address as a header
        req.header.set("x-module-address", moduleAddress);
        return await next(req);
      },
    ],
  });

  return createClient(Health, transport);
}

// Create repository health client with configurable address
export function createRepositoryHealthClient(repositoryAddress?: string) {
  // Use provided address or default
  const address = repositoryAddress || 'localhost:38002'

  const transport = createConnectTransport({
    baseUrl: `${window.location.protocol}//${window.location.hostname}:3000/connect`,
    jsonOptions: {
      registry: typeRegistry,
    },
    // Force binary for streaming support
    useBinaryFormat: true,
    interceptors: [
      (next) => async (req) => {
        // Set the repository address header
        req.header.set("x-module-address", address);
        return await next(req);
      },
    ],
  });

  return createClient(Health, transport);
}

// Export the registry in case components need it
export { typeRegistry };

// Export service instances for convenience
export const filesystemService = createFilesystemClient();
export const pipeDocRepositoryService = createPipeDocRepositoryClient();

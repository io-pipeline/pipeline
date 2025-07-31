import { ConnectRouter, Code, ConnectError } from "@connectrpc/connect";
import { 
  FilesystemService,
  GetChildrenRequest,
  GetNodeRequest,
  CreateNodeRequest,
  UpdateNodeRequest,
  DeleteNodeRequest,
  GetPathRequest,
  GetTreeRequest,
  MoveNodeRequest,
  CopyNodeRequest,
  SearchNodesRequest
} from "../gen/filesystem_service_pb";
import { 
  PipeDocRepositoryService,
  InsertDocumentRequest,
  GetDocumentRequest,
  GetDocumentsRequest,
  UpdateDocumentRequest,
  GetDocumentCountRequest,
  DeleteDocumentRequest,
  DeleteDocumentsRequest
} from "../gen/pipedoc_repository_pb";
import {
  PipeStepProcessor,
  ModuleProcessRequest,
  RegistrationRequest,
  TestProcessDataRequest
} from "../gen/pipe_step_processor_service_pb";
import { Health } from "../gen/health/v1/health_pb";
import {
  SchemaService,
  GetMessageSchemaRequest,
  GetAllMessageTypesRequest
} from "../gen/schema_service_pb";
import { createClient, Code as ConnectCode } from "@connectrpc/connect";
import { createGrpcTransport } from "@connectrpc/connect-node";
import { ProtobufSchemaLoader } from '@pipeline/protobuf-forms';
import path from 'path';
import { toStruct } from "@bufbuild/protobuf/wkt";

// Create a gRPC transport to connect to the repository service
const grpcTransport = createGrpcTransport({
  baseUrl: "http://localhost:38002",
  httpVersion: "2",
});

// Create a gRPC client for the repository service
const filesystemClient = createClient(FilesystemService, grpcTransport);

// Create a dynamic gRPC client factory for module connections
function createModuleClient(address: string) {
  const moduleTransport = createGrpcTransport({
    baseUrl: `http://${address}`,
    httpVersion: "2",
  });
  return createClient(PipeStepProcessor, moduleTransport);
}

// Create health check client factory
function createHealthClient(address: string) {
  const healthTransport = createGrpcTransport({
    baseUrl: `http://${address}`,
    httpVersion: "2",
  });
  return createClient(Health, healthTransport);
}

export default (router: ConnectRouter) => {
  // PipeStepProcessor service - proxies to dynamic module addresses
  router.service(PipeStepProcessor, {
    async processData(request: ModuleProcessRequest, context) {
      // Extract module address from headers or request metadata
      const moduleAddress = context.requestHeader.get("x-module-address");
      if (!moduleAddress) {
        throw new ConnectError("Module address not provided", Code.InvalidArgument);
      }
      
      console.log(`[Connect] Proxying processData to module at ${moduleAddress}`);
      try {
        const client = createModuleClient(moduleAddress);
        const response = await client.processData(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying processData:", error);
        throw error;
      }
    },

    async testProcessData(request: ModuleProcessRequest, context) {
      const moduleAddress = context.requestHeader.get("x-module-address");
      if (!moduleAddress) {
        throw new ConnectError("Module address not provided", Code.InvalidArgument);
      }
      
      console.log(`[Connect] Proxying testProcessData to module at ${moduleAddress}`);
      try {
        const client = createModuleClient(moduleAddress);
        const response = await client.testProcessData(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying testProcessData:", error);
        throw error;
      }
    },

    async getServiceRegistration(request: RegistrationRequest, context) {
      const moduleAddress = context.requestHeader.get("x-module-address");
      if (!moduleAddress) {
        throw new ConnectError("Module address not provided", Code.InvalidArgument);
      }
      
      console.log(`[Connect] Proxying getServiceRegistration to module at ${moduleAddress}`);
      try {
        const client = createModuleClient(moduleAddress);
        const response = await client.getServiceRegistration(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying getServiceRegistration:", error);
        throw error;
      }
    },
  });

  // Health service - proxies to dynamic module addresses
  router.service(Health, {
    async check(request, context) {
      const moduleAddress = context.requestHeader.get("x-module-address");
      if (!moduleAddress) {
        throw new ConnectError("Module address not provided", Code.InvalidArgument);
      }
      
      try {
        const client = createHealthClient(moduleAddress);
        const response = await client.check(request);
        return response;
      } catch (error) {
        console.error(`[Connect] Error proxying health check to ${moduleAddress}:`, error);
        throw error;
      }
    },

    async *watch(request, context) {
      const moduleAddress = context.requestHeader.get("x-module-address");
      if (!moduleAddress) {
        throw new ConnectError("Module address not provided", Code.InvalidArgument);
      }
      
      console.log(`[Connect] Proxying health watch to module at ${moduleAddress}`);
      try {
        const client = createHealthClient(moduleAddress);
        
        // For now, just do periodic checks since gRPC-Web doesn't support server streaming
        // We'll simulate streaming by checking every 5 seconds
        const signal = context.signal;
        
        while (!signal.aborted) {
          try {
            const response = await client.check(request);
            yield response;
            
            // Wait 5 seconds before next check
            await new Promise(resolve => {
              const timeout = setTimeout(resolve, 5000);
              signal.addEventListener("abort", () => clearTimeout(timeout), { once: true });
            });
          } catch (error) {
            console.error(`[Connect] Error in health watch for ${moduleAddress}:`, error);
            // Send NOT_SERVING status on error
            yield {
              status: 2 // NOT_SERVING
            };
            // Wait before retry
            await new Promise(resolve => {
              const timeout = setTimeout(resolve, 5000);
              signal.addEventListener("abort", () => clearTimeout(timeout), { once: true });
            });
          }
        }
      } catch (error) {
        console.error(`[Connect] Error setting up health watch for ${moduleAddress}:`, error);
        throw error;
      }
    }
  });

  // Filesystem service
  router.service(FilesystemService, {
    async getChildren(request: GetChildrenRequest) {
      console.log("[Connect] Proxying getChildren request to gRPC server...");
      try {
        const response = await filesystemClient.getChildren(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying getChildren:", error);
        throw error;
      }
    },

    async getNode(request: GetNodeRequest) {
      console.log("[Connect] Proxying getNode request to gRPC server...");
      try {
        const response = await filesystemClient.getNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying getNode:", error);
        throw error;
      }
    },

    async createNode(request: CreateNodeRequest) {
      console.log("[Connect] Proxying createNode request to gRPC server...");
      try {
        const response = await filesystemClient.createNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying createNode:", error);
        throw error;
      }
    },

    async updateNode(request: UpdateNodeRequest) {
      console.log("[Connect] Proxying updateNode request to gRPC server...");
      try {
        const response = await filesystemClient.updateNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying updateNode:", error);
        throw error;
      }
    },

    async deleteNode(request: DeleteNodeRequest) {
      console.log("[Connect] Proxying deleteNode request to gRPC server...");
      try {
        const response = await filesystemClient.deleteNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying deleteNode:", error);
        throw error;
      }
    },

    async getPath(request: GetPathRequest) {
      console.log("[Connect] Proxying getPath request to gRPC server...");
      try {
        const response = await filesystemClient.getPath(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying getPath:", error);
        throw error;
      }
    },

    async getTree(request: GetTreeRequest) {
      console.log("[Connect] Proxying getTree request to gRPC server...");
      try {
        const response = await filesystemClient.getTree(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying getTree:", error);
        throw error;
      }
    },

    async moveNode(request: MoveNodeRequest) {
      console.log("[Connect] Proxying moveNode request to gRPC server...");
      try {
        const response = await filesystemClient.moveNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying moveNode:", error);
        throw error;
      }
    },

    async copyNode(request: CopyNodeRequest) {
      console.log("[Connect] Proxying copyNode request to gRPC server...");
      try {
        const response = await filesystemClient.copyNode(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying copyNode:", error);
        throw error;
      }
    },

    async searchNodes(request: SearchNodesRequest) {
      console.log("[Connect] Proxying searchNodes request to gRPC server...");
      try {
        const response = await filesystemClient.searchNodes(request);
        return response;
      } catch (error) {
        console.error("[Connect] Error proxying searchNodes:", error);
        throw error;
      }
    },
  });

  // Schema service - provides JSON schemas for protobuf messages
  router.service(SchemaService, {
    async getMessageSchema(request: GetMessageSchemaRequest) {
      console.log("[Connect] Getting schema for message type:", request.messageType);
      
      try {
        // Create loader with options from request
        const loader = new ProtobufSchemaLoader({
          addUiHints: request.addUiHints ?? true,
          includeComments: request.includeComments ?? true
        });
        
        // Load the proto file
        const protoPath = path.join(__dirname, '../../proto/pipeline_core_types.proto');
        await loader.loadProtoFile(protoPath);
        
        // Get schema for the message type
        const jsonSchema = loader.getMessageSchema(request.messageType);
        
        // Convert JSON schema to Struct
        const structValue = toStruct(jsonSchema);
        
        return {
          schema: structValue,
          messageType: request.messageType
        };
      } catch (error) {
        console.error(`[Connect] Error loading schema for ${request.messageType}:`, error);
        throw new ConnectError(
          `Failed to load schema: ${(error as Error).message}`,
          Code.Internal
        );
      }
    },

    async getAllMessageTypes(request: GetAllMessageTypesRequest) {
      console.log("[Connect] Getting all message types");
      
      try {
        const loader = new ProtobufSchemaLoader();
        const protoPath = path.join(__dirname, '../../proto/pipeline_core_types.proto');
        await loader.loadProtoFile(protoPath);
        
        const schemas = loader.getAllMessageSchemas();
        const messageTypes = Object.keys(schemas);
        
        return {
          messageTypes
        };
      } catch (error) {
        console.error('[Connect] Error loading message types:', error);
        throw new ConnectError(
          `Failed to load message types: ${(error as Error).message}`,
          Code.Internal
        );
      }
    }
  });
  
  return router;
};
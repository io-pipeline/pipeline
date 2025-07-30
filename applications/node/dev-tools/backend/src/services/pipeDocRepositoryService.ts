import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';
import { promisify } from 'util';

// Load the proto file
const PROTO_PATH = path.join(__dirname, '../../proto/pipedoc_repository.proto');
const PROTO_INCLUDES = path.join(__dirname, '../../proto');

const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
  includeDirs: [PROTO_INCLUDES]
});

const protoDescriptor = grpc.loadPackageDefinition(packageDefinition) as any;
const PipeDocRepository = protoDescriptor.io.pipeline.repository.v1.PipeDocRepository;

export class PipeDocRepositoryService {
  private client: any;
  private createPipeDoc: any;
  private getPipeDoc: any;
  private listPipeDocs: any;
  private updatePipeDoc: any;
  private deletePipeDoc: any;

  constructor(address: string = 'localhost:38002') {
    this.client = new PipeDocRepository(
      address,
      grpc.credentials.createInsecure()
    );

    // Promisify the methods for easier use
    this.createPipeDoc = promisify(this.client.createPipeDoc.bind(this.client));
    this.getPipeDoc = promisify(this.client.getPipeDoc.bind(this.client));
    this.listPipeDocs = promisify(this.client.listPipeDocs.bind(this.client));
    this.updatePipeDoc = promisify(this.client.updatePipeDoc.bind(this.client));
    this.deletePipeDoc = promisify(this.client.deletePipeDoc.bind(this.client));
  }

  async create(document: any, tags?: Record<string, string>, description?: string) {
    const request = {
      document,
      tags: tags || {},
      description: description || ''
    };
    
    try {
      const response = await this.createPipeDoc(request);
      return response;
    } catch (error) {
      console.error('Error creating PipeDoc:', error);
      throw error;
    }
  }

  async get(storageId: string) {
    try {
      const response = await this.getPipeDoc({ storage_id: storageId });
      return response;
    } catch (error) {
      console.error('Error getting PipeDoc:', error);
      throw error;
    }
  }

  async list(pageSize?: number, pageToken?: string, filter?: string, orderBy?: string) {
    const request = {
      page_size: pageSize || 50,
      page_token: pageToken || '',
      filter: filter || '',
      order_by: orderBy || ''
    };
    
    try {
      const response = await this.listPipeDocs(request);
      return response;
    } catch (error) {
      console.error('Error listing PipeDocs:', error);
      throw error;
    }
  }

  async update(storageId: string, document?: any, tags?: Record<string, string>, description?: string) {
    const request: any = {
      storage_id: storageId
    };
    
    if (document) request.document = document;
    if (tags) request.tags = tags;
    if (description) request.description = description;
    
    try {
      const response = await this.updatePipeDoc(request);
      return response;
    } catch (error) {
      console.error('Error updating PipeDoc:', error);
      throw error;
    }
  }

  async delete(storageId: string) {
    try {
      await this.deletePipeDoc({ storage_id: storageId });
      return true;
    } catch (error) {
      console.error('Error deleting PipeDoc:', error);
      throw error;
    }
  }

  close() {
    grpc.closeClient(this.client);
  }
}

// Export singleton instance
export const pipeDocRepository = new PipeDocRepositoryService();
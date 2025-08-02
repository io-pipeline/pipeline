// IMPORTANT: DO NOT DELETE - These interfaces define the data structures for
// local storage of test documents, pipeline configurations, and batch jobs.
// Used by storageService.ts to persist user data to the local filesystem.
// This allows the dev tools to work without external database dependencies.

// Simple ObjectId replacement for local storage
type ObjectId = string;

export interface TestDocument {
  _id?: ObjectId | string;
  name: string;
  description?: string;
  moduleAddress: string;
  configId: string;
  request: any; // ModuleProcessRequest - the full .bin content
  response?: any; // ModuleProcessResponse
  metadata: {
    createdAt: Date;
    updatedAt: Date;
    tags: string[];
    fileInfo: {
      originalName: string;
      mimeType: string;
      size: number;
    };
  };
}

export interface PipelineChain {
  _id?: ObjectId | string;
  name: string;
  description: string;
  steps: Array<{
    order: number;
    moduleAddress: string;
    configId: string;
    testDataId?: ObjectId;
    mapping?: Array<{
      fromField: string;
      toField: string;
    }>;
  }>;
  metadata: {
    createdAt: Date;
    updatedAt: Date;
    author?: string;
  };
}

export interface BatchJob {
  _id?: ObjectId | string;
  name: string;
  testDataIds: ObjectId[];
  pipelineId?: ObjectId;
  schedule?: string; // Cron expression
  results: Array<{
    testDataId: ObjectId;
    status: 'pending' | 'running' | 'completed' | 'failed';
    startTime?: Date;
    endTime?: Date;
    output?: any;
  }>;
}
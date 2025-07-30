import { TestDocument, PipelineChain, BatchJob } from '../models/TestDocument';
import * as fs from 'fs/promises';
import * as path from 'path';

export interface StorageAdapter {
  // Test Documents
  saveTestDocument(doc: TestDocument): Promise<string>;
  getTestDocument(id: string): Promise<TestDocument | null>;
  listTestDocuments(filter?: any): Promise<TestDocument[]>;
  updateTestDocument(id: string, update: Partial<TestDocument>): Promise<boolean>;
  deleteTestDocument(id: string): Promise<boolean>;
  
  // Pipeline Chains
  savePipelineChain(chain: PipelineChain): Promise<string>;
  getPipelineChain(id: string): Promise<PipelineChain | null>;
  listPipelineChains(): Promise<PipelineChain[]>;
  deletePipelineChain(id: string): Promise<boolean>;
}

// Local file system implementation
class LocalStorageAdapter implements StorageAdapter {
  private storagePath: string;
  
  constructor(basePath: string = './dev-tools-data') {
    this.storagePath = basePath;
  }
  
  private async ensureDir(dir: string) {
    try {
      await fs.mkdir(dir, { recursive: true });
    } catch (error) {
      console.error('Failed to create directory:', error);
    }
  }
  
  private generateId(): string {
    return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
  
  // Test Documents
  async saveTestDocument(doc: TestDocument): Promise<string> {
    const docsPath = path.join(this.storagePath, 'testDocuments');
    await this.ensureDir(docsPath);
    
    const id = this.generateId();
    const now = new Date();
    const document = {
      ...doc,
      _id: id,
      metadata: {
        ...doc.metadata,
        createdAt: doc.metadata?.createdAt || now,
        updatedAt: now
      }
    };
    
    await fs.writeFile(
      path.join(docsPath, `${id}.json`),
      JSON.stringify(document, null, 2)
    );
    
    return id;
  }
  
  async getTestDocument(id: string): Promise<TestDocument | null> {
    const filePath = path.join(this.storagePath, 'testDocuments', `${id}.json`);
    
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      return JSON.parse(content);
    } catch (error) {
      return null;
    }
  }
  
  async listTestDocuments(filter?: any): Promise<TestDocument[]> {
    const docsPath = path.join(this.storagePath, 'testDocuments');
    await this.ensureDir(docsPath);
    
    try {
      const files = await fs.readdir(docsPath);
      const documents: TestDocument[] = [];
      
      for (const file of files) {
        if (file.endsWith('.json')) {
          const content = await fs.readFile(path.join(docsPath, file), 'utf-8');
          const doc = JSON.parse(content);
          
          // Simple filter implementation
          if (!filter || this.matchesFilter(doc, filter)) {
            documents.push(doc);
          }
        }
      }
      
      // Sort by updatedAt descending
      documents.sort((a, b) => {
        const aTime = new Date(a.metadata?.updatedAt || 0).getTime();
        const bTime = new Date(b.metadata?.updatedAt || 0).getTime();
        return bTime - aTime;
      });
      
      return documents.slice(0, 100); // Limit to 100 documents
    } catch (error) {
      console.error('Error listing documents:', error);
      return [];
    }
  }
  
  private matchesFilter(doc: any, filter: any): boolean {
    for (const [key, value] of Object.entries(filter)) {
      if (doc[key] !== value) {
        return false;
      }
    }
    return true;
  }
  
  async updateTestDocument(id: string, update: Partial<TestDocument>): Promise<boolean> {
    const existing = await this.getTestDocument(id);
    if (!existing) return false;
    
    const updated = {
      ...existing,
      ...update,
      metadata: {
        ...existing.metadata,
        ...update.metadata,
        updatedAt: new Date()
      }
    };
    
    await fs.writeFile(
      path.join(this.storagePath, 'testDocuments', `${id}.json`),
      JSON.stringify(updated, null, 2)
    );
    
    return true;
  }
  
  async deleteTestDocument(id: string): Promise<boolean> {
    const filePath = path.join(this.storagePath, 'testDocuments', `${id}.json`);
    
    try {
      await fs.unlink(filePath);
      return true;
    } catch (error) {
      return false;
    }
  }
  
  // Pipeline Chains
  async savePipelineChain(chain: PipelineChain): Promise<string> {
    const chainsPath = path.join(this.storagePath, 'pipelineChains');
    await this.ensureDir(chainsPath);
    
    const id = this.generateId();
    const now = new Date();
    const document = {
      ...chain,
      _id: id,
      metadata: {
        ...chain.metadata,
        createdAt: chain.metadata?.createdAt || now,
        updatedAt: now
      }
    };
    
    await fs.writeFile(
      path.join(chainsPath, `${id}.json`),
      JSON.stringify(document, null, 2)
    );
    
    return id;
  }
  
  async getPipelineChain(id: string): Promise<PipelineChain | null> {
    const filePath = path.join(this.storagePath, 'pipelineChains', `${id}.json`);
    
    try {
      const content = await fs.readFile(filePath, 'utf-8');
      return JSON.parse(content);
    } catch (error) {
      return null;
    }
  }
  
  async listPipelineChains(): Promise<PipelineChain[]> {
    const chainsPath = path.join(this.storagePath, 'pipelineChains');
    await this.ensureDir(chainsPath);
    
    try {
      const files = await fs.readdir(chainsPath);
      const chains: PipelineChain[] = [];
      
      for (const file of files) {
        if (file.endsWith('.json')) {
          const content = await fs.readFile(path.join(chainsPath, file), 'utf-8');
          chains.push(JSON.parse(content));
        }
      }
      
      // Sort by updatedAt descending
      chains.sort((a, b) => {
        const aTime = new Date(a.metadata?.updatedAt || 0).getTime();
        const bTime = new Date(b.metadata?.updatedAt || 0).getTime();
        return bTime - aTime;
      });
      
      return chains;
    } catch (error) {
      console.error('Error listing pipeline chains:', error);
      return [];
    }
  }
  
  async deletePipelineChain(id: string): Promise<boolean> {
    const filePath = path.join(this.storagePath, 'pipelineChains', `${id}.json`);
    
    try {
      await fs.unlink(filePath);
      return true;
    } catch (error) {
      return false;
    }
  }
}

// Storage Service
class StorageService {
  private adapter: StorageAdapter;
  
  constructor() {
    // Always use local storage now
    this.adapter = new LocalStorageAdapter();
  }
  
  async initialize(): Promise<void> {
    console.log('Storage service initialized with local file system');
  }
  
  getAdapter(): StorageAdapter {
    return this.adapter;
  }
  
  isUsingMongoDB(): boolean {
    return false; // Always local storage now
  }
  
  async reinitialize(): Promise<void> {
    // No-op for local storage
  }
}

// Export singleton instance
export const storageService = new StorageService();
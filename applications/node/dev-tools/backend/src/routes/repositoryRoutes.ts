import { Router } from 'express';
import { pipeDocRepository } from '../services/pipeDocRepositoryService';

const router = Router();

// Test endpoint to create a PipeDoc
router.post('/test-create', async (req, res) => {
  try {
    // Create a test document
    const testDoc = {
      id: `test-doc-${Date.now()}`,
      title: 'Test Document from Node.js',
      body: 'This is a test document created from the Node.js dev tools',
      source_uri: 'test://node-dev-tools',
      keywords: ['test', 'node', 'grpc'],
      document_type: 'test'
    };

    const tags = {
      source: 'node-dev-tools',
      environment: 'development',
      test: 'true'
    };

    const result = await pipeDocRepository.create(
      testDoc,
      tags,
      'Test document created to verify gRPC integration'
    );

    res.json({
      success: true,
      storageId: result.storage_id,
      document: result.stored_document
    });
  } catch (error: any) {
    console.error('Repository test error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to create document'
    });
  }
});

// List documents
router.get('/list', async (req, res) => {
  try {
    const pageSize = parseInt(req.query.pageSize as string) || 10;
    const pageToken = req.query.pageToken as string || '';
    
    const result = await pipeDocRepository.list(pageSize, pageToken);
    
    res.json({
      success: true,
      documents: result.documents,
      nextPageToken: result.next_page_token,
      totalCount: result.total_count
    });
  } catch (error: any) {
    console.error('Repository list error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to list documents'
    });
  }
});

// Save seed data with metadata
router.post('/save-seed', async (req, res) => {
  try {
    const { document, config, metadata } = req.body;
    
    // Enhance document with metadata
    const enhancedDoc = {
      ...document,
      metadata: {
        ...document.metadata,
        seedDataConfig: config,
        ...metadata
      }
    };
    
    const result = await pipeDocRepository.create(
      enhancedDoc,
      metadata.tags,
      metadata.description
    );
    
    res.json({
      success: true,
      storageId: result.storage_id,
      document: result.stored_document
    });
  } catch (error: any) {
    console.error('Repository save seed error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to save seed data'
    });
  }
});

// Update document metadata
router.put('/:storageId', async (req, res) => {
  try {
    const { description, tags } = req.body;
    
    const result = await pipeDocRepository.update(
      req.params.storageId,
      undefined, // don't update document
      tags,
      description
    );
    
    res.json({
      success: true,
      document: result
    });
  } catch (error: any) {
    console.error('Repository update error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to update document'
    });
  }
});

// Get a specific document
router.get('/:storageId', async (req, res) => {
  try {
    const result = await pipeDocRepository.get(req.params.storageId);
    
    res.json({
      success: true,
      document: result
    });
  } catch (error: any) {
    console.error('Repository get error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to get document'
    });
  }
});

// Delete a document
router.delete('/:storageId', async (req, res) => {
  try {
    await pipeDocRepository.delete(req.params.storageId);
    
    res.json({
      success: true,
      message: 'Document deleted successfully'
    });
  } catch (error: any) {
    console.error('Repository delete error:', error);
    res.status(500).json({
      success: false,
      error: error.message || 'Failed to delete document'
    });
  }
});

export default router;
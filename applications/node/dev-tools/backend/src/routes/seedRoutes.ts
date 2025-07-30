import { Router } from 'express';
import multer from 'multer';

const router = Router();

// Configure multer for file uploads
const storage = multer.memoryStorage();
const upload = multer({ storage });

// Create seed data with file upload
router.post('/create', upload.single('file'), async (req, res) => {
    if (!req.file) {
        return res.status(400).json({ error: 'No file uploaded' });
    }
    
    try {
        const { docId, streamId, title, config } = req.body;
        
        // Parse config if it's a string
        const parsedConfig = typeof config === 'string' ? JSON.parse(config) : config;
        
        // Create PipeDoc with file data
        const pipeDoc = {
            id: docId || `doc-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            title: title || req.file.originalname,
            source_uri: `file://${req.file.originalname}`,
            source_mime_type: req.file.mimetype,
            document_type: 'seed-data',
            blob: {
                data: req.file.buffer.toString('base64'),
                mime_type: req.file.mimetype,
                size: req.file.size,
                file_name: req.file.originalname
            },
            metadata: {
                source: 'dev-tool-seed-builder',
                created_at: new Date().toISOString(),
                file_name: req.file.originalname,
                file_size: req.file.size,
                mime_type: req.file.mimetype
            }
        };
        
        // Create the request
        const request = {
            stream_id: streamId || `stream-${Date.now()}`,
            document: pipeDoc,
            config: parsedConfig
        };
        
        res.json({ 
            success: true, 
            request,
            binarySize: req.file.size,
            mimeType: req.file.mimetype 
        });
    } catch (error) {
        console.error('Error creating seed data:', error);
        res.status(500).json({ 
            error: 'Failed to create seed data',
            details: (error as Error).message 
        });
    }
});

// Create seed data from text
router.post('/create-text', async (req, res) => {
    try {
        const { docId, streamId, title, content, mimeType, config } = req.body;
        
        // Create PipeDoc with text data
        const pipeDoc = {
            id: docId || `doc-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            title: title || 'Text Document',
            source_uri: 'text://inline',
            source_mime_type: mimeType || 'text/plain',
            document_type: 'seed-data',
            blob: {
                data: Buffer.from(content).toString('base64'),
                mime_type: mimeType || 'text/plain',
                size: Buffer.byteLength(content),
                file_name: `${title || 'document'}.txt`
            },
            metadata: {
                source: 'dev-tool-seed-builder',
                created_at: new Date().toISOString(),
                content_type: 'text'
            }
        };
        
        // Create the request
        const request = {
            stream_id: streamId || `stream-${Date.now()}`,
            document: pipeDoc,
            config: config || {}
        };
        
        res.json({ 
            success: true, 
            request,
            binarySize: Buffer.byteLength(content),
            mimeType: mimeType || 'text/plain'
        });
    } catch (error) {
        console.error('Error creating text seed data:', error);
        res.status(500).json({ 
            error: 'Failed to create text seed data',
            details: (error as Error).message 
        });
    }
});

export default router;
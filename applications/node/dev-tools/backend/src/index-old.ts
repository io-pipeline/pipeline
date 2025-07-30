import express from 'express';
import cors from 'cors';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';
import RefParser from '@apidevtools/json-schema-ref-parser';
import multer from 'multer';
import { database } from './config/database';
import { storageService } from './services/storageService';
import { dockerService } from './services/dockerService';
import repositoryRoutes from './routes/repositoryRoutes';

const app = express();
const PORT = 3000;

// Configure multer for memory storage
const upload = multer({ 
    storage: multer.memoryStorage(),
    limits: {
        fileSize: 50 * 1024 * 1024 // 50MB limit
    }
});

app.use(cors());
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Add repository routes
app.use('/api/repository', repositoryRoutes);

// Load proto files
const PROTO_PATH = path.join(__dirname, '../proto/pipe_step_processor_service.proto');
const HEALTH_PROTO_PATH = path.join(__dirname, '../proto/health/v1/health.proto');

const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
    includeDirs: [path.join(__dirname, '../proto')]
});

const healthPackageDefinition = protoLoader.loadSync(HEALTH_PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: Number, // Changed to Number to get numeric enum values
    defaults: true,
    oneofs: true,
    includeDirs: [path.join(__dirname, '../proto')]
});

// Create proto descriptors
const proto = grpc.loadPackageDefinition(packageDefinition) as any;
const healthProto = grpc.loadPackageDefinition(healthPackageDefinition) as any;

// Type definitions
interface ServiceRegistrationResponse {
    module_name: string;
    version: string;
    json_config_schema: string;
    module_type?: string;
    description?: string;
}

// Create gRPC client factory
function createClient(address: string) {
    const PipeStepProcessor = proto.io.pipeline.search.model.PipeStepProcessor;
    return new PipeStepProcessor(address, grpc.credentials.createInsecure());
}

// Create health check client
function createHealthClient(address: string) {
    const Health = healthProto.grpc.health.v1.Health;
    return new Health(address, grpc.credentials.createInsecure());
}

// Schema transformation
async function transformSchemaForUI(rawSchema: string): Promise<any> {
    try {
        const parsed = JSON.parse(rawSchema);
        
        // Extract the actual schema - it might be nested in an OpenAPI structure
        let schema = parsed;
        let configKey: string | undefined;
        
        if (parsed.components && parsed.components.schemas) {
            const schemas = parsed.components.schemas;
            
            // Generic approach: Find the main config schema
            // Look for schemas that:
            // 1. End with "Config"
            // 2. Have properties (not just a reference or simple type)
            // 3. Are not infrastructure-related configs
            const configCandidates = Object.keys(schemas).filter(key => 
                key.endsWith('Config') && 
                schemas[key].properties &&
                !key.match(/Transport|Grpc|Kafka|Pipeline|Module|Whitelist|Override/i)
            );
            
            // If we have multiple candidates, pick the one with the most properties
            // This usually indicates the main configuration object
            if (configCandidates.length > 0) {
                configKey = configCandidates.reduce((best, current) => {
                    const bestCount = Object.keys(schemas[best]?.properties || {}).length;
                    const currentCount = Object.keys(schemas[current]?.properties || {}).length;
                    return currentCount > bestCount ? current : best;
                });
                
                schema = schemas[configKey];
                console.log(`Found config schema: ${configKey}`);
            }
        }
        
        // Resolve all $ref references
        const resolved = await RefParser.dereference(parsed);
        
        // Extract the resolved schema if we found a config key
        if (configKey && (resolved as any).components && (resolved as any).components.schemas) {
            schema = (resolved as any).components.schemas[configKey];
        }
        
        return enhanceSchema(schema);
    } catch (error) {
        console.error('Error transforming schema:', error);
        throw error;
    }
}

function enhanceSchema(schema: any): any {
    // Add UI hints based on field types
    if (schema.properties) {
        Object.entries(schema.properties).forEach(([key, prop]: [string, any]) => {
            if (prop.type === 'boolean') {
                prop['x-ui-widget'] = 'switch';
            } else if (prop.enum) {
                prop['x-ui-widget'] = 'select';
            }
            
            // Recursively enhance nested objects
            if (prop.type === 'object' && prop.properties) {
                enhanceSchema(prop);
            }
            
            // Handle arrays - preserve x-suggestions and other custom properties
            if (prop.type === 'array' && prop.items) {
                // Preserve x-suggestions if found
                if (prop.items['x-suggestions']) {
                    // x-suggestions preserved for autocomplete functionality
                }
                
                // Recursively enhance array items if they're objects
                if (prop.items.type === 'object' && prop.items.properties) {
                    enhanceSchema(prop.items);
                }
            }
        });
    }
    return schema;
}

// Routes
app.post('/api/module-schema', async (req, res) => {
    const { address } = req.body;

    if (!address) {
        return res.status(400).json({ error: 'Missing module address' });
    }

    try {
        const client = createClient(address);
        
        // Call getServiceRegistration
        client.getServiceRegistration({}, async (error: any, response: ServiceRegistrationResponse) => {
            if (error) {
                console.error('gRPC error:', error);
                return res.status(500).json({ 
                    error: 'Failed to connect to module',
                    details: error.message 
                });
            }

            try {
                let transformedSchema = null;
                
                // Only transform schema if it exists
                if (response?.json_config_schema) {
                    transformedSchema = await transformSchemaForUI(response.json_config_schema);
                }
                
                res.json({
                    module_name: response.module_name,
                    version: response.version,
                    description: response.description,
                    module_type: response.module_type,
                    schema: transformedSchema,
                    raw_schema: response.json_config_schema
                });
            } catch (transformError) {
                res.status(500).json({ 
                    error: 'Failed to transform schema',
                    details: (transformError as Error).message 
                });
            }
        });
    } catch (error) {
        console.error('Error creating client:', error);
        res.status(500).json({ 
            error: 'Failed to create gRPC client',
            details: (error as Error).message 
        });
    }
});

// Create seed data with file upload
app.post('/api/seed/create', upload.single('file'), async (req, res) => {
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
                original_filename: req.file.originalname,
                upload_timestamp: new Date().toISOString()
            }
        };
        
        // Create ModuleProcessRequest
        const request = {
            document: pipeDoc,
            metadata: {
                stream_id: streamId || `stream-${Date.now()}`,
                pipe_step_name: 'seed-creation',
                processing_timestamp: new Date().toISOString()
            },
            config: {
                custom_json_config: parsedConfig || {}
            }
        };
        
        res.json({ request });
    } catch (error) {
        console.error('Error creating seed data:', error);
        res.status(500).json({ 
            error: 'Failed to create seed data',
            details: (error as Error).message 
        });
    }
});

// Execute a request against a module
app.post('/api/module-execute', async (req, res) => {
    const { address, request } = req.body;

    if (!address || !request) {
        return res.status(400).json({ error: 'Missing address or request' });
    }

    try {
        const client = createClient(address);
        
        // Call processData with the request
        client.processData(request, (error: any, response: any) => {
            if (error) {
                console.error('gRPC execution error:', error);
                return res.status(500).json({ 
                    error: 'Failed to execute request',
                    details: error.message 
                });
            }

            // Return the response
            res.json(response);
        });
    } catch (error) {
        console.error('Error executing request:', error);
        res.status(500).json({ 
            error: 'Failed to execute request',
            details: (error as Error).message 
        });
    }
});

// Health check for a module
app.post('/api/module-health', async (req, res) => {
    const { address } = req.body;

    if (!address) {
        return res.status(400).json({ error: 'Missing module address' });
    }

    try {
        const healthClient = createHealthClient(address);
        
        // Call Check with empty service name to check overall health
        healthClient.Check({ service: '' }, (error: any, response: any) => {
            if (error) {
                console.error('Health check error:', error);
                return res.json({ 
                    status: 'NOT_SERVING',
                    error: error.message 
                });
            }

            console.log('Health check response:', response);
            
            // Convert numeric status to string
            let statusString = 'UNKNOWN';
            if (response.status === 'SERVING' || response.status === 1) {
                statusString = 'SERVING';
            } else if (response.status === 'NOT_SERVING' || response.status === 2) {
                statusString = 'NOT_SERVING';
            } else if (response.status === 'SERVICE_UNKNOWN' || response.status === 3) {
                statusString = 'SERVICE_UNKNOWN';
            }
            
            res.json({
                status: statusString,
                serving: statusString === 'SERVING'
            });
        });
    } catch (error) {
        console.error('Error creating health client:', error);
        res.json({ 
            status: 'NOT_SERVING',
            error: (error as Error).message 
        });
    }
});

// Health check for repository service
app.get('/api/repository-health', async (req, res) => {
    try {
        const healthClient = createHealthClient('localhost:38002');
        
        // Call Check with empty service name to check overall health
        healthClient.Check({ service: '' }, (error: any, response: any) => {
            if (error) {
                console.error('Repository health check error:', error);
                return res.json({ 
                    status: 'NOT_SERVING',
                    healthy: false,
                    error: error.message 
                });
            }
            
            // Convert numeric status to string
            let statusString = 'UNKNOWN';
            if (response.status === 'SERVING' || response.status === 1) {
                statusString = 'SERVING';
            } else if (response.status === 'NOT_SERVING' || response.status === 2) {
                statusString = 'NOT_SERVING';
            } else if (response.status === 'SERVICE_UNKNOWN' || response.status === 3) {
                statusString = 'SERVICE_UNKNOWN';
            }
            
            res.json({
                status: statusString,
                healthy: statusString === 'SERVING'
            });
        });
    } catch (error) {
        console.error('Error creating repository health client:', error);
        res.json({ 
            status: 'NOT_SERVING',
            healthy: false,
            error: (error as Error).message 
        });
    }
});

app.get('/health', (req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

// Database status endpoint
app.get('/api/database/status', async (req, res) => {
    const dockerAvailable = await dockerService.isDockerAvailable();
    const containerStatus = dockerAvailable ? await dockerService.getContainerStatus() : null;
    
    // Check collections if MongoDB is connected
    let collections = null;
    if (database.isMongoAvailable()) {
        try {
            const db = database.getDb();
            if (db) {
                const collectionList = await db.listCollections().toArray();
                collections = collectionList.map(col => col.name);
            }
        } catch (error) {
            console.error('Failed to list collections:', error);
        }
    }
    
    res.json({
        connected: database.isMongoAvailable(),
        config: database.getConfig(),
        storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage',
        docker: {
            available: dockerAvailable,
            container: containerStatus
        },
        collections
    });
});

// Docker MongoDB management endpoints
app.post('/api/database/docker/start', async (req, res) => {
    try {
        const config = req.body.config;
        const success = await dockerService.startMongoContainer(config);
        
        if (success) {
            // Update config for Docker mode
            database.updateConfig({ mode: 'docker', enabled: true });
            await database.disconnect();
            const connected = await database.connect();
            
            if (connected) {
                // Force storage service to reinitialize with MongoDB
                await storageService.reinitialize();
                
                // Initialize collections
                try {
                    const collections = await database.initializeCollections();
                    res.json({ 
                        success: true, 
                        message: 'MongoDB container started and connected',
                        storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage',
                        collections
                    });
                } catch (error) {
                    res.json({ 
                        success: true, 
                        message: 'MongoDB connected but failed to create collections',
                        storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage',
                        error: (error as Error).message
                    });
                }
            } else {
                res.json({ 
                    success: false, 
                    message: 'MongoDB container started but connection failed' 
                });
            }
        } else {
            res.status(500).json({ 
                success: false, 
                message: 'Failed to start MongoDB container' 
            });
        }
    } catch (error) {
        console.error('Error starting MongoDB container:', error);
        res.status(500).json({ 
            error: 'Failed to start MongoDB container',
            details: (error as Error).message 
        });
    }
});

app.post('/api/database/docker/stop', async (req, res) => {
    try {
        const success = await dockerService.stopMongoContainer();
        
        if (success) {
            // Update config to disable MongoDB and reinitialize with localStorage
            database.updateConfig({ mode: 'disabled', enabled: false });
            await database.disconnect();
            await storageService.reinitialize();
            
            res.json({ 
                success: true, 
                message: 'MongoDB container stopped',
                storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage'
            });
        } else {
            res.status(500).json({ 
                success: false, 
                message: 'Failed to stop MongoDB container' 
            });
        }
    } catch (error) {
        console.error('Error stopping MongoDB container:', error);
        res.status(500).json({ 
            error: 'Failed to stop MongoDB container',
            details: (error as Error).message 
        });
    }
});

app.post('/api/database/docker/remove', async (req, res) => {
    try {
        const success = await dockerService.removeMongoContainer();
        res.json({ success, message: success ? 'Container removed' : 'Failed to remove container' });
    } catch (error) {
        console.error('Error removing MongoDB container:', error);
        res.status(500).json({ 
            error: 'Failed to remove MongoDB container',
            details: (error as Error).message 
        });
    }
});

// Initialize MongoDB collections
app.post('/api/database/init-collections', async (req, res) => {
    try {
        if (!database.isMongoAvailable()) {
            return res.status(400).json({ 
                error: 'MongoDB not connected',
                message: 'Please connect to MongoDB first'
            });
        }
        
        const collections = await database.initializeCollections();
        res.json({ 
            success: true,
            message: 'Collections initialized',
            ...collections
        });
    } catch (error) {
        console.error('Error initializing collections:', error);
        res.status(500).json({ 
            error: 'Failed to initialize collections',
            details: (error as Error).message 
        });
    }
});

// Manual MongoDB connection endpoint
app.post('/api/database/connect', async (req, res) => {
    try {
        const { connectionString } = req.body;
        
        if (!connectionString) {
            return res.status(400).json({ error: 'Connection string required' });
        }
        
        // Update config and reconnect
        database.updateConfig({ 
            mode: 'manual', 
            enabled: true,
            connectionString: connectionString 
        });
        
        await database.disconnect();
        const connected = await database.connect();
        
        if (connected) {
            // Force storage service to reinitialize with MongoDB
            await storageService.reinitialize();
            
            // Initialize collections
            try {
                const collections = await database.initializeCollections();
                res.json({ 
                    success: true, 
                    message: 'Connected to MongoDB',
                    storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage',
                    collections
                });
            } catch (error) {
                res.json({ 
                    success: true, 
                    message: 'MongoDB connected but failed to create collections',
                    storageType: storageService.isUsingMongoDB() ? 'mongodb' : 'localStorage',
                    error: (error as Error).message
                });
            }
        } else {
            res.json({ 
                success: false, 
                message: 'Failed to connect to MongoDB' 
            });
        }
    } catch (error) {
        console.error('Error connecting to MongoDB:', error);
        res.status(500).json({ 
            error: 'Failed to connect to MongoDB',
            details: (error as Error).message 
        });
    }
});

// Test Documents API
app.post('/api/test-documents', async (req, res) => {
    try {
        const document = req.body;
        const id = await storageService.getAdapter().saveTestDocument(document);
        res.json({ id, success: true });
    } catch (error) {
        console.error('Error saving test document:', error);
        res.status(500).json({ 
            error: 'Failed to save test document',
            details: (error as Error).message 
        });
    }
});

app.get('/api/test-documents', async (req, res) => {
    try {
        const filter = req.query;
        const documents = await storageService.getAdapter().listTestDocuments(filter);
        res.json({ documents, count: documents.length });
    } catch (error) {
        console.error('Error listing test documents:', error);
        res.status(500).json({ 
            error: 'Failed to list test documents',
            details: (error as Error).message 
        });
    }
});

app.get('/api/test-documents/:id', async (req, res) => {
    try {
        const document = await storageService.getAdapter().getTestDocument(req.params.id);
        if (!document) {
            return res.status(404).json({ error: 'Document not found' });
        }
        res.json(document);
    } catch (error) {
        console.error('Error getting test document:', error);
        res.status(500).json({ 
            error: 'Failed to get test document',
            details: (error as Error).message 
        });
    }
});

app.put('/api/test-documents/:id', async (req, res) => {
    try {
        const success = await storageService.getAdapter().updateTestDocument(
            req.params.id,
            req.body
        );
        res.json({ success });
    } catch (error) {
        console.error('Error updating test document:', error);
        res.status(500).json({ 
            error: 'Failed to update test document',
            details: (error as Error).message 
        });
    }
});

app.delete('/api/test-documents/:id', async (req, res) => {
    try {
        const success = await storageService.getAdapter().deleteTestDocument(req.params.id);
        res.json({ success });
    } catch (error) {
        console.error('Error deleting test document:', error);
        res.status(500).json({ 
            error: 'Failed to delete test document',
            details: (error as Error).message 
        });
    }
});

// Initialize database and start server
async function startServer() {
    try {
        // Try to connect to MongoDB
        await database.connect();
        
        // Initialize storage service
        await storageService.initialize();
        
        app.listen(PORT, () => {
            console.log(`Developer tool backend listening at http://localhost:${PORT}`);
            console.log(`Storage: ${storageService.isUsingMongoDB() ? 'MongoDB' : 'Local file-based'}`);
        });
    } catch (error) {
        console.error('Failed to start server:', error);
        process.exit(1);
    }
}

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\nShutting down gracefully...');
    await database.disconnect();
    process.exit(0);
});

startServer();
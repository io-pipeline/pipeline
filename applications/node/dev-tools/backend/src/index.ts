import express from 'express';
import cors from 'cors';
import multer from 'multer';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';
import { storageService } from './services/storageService';
import * as fs from 'fs';
import seedRoutes from './routes/seedRoutes';
import repositoryRoutes from './routes/repositoryRoutes';

const app = express();
const PORT = process.env.PORT || 3000;

// Configure multer for file uploads
const storage = multer.memoryStorage();
const upload = multer({ storage });

// Load proto files
const protoPath = path.join(__dirname, '../proto/pipe_step_processor_service.proto');
const healthProtoPath = path.join(__dirname, '../proto/health/v1/health.proto');

const packageDefinition = protoLoader.loadSync(protoPath, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});

const healthPackageDefinition = protoLoader.loadSync(healthProtoPath, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true
});

const proto = grpc.loadPackageDefinition(packageDefinition) as any;
const healthProto = grpc.loadPackageDefinition(healthPackageDefinition) as any;

// Middleware
app.use(cors());
app.use(express.json({ limit: '50mb' }));

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
            
            // Find the main schema (usually ends with 'Config')
            configKey = Object.keys(schemas).find(key => key.endsWith('Config'));
            
            if (configKey) {
                schema = schemas[configKey];
            } else {
                // If no Config schema, look for any object schema
                configKey = Object.keys(schemas).find(key => 
                    schemas[key].type === 'object' && schemas[key].properties
                );
                if (configKey) {
                    schema = schemas[configKey];
                }
            }
        }
        
        // If it's an OpenAPI schema with a single component, extract it
        if (!schema.properties && parsed.components && parsed.components.schemas) {
            const schemaKeys = Object.keys(parsed.components.schemas);
            if (schemaKeys.length === 1) {
                schema = parsed.components.schemas[schemaKeys[0]];
            }
        }
        
        // Use json-schema-ref-parser to resolve references
        const $RefParser = require('@apidevtools/json-schema-ref-parser');
        const resolved = await $RefParser.dereference(parsed);
        
        // Re-extract schema after dereferencing
        if (configKey && resolved.components && resolved.components.schemas) {
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
        
        // Call GetServiceRegistration to get schema
        client.GetServiceRegistration({}, async (error: any, response: any) => {
            if (error) {
                console.error('gRPC error:', error);
                return res.status(500).json({ 
                    error: 'Failed to get module schema',
                    details: error.message 
                });
            }

            console.log('Service registration response:', response);

            if (!response.json_config_schema) {
                return res.status(400).json({ 
                    error: 'Module does not provide a configuration schema' 
                });
            }

            try {
                const transformedSchema = await transformSchemaForUI(response.json_config_schema);
                res.json({
                    moduleInfo: {
                        name: response.name,
                        description: response.description,
                        schema: transformedSchema
                    }
                });
            } catch (schemaError) {
                console.error('Schema transformation error:', schemaError);
                res.status(500).json({ 
                    error: 'Failed to transform schema',
                    details: (schemaError as Error).message 
                });
            }
        });
    } catch (error) {
        console.error('Error creating gRPC client:', error);
        res.status(500).json({ 
            error: 'Failed to connect to module',
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

// Storage status endpoint (simplified)
app.get('/api/storage/status', async (req, res) => {
    res.json({
        storageType: 'localStorage',
        healthy: true
    });
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

app.get('/api/test-documents/:id', async (req, res) => {
    try {
        const doc = await storageService.getAdapter().getTestDocument(req.params.id);
        if (!doc) {
            return res.status(404).json({ error: 'Document not found' });
        }
        res.json(doc);
    } catch (error) {
        console.error('Error retrieving test document:', error);
        res.status(500).json({ 
            error: 'Failed to retrieve test document',
            details: (error as Error).message 
        });
    }
});

app.get('/api/test-documents', async (req, res) => {
    try {
        const documents = await storageService.getAdapter().listTestDocuments();
        res.json(documents);
    } catch (error) {
        console.error('Error listing test documents:', error);
        res.status(500).json({ 
            error: 'Failed to list test documents',
            details: (error as Error).message 
        });
    }
});

// Mount routes
app.use('/api/seed', seedRoutes);
app.use('/api/repository', repositoryRoutes);

// Initialize and start server
async function startServer() {
    try {
        // Initialize storage service
        await storageService.initialize();
        
        app.listen(PORT, () => {
            console.log(`Developer tool backend listening at http://localhost:${PORT}`);
            console.log(`Storage: Local file-based`);
        });
    } catch (error) {
        console.error('Failed to start server:', error);
        process.exit(1);
    }
}

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\nShutting down gracefully...');
    process.exit(0);
});

startServer();
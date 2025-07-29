import express from 'express';
import cors from 'cors';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';
import RefParser from '@apidevtools/json-schema-ref-parser';
import multer from 'multer';

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

// Load proto file
const PROTO_PATH = path.join(__dirname, '../proto/pipe_step_processor_service.proto');

const packageDefinition = protoLoader.loadSync(PROTO_PATH, {
    keepCase: true,
    longs: String,
    enums: String,
    defaults: true,
    oneofs: true,
    includeDirs: [path.join(__dirname, '../proto')]
});

// Create proto descriptor
const proto = grpc.loadPackageDefinition(packageDefinition) as any;

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
        if (configKey && resolved.components && resolved.components.schemas) {
            schema = resolved.components.schemas[configKey];
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

app.get('/health', (req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
    console.log(`Developer tool backend listening at http://localhost:${PORT}`);
});
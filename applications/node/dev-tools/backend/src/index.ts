import express from 'express';
import cors from 'cors';
import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';
import RefParser from '@apidevtools/json-schema-ref-parser';

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

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
        const resolved = await RefParser.dereference(parsed);
        return enhanceSchema(resolved);
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

            if (!response?.json_config_schema) {
                return res.status(404).json({ 
                    error: 'Module does not provide a configuration schema' 
                });
            }

            try {
                const transformedSchema = await transformSchemaForUI(response.json_config_schema);
                
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

app.get('/health', (req, res) => {
    res.json({ status: 'healthy', timestamp: new Date().toISOString() });
});

app.listen(PORT, () => {
    console.log(`Developer tool backend listening at http://localhost:${PORT}`);
});
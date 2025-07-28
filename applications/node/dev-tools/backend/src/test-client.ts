import * as grpc from '@grpc/grpc-js';
import * as protoLoader from '@grpc/proto-loader';
import path from 'path';

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

const proto = grpc.loadPackageDefinition(packageDefinition) as any;

// Test direct gRPC connection
async function testGrpcConnection(address: string) {
    console.log(`Testing connection to ${address}...`);
    
    const PipeStepProcessor = proto.io.pipeline.search.model.PipeStepProcessor;
    const client = new PipeStepProcessor(address, grpc.credentials.createInsecure());
    
    return new Promise((resolve, reject) => {
        client.getServiceRegistration({}, (error: any, response: any) => {
            if (error) {
                console.error('Error:', error);
                reject(error);
            } else {
                console.log('Success! Response:', response);
                if (response.json_config_schema) {
                    try {
                        const schema = JSON.parse(response.json_config_schema);
                        console.log('Parsed schema:', JSON.stringify(schema, null, 2));
                    } catch (e) {
                        console.log('Raw schema:', response.json_config_schema);
                    }
                }
                resolve(response);
            }
        });
    });
}

// Run the test
const moduleAddress = process.argv[2] || 'localhost:39101'; // Default to parser port
testGrpcConnection(moduleAddress)
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
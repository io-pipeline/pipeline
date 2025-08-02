# TypeScript Stub Generation and Usage Guide

## Overview
This document explains how to generate and use TypeScript stubs from the protobuf definitions in the pipeline project. The TypeScript stubs are used by the frontend applications to communicate with the gRPC services.

## Prerequisites
- Protocol Buffer compiler (`protoc`) installed
- Node.js and npm installed
- `@bufbuild/protoc-gen-es` and `@connectrpc/protoc-gen-connect-es` plugins installed

## Stub Generation Process

### 1. Install Required Dependencies
First, ensure you have the necessary npm packages installed in your frontend project:

```bash
npm install @bufbuild/protobuf @connectrpc/connect @connectrpc/connect-web
npm install --save-dev @bufbuild/protoc-gen-es @connectrpc/protoc-gen-connect-es
```

### 2. Generate TypeScript Stubs
From the project root, run the following command to generate TypeScript stubs:

```bash
# Navigate to the grpc-stubs directory
cd grpc-stubs

# Generate TypeScript stubs
protoc \
  --plugin=protoc-gen-es=../frontend/node_modules/.bin/protoc-gen-es \
  --plugin=protoc-gen-connect-es=../frontend/node_modules/.bin/protoc-gen-connect-es \
  --es_out=../frontend/src/generated \
  --es_opt=target=ts \
  --connect-es_out=../frontend/src/generated \
  --connect-es_opt=target=ts \
  -I src/main/proto \
  src/main/proto/*.proto
```

This will generate TypeScript files in the `frontend/src/generated` directory.

## Using the Generated Stubs

### 1. Import the Generated Code
```typescript
import { FilesystemService } from '@/generated/filesystem_service_connect';
import { CreateNodeRequest, Node, NodeType } from '@/generated/filesystem_service_pb';
import { createPromiseClient } from '@connectrpc/connect';
import { createConnectTransport } from '@connectrpc/connect-web';
```

### 2. Create a Transport
```typescript
const transport = createConnectTransport({
  baseUrl: 'http://localhost:8081', // Your service URL
  // Use binary format for better performance
  useBinaryFormat: true,
});
```

### 3. Create a Client
```typescript
const client = createPromiseClient(FilesystemService, transport);
```

### 4. Make Requests with Drive Parameter
**IMPORTANT**: All filesystem operations now require a `drive` parameter. Each application or test should use its own drive for isolation.

```typescript
// Example: Create a folder
async function createFolder(drive: string, name: string, parentId?: string) {
  const request = new CreateNodeRequest({
    drive: drive, // Required!
    name: name,
    type: NodeType.FOLDER,
    parentId: parentId,
    metadata: {
      createdBy: 'frontend-app'
    }
  });

  try {
    const response = await client.createNode(request);
    console.log('Created node:', response);
    return response;
  } catch (error) {
    console.error('Error creating node:', error);
    throw error;
  }
}

// Example: Get children of a node
async function getChildren(drive: string, parentId?: string) {
  const request = new GetChildrenRequest({
    drive: drive, // Required!
    parentId: parentId // Optional - if not provided, returns root nodes
  });

  const response = await client.getChildren(request);
  return response.nodes;
}

// Example: Format a drive (delete all data)
async function formatDrive(drive: string) {
  const request = new FormatFilesystemRequest({
    drive: drive, // Required!
    confirmation: 'DELETE_FILESYSTEM_DATA',
    dryRun: false
  });

  const response = await client.formatFilesystem(request);
  console.log(`Deleted ${response.nodesDeleted} files and ${response.foldersDeleted} folders`);
}
```

## Key Differences from Backend Usage

1. **Transport**: Frontend uses HTTP 1.1 with binary format via Connect-Web, while backend uses real gRPC/HTTP2
2. **Client Creation**: Frontend uses `createPromiseClient` with Connect transport
3. **Error Handling**: Errors come through as Connect errors, not gRPC StatusRuntimeException

## Best Practices

1. **Drive Naming**: Use meaningful drive names like `app-documents`, `user-uploads`, `test-data`
2. **Error Handling**: Always wrap calls in try-catch blocks
3. **Type Safety**: Use the generated TypeScript types for compile-time safety
4. **Binary Format**: Use binary format for better performance with large payloads

## Common Pitfalls

1. **Missing Drive Parameter**: All operations require a drive. There is no default drive.
2. **Cross-Drive Operations**: You cannot move/copy nodes between drives directly
3. **Drive Names**: Cannot contain colons (:) as they're used as delimiters

## Testing in Frontend

When writing tests, use unique drive names to ensure isolation:

```typescript
describe('FilesystemService', () => {
  let drive: string;
  
  beforeEach(() => {
    // Create unique drive for each test
    drive = `test-${Date.now()}-${Math.random().toString(36).substring(7)}`;
  });
  
  afterEach(async () => {
    // Clean up test data
    await client.formatFilesystem(new FormatFilesystemRequest({
      drive: drive,
      confirmation: 'DELETE_FILESYSTEM_DATA',
      dryRun: false
    }));
  });
  
  it('should create and retrieve a node', async () => {
    const node = await createFolder(drive, 'test-folder');
    expect(node.name).toBe('test-folder');
  });
});
```

## Regenerating Stubs

When protobuf definitions change:

1. Update the .proto files in `grpc-stubs/src/main/proto/`
2. Run the generation command above
3. Fix any TypeScript compilation errors in your frontend code
4. Update tests if the API has changed

## Troubleshooting

- **Import Errors**: Ensure the generated files are in the correct location and tsconfig includes them
- **Connection Errors**: Check that the backend service is running and CORS is configured
- **Type Errors**: Regenerate stubs after any .proto file changes
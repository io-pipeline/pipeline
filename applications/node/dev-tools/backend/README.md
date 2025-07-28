# Pipeline Developer Tools Backend

A TypeScript backend that connects to Pipeline modules via gRPC and provides a REST API for the frontend.

## Quick Start

```bash
# Install dependencies
npm install

# Run the development server
npm run dev

# Or run without watch mode
npm start

# Test gRPC connection to a module
npm run test-client localhost:39101
```

## Architecture

This backend uses:
- **@grpc/proto-loader** - Dynamic proto loading (no code generation needed)
- **Express** - REST API server
- **TypeScript** - Type safety without complex build steps
- **tsx** - Simple TypeScript execution

## API Endpoints

### POST /api/module-schema
Fetches and transforms a module's configuration schema.

**Request:**
```json
{
  "address": "localhost:39101"
}
```

**Response:**
```json
{
  "module_name": "parser",
  "version": "1.0.0",
  "description": "Document parser module",
  "schema": { /* Enhanced schema for UI */ },
  "raw_schema": "{ /* Original OpenAPI schema */ }"
}
```

### GET /health
Health check endpoint.

## How It Works

1. The backend loads proto files dynamically using `@grpc/proto-loader`
2. When you request a module's schema, it creates a gRPC client
3. It calls the module's `getServiceRegistration` method
4. The returned OpenAPI schema is transformed for UI rendering:
   - `$ref` references are resolved
   - UI hints are added (widget types, display order)
   - JSONForms compatibility is ensured

## Testing

To test the gRPC connection directly:
```bash
npm run test-client localhost:39101  # Parser
npm run test-client localhost:39102  # Chunker
npm run test-client localhost:39103  # Embedder
```

## Notes

- No proto compilation needed - uses dynamic loading
- Works with existing proto files in the `proto/` directory
- Transforms schemas for optimal UI rendering
- Simple TypeScript setup with minimal configuration
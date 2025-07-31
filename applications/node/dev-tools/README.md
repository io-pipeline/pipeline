# Pipeline Developer Tools

A Vue.js/TypeScript development environment for designing, testing, and prototyping document processing pipelines using gRPC/Connect protocol.

## Prerequisites

### Required Software

1. **Node.js** (v22 or higher)
   - Download from [nodejs.org](https://nodejs.org/)
   - Verify installation: `node --version`
   - Note: Node.js 22 is required for native TypeScript support and other modern features

2. **pnpm** (Package manager)
   - Install globally: `npm install -g pnpm`
   - Or use corepack (comes with Node.js 16.13+):
     ```bash
     corepack enable
     corepack prepare pnpm@latest --activate
     ```
   - Verify installation: `pnpm --version`

### Optional Software

3. **Docker** (if running repository service)
   - Required only for enhanced data storage features
   - The app works fine with local storage by default

## Installation

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd pipeline/applications/node/dev-tools
   ```

2. Run the setup script (recommended):
   ```bash
   # Linux/Mac
   ./setup.sh
   
   # Windows PowerShell
   ./setup.ps1
   ```

   The setup script will:
   - ✅ Check Node.js version (requires v22+)
   - ✅ Install pnpm if not present
   - ✅ Install all dependencies including:
     - `@bufbuild/buf` - Protocol buffer compiler
     - `@bufbuild/protoc-gen-es` - TypeScript code generator
     - `@connectrpc/connect` - Connect protocol implementation
     - All Vue.js and development dependencies
   - ✅ Generate TypeScript types from protobuf files

### Manual Installation

If you prefer to install manually:

```bash
# Install pnpm (if not already installed)
npm install -g pnpm

# Install all dependencies (including buf tools)
pnpm install

# Generate protobuf types
cd backend
./node_modules/.bin/buf generate
cd ../frontend
./node_modules/.bin/buf generate ../backend/proto
cd ..
```

## Running the Application

### Development Mode

1. Start the backend server:
   ```bash
   cd backend
   pnpm dev
   ```
   The backend will start on http://localhost:3000

2. In a new terminal, start the frontend:
   ```bash
   cd frontend
   pnpm dev
   ```
   The frontend will start on http://localhost:5173

3. Open your browser to http://localhost:5173

### Production Build

1. Build both frontend and backend:
   ```bash
   pnpm build
   ```

2. Start the production server:
   ```bash
   cd backend
   pnpm start
   ```

## Architecture

### Frontend (Vue 3 + TypeScript)
- **Location**: `/frontend`
- **Tech Stack**: Vue 3, Vuetify 3, TypeScript, Vite
- **Protocol**: Connect (gRPC-Web)
- **Features**:
  - Module Registry - Discover and connect to processing modules
  - Module Configuration - JSON Forms-based configuration UI
  - Data Seeding - Create test documents
  - Process Document - Test module processing
  - Admin Panel - Repository configuration and storage management

### Backend (Express + Connect)
- **Location**: `/backend`
- **Tech Stack**: Express, TypeScript, @connectrpc/connect
- **Role**: Proxy between frontend and gRPC services
- **Endpoints**: All communication via Connect protocol at `/connect`

## Configuration

### Environment Variables

The application uses sensible defaults. No environment variables required for basic operation.

### Repository Service (Optional)

For enhanced data storage features:

1. Run the repository service (default: localhost:38002)
2. Configure via Admin Panel in the UI
3. The app will transform to show enhanced features when connected

## Default Services

The dev tools expect these services (but work without them):

- **Repository Service**: `localhost:38002` (optional, for enhanced storage)
- **Module Services**: Configured through the UI

## Development Workflow

1. **Local Storage Mode** (Default)
   - No external dependencies
   - Configurations saved in browser localStorage
   - Perfect for quick prototyping

2. **Repository Mode** (Enhanced)
   - Connect to repository service via Admin panel
   - Enables document filesystem browser
   - Persistent storage across sessions

## Troubleshooting

### pnpm not found
```bash
npm install -g pnpm
```

### Port already in use
- Backend default: 3000
- Frontend default: 5173
- Change in respective `package.json` if needed

### Protobuf generation errors
Make sure you're in the correct directory when running buf generate:
- Backend: Run from `/backend` directory
- Frontend: Run from `/frontend` directory

### CORS issues
The backend is configured to accept requests from localhost:5173. If running on different ports, update CORS settings in `/backend/src/index.ts`

## Project Structure

```
dev-tools/
├── frontend/               # Vue.js frontend application
│   ├── src/
│   │   ├── components/    # Vue components
│   │   ├── services/      # Connect service clients
│   │   ├── stores/        # Pinia stores
│   │   └── gen/          # Generated protobuf types
│   └── package.json
├── backend/               # Express backend server
│   ├── src/
│   │   ├── routes/       # Connect route handlers
│   │   └── gen/         # Generated protobuf types
│   ├── proto/           # Protobuf definitions
│   └── package.json
└── package.json          # Workspace root
```

## What's Included

All necessary tools are installed automatically via npm/pnpm:

### Protocol Buffer Tools
- **@bufbuild/buf** - Modern protobuf toolchain
- **@bufbuild/protoc-gen-es** - Generates TypeScript from .proto files
- **@bufbuild/protobuf** - Runtime protobuf support

### Connect/gRPC Tools  
- **@connectrpc/connect** - Connect protocol implementation
- **@connectrpc/connect-web** - Browser transport for Connect
- **@connectrpc/connect-express** - Express.js integration
- **@connectrpc/connect-node** - Node.js gRPC transport

### No Additional Installation Required
- ❌ No need to install protoc separately
- ❌ No need to install buf CLI globally
- ❌ No need to install any system dependencies
- ✅ Everything runs through node_modules

## Key Features

- **Zero Configuration**: Works out of the box with sensible defaults
- **Protocol Buffers**: Type-safe communication using Connect protocol
- **Real-time Updates**: Streaming health checks for modules
- **Dual Storage Modes**: Local storage (default) or repository (enhanced)
- **JSON Forms Integration**: Dynamic UI generation from JSON schemas
- **Module Discovery**: Automatic detection of available processing modules
- **Self-Contained**: All tools included, no system dependencies

## License

[Your License Here]
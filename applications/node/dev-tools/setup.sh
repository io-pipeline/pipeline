#!/bin/bash

# Pipeline Dev Tools Setup Script

echo "Pipeline Developer Tools - Setup"
echo "================================"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js v18 or higher."
    echo "   Visit: https://nodejs.org/"
    exit 1
fi

NODE_VERSION=$(node -v | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 22 ]; then
    echo "❌ Node.js version is too old. Please upgrade to v22 or higher."
    echo "   Current version: $(node -v)"
    echo "   Node.js 22 is required for native TypeScript support and modern features."
    exit 1
fi

echo "✅ Node.js $(node -v) detected"

# Check/Install pnpm
if ! command -v pnpm &> /dev/null; then
    echo "📦 Installing pnpm..."
    npm install -g pnpm
    if [ $? -ne 0 ]; then
        echo "❌ Failed to install pnpm. Trying corepack..."
        corepack enable
        corepack prepare pnpm@latest --activate
    fi
fi

if ! command -v pnpm &> /dev/null; then
    echo "❌ Failed to install pnpm. Please install manually:"
    echo "   npm install -g pnpm"
    exit 1
fi

echo "✅ pnpm $(pnpm -v) detected"

# Install dependencies
echo ""
echo "📦 Installing dependencies..."
pnpm install

if [ $? -ne 0 ]; then
    echo "❌ Failed to install dependencies"
    exit 1
fi

# Generate protobuf types
echo ""
echo "🔧 Generating protobuf types..."

# Backend protos
echo "  - Generating backend types..."
cd backend
./node_modules/.bin/buf generate
if [ $? -ne 0 ]; then
    echo "❌ Failed to generate backend protobuf types"
    exit 1
fi

# Frontend protos
echo "  - Generating frontend types..."
cd ../frontend
./node_modules/.bin/buf generate ../backend/proto
if [ $? -ne 0 ]; then
    echo "❌ Failed to generate frontend protobuf types"
    exit 1
fi

cd ..

echo ""
echo "✅ Setup complete!"
echo ""
echo "To start the development servers:"
echo "  1. Backend:  cd backend && pnpm dev"
echo "  2. Frontend: cd frontend && pnpm dev (in a new terminal)"
echo "  3. Open http://localhost:5173 in your browser"
echo ""
echo "Happy coding! 🚀"
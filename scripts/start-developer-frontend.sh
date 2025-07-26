#!/bin/bash

# Start Developer Frontend Application
# Runs on port 39200 (HTTP) and 5175 (Quinoa dev server)

echo "🚀 Starting Developer Frontend application..."
echo "📡 HTTP Port: 39200"
echo "🎨 Frontend Port: 5175 (Vite dev server)"
echo "🔗 Will extract proto files and generate gRPC stubs"
echo "=" * 50

# Check if consul is running (optional for developer frontend)
if curl -s http://localhost:8500/v1/status/leader > /dev/null; then
    echo "✅ Consul detected on localhost:8500"
else
    echo "⚠️  Consul not running (optional for developer frontend)"
    echo "   To start consul: docker run -d --name consul -p 8500:8500 hashicorp/consul:1.21 agent -dev -ui -client=0.0.0.0"
fi

# Clean up any hanging Vite processes on our port to prevent contamination
echo "🧹 Cleaning up hanging processes on port 5175..."
lsof -ti:5175 2>/dev/null | xargs -r kill -9 2>/dev/null || true

# Check if our port is truly available
if ss -tulpn | grep -q :5175; then
    echo "❌ Port 5175 is still occupied. Please check running processes:"
    ss -tulpn | grep :5175
    echo "   Kill with: lsof -ti:5175 | xargs kill -9"
    exit 1
else
    echo "✅ Port 5175 is available"
fi

# Change to project root if we're in scripts directory
if [[ $(basename "$PWD") == "scripts" ]]; then
    cd ..
fi

echo "📂 Working directory: $PWD"

# Check if proto files need extraction
if [ ! -d "applications/developer-frontend/src/main/webui/proto" ] || [ ! -f "applications/developer-frontend/src/main/webui/proto/tika_parser.proto" ]; then
    echo "🔧 Extracting proto files..."
    ./gradlew :applications:developer-frontend:extractProtos
fi

# Check if gRPC stubs need generation
if [ ! -d "applications/developer-frontend/src/main/webui/generated" ] || [ ! -f "applications/developer-frontend/src/main/webui/generated/tika_parser_grpc_pb.js" ]; then
    echo "🔧 Generating gRPC stubs..."
    cd applications/developer-frontend/src/main/webui
    npm run generate-grpc
    cd ../../..
fi

echo "🏗️  Building and starting developer frontend..."
echo ""
echo "📋 Available endpoints:"
echo "   🌐 Frontend: http://localhost:5175/quinoa"
echo "   🔧 Backend: http://localhost:39200"
echo "   📊 Health: http://localhost:39200/q/health"
echo "   📖 Dev UI: http://localhost:39200/q/dev"
echo ""
echo "🎯 Ready to connect to modules via gRPC for testing!"
echo "   Example: Tika parser on localhost:39104"
echo ""

# Start the developer frontend in dev mode
./gradlew :applications:developer-frontend:quarkusDev
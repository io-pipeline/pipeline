#!/bin/bash

# Start PipeStream Engine in development mode
# This script provides a convenient way to start the engine for testing

echo "🚀 Starting PipeStream Engine in development mode..."
echo "📍 Working directory: $(pwd)"
echo ""

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    echo "   Expected to find settings.gradle in current directory"
    exit 1
fi

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "❌ Error: Gradle wrapper not found"
    exit 1
fi

echo "🔧 Starting PipeStream Engine on port 38100..."
echo "📚 Available endpoints after startup:"
echo "   • gRPC ConnectorEngine: localhost:38100"
echo "   • gRPC PipeStreamEngine: localhost:38100"
echo "   • REST Test API: http://localhost:38100/api/connector/test/"
echo "   • Swagger UI: http://localhost:38100/swagger-ui/"
echo "   • Health Check: http://localhost:38100/health"
echo ""
echo "🧪 Quick test commands:"
echo "   curl \"http://localhost:38100/api/connector/test/simple?connectorType=filesystem-crawler&text=Hello%20Pipeline\""
echo "   curl \"http://localhost:38100/health\""
echo ""
echo "⏹️  Press Ctrl+C to stop the engine"
echo ""
echo "Starting now..."
echo "=================================================================================="

# Start the engine in dev mode
./gradlew :applications:pipestream-engine:quarkusDev
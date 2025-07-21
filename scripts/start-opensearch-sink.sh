#!/bin/bash

# Start OpenSearch Sink module in development mode
# This script starts the module and provides log tailing options

PROJECT_ROOT="/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try_registration_service"
LOG_FILE="$PROJECT_ROOT/opensearch-sink-dev.log"
PID_FILE="$PROJECT_ROOT/opensearch-sink-dev.pid"

cd "$PROJECT_ROOT"

echo "🚀 Starting OpenSearch Sink module in dev mode..."
echo "📁 Project root: $PROJECT_ROOT"
echo "📄 Log file: $LOG_FILE"
echo "🔐 OpenSearch auth: using environment variables OPENSEARCH_USERNAME/OPENSEARCH_PASSWORD"
echo "   Note: Password must contain symbol and capital letter (e.g. P@ssw0rd123)"

# Kill existing process if running
if [ -f "$PID_FILE" ]; then
    OLD_PID=$(cat "$PID_FILE")
    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "🔄 Stopping existing OpenSearch Sink process (PID: $OLD_PID)..."
        kill "$OLD_PID"
        sleep 2
    fi
    rm -f "$PID_FILE"
fi

# Start the module in foreground
echo "▶️  Starting OpenSearch Sink module in dev container..."
echo "📊 Module will be available at: http://localhost:39104"
echo "🏥 Health check: http://localhost:39104/health" 
echo "📖 OpenAPI docs: http://localhost:39104/swagger-ui"
echo ""
echo "🛑 To stop: Press Ctrl+C"
echo ""

# Run in normal dev mode
echo "▶️  Starting OpenSearch Sink in normal dev mode..."
./gradlew :modules:opensearch-sink:quarkusDev

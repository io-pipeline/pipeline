#!/bin/bash

# Start all Pipeline Engine application services in correct order
# Run this after start-infrastructure.sh completes successfully

set -e

echo "🚀 Starting Pipeline Engine Services..."

# Check prerequisites
if ! curl -s http://localhost:8500/v1/status/leader > /dev/null; then
    echo "❌ Consul is not running. Run './scripts/start-infrastructure.sh' first."
    exit 1
fi

if ! curl -s -u admin:admin http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo "❌ OpenSearch is not running. Run './scripts/start-infrastructure.sh' first."
    exit 1
fi

echo "✅ Prerequisites check passed"
echo ""

# Set OpenSearch credentials
export OPENSEARCH_USERNAME=admin
export OPENSEARCH_PASSWORD=admin

# Function to start service and wait for readiness
start_service() {
    local service_name=$1
    local gradle_path=$2
    local port=$3
    local health_endpoint=$4
    
    echo "🟦 Starting $service_name..."
    
    # Check if port is already in use
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null ; then
        echo "⚠️  Port $port is already in use. Skipping $service_name"
        return 0
    fi
    
    # Start service in background
    nohup ./gradlew $gradle_path > "logs/$service_name.log" 2>&1 &
    local pid=$!
    echo $pid > "logs/$service_name.pid"
    
    echo "⏳ Waiting for $service_name to be ready on port $port..."
    for i in {1..60}; do
        if curl -s $health_endpoint > /dev/null 2>&1; then
            echo "✅ $service_name is ready (PID: $pid)"
            return 0
        fi
        sleep 3
        # Check if process is still running
        if ! kill -0 $pid 2>/dev/null; then
            echo "❌ $service_name process died during startup"
            cat "logs/$service_name.log" | tail -20
            exit 1
        fi
        if [ $i -eq 60 ]; then
            echo "❌ $service_name failed to start within 180 seconds"
            cat "logs/$service_name.log" | tail -20
            exit 1
        fi
    done
}

# Create logs directory
mkdir -p logs

echo "📋 Starting services in dependency order..."
echo ""

# 1. Registration Service (needed first for module registration)
start_service "registration-service" ":applications:registration-service:quarkusDev" 38001 "http://localhost:38001/q/health"

# 2. Processing Modules (can start in parallel but we'll do sequential for reliability)
start_service "parser" ":modules:parser:quarkusDev" 39101 "http://localhost:39101/q/health"
start_service "chunker" ":modules:chunker:quarkusDev" 39102 "http://localhost:39102/q/health" 
start_service "embedder" ":modules:embedder:quarkusDev" 39103 "http://localhost:39103/q/health"
start_service "opensearch-sink" ":modules:opensearch-sink:quarkusDev" 39104 "http://localhost:39104/q/health"

# 3. PipeStream Engine (orchestrator - needs modules to be registered)
start_service "pipestream-engine" ":applications:pipestream-engine:quarkusDev" 38100 "http://localhost:38100/q/health"

echo ""
echo "⏳ Waiting 10 seconds for service registration to complete..."
sleep 10

echo ""
echo "🔍 Verifying service registration in Consul..."
registered_services=$(curl -s http://localhost:8500/v1/catalog/services | jq -r 'keys[]' | grep -v consul)
echo "📊 Registered services:"
echo "$registered_services" | sed 's/^/   /'

echo ""
echo "🧪 Creating test pipeline configuration..."
response=$(curl -s -X POST http://localhost:38100/api/test/routing/create-test-pipeline)
echo "Pipeline creation result: $response"

echo ""
echo "🎉 All services started successfully!"
echo ""
echo "📋 Service Status:"
echo "   Registration Service:  http://localhost:38001 (Swagger: /swagger-ui)"
echo "   PipeStream Engine:     http://localhost:38100 (Swagger: /swagger-ui)" 
echo "   Parser Module:         http://localhost:39101"
echo "   Chunker Module:        http://localhost:39102"
echo "   Embedder Module:       http://localhost:39103"
echo "   OpenSearch Sink:       http://localhost:39104"
echo ""
echo "🔍 Infrastructure:"
echo "   Consul UI:             http://localhost:8500/ui"
echo "   OpenSearch:            http://localhost:9200 (admin/admin)"
echo ""
echo "📝 Logs are available in the 'logs/' directory"
echo "🛑 To stop all services: './scripts/stop-services.sh'"
echo ""
echo "▶️  Ready for testing! Try the verification script:"
echo "   ./scripts/test-pipeline.sh"
#!/bin/bash

# Stop all Pipeline Engine services and infrastructure

set -e

echo "🛑 Stopping Pipeline Engine Services..."

# Function to stop service by PID file
stop_service() {
    local service_name=$1
    local pid_file="logs/$service_name.pid"
    
    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if kill -0 $pid 2>/dev/null; then
            echo "🔴 Stopping $service_name (PID: $pid)..."
            kill $pid
            sleep 2
            # Force kill if still running
            if kill -0 $pid 2>/dev/null; then
                kill -9 $pid 2>/dev/null || true
            fi
        fi
        rm -f "$pid_file"
    else
        echo "⚪ $service_name was not running"
    fi
}

# Stop application services
echo "📋 Stopping application services..."
stop_service "pipestream-engine"
stop_service "opensearch-sink" 
stop_service "embedder"
stop_service "chunker"
stop_service "parser"
stop_service "registration-service"

# Kill any remaining Gradle daemon processes
echo "🔄 Stopping Gradle daemons..."
./gradlew --stop 2>/dev/null || true

# Stop infrastructure
echo "🏗️ Stopping infrastructure services..."

echo "🔴 Stopping OpenSearch..."
cd opensearch
docker-compose down 2>/dev/null || true
cd ..

echo "🔴 Stopping Consul..."
docker stop consul 2>/dev/null || true
docker rm consul 2>/dev/null || true

# Clean up logs directory
if [ -d "logs" ]; then
    echo "🧹 Cleaning up log files..."
    rm -f logs/*.pid
fi

echo ""
echo "✅ All services stopped successfully!"
echo ""
echo "💡 To restart everything:"
echo "   ./scripts/start-infrastructure.sh"
echo "   ./scripts/start-services.sh"
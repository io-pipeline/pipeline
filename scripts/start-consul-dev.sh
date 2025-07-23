#!/bin/bash

# Start Consul locally in dev mode with INFO level logging
# This script starts Consul for local development and testing

set -e

CONSUL_VERSION="1.21"
CONSUL_PORT="${CONSUL_PORT:-8500}"
CONSUL_DATA_DIR="${CONSUL_DATA_DIR:-./consul-data}"

echo "🚀 Starting Consul ${CONSUL_VERSION} in dev mode..."
echo "📍 Web UI will be available at: http://localhost:${CONSUL_PORT}"
echo "📊 Log Level: INFO"
echo "📁 Data Directory: ${CONSUL_DATA_DIR}"

# Create data directory if it doesn't exist
mkdir -p "${CONSUL_DATA_DIR}"

# Check if Consul is already running
if curl -s "http://localhost:${CONSUL_PORT}/v1/status/leader" >/dev/null 2>&1; then
    echo "⚠️  Consul is already running on port ${CONSUL_PORT}"
    echo "   You can access the UI at: http://localhost:${CONSUL_PORT}"
    exit 0
fi

# Check if Docker is available
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed or not available"
    exit 1
fi

# Stop any existing consul containers
echo "🧹 Stopping any existing Consul containers..."
docker stop consul-dev 2>/dev/null || true
docker rm consul-dev 2>/dev/null || true

# Start Consul in dev mode
echo "🔄 Starting Consul container..."
docker run -d \
    --name consul-dev \
    -p ${CONSUL_PORT}:8500 \
    -v "${CONSUL_DATA_DIR}:/consul/data" \
    -e CONSUL_BIND_INTERFACE=eth0 \
    hashicorp/consul:${CONSUL_VERSION} \
    consul agent \
    -dev \
    -client=0.0.0.0 \
    -ui \
    -log-level=INFO \
    -data-dir=/consul/data

# Wait for Consul to be ready
echo "⏳ Waiting for Consul to be ready..."
for i in {1..30}; do
    if curl -s "http://localhost:${CONSUL_PORT}/v1/status/leader" >/dev/null 2>&1; then
        echo "✅ Consul is ready!"
        echo ""
        echo "🎉 Consul dev environment started successfully!"
        echo ""
        echo "📋 Connection Details:"
        echo "   Web UI: http://localhost:${CONSUL_PORT}"
        echo "   API:    http://localhost:${CONSUL_PORT}/v1/"
        echo "   Health: http://localhost:${CONSUL_PORT}/v1/status/leader"
        echo ""
        echo "🔧 Configuration:"
        echo "   Mode: Development (data will not persist across restarts)"
        echo "   Log Level: INFO"
        echo "   Data Directory: ${CONSUL_DATA_DIR}"
        echo ""
        echo "🛑 To stop Consul:"
        echo "   docker stop consul-dev"
        echo "   docker rm consul-dev"
        echo ""
        break
    fi
    
    if [ $i -eq 30 ]; then
        echo "❌ Consul failed to start within 30 seconds"
        echo "📋 Container logs:"
        docker logs consul-dev
        exit 1
    fi
    
    echo "   Attempt $i/30 - waiting..."
    sleep 1
done
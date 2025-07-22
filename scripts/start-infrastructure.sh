#!/bin/bash

# Start Infrastructure Services (Consul + OpenSearch)
# Run this script first before starting any application services

set -e

echo "🚀 Starting Pipeline Engine Infrastructure..."

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "📊 Starting Consul (Service Discovery)..."
# Stop existing consul if running
docker stop consul 2>/dev/null || true
docker rm consul 2>/dev/null || true

# Start Consul
docker run -d \
  --name consul \
  -p 8500:8500 \
  -p 8600:8600/udp \
  hashicorp/consul:1.21 \
  agent -dev -ui -client=0.0.0.0

echo "⏳ Waiting for Consul to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:8500/v1/status/leader > /dev/null; then
        echo "✅ Consul is ready"
        break
    fi
    sleep 2
    if [ $i -eq 30 ]; then
        echo "❌ Consul failed to start within 60 seconds"
        exit 1
    fi
done

echo "🔍 Starting OpenSearch (Vector Database)..."
cd opensearch

# Create environment file if it doesn't exist
if [ ! -f .env ]; then
    cat > .env << 'EOF'
OPENSEARCH_USERNAME=admin
OPENSEARCH_PASSWORD=admin
EOF
    echo "📝 Created OpenSearch .env file"
fi

# Start OpenSearch
docker-compose down 2>/dev/null || true
docker-compose up -d

echo "⏳ Waiting for OpenSearch to be ready..."
for i in {1..60}; do
    if curl -s -u admin:admin http://localhost:9200/_cluster/health > /dev/null 2>&1; then
        echo "✅ OpenSearch is ready"
        break
    fi
    sleep 3
    if [ $i -eq 60 ]; then
        echo "❌ OpenSearch failed to start within 180 seconds"
        exit 1
    fi
done

cd ..

echo ""
echo "🎉 Infrastructure startup complete!"
echo ""
echo "📋 Service URLs:"
echo "   Consul UI:    http://localhost:8500/ui"
echo "   OpenSearch:   http://localhost:9200 (admin/admin)"
echo ""
echo "▶️  Next step: Run './scripts/start-services.sh' to start application services"
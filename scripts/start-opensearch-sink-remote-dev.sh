#!/bin/bash

# Start OpenSearch Sink using Quarkus remote dev mode in container

PROJECT_ROOT="/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try_registration_service"

cd "$PROJECT_ROOT"

echo "🚀 Starting OpenSearch Sink with Quarkus Remote Dev Mode in Container..."
echo "📁 Project root: $PROJECT_ROOT"
echo "🔐 OpenSearch auth: using environment variables OPENSEARCH_USERNAME/OPENSEARCH_PASSWORD"
echo "   Note: Password must contain symbol and capital letter (e.g. P@ssw0rd123)"
echo ""

# Kill any existing containers
echo "🧹 Cleaning up existing containers..."
docker stop opensearch-sink-dev 2>/dev/null || true
docker rm opensearch-sink-dev 2>/dev/null || true

# Step 1: Build mutable jar for remote dev mode
echo "🔧 Building mutable jar for remote development..."
./gradlew :modules:opensearch-sink:build -Dquarkus.package.type=mutable-jar

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

# Step 2: Build container image
echo "🐳 Building container image..."
docker build -f modules/opensearch-sink/src/main/docker/Dockerfile.jvm \
    -t opensearch-sink-dev \
    modules/opensearch-sink/

if [ $? -ne 0 ]; then
    echo "❌ Container build failed"
    exit 1
fi

# Step 3: Run container with remote dev mode
echo "🚀 Starting container with remote dev mode..."
echo "📊 Module will be available at: http://localhost:39104"
echo "🏥 Health check: http://localhost:39104/health"
echo "📖 OpenAPI docs: http://localhost:39104/swagger-ui"
echo ""
echo "🛑 To stop: Press Ctrl+C"
echo ""

# Run with remote dev mode enabled
docker run -it --rm \
    --name opensearch-sink-dev \
    -p 39104:39104 \
    -p 5005:5005 \
    -e QUARKUS_LAUNCH_DEVMODE=true \
    -e OPENSEARCH_USERNAME="${OPENSEARCH_USERNAME}" \
    -e OPENSEARCH_PASSWORD="${OPENSEARCH_PASSWORD}" \
    -e QUARKUS_HTTP_HOST=0.0.0.0 \
    -e QUARKUS_HTTP_PORT=39104 \
    --network host \
    opensearch-sink-dev

echo ""
echo "✅ Container stopped"
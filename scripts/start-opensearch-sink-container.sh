#!/bin/bash

# Start OpenSearch Sink module using Quarkus dev mode with container integration

PROJECT_ROOT="/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try_registration_service"

cd "$PROJECT_ROOT"

echo "🚀 Starting OpenSearch Sink module with Quarkus dev container support..."
echo "📁 Project root: $PROJECT_ROOT"
echo "🔐 OpenSearch auth: using environment variables OPENSEARCH_USERNAME/OPENSEARCH_PASSWORD"
echo "   Note: Password must contain symbol and capital letter (e.g. P@ssw0rd123)"
echo ""
echo "📊 Module will be available at: http://localhost:39104"
echo "🏥 Health check: http://localhost:39104/health" 
echo "📖 OpenAPI docs: http://localhost:39104/swagger-ui"
echo ""
echo "🛑 To stop: Press Ctrl+C"
echo ""

# Method 1: Use Quarkus dev services (auto-starts containers for dependencies)
echo "🐳 Starting with Quarkus dev services..."
./gradlew :modules:opensearch-sink:quarkusDev \
    -Dquarkus.devservices.enabled=true \
    -Dquarkus.live-reload.instrumentation=true

# Alternative Method 2: If the above doesn't work, try direct container approach
# echo "🐳 Building and running in container..."
# ./gradlew :modules:opensearch-sink:build -Dquarkus.package.type=uber-jar
# docker build -t opensearch-sink -f modules/opensearch-sink/src/main/docker/Dockerfile.jvm .
# docker run -it --rm -p 39104:39104 \
#     -e OPENSEARCH_USERNAME="$OPENSEARCH_USERNAME" \
#     -e OPENSEARCH_PASSWORD="$OPENSEARCH_PASSWORD" \
#     --network host \
#     opensearch-sink
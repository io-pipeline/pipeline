#!/bin/bash

# Start OpenSearch Sink module using Quarkus with container dev services

PROJECT_ROOT="/home/krickert/IdeaProjects/rokkon/gemini-help/next-pipelines/first_try_registration_service"

cd "$PROJECT_ROOT"

echo "🚀 Starting OpenSearch Sink with Quarkus Dev Container approach..."
echo "📁 Project root: $PROJECT_ROOT"
echo ""

# Try your suggested approach with corrected flags
echo "🐳 Attempting container-based dev mode..."
./gradlew :modules:opensearch-sink:quarkusDev \
    -Dquarkus.http.host=0.0.0.0 \
    -Dquarkus.http.port=39104 \
    -Dquarkus.container-image.build=true \
    -Dquarkus.devservices.enabled=true
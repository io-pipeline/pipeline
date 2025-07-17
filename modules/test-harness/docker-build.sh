#!/bin/bash
set -e

# Handle dev/prod mode
MODE=${1:-prod}

# Get to project root
cd "$(dirname "$0")/../.."

echo "Building test-harness module..."

# Build the test-harness module (skip tests for now)
./gradlew :modules:test-harness:build -x test

cd modules/test-harness

if [ "$MODE" = "dev" ]; then
    echo "Building development image with tag: pipeline/test-harness:dev"
    docker build -f src/main/docker/Dockerfile.dev -t pipeline/test-harness:dev .
    echo "Dev image built successfully!"
    echo "Run with: docker run -i --rm --network=host -e MODULE_AUTO_REGISTER_ENABLED=true pipeline/test-harness:dev"
    echo "Note: Dev image uses host networking - connects to localhost services"
else
    echo "Building production image with tag: pipeline/test-harness:latest"
    docker build -f src/main/docker/Dockerfile.jvm -t pipeline/test-harness:latest .
    echo "Production image built successfully!"
    echo "Run with: docker run -i --rm -p 39100:39100 -e REGISTRATION_SERVICE_HOST=registration-service -e CONSUL_HOST=consul pipeline/test-harness:latest"
    echo "Note: Production image expects registration-service and consul services in the same network"
fi
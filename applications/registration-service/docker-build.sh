#!/bin/bash
set -e

# Build the registration service Docker image with proper layering
echo "Building registration service Docker image..."

# Change to project root
cd ../..

# Build JVM image by default
IMAGE_TYPE=${1:-jvm}
TAG=${2:-latest}

if [ "$IMAGE_TYPE" = "native" ]; then
    echo "Building native image..."
    docker build -f applications/registration-service/src/main/docker/Dockerfile.native \
        -t pipeline/registration-service:native-$TAG .
else
    echo "Building JVM image..."
    docker build -f applications/registration-service/src/main/docker/Dockerfile.jvm \
        -t pipeline/registration-service:$TAG .
fi

echo "Docker image built successfully!"
echo ""
echo "To run the image:"
echo "  docker run -p 39100:39100 -e CONSUL_HOST=consul pipeline/registration-service:$TAG"
echo ""
echo "To run with docker compose:"
echo "  cd docker && docker compose -f docker-compose.yml -f registration-service.yml up"
#!/bin/bash
set -e

echo "Running registration-service in dev mode with host networking..."

# Check if consul is accessible on localhost
if ! nc -z localhost 8500 2>/dev/null; then
    echo "WARNING: Consul not accessible on localhost:8500"
    echo "Make sure docker compose is running with proper port mapping."
    echo ""
    echo "You can run consul with host networking:"
    echo "  docker run -d --name consul-dev --network host consul:1.19.1 agent -dev -client=0.0.0.0"
    echo ""
    read -p "Continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Export environment variables for local dev mode
export CONSUL_HOST=localhost
export CONSUL_PORT=8500
export QUARKUS_HTTP_PORT=38001
export QUARKUS_GRPC_SERVER_PORT=38001

echo "Starting Quarkus in dev mode with host networking..."
echo "Consul URL: http://localhost:8500"
echo "Service URL: http://localhost:38001"
echo ""

# Run from project root
cd ../..
./gradlew :applications:registration-service:quarkusDev \
    -Dquarkus.live-reload.instrumentation=true
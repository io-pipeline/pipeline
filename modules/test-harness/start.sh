#!/bin/bash

# Start script for test-harness module
# This script starts the test-harness service with proper configuration

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Default configuration
PORT=${PORT:-38006}
REGISTRATION_SERVICE_HOST=${REGISTRATION_SERVICE_HOST:-localhost}
REGISTRATION_SERVICE_PORT=${REGISTRATION_SERVICE_PORT:-38001}
CONSUL_HOST=${CONSUL_HOST:-localhost}
CONSUL_PORT=${CONSUL_PORT:-8500}
MODE=${1:-dev}

echo "Starting test-harness module..."
echo "Mode: $MODE"
echo "Port: $PORT"
echo "Registration Service: $REGISTRATION_SERVICE_HOST:$REGISTRATION_SERVICE_PORT"
echo "Consul: $CONSUL_HOST:$CONSUL_PORT"

# Navigate to project root
cd "$PROJECT_ROOT"

# Set environment variables
export QUARKUS_HTTP_PORT=$PORT
export REGISTRATION_SERVICE_HOST=$REGISTRATION_SERVICE_HOST
export REGISTRATION_SERVICE_PORT=$REGISTRATION_SERVICE_PORT
export CONSUL_HOST=$CONSUL_HOST
export CONSUL_PORT=$CONSUL_PORT

# Additional configuration for registration
export MODULE_AUTO_REGISTER_ENABLED=true
export MODULE_VERSION=1.0.0
export MODULE_TYPE=test-harness
export MODULE_METADATA="category=testing,purpose=integration-testing"

if [ "$MODE" = "dev" ]; then
    echo "Starting in dev mode..."
    ./gradlew :modules:test-harness:quarkusDev \
        -Dquarkus.profile=dev \
        -Dquarkus.http.port=$PORT \
        -Dquarkus.grpc.clients.registration-service.host=$REGISTRATION_SERVICE_HOST \
        -Dquarkus.grpc.clients.registration-service.port=$REGISTRATION_SERVICE_PORT \
        -Dquarkus.stork.registration-service.service-discovery.consul-host=$CONSUL_HOST \
        -Dquarkus.stork.registration-service.service-discovery.consul-port=$CONSUL_PORT \
        -Dmodule.auto-register.enabled=true
else
    echo "Building and starting in production mode..."
    
    # Build the module
    ./gradlew :modules:test-harness:build -x test
    
    # Run the JAR directly
    java -jar modules/test-harness/build/quarkus-app/quarkus-run.jar \
        -Dquarkus.profile=prod \
        -Dquarkus.http.port=$PORT \
        -Dquarkus.grpc.clients.registration-service.host=$REGISTRATION_SERVICE_HOST \
        -Dquarkus.grpc.clients.registration-service.port=$REGISTRATION_SERVICE_PORT \
        -Dquarkus.stork.registration-service.service-discovery.consul-host=$CONSUL_HOST \
        -Dquarkus.stork.registration-service.service-discovery.consul-port=$CONSUL_PORT \
        -Dmodule.auto-register.enabled=true
fi
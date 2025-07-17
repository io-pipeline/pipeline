#!/bin/bash
set -e

echo "Running registration-service in dev mode locally with Docker network access..."

# Get the Docker network gateway IP for consul access
CONSUL_IP=$(docker network inspect pipeline-net | grep -A 5 "consul" | grep "IPv4Address" | cut -d'"' -f4 | cut -d'/' -f1)

if [ -z "$CONSUL_IP" ]; then
    echo "Consul container not found in pipeline-net. Make sure docker compose is running."
    echo "Run: cd docker && docker compose up -d"
    exit 1
fi

echo "Found Consul at: $CONSUL_IP"

# Export environment variables for local dev mode
export CONSUL_HOST=$CONSUL_IP
export CONSUL_PORT=8500
export QUARKUS_HTTP_PORT=38001
export QUARKUS_GRPC_SERVER_PORT=38001

echo "Starting Quarkus in dev mode..."
echo "Consul URL: http://$CONSUL_HOST:$CONSUL_PORT"
echo "Service URL: http://localhost:38001"
echo ""

# Run from project root
cd ../..
./gradlew :applications:registration-service:quarkusDev
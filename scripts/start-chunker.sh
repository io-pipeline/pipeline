#!/bin/bash

# Start Chunker Module
# Runs on port 39102 in dev mode (unified HTTP/gRPC)

echo "🚀 Starting Chunker module..."
echo "📡 Port: 39102 (dev mode)"
echo "🔗 Will register with Consul as 'chunker' service"
echo "=" * 50

# Check if consul is running
if ! curl -s http://localhost:8500/v1/status/leader > /dev/null; then
    echo "❌ Consul is not running on localhost:8500"
    echo "   Start consul first: docker run -d --name consul -p 8500:8500 hashicorp/consul:1.21 agent -dev -ui -client=0.0.0.0"
    exit 1
fi

# Change to project root if we're in scripts directory
if [[ $(basename "$PWD") == "scripts" ]]; then
    cd ..
fi

echo "📂 Working directory: $PWD"
echo "🏗️  Building and starting chunker module..."

# Start the chunker in dev mode
./gradlew :modules:chunker:quarkusDev
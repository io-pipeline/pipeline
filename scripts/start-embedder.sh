#!/bin/bash

# Start Embedder Module  
# Runs on port 39103 in dev mode (unified HTTP/gRPC)

echo "🚀 Starting Embedder module..."
echo "📡 Port: 39103 (dev mode)"
echo "🔗 Will register with Consul as 'embedder' service"
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
echo "🏗️  Building and starting embedder module..."

# Java proxy settings for DJL model downloads (uncomment and configure as needed)
# export JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyHost=your-proxy-host"
# export JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyPort=8080"
# export JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyUser=username"
# export JAVA_OPTS="$JAVA_OPTS -Dhttp.proxyPassword=password"
# export JAVA_OPTS="$JAVA_OPTS -Dhttps.proxyHost=your-proxy-host"
# export JAVA_OPTS="$JAVA_OPTS -Dhttps.proxyPort=8080"
# export JAVA_OPTS="$JAVA_OPTS -Dhttps.proxyUser=username"
# export JAVA_OPTS="$JAVA_OPTS -Dhttps.proxyPassword=password"
# export JAVA_OPTS="$JAVA_OPTS -Dhttp.nonProxyHosts=localhost|127.0.0.1|*.internal.com|*.local"

echo "🌐 Java proxy settings: ${JAVA_OPTS:-'None configured (direct connection)'}"

# Start the embedder in dev mode
./gradlew :modules:embedder:quarkusDev
#!/bin/bash

# Stop the local Consul dev environment

set -e

echo "🛑 Stopping Consul dev environment..."

# Stop and remove the consul container
if docker ps -q -f name=consul-dev | grep -q .; then
    echo "🔄 Stopping Consul container..."
    docker stop consul-dev
    echo "🧹 Removing Consul container..."
    docker rm consul-dev
    echo "✅ Consul dev environment stopped successfully!"
else
    echo "ℹ️  No running Consul dev container found"
fi

# Check if there are any orphaned consul processes
if docker ps -a -q -f name=consul-dev | grep -q .; then
    echo "🧹 Cleaning up stopped Consul containers..."
    docker rm consul-dev
fi

echo "🎉 Consul cleanup complete!"
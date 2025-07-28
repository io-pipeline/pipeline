#!/bin/bash

# Script to regenerate gRPC stubs when needed

echo "🔄 Regenerating gRPC stubs..."

# Clean the grpc-stubs project
echo "🧹 Cleaning grpc-stubs project..."
./gradlew :grpc-stubs:clean :grpc-stubs:build

# Publish to local maven repo
echo "📦 Publishing stubs to local repository..."
./gradlew :grpc-stubs:publishToMavenLocal

echo "✅ gRPC stubs regenerated and published successfully!"
echo "💡 Your tests should now run faster without regenerating stubs."

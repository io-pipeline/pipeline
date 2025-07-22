#!/bin/bash

# Quick start script - runs complete deployment in one command
# This script automates the entire deployment process

set -e

echo "🚀 Pipeline Engine Quick Start"
echo "=============================="
echo ""
echo "This will:"
echo "1. Start infrastructure (Consul + OpenSearch)"
echo "2. Build and start all services"  
echo "3. Run comprehensive tests"
echo ""
echo "⏱️  Estimated time: 5-10 minutes"
echo ""

read -p "Continue? (y/N): " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

echo ""
echo "📋 Phase 1: Building project..."
echo "==============================="
./gradlew clean build
if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please fix compilation errors."
    exit 1
fi
echo "✅ Build completed successfully"

echo ""
echo "📋 Phase 2: Starting infrastructure..."  
echo "======================================="
./scripts/start-infrastructure.sh
if [ $? -ne 0 ]; then
    echo "❌ Infrastructure startup failed."
    exit 1
fi

echo ""
echo "📋 Phase 3: Starting application services..."
echo "============================================="
./scripts/start-services.sh
if [ $? -ne 0 ]; then
    echo "❌ Service startup failed."
    echo "🔍 Check logs in the 'logs/' directory"
    exit 1
fi

echo ""
echo "📋 Phase 4: Running validation tests..."
echo "======================================="
./scripts/test-pipeline.sh
if [ $? -ne 0 ]; then
    echo "⚠️  Some tests failed, but basic functionality may still work"
    echo "🔍 Check the test output above for details"
fi

echo ""
echo "🎉 Quick Start Complete!"
echo "========================"
echo ""
echo "📊 Your Pipeline Engine is running at:"
echo "   PipeStream Engine:  http://localhost:38100/swagger-ui"
echo "   Consul UI:          http://localhost:8500/ui"  
echo "   OpenSearch:         http://localhost:9200 (admin/admin)"
echo ""
echo "💡 Useful commands:"
echo "   Check status:       ./scripts/test-pipeline.sh"
echo "   View logs:          ls -la logs/"
echo "   Stop everything:    ./scripts/stop-services.sh"
echo ""
echo "📖 Full documentation: see DEPLOYMENT.md"
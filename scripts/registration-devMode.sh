#!/bin/bash

# Registration Service Development Mode Script
# Starts the registration service in Quarkus dev mode with live reload

echo "🚀 Starting Registration Service in Dev Mode..."
echo "📍 Port: 38001 (dev mode)"
echo "🔧 Live coding enabled"
echo "🏥 Health: http://localhost:38001/q/health"
echo "📊 Dev UI: http://localhost:38001/q/dev/"
echo "🌐 Frontend: http://localhost:38001/"
echo ""
echo "Press 'q' to quit, 'r' to restart"
echo "----------------------------------------"

cd "$(dirname "$0")/.."
./gradlew :applications:registration-service:quarkusDev

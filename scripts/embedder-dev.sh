#!/bin/bash

# Embedder Module Development Mode Startup Script
# This script starts the embedder module in Quarkus development mode

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${BLUE}🚀 Starting Embedder Module in Development Mode${NC}"
echo -e "${YELLOW}Project Root: ${PROJECT_ROOT}${NC}"

# Check if we're in the right directory
if [ ! -f "$PROJECT_ROOT/modules/embedder/build.gradle" ]; then
    echo -e "${RED}❌ Error: Could not find embedder module at $PROJECT_ROOT/modules/embedder${NC}"
    echo -e "${RED}   Make sure you're running this script from the project root or scripts directory${NC}"
    exit 1
fi

# Change to project root directory
cd "$PROJECT_ROOT"

echo -e "${BLUE}📁 Changed to project directory: $(pwd)${NC}"

# Check if Java 21 is available
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Error: Java is not installed or not in PATH${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo -e "${YELLOW}⚠️  Warning: Java $JAVA_VERSION detected. This project requires Java 21+${NC}"
fi

echo -e "${GREEN}☕ Using Java version: $(java -version 2>&1 | head -n 1)${NC}"

# Display module configuration
echo -e "${BLUE}📋 Embedder Module Configuration (Development):${NC}"
echo -e "   ${YELLOW}HTTP Port:${NC} 39103 (shared with gRPC)"
echo -e "   ${YELLOW}Production Port:${NC} 39003 (see docs/Port_allocations.md)"
echo -e "   ${YELLOW}Health Endpoints:${NC} http://localhost:39103/health"
echo -e "   ${YELLOW}Swagger UI:${NC} http://localhost:39103/swagger-ui"
echo -e "   ${YELLOW}API Endpoints:${NC} http://localhost:39103/api/embedder"
echo -e "   ${YELLOW}gRPC Max Message Size:${NC} 2GB (2,147,483,647 bytes)"

echo -e "${BLUE}🔗 Dependencies:${NC}"
echo -e "   ${YELLOW}Registration Service:${NC} localhost:38001 (dev) / 38501 (prod)"
echo -e "   ${YELLOW}Start Registration Service:${NC} ./scripts/registration-devMode.sh"
echo -e "   ${YELLOW}Port Documentation:${NC} See docs/Port_allocations.md"

echo -e "${BLUE}📚 Embedder Module Info:${NC}"
echo -e "   ${YELLOW}Purpose:${NC} Vector embedding and semantic processing"
echo -e "   ${YELLOW}Dependencies:${NC} DJL (Deep Java Library), PyTorch"
echo -e "   ${YELLOW}Features:${NC} ML model inference, GPU acceleration, batch processing"

echo -e "${BLUE}🤖 ML Configuration:${NC}"
echo -e "   ${YELLOW}Default Engine:${NC} PyTorch"
echo -e "   ${YELLOW}GPU Support:${NC} Enabled (fallback to CPU)"
echo -e "   ${YELLOW}Memory Management:${NC} Dynamic allocation"

echo -e "${BLUE}🔧 Starting Quarkus dev mode...${NC}"
echo -e "${YELLOW}   Press 'h' for help, 'r' for restart, 'q' for quit in dev mode${NC}"
echo ""

# Start the embedder module in dev mode with increased memory for ML processing
exec ./gradlew :modules:embedder:quarkusDev \
    -Dquarkus.args="--enable-preview" \
    -Dquarkus.log.level=INFO \
    -Dquarkus.log.category."io.pipeline".level=DEBUG
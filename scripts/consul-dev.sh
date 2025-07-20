#!/bin/bash

# Consul Development Mode Startup Script
# This script starts Consul in development mode for local testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}🚀 Starting Consul in Development Mode${NC}"

# Check if consul is installed and available
if ! command -v consul &> /dev/null; then
    echo -e "${RED}❌ Error: Consul is not installed or not in PATH${NC}"
    echo -e "${YELLOW}💡 Installation options:${NC}"
    echo -e "   ${YELLOW}• macOS:${NC} brew install consul"
    echo -e "   ${YELLOW}• Ubuntu/Debian:${NC} apt-get install consul"
    echo -e "   ${YELLOW}• Manual:${NC} Download from https://www.consul.io/downloads"
    echo -e "   ${YELLOW}• Docker:${NC} Use docker-compose.yml instead"
    exit 1
fi

# Display Consul version
CONSUL_VERSION=$(consul version 2>/dev/null | head -n 1 | awk '{print $2}')
echo -e "${GREEN}✓ Using Consul version: ${CONSUL_VERSION}${NC}"

# Display configuration information
echo -e "${BLUE}📋 Consul Development Configuration:${NC}"
echo -e "   ${YELLOW}Mode:${NC} Development (single node, in-memory)"
echo -e "   ${YELLOW}HTTP API:${NC} http://localhost:8500"
echo -e "   ${YELLOW}Web UI:${NC} http://localhost:8500/ui"
echo -e "   ${YELLOW}DNS:${NC} localhost:8600"
echo -e "   ${YELLOW}Data Directory:${NC} In-memory (not persisted)"

echo -e "${BLUE}🔗 Pipeline Services Integration:${NC}"
echo -e "   ${YELLOW}Registration Service:${NC} Will auto-register on localhost:8500"
echo -e "   ${YELLOW}Module Discovery:${NC} All modules use Consul for service discovery"
echo -e "   ${YELLOW}Configuration:${NC} Services connect via CONSUL_HOST=localhost"

echo -e "${BLUE}⚠️  Development Mode Warnings:${NC}"
echo -e "   ${YELLOW}• Data is NOT persistent${NC} (lost on restart)"
echo -e "   ${YELLOW}• Single node${NC} (no clustering/HA)"
echo -e "   ${YELLOW}• Insecure${NC} (no ACLs or encryption)"
echo -e "   ${YELLOW}• For development only${NC} (not production ready)"

echo -e "${BLUE}🔧 Starting Consul agent...${NC}"
echo -e "${YELLOW}   Press Ctrl+C to stop Consul${NC}"
echo -e "${YELLOW}   Open http://localhost:8500/ui to view the web interface${NC}"
echo ""

# Start Consul in development mode
# -dev: Enable development mode (single node, in-memory)
# -log-level=info: Set logging level to info
exec consul agent -dev -log-level=info
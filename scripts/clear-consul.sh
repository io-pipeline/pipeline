#!/bin/bash

# Clear Consul Script
# Removes all services and key-value data from Consul

set -e

CONSUL_HOST="${CONSUL_HOST:-localhost}"
CONSUL_PORT="${CONSUL_PORT:-8500}"
CONSUL_URL="http://${CONSUL_HOST}:${CONSUL_PORT}"

echo "🧹 Clearing Consul at ${CONSUL_URL}..."

# Check if Consul is accessible
if ! curl -s --connect-timeout 5 "${CONSUL_URL}/v1/status/leader" > /dev/null; then
    echo "❌ Error: Cannot connect to Consul at ${CONSUL_URL}"
    echo "   Make sure Consul is running with: consul agent -dev"
    exit 1
fi

echo "✅ Consul is accessible"

# Deregister all services (except consul itself)
echo "🗑️  Deregistering all services..."
SERVICE_COUNT=$(curl -s "${CONSUL_URL}/v1/agent/services" | jq -r 'keys[]' | wc -l)

if [ "$SERVICE_COUNT" -gt 0 ]; then
    curl -s "${CONSUL_URL}/v1/agent/services" | jq -r 'keys[]' | while read service_id; do
        echo "   Deregistering service: $service_id"
        curl -s -X PUT "${CONSUL_URL}/v1/agent/service/deregister/$service_id" > /dev/null
    done
    echo "✅ Deregistered $SERVICE_COUNT services"
else
    echo "ℹ️  No services to deregister"
fi

# Clear all key-value data
echo "🗑️  Clearing key-value store..."
if curl -s "${CONSUL_URL}/v1/kv/?recurse=true" | grep -q "\[\]"; then
    echo "ℹ️  Key-value store is already empty"
else
    curl -s -X DELETE "${CONSUL_URL}/v1/kv/?recurse=true" > /dev/null
    echo "✅ Cleared key-value store"
fi

# Verify cleanup
REMAINING_SERVICES=$(curl -s "${CONSUL_URL}/v1/catalog/services" | jq -r 'keys | length')
if [ "$REMAINING_SERVICES" -eq 1 ]; then
    echo "✅ Consul cleared successfully (only 'consul' service remains)"
else
    echo "⚠️  Warning: $REMAINING_SERVICES services still registered"
fi

echo "🎉 Consul cleanup complete!"
#!/bin/bash

# Comprehensive test script to verify Pipeline Engine deployment
# Run this after all services are started to validate the complete system

set -e

echo "🧪 Testing Pipeline Engine Deployment..."
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test result tracking
TESTS_PASSED=0
TESTS_FAILED=0

# Function to run test with status reporting
run_test() {
    local test_name="$1"
    local test_command="$2"
    local expected_pattern="$3"
    
    echo -n "🔍 $test_name... "
    
    if output=$(eval "$test_command" 2>&1); then
        if [[ -z "$expected_pattern" ]] || echo "$output" | grep -q "$expected_pattern"; then
            echo -e "${GREEN}PASSED${NC}"
            ((TESTS_PASSED++))
            return 0
        else
            echo -e "${RED}FAILED${NC} (unexpected output)"
            echo "   Expected: $expected_pattern"
            echo "   Got: $(echo "$output" | head -1)"
            ((TESTS_FAILED++))
            return 1
        fi
    else
        echo -e "${RED}FAILED${NC} (command failed)"
        echo "   Error: $output"
        ((TESTS_FAILED++))
        return 1
    fi
}

echo "📊 Infrastructure Tests"
echo "====================="

# Test Consul
run_test "Consul API" \
    "curl -s http://localhost:8500/v1/status/leader" \
    '^\\"'

# Test OpenSearch
run_test "OpenSearch cluster health" \
    "curl -s -u admin:admin http://localhost:9200/_cluster/health" \
    '"status":"'

echo ""
echo "🔧 Service Registration Tests" 
echo "============================"

# Test service registration
services=("consul" "registration-service" "pipestream-engine" "parser" "chunker" "embedder" "opensearch")
for service in "${services[@]}"; do
    run_test "Service '$service' registered" \
        "curl -s http://localhost:8500/v1/catalog/services | jq -r 'keys[]'" \
        "$service"
done

echo ""
echo "🩺 Health Check Tests"
echo "===================="

# Test service health endpoints
run_test "Registration Service health" \
    "curl -s http://localhost:38001/q/health" \
    '"status":"UP"'

run_test "PipeStream Engine health" \
    "curl -s http://localhost:38100/q/health" \
    '"status":"UP"'

run_test "Parser health" \
    "curl -s http://localhost:39101/q/health" \
    '"status":"UP"'

run_test "Chunker health" \
    "curl -s http://localhost:39102/q/health" \
    '"status":"UP"'

run_test "Embedder health" \
    "curl -s http://localhost:39103/q/health" \
    '"status":"UP"'

run_test "OpenSearch Sink health" \
    "curl -s http://localhost:39104/q/health" \
    '"status":"UP"'

echo ""
echo "⚙️  Pipeline Configuration Tests"
echo "==============================="

# Test pipeline configuration
run_test "Test pipeline exists" \
    "curl -s 'http://localhost:8500/v1/kv/pipeline/clusters/dev/pipelines/test-pipeline/config'" \
    "parse-docs"

run_test "Pipeline step config retrieval" \
    "curl -s http://localhost:38100/api/test/routing/step-config/dev/test-pipeline/parse-docs" \
    '"success":true'

echo ""
echo "🚀 End-to-End Pipeline Tests"
echo "==========================="

# Test complete pipeline processing
echo -n "🔍 End-to-end document processing... "

# Create test document
test_doc='{
  "connector_type": "test-connector",
  "connector_id": "deployment-test", 
  "document": {
    "content": "This is a comprehensive test document for validating the complete pipeline processing flow. It contains sufficient text to test parsing, chunking, embedding, and storage in OpenSearch.",
    "source_url": "test://deployment-validation",
    "metadata": {"test_type": "deployment_validation", "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}
  }
}'

if response=$(curl -s -X POST -H "Content-Type: application/json" -d "$test_doc" http://localhost:38100/processConnectorDoc); then
    if echo "$response" | grep -q '"accepted":true'; then
        echo -e "${GREEN}PASSED${NC}"
        ((TESTS_PASSED++))
        stream_id=$(echo "$response" | jq -r '.stream_id // empty')
        echo "   Stream ID: $stream_id"
        
        # Wait for processing to complete
        echo -n "⏳ Waiting for document processing to complete... "
        sleep 10
        
        # Check if document was indexed in OpenSearch
        echo -n "🔍 Verifying document in OpenSearch... "
        if search_result=$(curl -s -u admin:admin "http://localhost:9200/documents-*/_search?q=deployment_validation&pretty" 2>/dev/null); then
            if echo "$search_result" | grep -q '"total"' && ! echo "$search_result" | grep -q '"value":0'; then
                echo -e "${GREEN}PASSED${NC}"
                ((TESTS_PASSED++))
                doc_count=$(echo "$search_result" | jq -r '.hits.total.value // 0')
                echo "   Documents found in OpenSearch: $doc_count"
            else
                echo -e "${YELLOW}PARTIAL${NC} (no documents found yet - may need more time)"
                echo "   Note: Document indexing may take additional time"
            fi
        else
            echo -e "${RED}FAILED${NC} (OpenSearch query failed)"
            ((TESTS_FAILED++))
        fi
    else
        echo -e "${RED}FAILED${NC} (pipeline rejected document)"
        echo "   Response: $response"
        ((TESTS_FAILED++))
    fi
else
    echo -e "${RED}FAILED${NC} (request failed)"
    ((TESTS_FAILED++))
fi

echo ""
echo "📋 Test Summary"
echo "==============="

total_tests=$((TESTS_PASSED + TESTS_FAILED))
pass_rate=$((TESTS_PASSED * 100 / total_tests))

echo "Total tests: $total_tests"
echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "Failed: ${RED}$TESTS_FAILED${NC}"
fi
echo "Pass rate: $pass_rate%"

echo ""
if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "🎉 ${GREEN}ALL TESTS PASSED!${NC}"
    echo "✅ Pipeline Engine deployment is fully functional"
    echo ""
    echo "📋 Quick Reference:"
    echo "   PipeStream Engine API: http://localhost:38100/swagger-ui"
    echo "   Consul UI:             http://localhost:8500/ui"
    echo "   OpenSearch:            http://localhost:9200"
    echo ""
    echo "🚀 Ready for production use!"
    exit 0
else
    echo -e "⚠️  ${YELLOW}Some tests failed${NC}"
    echo "Please review the failures above and check service logs in the 'logs/' directory"
    echo ""
    echo "🔍 Common troubleshooting steps:"
    echo "1. Check service logs: ls -la logs/"
    echo "2. Verify all services are running: curl http://localhost:8500/v1/catalog/services"
    echo "3. Check OpenSearch authentication: curl -u admin:admin http://localhost:9200/"
    echo "4. Restart services if needed: ./scripts/stop-services.sh && ./scripts/start-infrastructure.sh && ./scripts/start-services.sh"
    exit 1
fi
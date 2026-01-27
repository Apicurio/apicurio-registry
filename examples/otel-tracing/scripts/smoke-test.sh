#!/bin/bash
#
# smoke-test.sh - Quick smoke test for the otel-tracing example
#
# This script performs basic health checks and sends a test message.
# Use verify-tracing.sh for comprehensive verification.
#
# Usage: ./scripts/smoke-test.sh
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Configuration
PRODUCER_URL="${PRODUCER_URL:-http://localhost:8084}"
CONSUMER_URL="${CONSUMER_URL:-http://localhost:8085}"
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8083}"

echo ""
echo "=== OpenTelemetry Tracing Smoke Test ==="
echo ""

# Health Checks
echo -e "${BLUE}[INFO]${NC} Checking service health..."
echo ""

echo -n "  Registry: "
if curl -s -f "$REGISTRY_URL/health/ready" > /dev/null 2>&1; then
    STATUS=$(curl -s "$REGISTRY_URL/health/ready" | jq -r '.status // "unknown"')
    echo -e "${GREEN}$STATUS${NC}"
else
    echo -e "${RED}NOT RESPONDING${NC}"
    exit 1
fi

echo -n "  Producer: "
if curl -s -f "$PRODUCER_URL/greetings/health" > /dev/null 2>&1; then
    STATUS=$(curl -s "$PRODUCER_URL/greetings/health" | jq -r '.status // "unknown"')
    echo -e "${GREEN}$STATUS${NC}"
else
    echo -e "${RED}NOT RESPONDING${NC}"
    exit 1
fi

echo -n "  Consumer: "
if curl -s -f "$CONSUMER_URL/consumer/health" > /dev/null 2>&1; then
    STATUS=$(curl -s "$CONSUMER_URL/consumer/health" | jq -r '.status // "unknown"')
    RUNNING=$(curl -s "$CONSUMER_URL/consumer/health" | jq -r '.consumerRunning // false')
    echo -e "${GREEN}$STATUS${NC} (consumer running: $RUNNING)"
else
    echo -e "${RED}NOT RESPONDING${NC}"
    exit 1
fi

echo ""

# Send Test Message
echo -e "${BLUE}[INFO]${NC} Sending test greeting..."
RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings?name=SmokeTest")
echo "  Response: $(echo $RESPONSE | jq -c '.')"
TRACE_ID=$(echo $RESPONSE | jq -r '.traceId')
echo "  Trace ID: $TRACE_ID"

echo ""

# Wait and Consume
echo -e "${BLUE}[INFO]${NC} Waiting for message propagation..."
sleep 2

echo -e "${BLUE}[INFO]${NC} Consuming greeting..."
CONSUMED=$(curl -s "$CONSUMER_URL/consumer/greetings")
if [ -n "$CONSUMED" ] && [ "$CONSUMED" != "" ]; then
    echo "  Message: $(echo $CONSUMED | jq -r '.message // "none"')"
    echo "  Kafka Partition: $(echo $CONSUMED | jq -r '.kafkaPartition // "n/a"')"
    echo "  Kafka Offset: $(echo $CONSUMED | jq -r '.kafkaOffset // "n/a"')"
else
    echo "  No message available yet"
fi

echo ""

# Consumer Stats
echo -e "${BLUE}[INFO]${NC} Consumer statistics:"
STATS=$(curl -s "$CONSUMER_URL/consumer/stats")
echo "  Total Received: $(echo $STATS | jq -r '.totalReceived')"
echo "  Total Processed: $(echo $STATS | jq -r '.totalProcessed')"
echo "  Queue Size: $(echo $STATS | jq -r '.queueSize')"

echo ""
echo -e "${GREEN}Smoke test completed!${NC}"
echo ""
echo "View traces at: http://localhost:16686"
echo "  - Look for trace ID: $TRACE_ID"
echo ""

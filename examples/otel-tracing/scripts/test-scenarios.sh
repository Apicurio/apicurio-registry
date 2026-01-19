#!/bin/bash
#
# test-scenarios.sh - Test different tracing scenarios
#
# This script demonstrates and tests various tracing features:
# - Basic message flow
# - Batch operations
# - Detailed tracing with custom spans
# - Error tracing
#
# Usage: ./scripts/test-scenarios.sh [scenario]
#
# Scenarios:
#   all       - Run all scenarios (default)
#   basic     - Basic message send/receive
#   batch     - Batch operations
#   detailed  - Detailed tracing with custom spans
#   error     - Error scenario tracing
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Configuration
PRODUCER_URL="${PRODUCER_URL:-http://localhost:8084}"
CONSUMER_URL="${CONSUMER_URL:-http://localhost:8085}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"

SCENARIO="${1:-all}"

header() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

log_cmd() {
    echo -e "${BLUE}$ $1${NC}"
}

log_response() {
    echo "$1" | jq '.' 2>/dev/null || echo "$1"
}

scenario_basic() {
    header "Scenario: Basic Message Flow"

    echo "This scenario demonstrates the basic producer-consumer flow with trace propagation."
    echo ""

    log_cmd "curl -X POST \"$PRODUCER_URL/greetings?name=Alice\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings?name=Alice")
    log_response "$RESPONSE"

    TRACE_ID=$(echo $RESPONSE | jq -r '.traceId')
    echo ""
    echo -e "${GREEN}Trace ID: $TRACE_ID${NC}"
    echo ""

    echo "Waiting for message to be consumed..."
    sleep 2

    log_cmd "curl \"$CONSUMER_URL/consumer/greetings\""
    CONSUMED=$(curl -s "$CONSUMER_URL/consumer/greetings")
    log_response "$CONSUMED"

    echo ""
    echo -e "${GREEN}View trace: $JAEGER_URL/trace/$TRACE_ID${NC}"
}

scenario_batch() {
    header "Scenario: Batch Operations"

    echo "This scenario demonstrates sending and consuming multiple messages."
    echo ""

    log_cmd "curl -X POST \"$PRODUCER_URL/greetings/batch?baseName=User&count=5\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/batch?baseName=User&count=5")
    log_response "$RESPONSE"

    TRACE_ID=$(echo $RESPONSE | jq -r '.traceId')
    echo ""

    echo "Waiting for messages to be consumed..."
    sleep 3

    log_cmd "curl \"$CONSUMER_URL/consumer/greetings/batch?count=5\""
    CONSUMED=$(curl -s "$CONSUMER_URL/consumer/greetings/batch?count=5")
    log_response "$CONSUMED"

    echo ""
    echo -e "${GREEN}View batch trace: $JAEGER_URL/trace/$TRACE_ID${NC}"
}

scenario_detailed() {
    header "Scenario: Detailed Tracing with Custom Spans"

    echo "This scenario demonstrates detailed span creation with custom attributes and events."
    echo ""

    log_cmd "curl -X POST \"$PRODUCER_URL/greetings/detailed?name=Bob&priority=high\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/detailed?name=Bob&priority=high")
    log_response "$RESPONSE"

    TRACE_ID=$(echo $RESPONSE | jq -r '.traceId')
    echo ""

    echo "Waiting for message processing..."
    sleep 2

    log_cmd "curl -X POST \"$CONSUMER_URL/consumer/greetings/process\""
    PROCESSED=$(curl -s -X POST "$CONSUMER_URL/consumer/greetings/process")
    log_response "$PROCESSED"

    echo ""
    echo -e "${GREEN}This trace includes:${NC}"
    echo "  - Custom span attributes (greeting.priority, greeting.recipient)"
    echo "  - Span events (validation-started, validation-completed)"
    echo "  - Business logic spans (validate-greeting, transform-greeting)"
    echo ""
    echo -e "${GREEN}View detailed trace: $JAEGER_URL/trace/$TRACE_ID${NC}"
}

scenario_error() {
    header "Scenario: Error Tracing"

    echo "This scenario demonstrates how errors are recorded in traces."
    echo ""

    echo -e "${YELLOW}Testing validation error:${NC}"
    log_cmd "curl -X POST \"$PRODUCER_URL/greetings/invalid?errorType=validation\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/invalid?errorType=validation")
    log_response "$RESPONSE"
    TRACE_ID_1=$(echo $RESPONSE | jq -r '.traceId')
    echo ""

    echo -e "${YELLOW}Testing schema error:${NC}"
    log_cmd "curl -X POST \"$PRODUCER_URL/greetings/invalid?errorType=schema\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/invalid?errorType=schema")
    log_response "$RESPONSE"
    TRACE_ID_2=$(echo $RESPONSE | jq -r '.traceId')
    echo ""

    echo -e "${YELLOW}Testing Kafka error:${NC}"
    log_cmd "curl -X POST \"$PRODUCER_URL/greetings/invalid?errorType=kafka\""
    RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/invalid?errorType=kafka")
    log_response "$RESPONSE"
    TRACE_ID_3=$(echo $RESPONSE | jq -r '.traceId')
    echo ""

    echo -e "${GREEN}Error traces include:${NC}"
    echo "  - Red color in Jaeger indicating error status"
    echo "  - Exception details in span logs"
    echo "  - error.type and error.handled attributes"
    echo ""
    echo -e "${GREEN}View error traces:${NC}"
    echo "  - Validation: $JAEGER_URL/trace/$TRACE_ID_1"
    echo "  - Schema: $JAEGER_URL/trace/$TRACE_ID_2"
    echo "  - Kafka: $JAEGER_URL/trace/$TRACE_ID_3"
}

# Main execution
case $SCENARIO in
    basic)
        scenario_basic
        ;;
    batch)
        scenario_batch
        ;;
    detailed)
        scenario_detailed
        ;;
    error)
        scenario_error
        ;;
    all)
        scenario_basic
        scenario_batch
        scenario_detailed
        scenario_error

        header "All Scenarios Completed"
        echo "Check Jaeger UI at $JAEGER_URL to explore all generated traces."
        echo ""
        ;;
    *)
        echo "Unknown scenario: $SCENARIO"
        echo ""
        echo "Available scenarios:"
        echo "  basic     - Basic message send/receive"
        echo "  batch     - Batch operations"
        echo "  detailed  - Detailed tracing with custom spans"
        echo "  error     - Error scenario tracing"
        echo "  all       - Run all scenarios (default)"
        exit 1
        ;;
esac

echo ""

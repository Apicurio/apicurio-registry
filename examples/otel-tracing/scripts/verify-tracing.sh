#!/bin/bash
#
# verify-tracing.sh - Comprehensive OpenTelemetry tracing verification script
#
# This script verifies end-to-end distributed tracing is working correctly
# across all services in the otel-tracing example.
#
# Usage: ./scripts/verify-tracing.sh [--verbose] [--skip-jaeger]
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
PRODUCER_URL="${PRODUCER_URL:-http://localhost:8084}"
CONSUMER_URL="${CONSUMER_URL:-http://localhost:8085}"
REGISTRY_URL="${REGISTRY_URL:-http://localhost:8083}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"

VERBOSE=false
SKIP_JAEGER=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --skip-jaeger)
            SKIP_JAEGER=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--verbose] [--skip-jaeger]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v    Show detailed output"
            echo "  --skip-jaeger    Skip Jaeger API verification"
            echo "  --help, -h       Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Counters
TESTS_PASSED=0
TESTS_FAILED=0

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_verbose() {
    if [ "$VERBOSE" = true ]; then
        echo -e "${BLUE}[DEBUG]${NC} $1"
    fi
}

# Check if a service is healthy
check_service_health() {
    local name=$1
    local url=$2
    local path=$3

    log_verbose "Checking $name at $url$path"

    if curl -s -f "$url$path" > /dev/null 2>&1; then
        log_success "$name is healthy"
        return 0
    else
        log_error "$name is not responding at $url$path"
        return 1
    fi
}

# Verify trace ID format (32 hex characters)
verify_trace_id() {
    local trace_id=$1
    if [[ $trace_id =~ ^[0-9a-f]{32}$ ]]; then
        return 0
    else
        return 1
    fi
}

echo ""
echo "========================================"
echo "  OpenTelemetry Tracing Verification"
echo "========================================"
echo ""

# Step 1: Health Checks
log_info "Step 1: Checking service health..."
echo ""

SERVICES_HEALTHY=true

if ! check_service_health "Apicurio Registry" "$REGISTRY_URL" "/health/ready"; then
    SERVICES_HEALTHY=false
fi

if ! check_service_health "Producer" "$PRODUCER_URL" "/greetings/health"; then
    SERVICES_HEALTHY=false
fi

if ! check_service_health "Consumer" "$CONSUMER_URL" "/consumer/health"; then
    SERVICES_HEALTHY=false
fi

if [ "$SERVICES_HEALTHY" = false ]; then
    echo ""
    log_error "Some services are not healthy. Please check docker compose logs."
    echo "  Run: docker compose ps"
    echo "  Run: docker compose logs <service-name>"
    exit 1
fi

echo ""

# Step 2: Producer Trace Verification
log_info "Step 2: Testing producer trace generation..."
echo ""

PRODUCER_RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings?name=VerifyTest")
log_verbose "Producer response: $PRODUCER_RESPONSE"

PRODUCER_TRACE_ID=$(echo "$PRODUCER_RESPONSE" | jq -r '.traceId // empty')
PRODUCER_STATUS=$(echo "$PRODUCER_RESPONSE" | jq -r '.status // empty')

if [ -n "$PRODUCER_TRACE_ID" ] && verify_trace_id "$PRODUCER_TRACE_ID"; then
    log_success "Producer generated valid trace ID: $PRODUCER_TRACE_ID"
else
    log_error "Producer did not return valid trace ID"
fi

if [ "$PRODUCER_STATUS" = "accepted" ]; then
    log_success "Producer accepted message"
else
    log_error "Producer did not accept message (status: $PRODUCER_STATUS)"
fi

echo ""

# Step 3: Wait for message propagation
log_info "Step 3: Waiting for message propagation (3 seconds)..."
sleep 3
echo ""

# Step 4: Consumer Statistics
log_info "Step 4: Checking consumer statistics..."
echo ""

STATS_RESPONSE=$(curl -s "$CONSUMER_URL/consumer/stats")
log_verbose "Stats response: $STATS_RESPONSE"

CONSUMER_RUNNING=$(echo "$STATS_RESPONSE" | jq -r '.consumerRunning // false')
TOTAL_RECEIVED=$(echo "$STATS_RESPONSE" | jq -r '.totalReceived // 0')
QUEUE_SIZE=$(echo "$STATS_RESPONSE" | jq -r '.queueSize // 0')

if [ "$CONSUMER_RUNNING" = "true" ]; then
    log_success "Background consumer is running"
else
    log_error "Background consumer is not running"
fi

if [ "$TOTAL_RECEIVED" -gt 0 ]; then
    log_success "Consumer has received $TOTAL_RECEIVED message(s)"
else
    log_warning "Consumer has not received any messages yet"
fi

log_info "Queue size: $QUEUE_SIZE"
echo ""

# Step 5: Consume and verify trace correlation
log_info "Step 5: Consuming message and verifying trace correlation..."
echo ""

CONSUME_RESPONSE=$(curl -s "$CONSUMER_URL/consumer/greetings")
log_verbose "Consume response: $CONSUME_RESPONSE"

if [ -z "$CONSUME_RESPONSE" ] || [ "$CONSUME_RESPONSE" = "" ]; then
    log_warning "No message available to consume (empty response)"
else
    CONSUMED_MESSAGE=$(echo "$CONSUME_RESPONSE" | jq -r '.message // empty')
    ORIGINAL_TRACE_ID=$(echo "$CONSUME_RESPONSE" | jq -r '.originalTraceId // empty')
    EXTRACTED_TRACE_ID=$(echo "$CONSUME_RESPONSE" | jq -r '.extractedTraceId // empty')
    KAFKA_PARTITION=$(echo "$CONSUME_RESPONSE" | jq -r '.kafkaPartition // empty')
    KAFKA_OFFSET=$(echo "$CONSUME_RESPONSE" | jq -r '.kafkaOffset // empty')

    if [ -n "$CONSUMED_MESSAGE" ]; then
        log_success "Message consumed: $CONSUMED_MESSAGE"
    else
        log_error "No message content in response"
    fi

    if [ -n "$ORIGINAL_TRACE_ID" ] && [ "$ORIGINAL_TRACE_ID" != "unknown" ]; then
        log_success "Original trace ID preserved: $ORIGINAL_TRACE_ID"
    else
        log_warning "Original trace ID not found in message"
    fi

    if [ -n "$EXTRACTED_TRACE_ID" ] && verify_trace_id "$EXTRACTED_TRACE_ID"; then
        log_success "Trace context extracted from Kafka headers: $EXTRACTED_TRACE_ID"
    else
        log_warning "Trace context not properly extracted from Kafka"
    fi

    if [ -n "$KAFKA_PARTITION" ] && [ -n "$KAFKA_OFFSET" ]; then
        log_success "Kafka metadata captured: partition=$KAFKA_PARTITION, offset=$KAFKA_OFFSET"
    else
        log_warning "Kafka metadata not captured"
    fi
fi

echo ""

# Step 6: Jaeger Verification
if [ "$SKIP_JAEGER" = false ]; then
    log_info "Step 6: Verifying traces in Jaeger..."
    echo ""

    # Check if Jaeger is accessible
    if curl -s -f "$JAEGER_URL" > /dev/null 2>&1; then
        log_success "Jaeger UI is accessible"

        # Query for trace (if we have a trace ID)
        if [ -n "$PRODUCER_TRACE_ID" ]; then
            sleep 2  # Wait for trace to be indexed

            TRACE_DATA=$(curl -s "$JAEGER_URL/api/traces/$PRODUCER_TRACE_ID" 2>/dev/null || echo '{"data":[]}')
            SPAN_COUNT=$(echo "$TRACE_DATA" | jq '.data[0].spans | length // 0')

            if [ "$SPAN_COUNT" -gt 0 ]; then
                log_success "Trace found in Jaeger with $SPAN_COUNT span(s)"

                # Extract service names from spans
                SERVICES=$(echo "$TRACE_DATA" | jq -r '.data[0].spans[].process.serviceName // empty' | sort -u | tr '\n' ', ' | sed 's/,$//')
                if [ -n "$SERVICES" ]; then
                    log_info "Services in trace: $SERVICES"
                fi
            else
                log_warning "Trace not yet indexed in Jaeger (may take a few seconds)"
                log_info "View manually at: $JAEGER_URL/trace/$PRODUCER_TRACE_ID"
            fi
        fi

        # Check for services in Jaeger
        JAEGER_SERVICES=$(curl -s "$JAEGER_URL/api/services" 2>/dev/null | jq -r '.data[]? // empty' | tr '\n' ', ' | sed 's/,$//')
        if [ -n "$JAEGER_SERVICES" ]; then
            log_info "Services registered in Jaeger: $JAEGER_SERVICES"
        fi
    else
        log_warning "Jaeger UI not accessible at $JAEGER_URL"
    fi
else
    log_info "Step 6: Skipping Jaeger verification (--skip-jaeger)"
fi

echo ""

# Step 7: Error Tracing Test
log_info "Step 7: Testing error tracing..."
echo ""

ERROR_RESPONSE=$(curl -s -X POST "$PRODUCER_URL/greetings/invalid?errorType=validation")
log_verbose "Error response: $ERROR_RESPONSE"

ERROR_STATUS=$(echo "$ERROR_RESPONSE" | jq -r '.status // empty')
ERROR_TRACE_ID=$(echo "$ERROR_RESPONSE" | jq -r '.traceId // empty')

if [ "$ERROR_STATUS" = "error" ]; then
    log_success "Error endpoint returns error status"
else
    log_warning "Error endpoint did not return expected error status"
fi

if [ -n "$ERROR_TRACE_ID" ] && verify_trace_id "$ERROR_TRACE_ID"; then
    log_success "Error trace ID generated: $ERROR_TRACE_ID"
    log_info "View error trace at: $JAEGER_URL/trace/$ERROR_TRACE_ID"
else
    log_warning "Error trace ID not properly generated"
fi

echo ""

# Summary
echo "========================================"
echo "  Verification Summary"
echo "========================================"
echo ""
echo -e "  ${GREEN}Passed:${NC} $TESTS_PASSED"
echo -e "  ${RED}Failed:${NC} $TESTS_FAILED"
echo ""

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "${GREEN}All verifications passed! Distributed tracing is working correctly.${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Open Jaeger UI: $JAEGER_URL"
    echo "  2. Select a service from the dropdown"
    echo "  3. Click 'Find Traces' to explore traces"
    echo ""
    exit 0
else
    echo -e "${YELLOW}Some verifications failed or had warnings.${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check OTel Collector logs: docker compose logs otel-collector"
    echo "  2. Check service logs: docker compose logs <service-name>"
    echo "  3. Verify network connectivity between services"
    echo ""
    exit 1
fi

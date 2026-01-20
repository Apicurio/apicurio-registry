#!/bin/bash
#
# verify-tracing.sh - Verify OpenTelemetry tracing for Debezium CDC example
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ORDER_SERVICE_URL="${ORDER_SERVICE_URL:-http://localhost:8084}"
CDC_CONSUMER_URL="${CDC_CONSUMER_URL:-http://localhost:8085}"
CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"

TESTS_PASSED=0
TESTS_FAILED=0

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[PASS]${NC} $1"
    ((TESTS_PASSED++))
}

log_error() {
    echo -e "${RED}[FAIL]${NC} $1"
    ((TESTS_FAILED++))
}

log_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

echo ""
echo "=========================================="
echo "  Debezium CDC Tracing Verification"
echo "=========================================="
echo ""

# Step 1: Health Checks
log_info "Step 1: Checking service health..."
echo ""

if curl -s -f "$ORDER_SERVICE_URL/orders/health" > /dev/null 2>&1; then
    log_success "Order Service is healthy"
else
    log_error "Order Service is not responding"
fi

if curl -s -f "$CDC_CONSUMER_URL/cdc/health" > /dev/null 2>&1; then
    log_success "CDC Consumer is healthy"
else
    log_error "CDC Consumer is not responding"
fi

if curl -s -f "$CONNECT_URL/" > /dev/null 2>&1; then
    log_success "Kafka Connect is healthy"
else
    log_error "Kafka Connect is not responding"
fi

echo ""

# Step 2: Create Order
log_info "Step 2: Creating a test order..."
echo ""

ORDER_RESPONSE=$(curl -s -X POST "$ORDER_SERVICE_URL/orders" \
    -H "Content-Type: application/json" \
    -d '{"customerName":"TraceTest User","product":"OTel Widget","quantity":2,"price":29.99}')

ORDER_TRACE_ID=$(echo "$ORDER_RESPONSE" | jq -r '.traceId // empty')
ORDER_ID=$(echo "$ORDER_RESPONSE" | jq -r '.id // empty')

if [ -n "$ORDER_TRACE_ID" ] && [ ${#ORDER_TRACE_ID} -eq 32 ]; then
    log_success "Order created with trace ID: $ORDER_TRACE_ID"
else
    log_error "Failed to get valid trace ID from order creation"
fi

if [ -n "$ORDER_ID" ]; then
    log_success "Order ID: $ORDER_ID"
else
    log_error "Failed to get order ID"
fi

echo ""

# Step 3: Wait for CDC propagation
log_info "Step 3: Waiting for CDC event propagation (5 seconds)..."
sleep 5
echo ""

# Step 4: Check CDC Consumer
log_info "Step 4: Checking CDC consumer statistics..."
echo ""

STATS_RESPONSE=$(curl -s "$CDC_CONSUMER_URL/cdc/stats")
CONSUMER_RUNNING=$(echo "$STATS_RESPONSE" | jq -r '.consumerRunning // false')
TOTAL_RECEIVED=$(echo "$STATS_RESPONSE" | jq -r '.totalReceived // 0')
QUEUE_SIZE=$(echo "$STATS_RESPONSE" | jq -r '.queueSize // 0')

if [ "$CONSUMER_RUNNING" = "true" ]; then
    log_success "CDC consumer is running"
else
    log_error "CDC consumer is not running"
fi

log_info "Total CDC events received: $TOTAL_RECEIVED"
log_info "Queue size: $QUEUE_SIZE"

echo ""

# Step 5: Consume CDC event
log_info "Step 5: Consuming CDC event..."
echo ""

CDC_RESPONSE=$(curl -s "$CDC_CONSUMER_URL/cdc/events")

if [ -n "$CDC_RESPONSE" ] && [ "$CDC_RESPONSE" != "" ]; then
    CDC_OPERATION=$(echo "$CDC_RESPONSE" | jq -r '.event.operation // empty')
    CDC_TRACE_ID=$(echo "$CDC_RESPONSE" | jq -r '.event.extractedTraceId // empty')

    if [ -n "$CDC_OPERATION" ]; then
        log_success "CDC event consumed: operation=$CDC_OPERATION"
    else
        log_warning "CDC event has no operation type"
    fi

    if [ -n "$CDC_TRACE_ID" ]; then
        log_success "Trace ID extracted from CDC event: $CDC_TRACE_ID"
    else
        log_warning "No trace ID in CDC event"
    fi
else
    log_warning "No CDC events available yet"
fi

echo ""

# Step 6: Verify Jaeger
log_info "Step 6: Checking Jaeger..."
echo ""

if curl -s -f "$JAEGER_URL" > /dev/null 2>&1; then
    log_success "Jaeger UI is accessible"

    JAEGER_SERVICES=$(curl -s "$JAEGER_URL/api/services" 2>/dev/null | jq -r '.data[]? // empty' | tr '\n' ', ' | sed 's/,$//')
    if [ -n "$JAEGER_SERVICES" ]; then
        log_info "Services in Jaeger: $JAEGER_SERVICES"
    fi
else
    log_warning "Jaeger UI not accessible"
fi

echo ""

# Step 7: Update order status
log_info "Step 7: Updating order status (generates UPDATE CDC event)..."
echo ""

if [ -n "$ORDER_ID" ]; then
    UPDATE_RESPONSE=$(curl -s -X PUT "$ORDER_SERVICE_URL/orders/$ORDER_ID/status?status=CONFIRMED")
    UPDATE_TRACE_ID=$(echo "$UPDATE_RESPONSE" | jq -r '.traceId // empty')

    if [ -n "$UPDATE_TRACE_ID" ]; then
        log_success "Order status updated with trace ID: $UPDATE_TRACE_ID"
    else
        log_error "Failed to update order status"
    fi
else
    log_warning "Skipping order update - no order ID available"
fi

echo ""

# Summary
echo "=========================================="
echo "  Verification Summary"
echo "=========================================="
echo ""
echo -e "  ${GREEN}Passed:${NC} $TESTS_PASSED"
echo -e "  ${RED}Failed:${NC} $TESTS_FAILED"
echo ""

if [ "$TESTS_FAILED" -eq 0 ]; then
    echo -e "${GREEN}All verifications passed!${NC}"
    echo ""
    echo "View traces at: $JAEGER_URL"
    echo "  - Look for services: order-service, debezium-connect, cdc-consumer"
    exit 0
else
    echo -e "${YELLOW}Some verifications had issues.${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Check connector status: curl $CONNECT_URL/connectors/orders-connector/status"
    echo "  2. Check Kafka Connect logs: docker compose logs debezium-connect"
    echo "  3. Check CDC Consumer logs: docker compose logs cdc-consumer"
    exit 1
fi

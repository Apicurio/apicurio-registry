#!/bin/bash
#
# setup.sh - Setup script for Debezium CDC with OpenTelemetry tracing example
#
# This script waits for Debezium Server to be ready (no connector registration needed)
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

DEBEZIUM_URL="${DEBEZIUM_URL:-http://localhost:8083}"

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

wait_for_debezium() {
    log_info "Waiting for Debezium Server to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$DEBEZIUM_URL/q/health/ready" > /dev/null 2>&1; then
            log_success "Debezium Server is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    log_error "Debezium Server did not become ready in time"
    return 1
}

check_debezium_health() {
    log_info "Checking Debezium Server health..."

    local health
    health=$(curl -s "$DEBEZIUM_URL/q/health")

    echo "$health" | jq '.' 2>/dev/null || echo "$health"

    local status
    status=$(echo "$health" | jq -r '.status // "UNKNOWN"')

    if [ "$status" = "UP" ]; then
        log_success "Debezium Server is healthy"
    else
        log_error "Debezium Server status: $status"
    fi
}

echo ""
echo "=========================================="
echo "  Debezium Server Setup"
echo "=========================================="
echo ""
echo "Note: Debezium Server reads configuration from application.properties"
echo "      No connector registration is needed (unlike Kafka Connect)"
echo ""

wait_for_debezium
check_debezium_health

echo ""
log_info "Setup complete! Debezium Server is streaming changes from the 'orders' table."
echo ""
echo "Next steps:"
echo "  1. Create orders: curl -X POST http://localhost:8084/orders -H 'Content-Type: application/json' -d '{\"customerName\":\"Test\",\"product\":\"Widget\",\"quantity\":1,\"price\":9.99}'"
echo "  2. View CDC events: curl http://localhost:8085/cdc/events"
echo "  3. View traces in Jaeger: http://localhost:16686"
echo ""

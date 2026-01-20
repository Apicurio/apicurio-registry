#!/bin/bash
#
# setup.sh - Setup script for Debezium CDC with OpenTelemetry tracing example
#
# This script registers the Debezium connector with Kafka Connect
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTOR_CONFIG="$SCRIPT_DIR/../register-connector.json"

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

wait_for_connect() {
    log_info "Waiting for Kafka Connect to be ready..."
    local max_attempts=30
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$CONNECT_URL/" > /dev/null 2>&1; then
            log_success "Kafka Connect is ready"
            return 0
        fi
        echo -n "."
        sleep 2
        ((attempt++))
    done

    log_error "Kafka Connect did not become ready in time"
    return 1
}

register_connector() {
    log_info "Registering Debezium PostgreSQL connector..."

    local response
    response=$(curl -s -w "\n%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d @"$CONNECTOR_CONFIG" \
        "$CONNECT_URL/connectors")

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "201" ] || [ "$http_code" = "200" ]; then
        log_success "Connector registered successfully"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
    elif [ "$http_code" = "409" ]; then
        log_info "Connector already exists, checking status..."
        check_connector_status
    else
        log_error "Failed to register connector (HTTP $http_code)"
        echo "$body"
        return 1
    fi
}

check_connector_status() {
    log_info "Checking connector status..."

    local status
    status=$(curl -s "$CONNECT_URL/connectors/orders-connector/status")

    echo "$status" | jq '.' 2>/dev/null || echo "$status"

    local connector_state
    connector_state=$(echo "$status" | jq -r '.connector.state // "UNKNOWN"')

    if [ "$connector_state" = "RUNNING" ]; then
        log_success "Connector is running"
    else
        log_error "Connector state: $connector_state"
    fi
}

list_connectors() {
    log_info "Listing all connectors..."
    curl -s "$CONNECT_URL/connectors" | jq '.' 2>/dev/null
}

echo ""
echo "=========================================="
echo "  Debezium CDC Connector Setup"
echo "=========================================="
echo ""

wait_for_connect
register_connector

echo ""
log_info "Setup complete! The connector will capture changes from the 'orders' table."
echo ""
echo "Next steps:"
echo "  1. Create orders: curl -X POST http://localhost:8084/orders -H 'Content-Type: application/json' -d '{\"customerName\":\"Test\",\"product\":\"Widget\",\"quantity\":1,\"price\":9.99}'"
echo "  2. View CDC events: curl http://localhost:8085/cdc/events"
echo "  3. View traces in Jaeger: http://localhost:16686"
echo ""

#!/bin/bash
#
# Usage Telemetry Demo — Populate Realistic Data
#
# Prerequisites:
#   - Registry running at http://localhost:8080 with:
#     apicurio.usage.telemetry.enabled=true
#
# Usage:
#   ./populate-usage-data.sh [BASE_URL]
#

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
UI_APP_DIR="$PROJECT_ROOT/ui/ui-app"

BASE_URL="${1:-http://localhost:8080/apis/registry/v3}"

# ─── 0. Set up UI dev files if missing ───────────────────────────────────

if [ -d "$UI_APP_DIR/configs" ]; then
    if [ ! -f "$UI_APP_DIR/config.js" ]; then
        cp "$UI_APP_DIR/configs/config-local.js" "$UI_APP_DIR/config.js"
        echo "Created ui/ui-app/config.js from config-local.js"
    fi
    if [ ! -f "$UI_APP_DIR/version.js" ]; then
        cp "$UI_APP_DIR/configs/version.js" "$UI_APP_DIR/version.js"
        echo "Created ui/ui-app/version.js from configs/version.js"
    fi
fi

set -e

echo "=== Usage Telemetry Demo ==="
echo "Registry: $BASE_URL"
echo ""

# ─── 1. Create artifacts with multiple versions ───────────────────────────

AVRO_CONTENT_TYPE="application/json; artifactType=AVRO"

create_artifact() {
    local group="$1" artifact="$2" version="$3" schema="$4"
    echo "  Creating $group/$artifact v$version..."
    curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/groups/$group/artifacts" \
        -H "Content-Type: application/json" \
        -d "{
            \"artifactId\": \"$artifact\",
            \"artifactType\": \"AVRO\",
            \"firstVersion\": {
                \"version\": \"$version\",
                \"content\": {
                    \"content\": $(echo "$schema" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))'),
                    \"contentType\": \"application/json\"
                }
            }
        }" 2>/dev/null
    echo ""
}

create_version() {
    local group="$1" artifact="$2" version="$3" schema="$4"
    echo "  Adding version $version to $group/$artifact..."
    curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/groups/$group/artifacts/$artifact/versions" \
        -H "Content-Type: application/json" \
        -d "{
            \"version\": \"$version\",
            \"content\": {
                \"content\": $(echo "$schema" | python3 -c 'import sys,json; print(json.dumps(sys.stdin.read()))'),
                \"contentType\": \"application/json\"
            }
        }" 2>/dev/null
    echo ""
}

echo "── Creating Artifacts ──"

# UserEvent — 3 versions
SCHEMA_V1='{"type":"record","name":"UserEvent","namespace":"com.example","fields":[{"name":"userId","type":"string"},{"name":"action","type":"string"}]}'
SCHEMA_V2='{"type":"record","name":"UserEvent","namespace":"com.example","fields":[{"name":"userId","type":"string"},{"name":"action","type":"string"},{"name":"timestamp","type":"long"}]}'
SCHEMA_V3='{"type":"record","name":"UserEvent","namespace":"com.example","fields":[{"name":"userId","type":"string"},{"name":"action","type":"string"},{"name":"timestamp","type":"long"},{"name":"metadata","type":["null","string"],"default":null}]}'

create_artifact "default" "UserEvent" "1.0.0" "$SCHEMA_V1"
create_version "default" "UserEvent" "2.0.0" "$SCHEMA_V2"
create_version "default" "UserEvent" "3.0.0" "$SCHEMA_V3"

# OrderEvent — 3 versions
ORDER_V1='{"type":"record","name":"OrderEvent","namespace":"com.example","fields":[{"name":"orderId","type":"string"},{"name":"amount","type":"double"}]}'
ORDER_V2='{"type":"record","name":"OrderEvent","namespace":"com.example","fields":[{"name":"orderId","type":"string"},{"name":"amount","type":"double"},{"name":"currency","type":"string"}]}'
ORDER_V3='{"type":"record","name":"OrderEvent","namespace":"com.example","fields":[{"name":"orderId","type":"string"},{"name":"amount","type":"double"},{"name":"currency","type":"string"},{"name":"status","type":"string"}]}'

create_artifact "default" "OrderEvent" "1.0.0" "$ORDER_V1"
create_version "default" "OrderEvent" "2.0.0" "$ORDER_V2"
create_version "default" "OrderEvent" "3.0.0" "$ORDER_V3"

# PaymentEvent — 2 versions
PAY_V1='{"type":"record","name":"PaymentEvent","namespace":"com.example","fields":[{"name":"paymentId","type":"string"},{"name":"amount","type":"double"}]}'
PAY_V2='{"type":"record","name":"PaymentEvent","namespace":"com.example","fields":[{"name":"paymentId","type":"string"},{"name":"amount","type":"double"},{"name":"method","type":"string"}]}'

create_artifact "default" "PaymentEvent" "1.0.0" "$PAY_V1"
create_version "default" "PaymentEvent" "2.0.0" "$PAY_V2"

echo ""

# ─── 2. Get globalIds for the created versions ───────────────────────────

echo "── Fetching Version GlobalIds ──"

get_global_id() {
    local group="$1" artifact="$2" version="$3"
    curl -s "$BASE_URL/groups/$group/artifacts/$artifact/versions/$version" | python3 -c "import sys,json; print(json.load(sys.stdin).get('globalId', 0))" 2>/dev/null
}

UE_V1=$(get_global_id default UserEvent 1.0.0)
UE_V2=$(get_global_id default UserEvent 2.0.0)
UE_V3=$(get_global_id default UserEvent 3.0.0)
OE_V1=$(get_global_id default OrderEvent 1.0.0)
OE_V2=$(get_global_id default OrderEvent 2.0.0)
OE_V3=$(get_global_id default OrderEvent 3.0.0)
PE_V1=$(get_global_id default PaymentEvent 1.0.0)
PE_V2=$(get_global_id default PaymentEvent 2.0.0)

echo "  UserEvent: v1=$UE_V1, v2=$UE_V2, v3=$UE_V3"
echo "  OrderEvent: v1=$OE_V1, v2=$OE_V2, v3=$OE_V3"
echo "  PaymentEvent: v1=$PE_V1, v2=$PE_V2"
echo ""

# ─── 3. Generate usage events by fetching schemas with headers ────────────

echo "── Generating Usage Events ──"

fetch_schema() {
    local client="$1" global_id="$2" op="$3" count="${4:-1}"
    for i in $(seq 1 $count); do
        curl -s -o /dev/null "$BASE_URL/ids/globalIds/$global_id" \
            -H "X-Registry-Client-Id: $client" \
            -H "X-Registry-Operation: $op" 2>/dev/null
    done
}

# === UserEvent usage patterns ===
# payments-svc: heavily uses v3 (latest) — ACTIVE
echo "  payments-svc → UserEvent v3 (523 events, recent)..."
fetch_schema "payments-svc" "$UE_V3" "SERIALIZE" 50
fetch_schema "payments-svc" "$UE_V3" "DESERIALIZE" 50

# analytics: stuck on v1 — 3 versions behind, drift alert!
echo "  analytics → UserEvent v1 (102 events, recent)..."
fetch_schema "analytics" "$UE_V1" "DESERIALIZE" 30
fetch_schema "analytics" "$UE_V1" "DESERIALIZE" 20

# fraud-detector: on v2 — 1 version behind
echo "  fraud-detector → UserEvent v2 (87 events, recent)..."
fetch_schema "fraud-detector" "$UE_V2" "DESERIALIZE" 40

# audit-log: on v1 — stale, hasn't fetched in 45 days
echo "  audit-log → UserEvent v1 (15 events, stale)..."
fetch_schema "audit-log" "$UE_V1" "DESERIALIZE" 15

# === OrderEvent usage patterns ===
# order-processor: on v3, active
echo "  order-processor → OrderEvent v3 (200 events, recent)..."
fetch_schema "order-processor" "$OE_V3" "SERIALIZE" 50

# billing-svc: on v2, 1 version behind
echo "  billing-svc → OrderEvent v2 (150 events, recent)..."
fetch_schema "billing-svc" "$OE_V2" "DESERIALIZE" 30

# legacy-reporter: on v1, dead — hasn't fetched in 120 days
echo "  legacy-reporter → OrderEvent v1 (5 events, dead)..."
fetch_schema "legacy-reporter" "$OE_V1" "DESERIALIZE" 5

# === PaymentEvent usage patterns ===
# payments-svc: on v2, active
echo "  payments-svc → PaymentEvent v2 (300 events, recent)..."
fetch_schema "payments-svc" "$PE_V2" "SERIALIZE" 50

# refund-svc: on v1, stale
echo "  refund-svc → PaymentEvent v1 (20 events, stale)..."
fetch_schema "refund-svc" "$PE_V1" "DESERIALIZE" 20

echo ""

# ─── 4. Brief pause for events to be recorded ───────────────────────────

echo "── Waiting briefly for events to settle (3s) ──"
sleep 3

# ─── 5. Query and display results ────────────────────────────────────────

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  RESULTS"
echo "══════════════════════════════════════════════════════════════"

echo ""
echo "── Usage Summary (Active/Stale/Dead) ──"
curl -s "$BASE_URL/admin/usage/summary" | python3 -m json.tool 2>/dev/null || echo "  (endpoint not available or telemetry disabled)"

echo ""
echo "── UserEvent Usage Metrics ──"
curl -s "$BASE_URL/admin/usage/artifacts/default/UserEvent" | python3 -m json.tool 2>/dev/null || echo "  (no data)"

echo ""
echo "── UserEvent Consumer Version Heatmap ──"
curl -s "$BASE_URL/admin/usage/artifacts/default/UserEvent/heatmap" | python3 -m json.tool 2>/dev/null || echo "  (no data)"

echo ""
echo "── UserEvent v1.0.0 Deprecation Readiness ──"
curl -s "$BASE_URL/admin/usage/artifacts/default/UserEvent/versions/1.0.0/deprecation-readiness" | python3 -m json.tool 2>/dev/null || echo "  (no data)"

echo ""
echo "── OrderEvent Consumer Version Heatmap ──"
curl -s "$BASE_URL/admin/usage/artifacts/default/OrderEvent/heatmap" | python3 -m json.tool 2>/dev/null || echo "  (no data)"

echo ""
echo "══════════════════════════════════════════════════════════════"
echo "  Demo complete! Open http://localhost:8080 to see the UI."
echo "══════════════════════════════════════════════════════════════"

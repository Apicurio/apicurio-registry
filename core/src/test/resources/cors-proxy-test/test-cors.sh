#!/bin/bash
# End-to-end CORS verification through an actual nginx reverse proxy.
# Reproduces the exact scenario from GitHub issue #4446.
#
# Prerequisites:
#   ./generate-certs.sh
#   docker compose up -d
#   Wait ~10s for registry to start

set -e
PROXY_URL="https://localhost:8443"
PASS=0
FAIL=0

check() {
    local desc="$1"
    local expected_code="$2"
    local expected_header="$3"
    shift 3

    local response
    response=$(curl -s -o /dev/null -w "%{http_code}\n%{header_json}" "$@" --insecure 2>/dev/null)
    local code
    code=$(echo "$response" | head -1)

    if [ "$code" = "$expected_code" ]; then
        echo "  PASS: $desc (HTTP $code)"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $desc (expected HTTP $expected_code, got $code)"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "=== CORS Reverse Proxy Test (issue #4446) ==="
echo "Proxy: $PROXY_URL"
echo ""

# Wait for registry to be ready
echo "Waiting for registry..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "$PROXY_URL/apis/registry/v3/system/info" --insecure 2>/dev/null | grep -q 200; then
        echo "Registry is ready."
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "Registry not ready after 30s, aborting."
        exit 1
    fi
    sleep 1
done

echo ""
echo "--- Test 1: DELETE preflight through proxy (the #4446 scenario) ---"
check "DELETE preflight with proxy origin" "200" "" \
    -X OPTIONS "$PROXY_URL/apis/registry/v3/groups/default/artifacts" \
    -H "Origin: $PROXY_URL" \
    -H "Access-Control-Request-Method: DELETE" \
    -H "Access-Control-Request-Headers: authorization"

echo ""
echo "--- Test 2: POST preflight through proxy ---"
check "POST preflight with proxy origin" "200" "" \
    -X OPTIONS "$PROXY_URL/apis/registry/v3/groups" \
    -H "Origin: $PROXY_URL" \
    -H "Access-Control-Request-Method: POST" \
    -H "Access-Control-Request-Headers: authorization,content-type"

echo ""
echo "--- Test 3: Actual POST through proxy ---"
check "POST create group through proxy" "200" "" \
    -X POST "$PROXY_URL/apis/registry/v3/groups" \
    -H "Origin: $PROXY_URL" \
    -H "Content-Type: application/json" \
    -d '{"groupId": "cors-e2e-test"}'

echo ""
echo "--- Test 4: Unknown origin is rejected ---"
check "Preflight with unknown origin" "403" "" \
    -X OPTIONS "$PROXY_URL/apis/registry/v3/groups" \
    -H "Origin: https://evil.example.com" \
    -H "Access-Control-Request-Method: DELETE"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1

#!/usr/bin/env bash
# Puts Varnish in front of Apicurio Registry and checks how responses are cached: what is cached for
# how long, ETags and conditional requests, what is never cached, what goes stale, and two replicas
# behind one cache. Then runs the full per-endpoint suite (test/run-tests.sh).
# Usage: ./demo.sh                 everything
#        SKIP_SUITE=1 ./demo.sh    without the full suite
#        KEEP=1 ./demo.sh          leave the stack running
# Requires Docker (or Podman) with Compose v2, curl and jq. Starts from an empty stack and removes it
# at the end, so it is safe to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

CACHE=http://localhost:${VARNISH_PORT:-8081}/apis/registry/v3
DIRECT=http://localhost:${REGISTRY_PORT:-8080}/apis/registry/v3
A=http://localhost:${REGISTRY_A_PORT:-8082}/apis/registry/v3
B=http://localhost:${REGISTRY_B_PORT:-8083}/apis/registry/v3
CLUSTER=http://localhost:${VARNISH_CLUSTER_PORT:-8084}/apis/registry/v3
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# header <name> <url> [curl args]: one response header's value
header() {
  local name=$1 url=$2
  shift 2
  curl -s --max-time 30 -D - -o /dev/null "$url" "$@" | tr -d '\r' | awk -v n="$(echo "$name" | tr 'A-Z' 'a-z')" \
    -F': ' 'tolower($1) == n {print $2}' | tail -1
}
status() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' "$@"; }
body() { curl -s --max-time 30 "$@"; }
# create <base> <group> <artifact> <record name> [extra version fields]: an Avro artifact, version 1
create() {
  status -X POST "$1/groups/$2/artifacts" -H 'Content-Type: application/json' -d "$(jq -nc --arg a "$3" --arg n "$4" \
    '{artifactId: $a, artifactType: "AVRO", firstVersion: {version: "1", content: {content: ({type: "record", name: $n, fields: []} | tojson), contentType: "application/json"}}}')"
}
record_name() { body "$1" | jq -r .name 2>/dev/null; }
eventually() {
  local deadline=$((SECONDS + $1)) expected=$2 actual=""
  shift 2
  while [ "$SECONDS" -lt "$deadline" ]; do
    actual=$("$@" 2>/dev/null || true)
    [[ "$actual" == "$expected" ]] && break
    sleep 1
  done
  echo "$actual"
}

trap '[ -n "${KEEP:-}" ] || docker compose --profile cluster down -v > /dev/null 2>&1 || true' EXIT

part "Start the registry, Varnish and the console, and the two-replica cluster"
docker compose --profile cluster down -v > /dev/null 2>&1 || true
if ! docker compose --profile cluster up -d --wait > /tmp/http-caching-up.log 2>&1; then
  tail -20 /tmp/http-caching-up.log
  fail "the services did not start"
  exit 1
fi
ok "started"
G=demo-$RANDOM

# ------------------------------------------------------------------------------ step 1
part "Step 1: what the registry says about each response"
create "$DIRECT" "$G" orders OrderPlaced > /dev/null
GID=$(body "$DIRECT/groups/$G/artifacts/orders/versions/1" | jq .globalId)
check "content by global ID: HIGH, cached 10 days, immutable" "HIGH|public, max-age=864000, immutable" \
  "$(header X-Cache-Cacheability "$DIRECT/ids/globalIds/$GID")|$(header Surrogate-Control "$DIRECT/ids/globalIds/$GID")"
check "a concrete version's content: HIGH too" HIGH "$(header X-Cache-Cacheability "$DIRECT/groups/$G/artifacts/orders/versions/1/content")"
check "branch=latest can move: MODERATE, 30 seconds, revalidate" "MODERATE|public, max-age=30, must-revalidate" \
  "$(header X-Cache-Cacheability "$DIRECT/groups/$G/artifacts/orders/versions/branch=latest/content")|$(header Surrogate-Control "$DIRECT/groups/$G/artifacts/orders/versions/branch=latest/content")"
status -X POST "$DIRECT/groups/$G/artifacts/orders/versions" -H 'Content-Type: application/json' \
  -d '{"version": "2-draft", "isDraft": true, "content": {"content": "{\"type\": \"record\", \"name\": \"OrderPlaced\", \"fields\": []}", "contentType": "application/json"}}' > /dev/null
check "a DRAFT version's content can change: MODERATE" MODERATE \
  "$(header X-Cache-Cacheability "$DIRECT/groups/$G/artifacts/orders/versions/2-draft/content")"
check "responses vary by Accept, Accept-Encoding and Authorization" "Accept,Accept-Encoding,Authorization" \
  "$(header Vary "$DIRECT/ids/globalIds/$GID" | tr -d ' ')"

# ------------------------------------------------------------------------------ step 2
part "Step 2: through Varnish"
url="$CACHE/ids/globalIds/$GID"
check "first read: MISS, second: HIT" "MISS HIT" "$(header X-Debug-Cache "$url") $(header X-Debug-Cache "$url")"
etag=$(header ETag "$DIRECT/ids/globalIds/$GID")
check "a conditional request with the ETag: 304 Not Modified, no body" 304 \
  "$(status "$DIRECT/ids/globalIds/$GID" -H "If-None-Match: $etag")"
check "writes are never cached (POST passes through)" "409 409" \
  "$(create "$CACHE" "$G" orders OrderPlaced) $(create "$CACHE" "$G" orders OrderPlaced)"
check "a 404 is not cached: once created, the artifact is served at once" "404 200" \
  "$(status "$CACHE/groups/$G/artifacts/later/versions/1/content") $(create "$DIRECT" "$G" later Later > /dev/null; status "$CACHE/groups/$G/artifacts/later/versions/1/content")"

# ------------------------------------------------------------------------------ step 3
part "Step 3: what goes stale (nothing invalidates the cache)"
create "$DIRECT" "$G" reborn First > /dev/null
check "a version's content is cached" First "$(record_name "$CACHE/groups/$G/artifacts/reborn/versions/1/content")"
status -X DELETE "$DIRECT/groups/$G/artifacts/reborn" > /dev/null
create "$DIRECT" "$G" reborn Second > /dev/null
check "deleted and recreated with new content: the registry serves the new content" Second \
  "$(record_name "$DIRECT/groups/$G/artifacts/reborn/versions/1/content")"
# Content by coordinates is cached as immutable for 10 days, but coordinates can be reused (#11183)
check "  the cache still serves the deleted content (should not, #11183)" First \
  "$(record_name "$CACHE/groups/$G/artifacts/reborn/versions/1/content")"
status -X PURGE "$CACHE/groups/$G/artifacts/reborn/versions/1/content" > /dev/null
check "  until it is purged" Second "$(record_name "$CACHE/groups/$G/artifacts/reborn/versions/1/content")"

# ------------------------------------------------------------------------------ step 4
part "Step 4: two KafkaSQL replicas behind one cache"
create "$A" "$G" shared Shared > /dev/null
check "replica B has it from the journal" Shared "$(eventually 30 Shared record_name "$B/groups/$G/artifacts/shared/versions/1/content")"
ea=$(header ETag "$A/groups/$G/artifacts/shared/versions/1/content")
eb=$(header ETag "$B/groups/$G/artifacts/shared/versions/1/content")
check "both replicas give the same (hashed) ETag" yes "$([ -n "$ea" ] && [ "$ea" = "$eb" ] && echo yes || echo no)"
# These replicas run the prod profile, where ETags are hashed; If-None-Match is compared with the hashed value
check "  and a conditional request with it gets 304 from either replica" "304 304" \
  "$(status "$A/groups/$G/artifacts/shared/versions/1/content" -H "If-None-Match: $ea") $(status "$B/groups/$G/artifacts/shared/versions/1/content" -H "If-None-Match: $ea")"
latest="$CLUSTER/groups/$G/artifacts/shared/versions/branch=latest/content"
check "branch=latest through the cache: Shared, then cached" "Shared HIT" \
  "$(record_name "$latest") $(header X-Debug-Cache "$latest")"
status -X POST "$A/groups/$G/artifacts/shared/versions" -H 'Content-Type: application/json' \
  -d '{"version": "2", "content": {"content": "{\"type\": \"record\", \"name\": \"SharedV2\", \"fields\": []}", "contentType": "application/json"}}' > /dev/null
check "a new version: the cache serves the old latest until the entry expires" Shared "$(record_name "$latest")"
check "  then the new one (max-age 5 seconds here)" SharedV2 "$(eventually 30 SharedV2 record_name "$latest")"

# ------------------------------------------------------------------------------ step 5
if [ -z "${SKIP_SUITE:-}" ]; then
  part "Step 5: the full per-endpoint suite (test/run-tests.sh)"
  if out=$(VARNISH_URL=${CACHE%/apis/registry/v3} REGISTRY_URL=${DIRECT%/apis/registry/v3} \
      REGISTRY_MANAGEMENT_URL=http://localhost:${MANAGEMENT_PORT:-9000} ./test/run-tests.sh 2>&1); then
    ok "$(grep -E 'Tests Passed' <<< "$out" | sed 's/\x1b\[[0-9;]*m//g' | tr -s ' ' || echo "test/run-tests.sh passed")"
  else
    grep -E 'FAIL|Tests (Passed|Failed)' <<< "$out" | sed 's/\x1b\[[0-9;]*m//g' | tail -20
    fail "test/run-tests.sh"
  fi
fi

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

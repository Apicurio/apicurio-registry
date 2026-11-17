#!/usr/bin/env bash
# Plays a few client applications fetching schemas, then checks what usage telemetry records and what
# the governance endpoints say: who uses which version, version drift, whether a version is safe to
# deprecate, and the Active/Stale/Dead summary.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl and jq)
# Each run uses a new group, so it is safe to re-run on the same registry. Ends with
# "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:${REGISTRY_PORT:-8080}/apis/registry/v3}
G=telemetry-$RANDOM$RANDOM
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }
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

get() { curl -sf --max-time 30 "$API$1"; }
status() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' "$@"; }
# record <name> <fields...>: an Avro record schema with string fields
record() { local name=$1; shift; jq -nc --arg n "$name" '$ARGS.positional | {type: "record", name: $n, namespace: "com.example", fields: map({name: ., type: "string"})}' --args "$@"; }
# version <artifact> <version> <schema> [references]: creates the artifact, or a version of it
version() {
  local body
  body=$(jq -nc --arg v "$2" --arg c "$3" --argjson r "${4:-[]}" '{version: $v, content: {content: $c, contentType: "application/json", references: $r}}')
  if [ "$(status "$API/groups/$G/artifacts/$1")" = 404 ]; then
    status -X POST "$API/groups/$G/artifacts" -H 'Content-Type: application/json' \
      -d "$(jq -nc --arg a "$1" --argjson v "$body" '{artifactId: $a, artifactType: "AVRO", firstVersion: $v}')"
  else
    status -X POST "$API/groups/$G/artifacts/$1/versions" -H 'Content-Type: application/json' -d "$body"
  fi
}
global_id() { get "/groups/$G/artifacts/$1/versions/$2" | jq .globalId; }
content_id() { get "/groups/$G/artifacts/$1/versions/$2" | jq .contentId; }
# fetch <client> <globalId> [times]: what a serializer does, with the headers the Apicurio serdes send
fetch() {
  local i
  for i in $(seq 1 "${3:-1}"); do
    status "$API/ids/globalIds/$2" -H "X-Registry-Client-Id: $1" -H "X-Registry-Operation: DESERIALIZE" > /dev/null
  done
}
# clients <artifact>: per version, the clients usage telemetry recorded
clients() { get "/admin/usage/artifacts/$G/$1" | jq -r '[.versions[] | "\(.version):\(.clients | sort | join("+"))"] | join(" ")'; }
summary_active() { get /admin/usage/summary | jq .active; }

part "Before you start"
until curl -sf -o /dev/null "$API/system/info"; do sleep 2; done
check "the usage endpoints answer (telemetry is enabled)" 200 "$(status "$API/admin/usage/summary")"
active_before=$(summary_active)

# ------------------------------------------------------------------------------ step 1
part "Step 1: three versions of user-event, and the applications that read them"
version user-event 1 "$(record UserEvent userId)" > /dev/null
version user-event 2 "$(record UserEvent userId action)" > /dev/null
version user-event 3 "$(record UserEvent userId action at)" > /dev/null
V1=$(global_id user-event 1); V2=$(global_id user-event 2); V3=$(global_id user-event 3)
fetch payments-svc "$V3" 20
fetch fraud-detector "$V2"
fetch analytics "$V1"
fetch audit-log "$V1"
check "each version lists the applications that fetched it" "1:analytics+audit-log 2:fraud-detector 3:payments-svc" \
  "$(eventually 30 "1:analytics+audit-log 2:fraud-detector 3:payments-svc" clients user-event)"
check "20 fetches by one application within a minute are one event (deduplicated)" 1 \
  "$(get "/admin/usage/artifacts/$G/user-event" | jq '.versions[] | select(.version == "3") | .totalFetches')"
check "a version fetched in the last 7 days is ACTIVE" "ACTIVE ACTIVE ACTIVE" \
  "$(get "/admin/usage/artifacts/$G/user-event" | jq -r '[.versions[].classification] | join(" ")')"

# ------------------------------------------------------------------------------ step 2
part "Step 2: what is recorded, and what is not"
version order-placed 1 "$(record OrderPlaced orderId)" > /dev/null
O1=$(global_id order-placed 1)
status "$API/ids/globalIds/$O1" > /dev/null
status "$API/ids/globalIds/$O1" -H 'X-Registry-Client-Id: not a valid id!' > /dev/null
status "$API/ids/contentIds/$(content_id order-placed 1)" -H 'X-Registry-Client-Id: by-content-id' > /dev/null
status "$API/groups/$G/artifacts/order-placed/versions/1/content" -H 'X-Registry-Client-Id: by-coordinates' > /dev/null
check "recorded: by content ID too; not: without a client ID, an invalid one, or by coordinates" "1:by-content-id" \
  "$(eventually 30 "1:by-content-id" clients order-placed)"

# ------------------------------------------------------------------------------ step 3
part "Step 3: version drift"
heatmap() { get "/admin/usage/artifacts/$G/user-event/heatmap"; }
check "versions behind the latest, per application" "analytics:2 audit-log:2 fraud-detector:1 payments-svc:0" \
  "$(heatmap | jq -r '[.consumers[] | "\(.clientId):\(.versionsBehind)"] | sort | join(" ")')"
check "a drift alert at 2 versions behind (apicurio.usage.drift-alert-threshold)" "analytics audit-log" \
  "$(heatmap | jq -r '[.consumers[] | select(.driftAlert) | .clientId] | sort | join(" ")')"

# ------------------------------------------------------------------------------ step 4
part "Step 4: is it safe to deprecate?"
readiness() { get "/admin/usage/artifacts/$G/user-event/versions/$1/deprecation-readiness"; }
check "version 1 is not: analytics and audit-log still use it" "false analytics,audit-log" \
  "$(readiness 1 | jq -r '"\(.safeToDeprecate) \([.activeConsumers[].clientId] | sort | join(","))"')"
version user-event 4 "$(record UserEvent userId action at source)" > /dev/null
check "version 4, which nobody uses yet, is" true "$(readiness 4 | jq .safeToDeprecate)"
check "drift counts from the newest version anyone fetched: version 4 changes nothing yet" "analytics:2 payments-svc:0" \
  "$(heatmap | jq -r '[.consumers[] | select(.clientId == "analytics" or .clientId == "payments-svc") | "\(.clientId):\(.versionsBehind)"] | sort | join(" ")')"
check "deprecate, then sunset version 1 (SUNSET: the migration deadline has passed)" "204 204 SUNSET" \
  "$(status -X PUT "$API/groups/$G/artifacts/user-event/versions/1/state" -H 'Content-Type: application/json' -d '{"state": "DEPRECATED"}') $(status -X PUT "$API/groups/$G/artifacts/user-event/versions/1/state" -H 'Content-Type: application/json' -d '{"state": "SUNSET"}') $(get "/groups/$G/artifacts/user-event/versions/1" | jq -r .state)"

# ------------------------------------------------------------------------------ step 5
part "Step 5: the summary, and references"
version address 1 "$(record Address city)" > /dev/null
version customer 1 "$(jq -nc '{type: "record", name: "Customer", namespace: "com.example", fields: [{name: "home", type: "com.example.Address"}]}')" \
  "[{\"groupId\": \"$G\", \"artifactId\": \"address\", \"version\": \"1\", \"name\": \"com.example.Address\"}]" > /dev/null
fetch crm "$(global_id customer 1)"
# This run made 6 versions ACTIVE: user-event 1-3, order-placed 1, customer 1, and address 1, which
# nobody fetched but customer references
expected=$((active_before + 6))
check "the summary counts every version fetched recently, and the versions they reference" "$expected" \
  "$(eventually 60 "$expected" summary_active)"
check "  but address itself lists no usage: it was never fetched" 0 \
  "$(get "/admin/usage/artifacts/$G/address" | jq '.versions | length')"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed. The console is at http://localhost:${UI_PORT:-8888}."

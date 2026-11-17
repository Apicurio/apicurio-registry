#!/usr/bin/env bash
# Checks the switches that bound what users can do with a registry: limits, disabled APIs, the
# deletion switches and read-only mode, on two replicas sharing one database.
# Usage: ./demo.sh            KEEP=1 ./demo.sh   (leave the stack running)
# Requires Docker (or Podman) with Compose v2, curl and jq. Starts from an empty database and removes
# it at the end (the limits count what exists), so it is safe to re-run. Ends with
# "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

A=http://localhost:${REGISTRY_PORT:-8081}
B=http://localhost:${REGISTRY_B_PORT:-8082}
V3=/apis/registry/v3
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

# call <method> <url> [body]: "<status> <title>" (the title is the error message)
call() {
  local out data=()
  [ -n "${3:-}" ] && data=(-d "$3")
  out=$(curl -s --max-time 30 -w '\n%{http_code}' -X "$1" "$2" -H 'Content-Type: application/json' "${data[@]}" || true)
  echo "$(tail -1 <<< "$out") $(sed '$d' <<< "$out" | jq -r '.title // empty' 2>/dev/null)"
}
code() { call "$@" | cut -d' ' -f1; }
# artifact <id> [extra JSON merged into the request]: an Avro artifact with one version
artifact() {
  jq -nc --arg a "$1" --argjson x "${2:-{\}}" \
    '{artifactId: $a, artifactType: "AVRO", firstVersion: {content: {content: "\"string\"", contentType: "application/json"}}} + $x'
}
version() { jq -nc --arg c "$1" '{content: {content: $c, contentType: "application/json"}}'; }
set_property() { code PUT "$1$V3/admin/config/properties/$2" "{\"value\": \"$3\"}"; }

trap '[ -n "${KEEP:-}" ] || docker compose down -v > /dev/null 2>&1 || true' EXIT

part "Start PostgreSQL and two registry replicas"
docker compose down -v > /dev/null 2>&1 || true
if ! docker compose up -d --wait > /tmp/guardrails-up.log 2>&1; then
  tail -20 /tmp/guardrails-up.log
  fail "the services did not start"
  exit 1
fi
ok "started"

# ------------------------------------------------------------------------------ step 1
part "Step 1: limits (apicurio.limits.config.*)"
check "a name longer than 20 characters is refused (409, with the limit in the message)" \
  "409 Maximum artifact name length exceeded (limit: 20 characters, actual: 27 characters)" \
  "$(call POST "$A$V3/groups/team/artifacts" "$(artifact too-long '{"name": "a-name-that-is-way-too-long"}')")"
big=$(jq -nc '{type: "record", name: "Big", fields: [range(40) | {name: "field\(.)", type: "string"}]}')
check "a schema larger than 512 bytes is refused" "409 yes" \
  "$(call POST "$A$V3/groups/team/artifacts" "$(artifact big "$(jq -nc --arg c "$big" '{firstVersion: {content: {content: $c, contentType: "application/json"}}}')")" \
     | awk '{print $1, ($0 ~ /Maximum size of artifact version exceeded \(limit: 512 bytes/ ? "yes" : "no")}')"
check "more than 2 labels are refused" "409 Maximum number of labels exceeded for this artifact (limit: 2, actual: 3)" \
  "$(call POST "$A$V3/groups/team/artifacts" "$(artifact labelled '{"labels": {"a": "1", "b": "2", "c": "3"}}')")"
check "a label longer than 32 bytes is refused" 409 \
  "$(code POST "$A$V3/groups/team/artifacts" "$(artifact long-label '{"labels": {"owner": "a-team-with-a-very-long-name-indeed"}}')")"
check "within the limits, an artifact is created" 200 "$(code POST "$A$V3/groups/team/artifacts" "$(artifact orders)")"
check "  a second version is accepted, a third is refused (2 versions per artifact)" "200 409" \
  "$(code POST "$A$V3/groups/team/artifacts/orders/versions" "$(version '"int"')") $(code POST "$A$V3/groups/team/artifacts/orders/versions" "$(version '"long"')")"
check "limits apply to updates too: a 42-character description is refused" 409 \
  "$(code PUT "$A$V3/groups/team/artifacts/orders" '{"description": "a description longer than forty characters"}')"
check "reads are never limited" 200 "$(code GET "$A$V3/groups/team/artifacts/orders/versions/branch=latest/content")"

# ------------------------------------------------------------------------------ step 2
part "Step 2: disabled APIs (apicurio.disable.apis)"
check "the Confluent-compatible API and the v2 API answer 404" "404 404" \
  "$(code GET "$A/apis/ccompat/v7/subjects") $(code GET "$A/apis/registry/v2/search/artifacts")"
check "the v3 API is unaffected" 200 "$(code GET "$A$V3/search/artifacts")"

# ------------------------------------------------------------------------------ step 3
part "Step 3: deletion is off by default, and switched on at runtime"
check "deleting an artifact: 405, not enabled" "405 Artifact deletion operation is not enabled." \
  "$(call DELETE "$A$V3/groups/team/artifacts/orders")"
check "switch it on through the admin API (no restart)" 204 "$(set_property "$A" apicurio.rest.deletion.artifact.enabled true)"
code POST "$A$V3/groups/team/artifacts" "$(artifact scratch)" > /dev/null
check "  the other replica picks it up from the database (here within 5 seconds)" 204 \
  "$(eventually 30 204 code DELETE "$B$V3/groups/team/artifacts/scratch")"
set_property "$A" apicurio.rest.deletion.artifact.enabled false > /dev/null

# ------------------------------------------------------------------------------ step 4
part "Step 4: read-only mode (apicurio.storage.read-only.enabled)"
check "switched on at runtime" 204 "$(set_property "$A" apicurio.storage.read-only.enabled true)"
check "writes are refused (409), reads still work" "409 200" \
  "$(code POST "$A$V3/groups" '{"groupId": "blocked"}') $(code GET "$A$V3/groups/team/artifacts/orders")"
check "the console is told why" "true storage" \
  "$(curl -s "$A$V3/system/uiConfig" | jq -r '"\(.features.readOnly) \(.features.readOnlyReason)"')"
check "the other replica follows" 409 "$(eventually 30 409 code POST "$B$V3/groups" '{"groupId": "blocked-b"}')"
check "  and can switch it off again: configuration is not data" 204 "$(set_property "$B" apicurio.storage.read-only.enabled false)"
check "writes work again on both" "200 200" \
  "$(eventually 30 200 code POST "$A$V3/groups" '{"groupId": "allowed-a"}') $(eventually 30 200 code POST "$B$V3/groups" '{"groupId": "allowed-b"}')"

# ------------------------------------------------------------------------------ step 5
part "Step 5: limits and replicas"
# The artifact counters are kept in each replica's memory, so each replica allows up to the limit
# (#11218)
for i in 1 2 3 4 5 6; do
  code POST "$A$V3/groups/fill/artifacts" "$(artifact "a$i")" > /dev/null
  code POST "$B$V3/groups/fill/artifacts" "$(artifact "b$i")" > /dev/null
done
total=$(curl -s "$A$V3/search/artifacts?limit=100" | jq .count)
check "two replicas together hold more than max-artifacts (6): the limit is per replica today (#11218)" yes \
  "$([ "$total" -gt 6 ] && echo yes || echo "no ($total)")"
echo "  info  $total artifacts with max-artifacts=6"
check "  each replica refuses once its own count reaches the limit" "409 409" \
  "$(code POST "$A$V3/groups/fill/artifacts" "$(artifact a-extra)") $(code POST "$B$V3/groups/fill/artifacts" "$(artifact b-extra)")"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

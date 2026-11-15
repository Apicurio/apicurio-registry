#!/usr/bin/env bash
# Adds two artifact types to Apicurio Registry, one implemented in Java (MARKDOWN) and one by a
# webhook service (CSV), and checks what each provider does: detection, validity and compatibility
# rules, canonical lookups; what the registry sends to the webhook; and what happens when it is down.
# Usage: ./demo.sh            KEEP=1 ./demo.sh   (leave the stack running)
# Requires Docker (or Podman) with Compose v2, curl and jq; builds the provider jar with build.sh
# (JDK 21) when it is missing. Starts from an empty registry and removes it at the end, so it is safe
# to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

API=http://localhost:${REGISTRY_PORT:-8080}/apis/registry/v3
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# call <method> <path> [body]: prints "<status> <body>"
call() {
  local out data=()
  [ -n "${3:-}" ] && data=(-d "$3")
  out=$(curl -s --max-time "${4:-30}" -w '\n%{http_code}' -X "$1" "$API$2" -H 'Content-Type: application/json' "${data[@]}" || true)
  echo "$(tail -1 <<< "$out") $(sed '$d' <<< "$out")"
}
status() { cut -d' ' -f1 <<< "$1"; }
body() { cut -d' ' -f2- <<< "$1"; }
# A version body from a file or a string, with the content type
version_of() { jq -nc --arg c "$1" --arg t "$2" '{content: {content: $c, contentType: $t}}'; }
artifact_of() { jq -nc --arg id "$1" --arg c "$2" --arg t "$3" '{artifactId: $id, firstVersion: {content: {content: $c, contentType: $t}}}'; }
causes() { jq -r '[.causes[]?.description] | join(" | ")' <<< "$1"; }
webhook_requests() { docker compose exec -T csv-webhook wget -qO- http://127.0.0.1:8080/requests; }

trap '[ -n "${KEEP:-}" ] || docker compose down -v > /dev/null 2>&1 || true' EXIT

part "Build the MARKDOWN providers and the registry image"
if [ ! -f target/custom-artifact-types.jar ]; then
  ./build.sh || { fail "build.sh"; exit 1; }
fi
ok "target/custom-artifact-types.jar"
docker compose down -v > /dev/null 2>&1 || true
if ! docker compose up -d --build --wait > /tmp/custom-types-up.log 2>&1; then
  tail -30 /tmp/custom-types-up.log
  fail "the services did not start"
  exit 1
fi
ok "the derived image is built (re-augmented with the jar) and started"

part "Step 1: the artifact types"
types=$(curl -s "$API/admin/config/artifactTypes" | jq -r '[.[].name] | sort | join(" ")')
check "MARKDOWN and CSV are artifact types" "yes yes" \
  "$(grep -qw MARKDOWN <<< "$types" && echo yes || echo no) $(grep -qw CSV <<< "$types" && echo yes || echo no)"
check "  next to the standard ones (includeStandardArtifactTypes)" "yes" \
  "$(grep -qw AVRO <<< "$types" && grep -qw OPENAPI <<< "$types" && echo yes || echo no)"

# ------------------------------------------------------------------------------ MARKDOWN (Java)
part "Step 2: MARKDOWN, implemented by Java classes in the image"
r=$(call POST /groups/docs/artifacts "$(artifact_of orders-guide "$(cat samples/getting-started.md)" text/markdown)")
check "created without an artifactType: the ContentAccepter detects MARKDOWN" "200 MARKDOWN" \
  "$(status "$r") $(body "$r" | jq -r .artifact.artifactType)"
call POST /groups/docs/artifacts/orders-guide/rules '{"ruleType": "VALIDITY", "config": "FULL"}' > /dev/null
r=$(call POST /groups/docs/artifacts/orders-guide/versions "$(version_of "$(cat samples/invalid-no-title.md)" text/markdown)")
check "VALIDITY: a document without a title is rejected (ContentValidator)" "400 yes" \
  "$(status "$r") $(grep -q 'level-1 heading' <<< "$(causes "$(body "$r")")" && echo yes || echo no)"
call POST /groups/docs/artifacts/orders-guide/rules '{"ruleType": "COMPATIBILITY", "config": "BACKWARD"}' > /dev/null
r=$(call POST /groups/docs/artifacts/orders-guide/versions "$(version_of "$(cat samples/getting-started-v2-removed-section.md)" text/markdown)")
check "COMPATIBILITY: removing a section is rejected (CompatibilityChecker)" "400 yes" \
  "$(status "$r") $(grep -q 'was removed' <<< "$(causes "$(body "$r")")" && echo yes || echo no)"
r=$(call POST /groups/docs/artifacts/orders-guide/versions "$(version_of "$(cat samples/getting-started-v2.md)" text/markdown)")
check "  adding one is accepted: version 2" "200 2" "$(status "$r") $(body "$r" | jq -r .version)"
r=$(call POST "/groups/docs/artifacts?ifExists=FIND_OR_CREATE_VERSION&canonical=true" \
  "$(artifact_of orders-guide "$(sed 's/$/   /' samples/getting-started.md)" text/markdown)")
check "the same document with trailing spaces is found as version 1, canonically (ContentCanonicalizer)" "200 1" \
  "$(status "$r") $(body "$r" | jq -r .version.version)"

# ------------------------------------------------------------------------------ CSV (webhook)
part "Step 3: CSV, implemented by a webhook service"
r=$(call POST /groups/data/artifacts "$(artifact_of customers "id,name,email" text/csv)")
check "created without an artifactType: the webhook accepts text/csv as CSV" "200 CSV" \
  "$(status "$r") $(body "$r" | jq -r .artifact.artifactType)"
call POST /groups/data/artifacts/customers/rules '{"ruleType": "VALIDITY", "config": "FULL"}' > /dev/null
r=$(call POST /groups/data/artifacts/customers/versions "$(version_of "id,name,name" text/csv)")
check "VALIDITY: a duplicate column is rejected by the webhook" "400 Duplicate column 'name'" \
  "$(status "$r") $(causes "$(body "$r")")"
call POST /groups/data/artifacts/customers/rules '{"ruleType": "COMPATIBILITY", "config": "BACKWARD"}' > /dev/null
r=$(call POST /groups/data/artifacts/customers/versions "$(version_of "id,name" text/csv)")
check "COMPATIBILITY: dropping a column is rejected by the webhook" "400 Column 'email' was removed" \
  "$(status "$r") $(causes "$(body "$r")")"
r=$(call POST /groups/data/artifacts/customers/versions "$(version_of "id,name,email,phone" text/csv)")
check "  adding one is accepted: version 2" "200 2" "$(status "$r") $(body "$r" | jq -r .version)"
call PUT /groups/data/artifacts/customers/rules/COMPATIBILITY '{"ruleType": "COMPATIBILITY", "config": "FORWARD"}' > /dev/null
r=$(call POST /groups/data/artifacts/customers/versions "$(version_of "id,name,email,phone,fax" text/csv)")
check "  the webhook receives the level: with FORWARD, adding a column is rejected instead" "400 Column 'fax' was added" \
  "$(status "$r") $(causes "$(body "$r")")"
call PUT /groups/data/artifacts/customers/rules/COMPATIBILITY '{"ruleType": "COMPATIBILITY", "config": "BACKWARD"}' > /dev/null
r=$(call POST "/groups/data/artifacts?ifExists=FIND_OR_CREATE_VERSION&canonical=true" \
  "$(artifact_of customers "ID,Name,Email" text/csv)")
check "different case is found as version 1, canonically (the webhook lower-cases the columns)" "200 1" \
  "$(status "$r") $(body "$r" | jq -r .version.version)"
paths=$(webhook_requests | jq -r '[.[].path] | unique | join(" ")')
check "the registry called each provider's URL" "/accept /canonicalize /compatibility /validate" "$paths"
# artifact-types.json configures "headers" for every webhook, and the documentation says they are
# sent, for example for authentication; the registry sends none today (#11151)
check "the configured Authorization header is not sent today (should be, #11151)" "0" \
  "$(webhook_requests | jq '[.[] | select(.authorization == "Bearer csv-webhook-token")] | length')"

part "Step 4: when the webhook is down"
call POST /groups/data/artifacts "$(artifact_of orders "id,total" text/csv)" > /dev/null
call POST /groups/data/artifacts/orders/rules '{"ruleType": "VALIDITY", "config": "FULL"}' > /dev/null
docker compose stop csv-webhook > /dev/null 2>&1
# Each call below waits about a minute for the unreachable webhook (#11152)
r=$(call POST /groups/data/artifacts/orders/versions "$(version_of "id,id" text/csv)" 180)
check "VALIDITY fails open: an invalid version is accepted (should be rejected, #11152)" "200 2" \
  "$(status "$r") $(body "$r" | jq -r .version)"
r=$(call POST /groups/data/artifacts/customers/versions "$(version_of "id" text/csv)" 180)
check "COMPATIBILITY fails closed: the change is rejected" 400 "$(status "$r")"
check "  but the message exposes the exception (#11152)" yes \
  "$(grep -q 'UnknownHostException' <<< "$(causes "$(body "$r")")" && echo yes || echo no)"
check "the registry logs each failure" yes \
  "$(logs=$(docker compose logs apicurio-registry 2>&1); grep -q 'Error invoking webhook' <<< "$logs" && echo yes || echo no)"
docker compose start csv-webhook > /dev/null 2>&1

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

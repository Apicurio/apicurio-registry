#!/usr/bin/env bash
# Creates the sample data and checks every rule behaviour shown in GUIDE.md, against the running example.
# Usage: ./demo.sh   (after `docker compose up -d`; requires curl, jq and JDK 21+)
#        SKIP_JAVA=1 ./demo.sh   skips step 13, the Java validation client (then no JDK is needed)
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
# Safe to run again: it starts by deleting the example's groups and global rules.
set -euo pipefail
cd "$(dirname "$0")"

API=${API:-http://localhost:8081/apis}
V3="$API/registry/v3"
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }

# check <description> <expected> <actual>
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

# call <method> <path> [json body]: sets $status and $body (the response)
call() {
  local out
  out=$(curl -s --max-time 30 -w '\n%{http_code}' -X "$1" "$V3$2" \
    ${3:+-H 'Content-Type: application/json' -d "$3"})
  status=${out##*$'\n'}
  body=${out%$'\n'*}
}

# version_body <schema file> [artifact type]: a CreateVersion request for a schema file
version_body() {
  jq -n --rawfile c "schemas/$1" '{content: {content: $c, contentType: "application/json"}}'
}

# artifact_body <artifactId> <type> <schema file>: a CreateArtifact request with a first version
artifact_body() {
  jq -n --arg a "$1" --arg t "$2" --rawfile c "schemas/$3" \
    '{artifactId: $a, artifactType: $t, firstVersion: {content: {content: $c, contentType: "application/json"}}}'
}

# rule <path prefix> <type> <config>: creates a rule (global prefix is /admin)
rule() { call POST "$1/rules" "{\"ruleType\": \"$2\", \"config\": \"$3\"}"; }

# expect_violation <description> <rule type>: checks the last response is a rule violation
expect_violation() {
  check "$1: status" 400 "$status"
  check "$1: rule" "$2" "$(jq -r '.ruleViolation.ruleType' <<< "$body" 2>/dev/null)"
}

version_count() { call GET "/groups/$1/artifacts/$2/versions"; jq -r .count <<< "$body"; }

# wait_for <name> <url>: polls every 2s, gives up after WAIT_TIMEOUT seconds (default 180)
wait_for() {
  local deadline=$((SECONDS + ${WAIT_TIMEOUT:-180}))
  until curl -sf "$2" > /dev/null; do
    if [ "$SECONDS" -ge "$deadline" ]; then
      echo "Timed out waiting for $1 at $2 (is 'docker compose up' running?)" >&2
      exit 1
    fi
    sleep 2
  done
}

echo "Waiting for Registry..."
wait_for Registry "$V3/system/info"

echo "Resetting the example data..."
for g in orders sandbox; do call DELETE "/groups/$g"; done
call DELETE /admin/rules
for g in orders sandbox; do call POST /groups "{\"groupId\": \"$g\"}"; done

# ------------------------------------------------------------------------------ step 4
part "Step 4: a validity rule rejects content that is not valid"
call POST /groups/orders/artifacts "$(artifact_body broken JSON invalid.json)"
check "without a rule, invalid JSON Schema is accepted" 200 "$status"
call DELETE /groups/orders/artifacts/broken
rule /admin VALIDITY FULL
check "global VALIDITY=FULL created" 204 "$status"
call POST /groups/orders/artifacts "$(artifact_body broken JSON invalid.json)"
expect_violation "invalid JSON Schema" VALIDITY
call GET /groups/orders/artifacts/broken
check "the rejected artifact was not created" 404 "$status"

# ------------------------------------------------------------------------------ step 5
part "Step 5: BACKWARD compatibility (new readers must read old data)"
call POST /groups/orders/artifacts "$(artifact_body order-placed AVRO order-placed-v1.avsc)"
check "order-placed version 1 created" 200 "$status"
rule /groups/orders/artifacts/order-placed COMPATIBILITY BACKWARD
check "artifact COMPATIBILITY=BACKWARD created" 204 "$status"
call POST /groups/orders/artifacts/order-placed/versions "$(version_body order-placed-v2-no-default.avsc)"
expect_violation "adding a field without a default" COMPATIBILITY
check "the cause names the field" "amount at /fields/1" \
  "$(jq -r '.ruleViolation.causes[0] | "\(.description) at \(.context)"' <<< "$body")"
call POST /groups/orders/artifacts/order-placed/versions "$(version_body order-placed-v2.avsc)"
check "adding the field with a default is accepted" 200 "$status"
check "order-placed now has 2 versions" 2 "$(version_count orders order-placed)"

# ------------------------------------------------------------------------------ step 6
part "Step 6: FORWARD compatibility (old readers must read new data)"
call POST /groups/orders/artifacts "$(artifact_body order-cancelled AVRO order-cancelled-v1.avsc)"
rule /groups/orders/artifacts/order-cancelled COMPATIBILITY FORWARD
call POST /groups/orders/artifacts/order-cancelled/versions "$(version_body order-cancelled-add-field.avsc)"
check "FORWARD accepts a new field without a default" 200 "$status"
call POST /groups/orders/artifacts/order-cancelled/versions "$(version_body order-cancelled-remove-field.avsc)"
expect_violation "FORWARD: removing a field old readers need" COMPATIBILITY

# ------------------------------------------------------------------------------ step 7
part "Step 7: the most specific rule wins, per rule type"
rule /admin COMPATIBILITY BACKWARD
check "global COMPATIBILITY=BACKWARD created" 204 "$status"
rule /groups/sandbox COMPATIBILITY NONE
check "group sandbox COMPATIBILITY=NONE created" 204 "$status"
call POST /groups/orders/artifacts "$(artifact_body order-paid AVRO order-placed-v1.avsc)"
call POST /groups/orders/artifacts/order-paid/versions "$(version_body order-placed-v2-no-default.avsc)"
expect_violation "orders/order-paid uses the global rule" COMPATIBILITY
call POST /groups/sandbox/artifacts "$(artifact_body order-paid AVRO order-placed-v1.avsc)"
call POST /groups/sandbox/artifacts/order-paid/versions "$(version_body order-placed-v2-no-default.avsc)"
check "sandbox/order-paid: the group rule NONE wins" 200 "$status"
rule /groups/orders/artifacts/order-paid COMPATIBILITY NONE
call POST /groups/orders/artifacts/order-paid/versions "$(version_body order-placed-v2-no-default.avsc)"
check "orders/order-paid: an artifact rule NONE wins" 200 "$status"
call POST /groups/sandbox/artifacts "$(artifact_body still-validated JSON invalid.json)"
expect_violation "sandbox still has the global VALIDITY rule" VALIDITY

# ------------------------------------------------------------------------------ step 8
part "Step 8: dry run checks a change without saving it"
call POST "/groups/orders/artifacts/order-placed/versions?dryRun=true" "$(version_body order-placed-id-as-number.avsc)"
expect_violation "dry run of an incompatible change" COMPATIBILITY
call POST "/groups/orders/artifacts/order-placed/versions?dryRun=true" "$(version_body order-placed-v2.avsc)"
check "dry run of a compatible change" 200 "$status"
check "nothing was saved: still 2 versions" 2 "$(version_count orders order-placed)"

# ------------------------------------------------------------------------------ step 9
part "Step 9: transitive rules check every version that is not disabled"
call POST /groups/orders/artifacts "$(artifact_body customer AVRO customer-v1.avsc)"
call POST /groups/orders/artifacts/customer/versions "$(version_body customer-v2.avsc)"
check "customer version 2 created" 200 "$status"
rule /groups/orders/artifacts/customer COMPATIBILITY BACKWARD
call POST "/groups/orders/artifacts/customer/versions?dryRun=true" "$(version_body customer-v3.avsc)"
check "BACKWARD: version 3 only has to read version 2" 200 "$status"
call PUT /groups/orders/artifacts/customer/rules/COMPATIBILITY '{"config": "BACKWARD_TRANSITIVE"}'
call POST "/groups/orders/artifacts/customer/versions?dryRun=true" "$(version_body customer-v3.avsc)"
expect_violation "BACKWARD_TRANSITIVE: version 3 cannot read version 1" COMPATIBILITY
call PUT /groups/orders/artifacts/customer/versions/1/state '{"state": "DEPRECATED"}'
call POST "/groups/orders/artifacts/customer/versions?dryRun=true" "$(version_body customer-v3.avsc)"
expect_violation "a DEPRECATED version still counts" COMPATIBILITY
call PUT /groups/orders/artifacts/customer/versions/1/state '{"state": "DISABLED"}'
call POST /groups/orders/artifacts/customer/versions "$(version_body customer-v3.avsc)"
check "a DISABLED version does not count" 200 "$status"

# ------------------------------------------------------------------------------ step 10
part "Step 10: drafts skip the rules until they are published"
# BACKWARD_TRANSITIVE, not BACKWARD: with a non-transitive rule, publishing compares the draft with
# itself and passes (https://github.com/Apicurio/apicurio-registry/issues/11001).
call POST /groups/orders/artifacts "$(artifact_body order-returned AVRO order-placed-v1.avsc)"
rule /groups/orders/artifacts/order-returned COMPATIBILITY BACKWARD_TRANSITIVE
call POST /groups/orders/artifacts/order-returned/versions \
  "$(jq -n --rawfile c schemas/order-placed-id-as-number.avsc '{version: "draft-1", isDraft: true, content: {content: $c, contentType: "application/json"}}')"
check "an incompatible draft is saved" 200 "$status"
check "its state is DRAFT" DRAFT "$(jq -r .state <<< "$body")"
call PUT /groups/orders/artifacts/order-returned/versions/draft-1/state '{"state": "ENABLED"}'
expect_violation "publishing the draft runs the rules" COMPATIBILITY
call GET /groups/orders/artifacts/order-returned/versions/draft-1
check "the draft stays a draft" DRAFT "$(jq -r .state <<< "$body")"

# ------------------------------------------------------------------------------ step 11
part "Step 11: an integrity rule checks references"
rule /groups/orders INTEGRITY FULL
check "group INTEGRITY=FULL created" 204 "$status"
shipment=$(jq -n --rawfile c schemas/shipment.json '{artifactId: "shipment", artifactType: "JSON",
  firstVersion: {content: {content: $c, contentType: "application/json",
    references: [{name: "address.json", groupId: "orders", artifactId: "address", version: "1"}]}}}')
call POST /groups/orders/artifacts "$shipment"
expect_violation "a reference to an artifact that does not exist" INTEGRITY
check "the cause names the reference" "address.json" "$(jq -r '.ruleViolation.causes[0].context' <<< "$body")"
call POST /groups/orders/artifacts "$(artifact_body address JSON address.json)"
check "address created" 200 "$status"
call POST /groups/orders/artifacts "$shipment"
check "the same shipment is accepted once address exists" 200 "$status"

# ------------------------------------------------------------------------------ step 12
part "Step 12: the rules in effect"
call GET /admin/rules
check "global rules" "COMPATIBILITY,VALIDITY" "$(jq -r 'sort | join(",")' <<< "$body")"
call GET /groups/orders/rules
check "group orders rules" "INTEGRITY" "$(jq -r 'sort | join(",")' <<< "$body")"

# ------------------------------------------------------------------------------ step 13
part "Step 13: validating data on the client"
if [ -n "${SKIP_JAVA:-}" ]; then
  echo "  skip  the validation client (SKIP_JAVA is set)"
else
  if [ ! -d validation-client/target/classes ]; then
    echo "  building the validation client (first run only, a few minutes)..."
    log=$(mktemp)
    ./build.sh > "$log" 2>&1 || { tail -40 "$log"; echo "build.sh failed; full log: $log" >&2; exit 1; }
  fi
  if out=$(../../mvnw -q exec:java -f validation-client -Dregistry.url="$V3" 2>&1); then
    grep -E '^  ok ' <<< "$out" | sed 's/^  ok    /  ok    client: /'
    ok "the validation client's checks passed"
  else
    grep -E '^  (ok|FAIL) ' <<< "$out" || tail -20 <<< "$out"
    fail "the validation client's checks failed"
  fi
fi

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

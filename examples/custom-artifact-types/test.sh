#!/usr/bin/env bash
# Builds the provider jar and the derived registry image, then exercises the MARKDOWN artifact
# type through the REST API. Requires Maven, Docker (compose), curl and jq.
#
#   ./test.sh                                                        # latest-snapshot-mutable base image
#   REGISTRY_IMAGE=apicurio/apicurio-registry:3.4.0-mutable ./test.sh
set -euo pipefail
cd "$(dirname "$0")"

# Use the default (docker driver) builder so that a locally built base image is visible;
# set BUILDX_BUILDER yourself to use a different one.
export BUILDX_BUILDER="${BUILDX_BUILDER:-default}"

API="http://localhost:8080/apis/registry/v3"
GROUP="docs"
ARTIFACT="orders-getting-started"
TMP="$(mktemp)"
trap 'rm -f "$TMP"; docker compose down -v >/dev/null 2>&1 || true' EXIT

pass() { printf '  \033[32mPASS\033[0m %s\n' "$*"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; docker compose logs apicurio-registry | tail -50; exit 1; }

# request METHOD PATH [JSON-BODY] -> sets STATUS and BODY
request() {
  local method="$1" path="$2" data="${3:-}"
  if [ -n "$data" ]; then
    STATUS="$(curl -sS -o "$TMP" -w '%{http_code}' -X "$method" -H 'Content-Type: application/json' \
      --data-binary "$data" "$API$path")"
  else
    STATUS="$(curl -sS -o "$TMP" -w '%{http_code}' -X "$method" "$API$path")"
  fi
  BODY="$(cat "$TMP")"
}
expect() { [ "$STATUS" = "$1" ] || fail "$2 -> expected HTTP $1, got $STATUS: $BODY"; }
# rule violations are reported as HTTP 400 (compatibility) or 409 (validity / conflicts)
expect_violation() {
  case "$STATUS" in 400|409) ;; *) fail "$1 -> expected a rule violation (HTTP 400/409), got $STATUS: $BODY" ;; esac
  [ -z "$2" ] || echo "$BODY" | grep -q "$2" || fail "$1 -> violation does not mention '$2': $BODY"
}
create_artifact_body() { jq -n --rawfile c "$1" --arg id "$ARTIFACT" \
  '{artifactId: $id, firstVersion: {content: {content: $c, contentType: "text/markdown"}}}'; }
create_version_body() { jq -n --rawfile c "$1" '{content: {content: $c, contentType: "text/markdown"}}'; }
violations() { echo "$BODY" | jq -r '[.causes[]?.description] | join(" | ")' 2>/dev/null || echo "$BODY"; }

echo "Building the provider jar..."
mvn -q package -DskipTests

echo "Building and starting the registry (${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot-mutable})..."
docker compose up -d --build --wait apicurio-registry
for _ in $(seq 1 60); do curl -sf "$API/system/info" >/dev/null && break; sleep 2; done
curl -sf "$API/system/info" >/dev/null || fail "registry not reachable"

echo "1. MARKDOWN is a registered artifact type"
request GET /admin/config/artifactTypes
expect 200 "list artifact types"
echo "$BODY" | jq -e 'map(.name) | index("MARKDOWN")' >/dev/null || fail "MARKDOWN not in: $BODY"
pass "$(echo "$BODY" | jq -c 'map(.name)')"

echo "2. Artifact type is auto-detected (no artifactType in the request)"
request POST "/groups/$GROUP/artifacts" "$(create_artifact_body samples/getting-started.md)"
expect 200 "create artifact"
[ "$(echo "$BODY" | jq -r .artifact.artifactType)" = "MARKDOWN" ] || fail "unexpected type: $BODY"
pass "artifactType=MARKDOWN, version $(echo "$BODY" | jq -r .version.version)"

echo "3. Enable the VALIDITY=FULL rule; a document without a title is rejected (ContentValidator)"
request POST "/groups/$GROUP/artifacts/$ARTIFACT/rules" '{"ruleType":"VALIDITY","config":"FULL"}'
expect 204 "validity rule"
request POST "/groups/$GROUP/artifacts/$ARTIFACT/versions" "$(create_version_body samples/invalid-no-title.md)"
expect_violation "invalid document" "level-1 heading"
pass "$(violations)"

echo "4. Enable the COMPATIBILITY=BACKWARD rule; removing a section is rejected (CompatibilityChecker)"
request POST "/groups/$GROUP/artifacts/$ARTIFACT/rules" '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}'
expect 204 "compatibility rule"
request POST "/groups/$GROUP/artifacts/$ARTIFACT/versions" "$(create_version_body samples/getting-started-v2-removed-section.md)"
expect_violation "incompatible document" "was removed"
pass "$(violations)"

echo "5. Adding a section is accepted as version 2"
request POST "/groups/$GROUP/artifacts/$ARTIFACT/versions" "$(create_version_body samples/getting-started-v2.md)"
expect 200 "compatible document"
pass "version $(echo "$BODY" | jq -r .version)"

echo "6. Content with different whitespace matches version 1 when compared canonically (ContentCanonicalizer)"
sed 's/$/   /' samples/getting-started.md | tr -d '\r' > "$TMP.md"
request POST "/groups/$GROUP/artifacts?ifExists=FIND_OR_CREATE_VERSION&canonical=true" "$(create_artifact_body "$TMP.md")"
rm -f "$TMP.md"
expect 200 "find or create version"
V="$(echo "$BODY" | jq -r .version.version)"
[ "$V" = "1" ] || fail "expected the canonical lookup to resolve to version 1, got $V: $BODY"
pass "resolved to version $V"

echo
echo "All checks passed. UI: http://localhost:8888 (run 'docker compose down' to stop)."
trap 'rm -f "$TMP"' EXIT

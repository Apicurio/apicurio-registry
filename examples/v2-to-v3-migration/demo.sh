#!/usr/bin/env bash
# Seeds an Apicurio Registry 2.6 server, migrates it to 3.x with the documented export and import
# scripts, and checks that nothing changed: every version with its globalId, contentId, state, content
# and references, the metadata, the rules, and the v2 API on the new server.
# Usage: ./demo.sh            KEEP=1 ./demo.sh   (leave the servers running)
# Requires Docker (or Podman) with Compose v2, curl, jq and unzip. Starts from empty servers and
# removes them at the end, so it is safe to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

V2=http://localhost:${V2_PORT:-8081}
V3=http://localhost:${V3_PORT:-8082}
V3_MANAGEMENT=http://localhost:${V3_MANAGEMENT_PORT:-9002}
V3_RENUM=http://localhost:${V3_RENUM_PORT:-8083}
V3_COPY=http://localhost:${V3_COPY_PORT:-8084}
SCRIPTS=../../docs/scripts
WORK=$(mktemp -d)
failures=0

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }
same() {
  if diff <(echo "$2") <(echo "$3") > "$WORK/diff"; then ok "$1"; else fail "$1:"; sed 's/^/          /' "$WORK/diff"; fi
}

cleanup() {
  [ -n "${KEEP:-}" ] || docker compose --profile kafkasql down -v > /dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

# ----------------------------------------------------------------------------------- v2 seeding
# v2 API: the artifact ID, type and version are headers; references need the extended content type
v2_create() { # group artifact type file [version] [references-json]
  local url="$V2/apis/registry/v2/groups/$1/artifacts" ct body
  case "$4" in *.yaml) ct=application/x-yaml ;; *.proto) ct=application/x-protobuf ;; *) ct=application/json ;; esac
  body=$(cat "seed/$4")
  if [ -n "${6:-}" ]; then
    ct=application/create.extended+json
    body=$(jq -n --arg c "$body" --argjson r "$6" '{content: $c, references: $r}')
  fi
  curl -sf -o /dev/null -X POST "$url" -H "Content-Type: $ct" -H "X-Registry-ArtifactId: $2" \
    -H "X-Registry-ArtifactType: $3" ${5:+-H "X-Registry-Version: $5"} --data-binary "$body"
}
v2_version() { # group artifact file version [references-json]
  local ct=application/json body
  body=$(cat "seed/$3")
  if [ -n "${5:-}" ]; then
    ct=application/create.extended+json
    body=$(jq -n --arg c "$body" --argjson r "$5" '{content: $c, references: $r}')
  fi
  curl -sf -o /dev/null -X POST "$V2/apis/registry/v2/groups/$1/artifacts/$2/versions" \
    -H "Content-Type: $ct" -H "X-Registry-Version: $4" --data-binary "$body"
}
v2() { curl -sf -o /dev/null -X "$1" "$V2/apis/registry/v2$2" -H 'Content-Type: application/json' -d "$3"; }

seed() {
  local address='[{"groupId": "common", "artifactId": "address", "version": "1", "name": "com.example.common.Address"}]'
  local money='[{"groupId": "common", "artifactId": "money", "version": "1", "name": "money.proto"}]'
  v2 POST /admin/rules '{"type": "VALIDITY", "config": "FULL"}'
  v2_create common address AVRO address.avsc 1
  v2_create orders customer AVRO customer.avsc 1 "$address"
  v2 POST /groups/orders/artifacts/customer/rules '{"type": "COMPATIBILITY", "config": "BACKWARD"}'
  v2_version orders customer customer-v2.avsc 2 "$address"
  v2 PUT /groups/orders/artifacts/customer/meta \
    '{"name": "Customer", "description": "A customer and their address", "labels": ["pii"], "properties": {"owner": "team-orders"}}'
  v2 PUT /groups/orders/artifacts/customer/versions/1/state '{"state": "DEPRECATED"}'
  v2_create common money PROTOBUF money.proto 1
  v2_create payments payment PROTOBUF payment.proto 1 "$money"
  v2_create billing invoice JSON invoice.json 1.0
  v2_version billing invoice invoice.json 1.1
  v2 PUT /groups/billing/artifacts/invoice/versions/1.1/state '{"state": "DISABLED"}'
  v2_create orders orders-api OPENAPI orders-api.yaml
  # No group, and the same content twice: one contentId shared by two artifacts
  v2_create default legacy AVRO legacy.avsc
  v2_create default legacy-copy AVRO legacy.avsc
}

# --------------------------------------------------------------------------------- the inventory
# inventory <base-url> <api>: one line per version, through the v2 or the v3 API:
#   group/artifact@version globalId contentId state sha256(content) [references]
inventory() {
  local base=$1/apis/registry/$2 versions
  if [ "$2" = v2 ]; then
    versions=$(curl -sf "$base/search/artifacts?limit=100" | jq -r '.artifacts[] | "\(.groupId // "default") \(.id)"' |
      while read -r g a; do
        curl -sf "$base/groups/$g/artifacts/$a/versions?limit=100" |
          jq -c --arg g "$g" --arg a "$a" '.versions[] | {g: $g, a: $a, v: .version, gid: .globalId, cid: .contentId, s: .state}'
      done)
  else
    versions=$(curl -sf "$base/search/versions?limit=100" |
      jq -c '.versions[] | {g: (.groupId // "default"), a: .artifactId, v: .version, gid: .globalId, cid: .contentId, s: .state}')
  fi
  echo "$versions" | while read -r line; do
    local gid sha refs
    gid=$(jq -r .gid <<< "$line")
    # A DISABLED version's content is not served by globalId (but see #11116)
    if [ "$(jq -r .s <<< "$line")" = DISABLED ]; then
      sha=disabled
    else
      sha=$(curl -sf "$base/ids/globalIds/$gid" | shasum -a 256 | cut -c1-12)
    fi
    refs=$(curl -sf "$base/ids/globalIds/$gid/references" | jq -c '[.[] | "\(.groupId)/\(.artifactId)@\(.version)"] | sort')
    jq -r --arg sha "$sha" --arg refs "$refs" '"\(.g)/\(.a)@\(.v) \(.gid) \(.cid) \(.s) \($sha) \($refs)"' <<< "$line"
  done | sort
}
rules() { # base-url: global rules, then each artifact's
  local base=$1/apis/registry/v3
  for r in $(curl -sf "$base/admin/rules" | jq -r '.[]'); do
    echo "global $r $(curl -sf "$base/admin/rules/$r" | jq -r .config)"
  done
  for r in $(curl -sf "$base/groups/orders/artifacts/customer/rules" | jq -r '.[]'); do
    echo "orders/customer $r $(curl -sf "$base/groups/orders/artifacts/customer/rules/$r" | jq -r .config)"
  done
}

# ------------------------------------------------------------------------------------------ run
part "Start a 2.6 server and three empty 3.x servers"
docker compose --profile kafkasql down -v > /dev/null 2>&1 || true
if ! docker compose up -d --wait > "$WORK/up.log" 2>&1; then
  tail -20 "$WORK/up.log"
  fail "the services did not start"
  exit 1
fi
check "registry-v2 is 2.6" 2.6.13.Final "$(curl -sf "$V2/apis/registry/v2/system/info" | jq -r .version)"
check "registry-v3 is 3.x" 3 "$(curl -sf "$V3/apis/registry/v3/system/info" | jq -r '.version | split(".")[0]')"

part "Seed v2: 8 artifacts, 10 versions, references, rules, metadata, states"
seed
BEFORE=$(inventory "$V2" v2)
check "v2 has 10 versions" 10 "$(echo "$BEFORE" | wc -l | tr -d ' ')"
echo "$BEFORE" | sed 's/^/  info  /'

part "Export from v2 and import into v3, with the documented scripts"
export AUTH_ENABLED=false SKIP_TLS_VERIFY=false
REGISTRY_URL=$V2 REGISTRY_API_VERSION=v2 EXPORT_FILE=$WORK/v2-export.zip \
  bash $SCRIPTS/apicurio-export-registry-data.sh > "$WORK/export.log" 2>&1 \
  && ok "apicurio-export-registry-data.sh" || { fail "apicurio-export-registry-data.sh"; tail "$WORK/export.log"; }
check "the export's manifest names the 2.6 server" 2.6.13.Final \
  "$(unzip -p "$WORK/v2-export.zip" '*.Manifest.json' 2>/dev/null | jq -r .systemVersion)"
REGISTRY_URL=$V3 REGISTRY_API_VERSION=v3 IMPORT_FILE=$WORK/v2-export.zip \
  bash $SCRIPTS/apicurio-import-registry-data.sh > "$WORK/import.log" 2>&1 < /dev/null \
  && ok "apicurio-import-registry-data.sh" || { fail "apicurio-import-registry-data.sh"; tail "$WORK/import.log"; }
REGISTRY_URL=$V3 MANAGEMENT_URL=$V3_MANAGEMENT REGISTRY_VERSION=v3 \
  bash $SCRIPTS/apicurio-verify-registry-version.sh > "$WORK/verify.log" 2>&1 < /dev/null \
  && ok "apicurio-verify-registry-version.sh" || { fail "apicurio-verify-registry-version.sh"; tail "$WORK/verify.log"; }

part "Parity: v3 holds exactly what v2 held"
same "every version: coordinates, globalId, contentId, state, content, references" "$BEFORE" "$(inventory "$V3" v3)"
same "the same, through the v2 API that 3.x still serves" "$BEFORE" "$(inventory "$V3" v2)"
check "content shared by two artifacts keeps one contentId" 1 \
  "$(inventory "$V3" v3 | awk '/^default\/legacy/ {print $3}' | sort -u | wc -l | tr -d ' ')"
check "the rules" "global VALIDITY FULL
orders/customer COMPATIBILITY BACKWARD" "$(rules "$V3")"
check "v2 labels and properties become v3 labels" '{"owner":"team-orders","pii":null}' \
  "$(curl -sf "$V3/apis/registry/v3/groups/orders/artifacts/customer" | jq -c '.labels | to_entries | sort_by(.key) | from_entries')"
check "the artifact's name and description" "Customer|A customer and their address" \
  "$(curl -sf "$V3/apis/registry/v3/groups/orders/artifacts/customer" | jq -r '"\(.name)|\(.description)"')"
check "every artifact has a 'latest' branch, in version order" "1.0 1.1" \
  "$(curl -sf "$V3/apis/registry/v3/groups/billing/artifacts/invoice/branches/latest/versions" | jq -r '[.versions[].version] | reverse | join(" ")')"
check "the latest version skips the DISABLED one, as in v2" 1.0 \
  "$(curl -sf "$V3/apis/registry/v3/groups/billing/artifacts/invoice/versions/branch=latest" | jq -r .version)"
check "a serializer's globalId resolves to the same schema (Confluent API, by contentId)" "$(cat seed/address.avsc | jq -c .)" \
  "$(curl -sf "$V3/apis/ccompat/v7/schemas/ids/$(curl -sf "$V2/apis/registry/v2/groups/common/artifacts/address/meta" | jq .contentId)" | jq -r .schema | jq -c .)"
incompatible() { # base-url: POST an incompatible version of orders/customer, print the status
  curl -s -o /dev/null -w '%{http_code}' -X POST "$1/apis/registry/v3/groups/orders/artifacts/customer/versions" \
    -H 'Content-Type: application/json' -d "$(jq -n --arg c "$(cat seed/customer-incompatible.avsc)" \
      --argjson r '[{"groupId": "common", "artifactId": "address", "version": "1", "name": "com.example.common.Address"}]' \
      '{content: {content: $c, contentType: "application/json", references: $r}}')"
}
check "the rules still apply: an incompatible version is rejected, with 400 (v2 returned 409)" 400 "$(incompatible "$V3")"

part "Import guards"
check "the import script stops on a registry that is not empty" 1 \
  "$(REGISTRY_URL=$V3 REGISTRY_API_VERSION=v3 IMPORT_FILE=$WORK/v2-export.zip bash $SCRIPTS/apicurio-import-registry-data.sh \
     > /dev/null 2>&1 < /dev/null; echo $?)"
check "  and so does the registry: 409" 409 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$V3/apis/registry/v3/admin/import" -H 'Content-Type: application/zip' --data-binary "@$WORK/v2-export.zip")"

part "Import with new IDs, into a registry that already has data"
# Content that is not in the export: content that is (the same bytes) would be linked to the wrong
# versions (https://github.com/Apicurio/apicurio-registry/issues/11115)
curl -sf -o /dev/null -X POST "$V3_RENUM/apis/registry/v3/groups/existing/artifacts" -H 'Content-Type: application/json' \
  -d "$(jq -n --arg c '{"type": "object", "title": "AlreadyHere"}' '{artifactId: "already-here", artifactType: "JSON", firstVersion: {content: {content: $c, contentType: "application/json"}}}')"
check "import with X-Registry-Preserve-GlobalId/ContentId: false and requireEmptyRegistry=false" 204 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$V3_RENUM/apis/registry/v3/admin/import?requireEmptyRegistry=false" \
     -H 'Content-Type: application/zip' -H 'X-Registry-Preserve-GlobalId: false' -H 'X-Registry-Preserve-ContentId: false' \
     --data-binary "@$WORK/v2-export.zip")"
RENUM=$(inventory "$V3_RENUM" v3 | grep -v '^existing/')
strip_ids() { awk '{$2 = ""; $3 = ""; print}'; }
same "the same versions, states, content and references" "$(echo "$BEFORE" | strip_ids)" "$(echo "$RENUM" | strip_ids)"
check "but new globalIds: a payload serialized against v2 would find the wrong schema" 0 \
  "$(comm -12 <(echo "$BEFORE" | awk '{print $1, $2}') <(echo "$RENUM" | awk '{print $1, $2}') | wc -l | tr -d ' ')"

part "Backup and restore within 3.x: export v3, import into an empty 3.x"
curl -sf -o "$WORK/v3-export.zip" "$V3/apis/registry/v3/admin/export" && ok "export from v3" || fail "export from v3"
check "import into registry-v3-copy" 204 \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$V3_COPY/apis/registry/v3/admin/import" -H 'Content-Type: application/zip' --data-binary "@$WORK/v3-export.zip")"
same "identical to the original" "$BEFORE" "$(inventory "$V3_COPY" v3)"
same "including the rules" "$(rules "$V3")" "$(rules "$V3_COPY")"
curl -sf -o /dev/null -X PUT "$V3_COPY/apis/registry/v3/groups/billing/artifacts/invoice/versions/1.1/state" \
  -H 'Content-Type: application/json' -d '{"state": "ENABLED"}'
# 1.1 has the same content as 1.0
check "a DISABLED version kept its content: re-enabled, it is served" "$(awk '/^billing\/invoice@1.0 / {print $5}' <<< "$BEFORE")" \
  "$(curl -sf "$V3_COPY/apis/registry/v3/groups/billing/artifacts/invoice/versions/1.1/content" | shasum -a 256 | cut -c1-12)"

part "KafkaSQL: 3.x does not reuse a 2.6 journal topic"
docker compose stop registry-v3 registry-v3-renum registry-v3-copy > /dev/null 2>&1
docker compose --profile kafkasql up -d --wait registry-v2-kafkasql > "$WORK/ks.log" 2>&1 || { fail "kafkasql: 2.6 did not start"; tail "$WORK/ks.log"; }
v3_kafkasql_refuses() { # the reason expected in the log
  docker rm -f migration-registry-v3-kafkasql > /dev/null 2>&1 || true
  docker compose --profile kafkasql up -d --no-deps registry-v3-kafkasql > /dev/null 2>&1
  for _ in $(seq 1 90); do
    [ "$(docker inspect -f '{{.State.Status}}' migration-registry-v3-kafkasql 2>/dev/null)" = exited ] && break
    sleep 2
  done
  echo "$(docker inspect -f '{{.State.Status}}' migration-registry-v3-kafkasql 2>/dev/null)" \
    "$(docker logs migration-registry-v3-kafkasql 2>&1 | grep -c "$1" | awk '{print ($1 > 0)}')"
}
check "3.x on the 2.6 topic stops: 2.6 made it compacted, 3.x requires no compaction or retention" "exited 1" \
  "$(v3_kafkasql_refuses "Topic must not have 'cleanup.policy=compact'")"
docker compose --profile kafkasql exec -T kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9092 --alter \
  --entity-type topics --entity-name kafkasql-journal --add-config cleanup.policy=delete,retention.ms=-1,retention.bytes=-1 \
  > /dev/null 2>&1
check "with the topic reconfigured, 3.x still stops: the records are v2's" "exited 1" \
  "$(v3_kafkasql_refuses "contains records from Apicurio Registry v2")"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

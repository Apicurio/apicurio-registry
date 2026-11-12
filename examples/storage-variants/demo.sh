#!/usr/bin/env bash
# Runs the same registry on each storage variant, one at a time, and checks what each one guarantees.
# Usage: ./demo.sh                      every variant (mssql only on amd64)
#        VARIANTS="gitops" ./demo.sh    some of them: postgresql mysql mssql kafkasql gitops kubernetesops
# Requires Docker (or Podman) with Compose v2, curl, jq and git. Each variant is started with
# `docker compose --profile <variant> up`, checked, and removed with its data, so it is safe to re-run.
# Prints one ok/FAIL line per check, ends with "All checks passed." and exits non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

# In CI, each job runs one variant (ci.env: CI_MATRIX)
[ -n "${CI_VARIANT:-}" ] && VARIANTS=$CI_VARIANT
if [ -z "${VARIANTS:-}" ]; then
  VARIANTS="postgresql mysql kafkasql gitops kubernetesops"
  case "$(uname -m)" in x86_64 | amd64) VARIANTS="postgresql mysql mssql kafkasql gitops kubernetesops" ;; esac
fi
A=${API:-http://localhost:8081/apis}/registry/v3
B=${API_B:-http://localhost:8082/apis}/registry/v3
failures=0
current=""

ok()   { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; failures=$((failures + 1)); }
part() { printf '\n%s\n' "$1"; }
check() { if [[ "$3" == "$2" ]]; then ok "$1"; else fail "$1: got '$3', expected '$2'"; fi; }

get() { curl -s --max-time 30 "$1"; }
code() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$1" "$2" ${3:+-H 'Content-Type: application/json' -d "$3"}; }
compose() { docker compose --profile "$current" "$@"; }

# version_body <schema file>: a CreateVersion request
version_body() { jq -n --rawfile c "gitops-seed/$1" '{content: {content: $c, contentType: "application/json"}}'; }
versions() { get "$1/groups/orders/artifacts/order-placed/versions" | jq -r '[.versions[]?.version] | join(",")' 2>/dev/null; }

# eventually <seconds> <expected> <command...>: polls the command until it prints <expected>
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

# up <variant>: starts a variant's services and waits for them to be healthy
up() {
  current=$1
  # Start clean, whatever was running before (in CI, the runner may have started this profile)
  compose down -v > /dev/null 2>&1 || true
  if ! compose up -d --wait > /tmp/storage-up.log 2>&1; then
    tail -20 /tmp/storage-up.log
    compose logs --no-color --tail 40
    fail "$current: the services did not start"
    return 1
  fi
  # Healthy inside the container is not always reachable from the host yet (port forwarding)
  local deadline=$((SECONDS + 60))
  until curl -sf --max-time 5 -o /dev/null "$A/system/info"; do
    [ "$SECONDS" -ge "$deadline" ] && { fail "$current: the registry is not reachable at $A"; return 1; }
    sleep 1
  done
}
down() {
  if [ -n "$current" ]; then compose down -v > /dev/null 2>&1 || true; fi
  current=""
}
# KEEP=1 leaves the last variant running (for screenshots, or to look around)
trap '[ -n "${KEEP:-}" ] || down' EXIT

# restart <service> <base>: restarts a registry and waits until it answers again
restart() {
  compose restart "$1" > /dev/null 2>&1
  local deadline=$((SECONDS + 180))
  until curl -sf --max-time 5 -o /dev/null "$2/system/info"; do
    [ "$SECONDS" -ge "$deadline" ] && { fail "$1 did not come back"; return 1; }
    sleep 2
  done
}

# writable_checks <base>: the checks every writable variant passes
writable_checks() {
  local base=$1
  check "create orders/order-placed version 1, with a BACKWARD rule" 200 \
    "$(code POST "$base/groups/orders/artifacts" "$(jq -n --rawfile c gitops-seed/order-placed-v1.avsc \
      '{artifactId: "order-placed", artifactType: "AVRO", firstVersion: {content: {content: $c, contentType: "application/json"}}}')")"
  code POST "$base/groups/orders/artifacts/order-placed/rules" '{"ruleType": "COMPATIBILITY", "config": "BACKWARD"}' > /dev/null
  check "an incompatible version is rejected by the rule" 400 \
    "$(code POST "$base/groups/orders/artifacts/order-placed/versions" "$(version_body order-placed-v2-incompatible.avsc)")"
  check "a compatible version 2 is accepted" 200 \
    "$(code POST "$base/groups/orders/artifacts/order-placed/versions" "$(version_body order-placed-v2.avsc)")"
  check "search finds it" 1 "$(get "$base/search/artifacts?name=order-placed&groupId=orders" | jq -r .count)"
  check "read-only mode switched on at runtime" 204 \
    "$(code PUT "$base/admin/config/properties/apicurio.storage.read-only.enabled" '{"value": "true"}')"
  check "  then writes are refused" 409 "$(code POST "$base/groups" '{"groupId": "blocked"}')"
  check "  reads still work" "1,2" "$(versions "$base")"
  code PUT "$base/admin/config/properties/apicurio.storage.read-only.enabled" '{"value": "false"}' > /dev/null
  check "read-only off again: writes work" 200 "$(code POST "$base/groups" '{"groupId": "allowed"}')"
}

# ------------------------------------------------------------------------------------ postgresql
run_postgresql() {
  part "PostgreSQL: two registries, one database"
  up postgresql || return 0
  writable_checks "$A"
  check "the other replica sees every write at once" "1,2" "$(versions "$B")"
  check "the schema version is recorded in the database" 113 \
    "$(compose exec -T postgres psql -U registry -d registry -tAc "select propvalue from apicurio where propname = 'db_version'")"
  restart registry-pg-a "$A"
  check "after a restart, the data is still there" "1,2" "$(eventually 60 "1,2" versions "$A")"
  down
}

# ----------------------------------------------------------------------------------------- mysql
run_mysql() {
  part "MySQL"
  up mysql || return 0
  writable_checks "$A"
  check "the schema version is recorded in the database" 113 \
    "$(compose exec -T mysql mysql -uregistry -pregistry registry -sNe "select propValue from apicurio where propName = 'db_version'" 2>/dev/null)"
  restart registry-mysql "$A"
  check "after a restart, the data is still there" "1,2" "$(eventually 60 "1,2" versions "$A")"
  down
}

# ----------------------------------------------------------------------------------------- mssql
run_mssql() {
  part "SQL Server"
  up mssql || return 0
  writable_checks "$A"
  restart registry-mssql "$A"
  check "after a restart, the data is still there" "1,2" "$(eventually 60 "1,2" versions "$A")"
  down
}

# -------------------------------------------------------------------------------------- kafkasql
run_kafkasql() {
  part "KafkaSQL: two registries, one journal topic"
  up kafkasql || return 0
  writable_checks "$A"
  check "the other replica catches up from the topic" "1,2" "$(eventually 30 "1,2" versions "$B")"
  check "a setting changed on one replica reaches the other" 409 \
    "$(code PUT "$A/admin/config/properties/apicurio.storage.read-only.enabled" '{"value": "true"}' > /dev/null
       eventually 30 409 code POST "$B/groups" '{"groupId": "blocked-b"}')"
  code PUT "$A/admin/config/properties/apicurio.storage.read-only.enabled" '{"value": "false"}' > /dev/null
  local config
  config=$(compose exec -T kafka /opt/kafka/bin/kafka-configs.sh --bootstrap-server kafka:9092 \
    --entity-type topics --entity-name kafkasql-journal --describe 2>/dev/null)
  check "the journal topic keeps every record: cleanup.policy=delete, retention.ms=-1" "yes yes" \
    "$(grep -q 'cleanup.policy=delete' <<< "$config" && echo yes || echo no) $(grep -q 'retention.ms=-1' <<< "$config" && echo yes || echo no)"
  restart registry-kafkasql-b "$B"
  check "a restarted replica rebuilds everything from the topic" "1,2" "$(eventually 60 "1,2" versions "$B")"
  down
}

# ---------------------------------------------------------------------------------------- gitops
# Plain commits: no user configuration, no hooks from the environment
git_() { git -C work/repo -c user.name=demo -c user.email=demo@example.com -c core.hooksPath=/dev/null "$@"; }
git_commit() { git_ commit -qam "$1"; }
gitops_status() { get "$A/admin/gitops/status"; }
gitops_errors() { gitops_status | jq -r '.errors | length'; }

run_gitops() {
  part "GitOps: a read-only registry loading a git repository"
  current=gitops
  compose down -v > /dev/null 2>&1 || true
  # Empty the repository directory, but keep it: it is the bind mount's source
  find work/repo -mindepth 1 -not -name .gitkeep -delete 2>/dev/null || true
  mkdir -p work/repo
  cp gitops-seed/registry.registry.yaml gitops-seed/orders.registry.yaml gitops-seed/order-placed.registry.yaml \
    gitops-seed/order-placed-v1.avsc work/repo/
  git_ init -q -b main
  git_ add .
  git_commit "Version 1 of orders/order-placed"
  chmod -R a+rX work/repo
  up gitops || return 0

  check "the repository's artifact is loaded" "1" "$(versions "$A")"
  check "global rules come from the repository" FULL "$(get "$A/admin/rules/VALIDITY" | jq -r .config)"
  local gid
  gid=$(get "$A/groups/orders/artifacts/order-placed/versions/1" | jq -r .globalId)
  check "IDs are deterministic, in the upper half of the ID range" yes \
    "$([ "$gid" -gt 4611686018427387903 ] && echo yes || echo no)"
  check "writes through the API are refused" 409 "$(code POST "$A/groups" '{"groupId": "blocked"}')"
  check "the console is told why" "true storage" \
    "$(get "$A/system/uiConfig" | jq -r '"\(.features.readOnly) \(.features.readOnlyReason)"')"
  check "sync status" "IDLE 0" "$(gitops_status | jq -r '"\(.syncState) \(.errors | length)"')"

  # A new commit: version 2
  cp gitops-seed/order-placed-v2.avsc work/repo/
  cat >> work/repo/order-placed.registry.yaml <<'EOF'
  - version: "2"
    state: ENABLED
    content: ./order-placed-v2.avsc
EOF
  git_ add . && git_commit "Version 2"
  chmod -R a+rX work/repo
  check "a commit is picked up: version 2 appears" "1,2" "$(eventually 60 "1,2" versions "$A")"

  # A broken commit: version 3 breaks the BACKWARD rule
  cp gitops-seed/order-placed-v2-incompatible.avsc work/repo/order-placed-v3.avsc
  cat >> work/repo/order-placed.registry.yaml <<'EOF'
  - version: "3"
    state: ENABLED
    content: ./order-placed-v3.avsc
EOF
  git_ add . && git_commit "Version 3, incompatible"
  chmod -R a+rX work/repo
  local errors
  errors=$(eventually 60 1 gitops_errors)
  check "a commit that breaks a rule is reported in the sync status" yes "$([ "${errors:-0}" -ge 1 ] && echo yes || echo no)"
  check "  and the last good data is still served" "1,2" "$(versions "$A")"
  gitops_status | jq -r '.errors[0] | "  info  \(.detail)"' | cut -c1-160

  git_ revert --no-edit HEAD > /dev/null
  chmod -R a+rX work/repo
  check "reverting the commit clears the error" 0 "$(eventually 60 0 gitops_errors)"

  restart registry-gitops "$A"
  global_id() { get "$A/groups/orders/artifacts/order-placed/versions/1" | jq -r .globalId; }
  check "after a restart (empty in-memory copy, reloaded from git), the same global ID" "$gid" \
    "$(eventually 60 "$gid" global_id)"
  [ -n "${KEEP:-}" ] || down
}

# --------------------------------------------------------------------------------- kubernetesops
run_kubernetesops() {
  part "KubernetesOps: a read-only registry loading ConfigMaps"
  up kubernetesops || return 0
  check "the ConfigMap's artifact is loaded" "1" "$(versions "$A")"
  check "writes through the API are refused" 409 "$(code POST "$A/groups" '{"groupId": "blocked"}')"
  check "the console is told why" "true storage" \
    "$(get "$A/system/uiConfig" | jq -r '"\(.features.readOnly) \(.features.readOnlyReason)"')"
  compose exec -T k3s kubectl apply -n registry -f - < configmaps/v2.yaml > /dev/null
  check "an updated ConfigMap is picked up: version 2 appears" "1,2" "$(eventually 60 "1,2" versions "$A")"
  down
}

for v in $VARIANTS; do
  case "$v" in
    postgresql | mysql | mssql | kafkasql | gitops | kubernetesops) "run_$v" ;;
    *) fail "unknown variant '$v'" ;;
  esac
done

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

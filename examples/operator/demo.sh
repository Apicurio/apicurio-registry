#!/usr/bin/env bash
# Runs the Apicurio Registry operator on a local kind cluster and checks what it does with an
# ApicurioRegistry3 custom resource: in memory, on PostgreSQL, on KafkaSQL with two replicas, with
# configuration from the CR, and with an invalid CR.
# Usage: ./demo.sh                 (builds the operator with build.sh when its image is missing)
#        KEEP=1 ./demo.sh          leave the cluster running
# Requires kind, kubectl, curl, jq, and Docker or Podman (KIND_EXPERIMENTAL_PROVIDER=podman).
# Creates the kind cluster $KIND_CLUSTER (default apicurio-operator-example) and deletes it at the end,
# so it is safe to re-run. Ends with "All checks passed.", non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")"

CLUSTER=${KIND_CLUSTER:-apicurio-operator-example}
OPERATOR_IMAGE=${OPERATOR_IMAGE:-apicurio/apicurio-registry-3-operator:example}
REGISTRY_IMAGE=${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot}
REGISTRY_UI_IMAGE=${REGISTRY_UI_IMAGE:-apicurio/apicurio-registry-ui:latest-snapshot}
PORT=${PORT:-18080}
K="kubectl --context kind-$CLUSTER"
failures=0
forward_pid=""

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
    sleep 2
  done
  echo "$actual"
}

# forward <namespace> <target>: port-forward localhost:$PORT to the target's port 8080
forward() {
  unforward
  $K -n "$1" port-forward "$2" "$PORT:8080" > /dev/null 2>&1 &
  forward_pid=$!
  local deadline=$((SECONDS + 30))
  until curl -sf -o /dev/null "http://localhost:$PORT/apis/registry/v3/system/info"; do
    [ "$SECONDS" -ge "$deadline" ] && return 1
    sleep 1
  done
}
unforward() { if [ -n "$forward_pid" ]; then kill "$forward_pid" 2> /dev/null || true; wait "$forward_pid" 2> /dev/null || true; forward_pid=""; fi; }
api() { curl -s --max-time 30 -o /dev/null -w '%{http_code}' -X "$1" "http://localhost:$PORT/apis/registry/v3$2" -H 'Content-Type: application/json' ${3:+-d "$3"}; }
artifact() { jq -nc --arg a "$1" '{artifactId: $a, artifactType: "AVRO", firstVersion: {content: {content: "\"string\"", contentType: "application/json"}}}'; }
ready() { $K -n "$1" get apicurioregistry3 registry -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}'; }
condition_reason() { $K -n "$1" get apicurioregistry3 registry -o jsonpath="{.status.conditions[?(@.type==\"$2\")].reason}"; }
app_env() { $K -n "$1" get deploy registry-app-deployment -o json | jq -c --arg n "$2" '.spec.template.spec.containers[0].env[] | select(.name == $n) | if .valueFrom then {secret: .valueFrom.secretKeyRef.name} else .value end'; }
names() { $K -n "$1" get "$2" -o name 2> /dev/null | sed 's#.*/##' | sort | tr '\n' ' ' | sed 's/ $//'; }
# variant <namespace> <manifests...>: a fresh namespace with its manifests applied
variant() {
  local ns=$1
  shift
  $K delete namespace "$ns" --wait=true > /dev/null 2>&1 || true
  $K create namespace "$ns" > /dev/null
  local f
  for f in "$@"; do $K -n "$ns" apply -f "$f" > /dev/null; done
}

cleanup() {
  unforward
  if [ "$failures" -gt 0 ] && kind get clusters 2> /dev/null | grep -qx "$CLUSTER"; then
    echo "  info  pods and recent events, for diagnosis:"
    $K get pods -A 2>&1 | grep -v kube-system | sed 's/^/        /'
    $K get events -A --sort-by=.lastTimestamp 2>&1 | tail -15 | sed 's/^/        /'
    $K -n apicurio-operator logs deploy/"$($K -n apicurio-operator get deploy -o jsonpath='{.items[0].metadata.name}')" --tail=30 2>&1 | sed 's/^/        /'
  fi
  [ -n "${KEEP:-}" ] || kind delete cluster --name "$CLUSTER" > /dev/null 2>&1 || true
}
trap cleanup EXIT

# ------------------------------------------------------------------------------ step 1
part "Step 1: a cluster, and the operator"
# Build the operator once; regenerate the install file every run, so it names the images of this run
if ! docker image inspect "$OPERATOR_IMAGE" > /dev/null 2>&1; then
  ./build.sh > /tmp/operator-build.log 2>&1 || { tail -20 /tmp/operator-build.log; fail "build.sh"; exit 1; }
fi
OPERATOR_IMAGE=$OPERATOR_IMAGE REGISTRY_IMAGE=$REGISTRY_IMAGE REGISTRY_UI_IMAGE=$REGISTRY_UI_IMAGE \
  ./build.sh --install-file > /tmp/operator-build.log 2>&1 || { tail -20 /tmp/operator-build.log; fail "build.sh --install-file"; exit 1; }
kind delete cluster --name "$CLUSTER" > /dev/null 2>&1 || true
kind create cluster --name "$CLUSTER" --wait 120s > /tmp/operator-kind.log 2>&1 || { tail /tmp/operator-kind.log; fail "kind create cluster"; exit 1; }
# Images built or loaded locally go into the cluster; anything else is pulled from its registry
for image in "$OPERATOR_IMAGE" "$REGISTRY_IMAGE" "$REGISTRY_UI_IMAGE"; do
  if docker image inspect "$image" > /dev/null 2>&1; then
    kind load docker-image "$image" --name "$CLUSTER" > /dev/null 2>&1 || fail "kind load $image"
  fi
done
$K create namespace apicurio-operator > /dev/null
$K -n apicurio-operator apply -f target/install.yaml > /dev/null 2>&1
# The install file pulls the operator image every time (Always); use the image loaded into kind
operator=$($K -n apicurio-operator get deploy -o name | head -1)
$K -n apicurio-operator patch "$operator" --type=json \
  -p '[{"op": "replace", "path": "/spec/template/spec/containers/0/imagePullPolicy", "value": "IfNotPresent"}]' > /dev/null
check "the CRD is installed" apicurioregistries3.registry.apicur.io \
  "$($K get crd apicurioregistries3.registry.apicur.io -o jsonpath='{.metadata.name}' 2> /dev/null)"
check "the operator is running" "successfully rolled out" \
  "$($K -n apicurio-operator rollout status "$operator" --timeout=240s 2>&1 | grep -o 'successfully rolled out')"

# ------------------------------------------------------------------------------ step 2
part "Step 2: the smallest CR (in memory)"
variant demo-memory k8s/registry-in-memory.yaml
check "the CR becomes Ready" True "$(eventually 300 True ready demo-memory)"
check "the operator created the app and the console" "registry-app-deployment registry-ui-deployment" "$(names demo-memory deploy)"
check "  their services" "registry-app-service registry-ui-service" "$(names demo-memory svc)"
check "  and network policies" "registry-app-networkpolicy registry-ui-networkpolicy" "$(names demo-memory networkpolicy)"
# A budget for a single pod would allow no disruption, and block node drains
check "no PodDisruptionBudget for the single app and console pods" "" "$(names demo-memory pdb)"
check "the deployments belong to the CR (deleted with it)" ApicurioRegistry3 \
  "$($K -n demo-memory get deploy registry-app-deployment -o jsonpath='{.metadata.ownerReferences[0].kind}')"
forward demo-memory svc/registry-app-service || fail "port-forward to registry-app-service"
check "the registry answers, and stores an artifact" 200 "$(api POST /groups/demo/artifacts "$(artifact hello)")"
check "deleting is off by default" 405 "$(api DELETE /groups/demo/artifacts/hello)"
unforward
# Configuration the CR has no field for goes in spec.app.env; it wins over what the operator sets
$K -n demo-memory patch apicurioregistry3 registry --type=merge \
  -p '{"spec": {"app": {"env": [{"name": "APICURIO_REST_DELETION_ARTIFACT_ENABLED", "value": "true"}]}}}' > /dev/null
check "spec.app.env reaches the deployment" '"true"' "$(eventually 60 '"true"' app_env demo-memory APICURIO_REST_DELETION_ARTIFACT_ENABLED)"
$K -n demo-memory rollout status deploy/registry-app-deployment --timeout=240s > /dev/null 2>&1 || true
check "  and the CR is Ready again after the rollout" True "$(eventually 120 True ready demo-memory)"
forward demo-memory svc/registry-app-service || fail "port-forward after the rollout"
check "  deleting now works (in memory, so the artifact is recreated first)" "200 204" \
  "$(api POST /groups/demo/artifacts "$(artifact hello)") $(api DELETE /groups/demo/artifacts/hello)"
unforward
$K -n demo-memory delete apicurioregistry3 registry > /dev/null
check "deleting the CR removes what the operator created" "" "$(eventually 120 "" names demo-memory deploy)"

# ------------------------------------------------------------------------------ step 3
part "Step 3: PostgreSQL"
variant demo-postgresql k8s/postgresql.yaml
$K -n demo-postgresql rollout status deploy/postgres --timeout=180s > /dev/null
$K -n demo-postgresql apply -f k8s/registry-postgresql.yaml > /dev/null
check "the CR becomes Ready" True "$(eventually 300 True ready demo-postgresql)"
check "the storage settings are environment variables" "sql postgresql" \
  "$(app_env demo-postgresql APICURIO_STORAGE_KIND | tr -d '"') $(app_env demo-postgresql APICURIO_STORAGE_SQL_KIND | tr -d '"')"
check "  and the password comes from the Secret, not the CR" '{"secret":"registry-db"}' \
  "$(app_env demo-postgresql APICURIO_DATASOURCE_PASSWORD)"
forward demo-postgresql svc/registry-app-service || fail "port-forward"
check "store an artifact" 200 "$(api POST /groups/demo/artifacts "$(artifact durable)")"
unforward
$K -n demo-postgresql delete pod -l app.kubernetes.io/component=app --wait=true > /dev/null
$K -n demo-postgresql rollout status deploy/registry-app-deployment --timeout=240s > /dev/null 2>&1 || true
forward demo-postgresql svc/registry-app-service || fail "port-forward to the new pod"
check "a new pod still has it: the data is in PostgreSQL" 200 "$(api GET /groups/demo/artifacts/durable)"
unforward

# ------------------------------------------------------------------------------ step 4
part "Step 4: KafkaSQL, two replicas"
variant demo-kafkasql k8s/kafka.yaml
$K -n demo-kafkasql rollout status deploy/kafka --timeout=180s > /dev/null
$K -n demo-kafkasql apply -f k8s/registry-kafkasql.yaml > /dev/null
check "the CR becomes Ready" True "$(eventually 420 True ready demo-kafkasql)"
pods=$($K -n demo-kafkasql get pods -l app.kubernetes.io/component=app -o name | sort)
check "two app pods (spec.app.replicas: 2)" 2 "$(echo "$pods" | grep -c .)"
check "  and a PodDisruptionBudget, because there is more than one" registry-app-poddisruptionbudget \
  "$(names demo-kafkasql pdb | tr ' ' '\n' | grep app || true)"
forward demo-kafkasql "$(echo "$pods" | head -1)" || fail "port-forward to the first pod"
check "a write through the first pod" 200 "$(api POST /groups/demo/artifacts "$(artifact shared)")"
unforward
forward demo-kafkasql "$(echo "$pods" | tail -1)" || fail "port-forward to the second pod"
check "  is read through the second (the journal)" 200 "$(eventually 30 200 api GET /groups/demo/artifacts/shared)"
unforward

# ------------------------------------------------------------------------------ step 5
part "Step 5: a CR the operator cannot apply"
variant demo-invalid
cat <<'EOF' | $K -n demo-invalid apply -f - > /dev/null
apiVersion: registry.apicur.io/v1
kind: ApicurioRegistry3
metadata:
  name: registry
spec:
  app:
    storage:
      type: postgresql
      sql:
        dataSource:
          url: jdbc:mysql://db:3306/registry
          username: registry
EOF
check "a postgresql storage with a MySQL URL is reported on the CR" OperatorException \
  "$(eventually 60 OperatorException condition_reason demo-invalid OperatorError)"
check "  and the CR is not Ready" False "$(ready demo-invalid)"
echo "  info  $($K -n demo-invalid get apicurioregistry3 registry -o jsonpath='{.status.conditions[?(@.type=="OperatorError")].message}' | tail -1 | cut -c1-140)"

if [[ $failures -gt 0 ]]; then
  echo
  echo "$failures check(s) failed."
  exit 1
fi
echo
echo "All checks passed."

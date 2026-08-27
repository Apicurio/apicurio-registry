#!/usr/bin/env bash
# Runs the RegistryApiSimulation Gatling load *outside* the cluster (directly on this machine,
# against the registry via a NodePort Service) instead of as an in-cluster Job.
#
# Why this exists: running the load generator as a pod inside the same (typically small, e.g.
# local Minikube or a CI runner) cluster as the registry under test has two problems that
# specifically distort results at higher concurrency (a few hundred+ concurrent clients):
#
#   1. A single Gatling JVM's own connection handling degrades at high per-process concurrency
#      (observed: severe TCP TIME_WAIT accumulation and eventual ephemeral-port exhaustion above
#      roughly 250-300 concurrent connections from one process) - a client-tooling artifact, not
#      a registry limit. This isn't a problem at the modest concurrency the default in-cluster Job
#      test uses (see perf-job.yaml's PERF_USERS default), only when deliberately pushing for the
#      registry's real capacity ceiling.
#   2. The registry, its database, Kafka, Keycloak, the operator, *and* the load generator all end
#      up competing for the same node's CPU/memory - inflating latency and capping throughput in a
#      way that reflects the shared test environment's resource contention, not the registry's own
#      capacity. This is easy to miss because it looks superficially like a registry-side
#      bottleneck (rising latency, restarts under load) unless you check whether CPU is actually
#      saturated.
#
# Running the load generator as a separate process outside the cluster, talking to the registry
# over the network like a real remote client would, avoids both: this process has the whole
# machine's/runner's own resources to itself, and issuing many concurrent requests from one
# external process doesn't suffer from problem (1) the way piling that same concurrency into a
# single in-cluster pod does (observed to scale cleanly to 1600+ concurrent connections without
# the TIME_WAIT/port-exhaustion problem reappearing).
#
# Usage:
#   run-external-load.sh <perf-tests-jar> [PERF_USERS] [PERF_DURATION_SECONDS] [PERF_WRITE_RATIO]
#
# Must be run after a scenario's deploy.sh (postgresql or kafkasql) has already stood up the
# topology, including the registry CR, in the current kubectl context/namespace (default).
set -euo pipefail

JAR="${1:?Usage: run-external-load.sh <perf-tests-jar> [PERF_USERS] [PERF_DURATION_SECONDS] [PERF_WRITE_RATIO]}"
PERF_USERS="${2:-300}"
PERF_DURATION_SECONDS="${3:-30}"
PERF_WRITE_RATIO="${4:-0}"

echo "Exposing the registry and Keycloak outside the cluster..."
kubectl -n default patch svc perf-test-app-service -p '{"spec":{"type":"NodePort"}}'
kubectl -n default patch svc keycloak -p '{"spec":{"type":"NodePort"}}'

MINIKUBE_IP="$(minikube ip)"
APP_NODEPORT="$(kubectl -n default get svc perf-test-app-service -o jsonpath='{.spec.ports[0].nodePort}')"
KEYCLOAK_NODEPORT="$(kubectl -n default get svc keycloak -o jsonpath='{.spec.ports[0].nodePort}')"
KEYCLOAK_EXTERNAL_URL="http://${MINIKUBE_IP}:${KEYCLOAK_NODEPORT}/realms/registry"

# The registry validates the OAuth token's issuer claim against its own configured
# authServerUrl. Keycloak stamps the issuer claim with whatever host:port the token *request*
# used - so if the load generator (external, hitting the NodePort address) and the registry
# (internal, configured with Keycloak's in-cluster DNS name) disagree on that address, every
# request fails with 401 even though the token itself is valid. Point the CR's authServerUrl at
# the same externally-reachable address the load generator will use, so both sides agree.
echo "Aligning the registry's authServerUrl with the externally-reachable Keycloak address..."
kubectl -n default patch apicurioregistry3 perf-test --type='json' -p="[
  {\"op\":\"replace\",\"path\":\"/spec/app/auth/authServerUrl\",\"value\":\"${KEYCLOAK_EXTERNAL_URL}\"}
]"
kubectl -n default rollout status deployment/perf-test-app-deployment --timeout=3m

REGISTRY_URL="http://${MINIKUBE_IP}:${APP_NODEPORT}/apis/registry/v3"

echo "Running the load generator externally: PERF_USERS=${PERF_USERS} PERF_DURATION_SECONDS=${PERF_DURATION_SECONDS} PERF_WRITE_RATIO=${PERF_WRITE_RATIO}"
echo "  REGISTRY_URL=${REGISTRY_URL}"
echo "  AUTH_TOKEN_ENDPOINT=${KEYCLOAK_EXTERNAL_URL}/protocol/openid-connect/token"

REGISTRY_URL="$REGISTRY_URL" \
AUTH_TOKEN_ENDPOINT="${KEYCLOAK_EXTERNAL_URL}/protocol/openid-connect/token" \
AUTH_CLIENT_ID="registry-api" \
AUTH_CLIENT_SECRET="perf-test-secret" \
PERF_USERS="$PERF_USERS" \
PERF_DURATION_SECONDS="$PERF_DURATION_SECONDS" \
PERF_WRITE_RATIO="$PERF_WRITE_RATIO" \
PERF_PAUSE_MIN_MS="0" \
PERF_PAUSE_MAX_MS="0" \
PERF_SKIP_KAFKA="true" \
java -Dgatling.resultsFolder="${GATLING_RESULTS_FOLDER:-/tmp/external-load-gatling}" -jar "$JAR"

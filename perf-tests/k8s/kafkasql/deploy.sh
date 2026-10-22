#!/usr/bin/env bash
# Deploys the KafkaSQL-backed perf-test topology, with injected network latency between the
# registry and its Kafka storage backend via Toxiproxy (see toxiproxy.yaml/kafka.yaml) to
# approximate a real deployment (a remote/managed Kafka cluster) rather than a same-node,
# near-zero-latency broker. Run from the repo root, or anywhere - paths below are relative to
# this script.
set -euo pipefail
cd "$(dirname "$0")"

kubectl -n default apply -f kafka.yaml
kubectl -n default apply -f toxiproxy.yaml
kubectl -n default create configmap keycloak-realm-import \
  --from-file=realm.json=../common/realm-import.json --dry-run=client -o yaml \
  | kubectl -n default apply -f -
kubectl -n default apply -f ../common/keycloak.yaml

kubectl -n default rollout status deployment/kafka --timeout=3m
kubectl -n default rollout status deployment/toxiproxy --timeout=3m
kubectl -n default rollout status deployment/keycloak --timeout=3m

kubectl -n default apply -f registry-cr.yaml
sleep 10
kubectl -n default rollout status deployment/perf-test-app-deployment --timeout=5m

echo "KafkaSQL scenario deployed. Run the perf-test Job with:"
echo '  sed -e "s|\${PERF_TEST_IMAGE}|<image>|" -e "s|\${KAFKA_BOOTSTRAP_SERVERS}|toxiproxy.default.svc:9094|" ../common/perf-job.yaml | kubectl -n default apply -f -'

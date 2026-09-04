#!/usr/bin/env bash
set -euo pipefail

PRODUCT="${1:?Usage: run-product.sh <apicurio|confluent|karapace|redpanda> <operation> <output-directory>}"
OPERATION="${2:?Missing benchmark operation}"
OUTPUT_DIR="${3:?Missing output directory}"
NAMESPACE="comparison-${PRODUCT}-$(openssl rand -hex 6)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -n "${COMPARISON_JAR:-}" ]]; then
    JAR="$COMPARISON_JAR"
else
    JAR_LIST="$(find "$ROOT/target" -name '*-jar-with-dependencies.jar')"
    [[ "$(printf '%s\n' "$JAR_LIST" | sed '/^$/d' | wc -l | tr -d ' ')" -eq 1 ]] \
        || { echo "Expected exactly one comparison runner JAR" >&2; exit 2; }
    JAR="$JAR_LIST"
fi
USERS="${PERF_USERS:-100}"
WARMUP="${PERF_WARMUP_SECONDS:-60}"
DURATION="${PERF_DURATION_SECONDS:-180}"
SEEDS="${PERF_SEED_SCHEMAS:-1000}"
USERNAME="benchmark"
PASSWORD="$(openssl rand -base64 32 | tr -d '/+=')"
CREATED_NAMESPACE=false
TLS_DIR=""
SAMPLER_PID=""

[[ "$PRODUCT" =~ ^(apicurio|confluent|karapace|redpanda)$ ]] || { echo "Invalid product" >&2; exit 2; }
[[ "$OPERATION" =~ ^(READ_ID|READ_VERSION|REGISTER_NEW_SUBJECT|REGISTER_NEW_VERSION|REGISTER_IDEMPOTENT|COMPATIBILITY)$ ]] \
    || { echo "Invalid operation" >&2; exit 2; }
for value in "$USERS" "$WARMUP" "$DURATION" "$SEEDS"; do
    [[ "$value" =~ ^[1-9][0-9]*$ ]] || { echo "Numeric benchmark parameters must be positive integers" >&2; exit 2; }
done

case "$PRODUCT" in
    apicurio) UPSTREAM_PATH=/apis/ccompat/v7 ;;
    confluent|karapace|redpanda) UPSTREAM_PATH= ;;
    *) echo "Unsupported product: $PRODUCT" >&2; exit 2 ;;
esac

mkdir -p "$OUTPUT_DIR"
cleanup() {
    if [[ -n "$SAMPLER_PID" ]]; then
        kill "$SAMPLER_PID" >/dev/null 2>&1 || true
        wait "$SAMPLER_PID" 2>/dev/null || true
    fi
    if [[ -n "$TLS_DIR" ]]; then
        rm -rf "$TLS_DIR"
    fi
    kubectl get all,pvc -n "$NAMESPACE" -o yaml > "$OUTPUT_DIR/kubernetes-resources-final.yaml" 2>/dev/null || true
    kubectl describe pods -n "$NAMESPACE" > "$OUTPUT_DIR/pod-describe.txt" 2>/dev/null || true
    mkdir -p "$OUTPUT_DIR/pod-logs"
    for pod in $(kubectl get pods -n "$NAMESPACE" -o name 2>/dev/null); do
        # Keep diagnostics bounded across repeated multi-product runs. Full Kafka logs can grow
        # by hundreds of megabytes and are not needed when startup/health tails are preserved.
        kubectl logs -n "$NAMESPACE" "$pod" --all-containers --tail=10000 \
            > "$OUTPUT_DIR/pod-logs/${pod#pod/}.log" 2>&1 || true
    done
    if [[ "$CREATED_NAMESPACE" == true ]]; then
        kubectl delete namespace "$NAMESPACE" --wait=true --timeout=3m >/dev/null 2>&1 || true
    fi
}

kubectl create namespace "$NAMESPACE"
CREATED_NAMESPACE=true
trap cleanup EXIT

if [[ "$PRODUCT" != redpanda ]]; then
    kubectl apply -n "$NAMESPACE" -f "$ROOT/k8s/common/kafka.yaml"
    kubectl rollout status -n "$NAMESPACE" statefulset/kafka --timeout=5m
fi

if [[ "$PRODUCT" == apicurio ]]; then
    : "${APICURIO_IMAGE:?APICURIO_IMAGE must identify the Apicurio image under test}"
    [[ "$APICURIO_IMAGE" =~ ^[A-Za-z0-9._:/@-]+$ ]] || { echo "Invalid APICURIO_IMAGE" >&2; exit 2; }
    sed "s|\${APICURIO_IMAGE}|$APICURIO_IMAGE|g" "$ROOT/k8s/apicurio/registry.yaml" | kubectl apply -n "$NAMESPACE" -f -
else
    kubectl apply -n "$NAMESPACE" -f "$ROOT/k8s/$PRODUCT/registry.yaml"
fi

if [[ "$PRODUCT" == redpanda ]]; then
    kubectl rollout status -n "$NAMESPACE" statefulset/schema-registry --timeout=8m
else
    kubectl rollout status -n "$NAMESPACE" deployment/schema-registry --timeout=8m
fi

NODE_IP="$(minikube ip)"
TLS_DIR="$(mktemp -d)"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj '/CN=benchmark.local' \
    -addext "subjectAltName=DNS:benchmark.local,IP:$NODE_IP" \
    -keyout "$TLS_DIR/tls.key" -out "$TLS_DIR/tls.crt" >/dev/null 2>&1
keytool -importcert -noprompt -alias benchmark -file "$TLS_DIR/tls.crt" \
    -keystore "$TLS_DIR/truststore.p12" -storetype PKCS12 -storepass changeit >/dev/null 2>&1
# Nginx verifies this on every request. A deliberately slow password hash can saturate the shared
# proxy before any registry is stressed, turning the run into a password-hashing benchmark. The
# credential is random, high-entropy, single-run, TLS-protected, and never persisted, so an
# RFC 2307 SHA digest provides the required common Basic-auth check without becoming the limiter.
HASH="{SHA}$(printf '%s' "$PASSWORD" | openssl dgst -sha1 -binary | openssl base64 -A)"
printf '%s:%s\n' "$USERNAME" "$HASH" > "$TLS_DIR/htpasswd"

kubectl create secret generic benchmark-tls -n "$NAMESPACE" \
    --from-file=tls.crt="$TLS_DIR/tls.crt" --from-file=tls.key="$TLS_DIR/tls.key"
kubectl create secret generic benchmark-basic-auth -n "$NAMESPACE" --from-file=htpasswd="$TLS_DIR/htpasswd"
sed "s|\${UPSTREAM_PATH}|$UPSTREAM_PATH|g" "$ROOT/k8s/common/proxy.conf.template" \
    | kubectl create configmap benchmark-proxy -n "$NAMESPACE" --from-file=default.conf=/dev/stdin
kubectl apply -n "$NAMESPACE" -f "$ROOT/k8s/common/proxy.yaml"
kubectl rollout status -n "$NAMESPACE" deployment/benchmark-proxy --timeout=3m

NODE_PORT="$(kubectl get svc benchmark-proxy -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')"
URL="https://${NODE_IP}:${NODE_PORT}"

# Pod readiness does not guarantee the new NodePort is immediately reachable from the host.
# Establish the trusted route first so the negative control below tests only certificate trust.
for attempt in $(seq 1 30); do
    if curl --silent --show-error --cacert "$TLS_DIR/tls.crt" --user "$USERNAME:$PASSWORD" \
        "$URL/" > /dev/null 2>&1; then
        break
    fi
    if [[ "$attempt" -eq 30 ]]; then
        echo "Benchmark proxy NodePort did not become reachable" >&2
        exit 1
    fi
    sleep 1
done

# Prove the generated certificate is rejected without the benchmark CA. curl exit code 60 is
# specifically a peer-certificate verification failure; accepting any other failure would let a
# broken route/proxy masquerade as a successful TLS negative control.
set +e
curl --silent --show-error "$URL/" > /dev/null 2> "$OUTPUT_DIR/untrusted-negative-control.log"
CURL_STATUS=$?
set -e
if [[ "$CURL_STATUS" -eq 0 ]]; then
    echo "TLS negative control unexpectedly trusted the generated certificate" >&2
    exit 1
fi
[[ "$CURL_STATUS" -eq 60 ]] || { echo "TLS negative control failed for an unexpected reason" >&2; exit 1; }

POD="$(kubectl get pod -n "$NAMESPACE" -l app=schema-registry -o jsonpath='{.items[0].metadata.name}')"
IMAGE="$(kubectl get pod -n "$NAMESPACE" "$POD" -o jsonpath='{.spec.containers[0].image}')"
IMAGE_ID="$(kubectl get pod -n "$NAMESPACE" "$POD" -o jsonpath='{.status.containerStatuses[0].imageID}')"
REPLICAS=""
if [[ "$PRODUCT" == redpanda ]]; then
    REPLICAS="$(kubectl get statefulset -n "$NAMESPACE" schema-registry -o jsonpath='{.spec.replicas}')"
else
    REPLICAS="$(kubectl get deployment -n "$NAMESPACE" schema-registry -o jsonpath='{.spec.replicas}')"
fi
PROXY_IMAGE_ID="$(kubectl get pod -n "$NAMESPACE" -l app=benchmark-proxy -o jsonpath='{.items[0].status.containerStatuses[0].imageID}')"
KAFKA_IMAGE_ID=""
if [[ "$PRODUCT" != redpanda ]]; then
    KAFKA_IMAGE_ID="$(kubectl get pod -n "$NAMESPACE" kafka-0 -o jsonpath='{.status.containerStatuses[0].imageID}')"
fi
NODE_CAPACITY="$(kubectl get node -o json | python3 -c 'import json,sys; n=json.load(sys.stdin)["items"][0]; print(json.dumps(n["status"]["capacity"]))')"
HOST="$(uname -a)" JAVA_VERSION="$(java -version 2>&1 | head -1)" \
PRODUCT="$PRODUCT" OPERATION="$OPERATION" NAMESPACE="$NAMESPACE" URL="$URL" USERS="$USERS" \
WARMUP="$WARMUP" DURATION="$DURATION" SEEDS="$SEEDS" IMAGE="$IMAGE" IMAGE_ID="$IMAGE_ID" \
REPLICAS="$REPLICAS" PROXY_IMAGE_ID="$PROXY_IMAGE_ID" KAFKA_IMAGE_ID="$KAFKA_IMAGE_ID" NODE_CAPACITY="$NODE_CAPACITY" \
python3 - <<'PY' > "$OUTPUT_DIR/deployment-metadata.json"
import json
import os

keys = ["PRODUCT", "OPERATION", "NAMESPACE", "URL", "USERS", "WARMUP", "DURATION", "SEEDS", "IMAGE", "IMAGE_ID", "REPLICAS", "PROXY_IMAGE_ID", "KAFKA_IMAGE_ID", "HOST", "JAVA_VERSION"]
data = {key.lower(): os.environ[key] for key in keys}
for key in ["users", "warmup", "duration", "seeds", "replicas"]:
    data[key] = int(data[key])
data["nodecapacity"] = json.loads(os.environ["NODE_CAPACITY"])
print(json.dumps(data, indent=2))
PY
kubectl get all,pvc -n "$NAMESPACE" -o yaml > "$OUTPUT_DIR/kubernetes-resources.yaml"

for attempt in $(seq 1 60); do
    REGISTRY_METRICS="$(kubectl top pod -n "$NAMESPACE" -l app=schema-registry --containers --no-headers 2>/dev/null || true)"
    PROXY_METRICS="$(kubectl top pod -n "$NAMESPACE" -l app=benchmark-proxy --containers --no-headers 2>/dev/null || true)"
    if kubectl top node >/dev/null 2>&1 && [[ -n "$REGISTRY_METRICS" && -n "$PROXY_METRICS" ]]; then
        break
    fi
    if [[ "$attempt" -eq 60 ]]; then
        echo "Kubernetes resource metrics are unavailable; refusing to produce an unverifiable result" >&2
        exit 1
    fi
    sleep 2
done

(
    while true; do
        date -u +'%Y-%m-%dT%H:%M:%SZ'
        kubectl top pod -n "$NAMESPACE" --containers 2>&1 || true
        sleep 5
    done
) > "$OUTPUT_DIR/resource-usage.log" &
SAMPLER_PID=$!

set +e
SCHEMA_REGISTRY_URL="$URL" PRODUCT_NAME="$PRODUCT" PERF_OPERATION="$OPERATION" \
PERF_USERS="$USERS" PERF_WARMUP_SECONDS="$WARMUP" PERF_DURATION_SECONDS="$DURATION" \
PERF_SEED_SCHEMAS="$SEEDS" BASIC_AUTH_USERNAME="$USERNAME" BASIC_AUTH_PASSWORD="$PASSWORD" \
BENCHMARK_TRUSTSTORE="$TLS_DIR/truststore.p12" BENCHMARK_TRUSTSTORE_PASSWORD=changeit \
java -Djavax.net.ssl.trustStore="$TLS_DIR/truststore.p12" -Djavax.net.ssl.trustStorePassword=changeit \
    -Dgatling.resultsFolder="$OUTPUT_DIR/gatling" -jar "$JAR" 2>&1 | tee "$OUTPUT_DIR/console.log"
STATUS=${PIPESTATUS[0]}
set -e
kill "$SAMPLER_PID" >/dev/null 2>&1 || true
wait "$SAMPLER_PID" 2>/dev/null || true
SAMPLER_PID=""
exit "$STATUS"

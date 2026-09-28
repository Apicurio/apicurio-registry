#!/usr/bin/env bash
# Local TLS registry for the operator OLM bundle and catalog images in CI (#10284).
#
# The bundle and catalog images are named $OLM_REGISTRY_HOST/... (default registry.olm-ci.local:5443)
# and never leave the workflow, so CI no longer depends on a public ephemeral registry such as ttl.sh.
#
#   host    Registry trusted by the host only, with a throwaway self-signed CA. Used by the build job
#           (`make bundle catalog` push; opm pulls the new bundle with --skip-tls-verify while rendering,
#           HTTPS because opm's --use-http would also apply to the historical bundles on quay.io), and
#           by the OLM v0 job, whose images the kubelet pulls through the host Docker daemon (Minikube
#           driver=none). OLM v0 needs a registry, not just `docker load`: its catalog unpack pod uses
#           imagePullPolicy: Always.
#   olmv1   OLM v1 test job. catalogd and operator-controller pull images themselves from inside pods,
#           so the registry must be reachable from the pod network and trusted by them. Its certificate
#           is issued by OLM v1's own cert-manager ClusterIssuer (olmv1-ca), whose CA both components
#           already load via --pull-cas-dir, and CoreDNS resolves the registry name to the node.
set -euo pipefail

MODE=${1:?usage: olm-ci-registry.sh host|olmv1}
HOST_PORT=${OLM_REGISTRY_HOST:-registry.olm-ci.local:5443}
NAME=${HOST_PORT%:*}
PORT=${HOST_PORT##*:}
WORK=${RUNNER_TEMP:-/tmp}/olm-ci-registry
mkdir -p "$WORK"

wait_for() {
    local description=$1
    shift
    for _ in $(seq 1 60); do
        if "$@" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    echo "Timed out waiting for $description" >&2
    return 1
}

case "$MODE" in
host)
    openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=olm-ci-ca" \
        -keyout "$WORK/ca.key" -out "$WORK/ca.crt" 2>/dev/null
    openssl req -newkey rsa:2048 -nodes -subj "/CN=$NAME" \
        -keyout "$WORK/tls.key" -out "$WORK/tls.csr" 2>/dev/null
    printf 'subjectAltName=DNS:%s\n' "$NAME" >"$WORK/san.ext"
    openssl x509 -req -in "$WORK/tls.csr" -CA "$WORK/ca.crt" -CAkey "$WORK/ca.key" \
        -CAcreateserial -days 1 -extfile "$WORK/san.ext" -out "$WORK/tls.crt" 2>/dev/null
    ;;
olmv1)
    kubectl apply -f - <<EOF
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: olm-ci-registry
  namespace: cert-manager
spec:
  secretName: olm-ci-registry-tls
  dnsNames:
    - $NAME
  issuerRef:
    kind: ClusterIssuer
    name: olmv1-ca
EOF
    kubectl -n cert-manager wait certificate/olm-ci-registry --for=condition=Ready --timeout=120s
    for key in tls.crt tls.key ca.crt; do
        kubectl -n cert-manager get secret olm-ci-registry-tls -o "jsonpath={.data.${key//./\\.}}" \
            | base64 -d >"$WORK/$key"
    done

    # Resolve the registry name to the node from inside pods (catalogd, operator-controller).
    NODE_IP=$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')
    COREFILE=$(kubectl -n kube-system get configmap coredns -o jsonpath='{.data.Corefile}')
    COREFILE=$(printf '%s\n' "$COREFILE" | python3 -c '
import sys
name, ip = sys.argv[1], sys.argv[2]
lines = sys.stdin.read().splitlines()
entry = f"       {ip} {name}"
for i, line in enumerate(lines):
    if line.strip().startswith("hosts") and line.rstrip().endswith("{"):
        lines.insert(i + 1, entry)
        break
else:
    for i, line in enumerate(lines):
        if line.strip().startswith(".:53"):
            lines[i + 1:i + 1] = ["    hosts {", entry, "       fallthrough", "    }"]
            break
    else:
        sys.exit("CoreDNS Corefile has no .:53 server block")
print("\n".join(lines))
' "$NAME" "$NODE_IP")
    kubectl -n kube-system create configmap coredns --from-literal=Corefile="$COREFILE" \
        --dry-run=client -o yaml | kubectl apply -f -
    kubectl -n kube-system rollout restart deployment/coredns
    kubectl -n kube-system rollout status deployment/coredns --timeout=120s
    ;;
*)
    echo "Unknown mode: $MODE (expected host or olmv1)" >&2
    exit 1
    ;;
esac

# Host side: resolve the name locally and let Docker trust the registry for pushes.
grep -q " $NAME\$" /etc/hosts || echo "127.0.0.1 $NAME" | sudo tee -a /etc/hosts >/dev/null
sudo mkdir -p "/etc/docker/certs.d/$HOST_PORT"
sudo cp "$WORK/ca.crt" "/etc/docker/certs.d/$HOST_PORT/ca.crt"

docker run -d --name olm-ci-registry --restart=always -p "$PORT:$PORT" -v "$WORK:/certs:ro" \
    -e REGISTRY_HTTP_ADDR="0.0.0.0:$PORT" \
    -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/tls.crt \
    -e REGISTRY_HTTP_TLS_KEY=/certs/tls.key \
    ghcr.io/distribution/distribution:3.0 >/dev/null
wait_for "registry $HOST_PORT" curl -sf --cacert "$WORK/ca.crt" "https://$HOST_PORT/v2/"
echo "OLM CI registry ready at https://$HOST_PORT"

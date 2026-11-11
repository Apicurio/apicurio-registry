#!/bin/sh
# Runs once in the k3s image (service k8s-setup), after the API server is ready (step 10):
#  - a service account for the registry, allowed to call the TokenReview API (system:auth-delegator);
#  - a kubeconfig for the registry, with that account's token, written to the shared volume;
#  - two application service accounts, in namespaces "payments" and "auditors", whose tokens
#    demo.sh uses to call the registry.
set -eu
# k3s writes its admin kubeconfig for 127.0.0.1; from this container the API server is "k3s"
until [ -f /output/kubeconfig.yaml ]; do sleep 2; done
sed 's#https://127.0.0.1:6443#https://k3s:6443#' /output/kubeconfig.yaml > /tmp/admin.yaml
export KUBECONFIG=/tmp/admin.yaml
until kubectl get --raw /readyz > /dev/null 2>&1; do sleep 2; done
kubectl create namespace apicurio --dry-run=client -o yaml | kubectl apply -f -
kubectl -n apicurio create serviceaccount registry --dry-run=client -o yaml | kubectl apply -f -
kubectl create clusterrolebinding registry-tokenreview --clusterrole=system:auth-delegator \
  --serviceaccount=apicurio:registry --dry-run=client -o yaml | kubectl apply -f -
for ns in payments auditors; do
  kubectl create namespace "$ns" --dry-run=client -o yaml | kubectl apply -f -
  kubectl -n "$ns" create serviceaccount app --dry-run=client -o yaml | kubectl apply -f -
done
token=$(kubectl -n apicurio create token registry --duration 24h)
cat > /shared/kubeconfig <<KUBECONFIG
apiVersion: v1
kind: Config
clusters:
- name: k3s
  cluster:
    server: https://k3s:6443
    insecure-skip-tls-verify: true
users:
- name: registry
  user:
    token: $token
contexts:
- name: registry
  context: {cluster: k3s, user: registry}
current-context: registry
KUBECONFIG
chmod 644 /shared/kubeconfig
echo "kubeconfig for the registry written"

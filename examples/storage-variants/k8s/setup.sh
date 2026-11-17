#!/bin/sh
# Runs once in the k3s image (service k8s-setup), after the API server is ready:
#  - namespace "registry", with the first version of the registry data as ConfigMaps;
#  - a service account for the registry, allowed to get, list and watch ConfigMaps there;
#  - a kubeconfig for the registry, with that account's token, on the shared volume.
set -eu
until [ -f /output/kubeconfig.yaml ]; do sleep 2; done
# k3s writes its admin kubeconfig for 127.0.0.1; from this container the API server is "k3s"
sed 's#https://127.0.0.1:6443#https://k3s:6443#' /output/kubeconfig.yaml > /tmp/admin.yaml
export KUBECONFIG=/tmp/admin.yaml
until kubectl get --raw /readyz > /dev/null 2>&1; do sleep 2; done

kubectl create namespace registry --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n registry -f /configmaps/v1.yaml
kubectl -n registry create serviceaccount apicurio-registry --dry-run=client -o yaml | kubectl apply -f -
kubectl -n registry create role configmap-reader --verb=get,list,watch --resource=configmaps \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n registry create rolebinding apicurio-registry-configmaps --role=configmap-reader \
  --serviceaccount=registry:apicurio-registry --dry-run=client -o yaml | kubectl apply -f -

token=$(kubectl -n registry create token apicurio-registry --duration 24h)
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
  context: {cluster: k3s, user: registry, namespace: registry}
current-context: registry
KUBECONFIG
chmod 644 /shared/kubeconfig
echo "kubeconfig for the registry written"

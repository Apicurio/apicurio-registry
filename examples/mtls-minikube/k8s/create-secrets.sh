#!/bin/bash

# Script to create Kubernetes secrets from the generated certificates
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="${SCRIPT_DIR}/../certs"
NAMESPACE="apicurio-mtls"

# Check if certificates exist
if [ ! -f "${CERTS_DIR}/server-keystore.p12" ]; then
    echo "ERROR: Certificates not found. Please run certs/generate-certs.sh first."
    exit 1
fi

echo "Creating Kubernetes secrets for mTLS..."

# Create namespace if it doesn't exist
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# Create secret with server certificates
kubectl create secret generic registry-mtls-certs \
    --from-file=server-keystore.p12="${CERTS_DIR}/server-keystore.p12" \
    --from-file=server-truststore.p12="${CERTS_DIR}/server-truststore.p12" \
    --from-literal=keystore-password=apicurio \
    --from-literal=truststore-password=apicurio \
    --namespace=${NAMESPACE} \
    --dry-run=client -o yaml | kubectl apply -f -

# Create secret with client certificates (for client applications)
kubectl create secret generic registry-mtls-client-certs \
    --from-file=client-keystore.p12="${CERTS_DIR}/client-keystore.p12" \
    --from-file=client-truststore.p12="${CERTS_DIR}/client-truststore.p12" \
    --from-file=ca-cert.pem="${CERTS_DIR}/ca-cert.pem" \
    --from-literal=keystore-password=apicurio \
    --from-literal=truststore-password=apicurio \
    --namespace=${NAMESPACE} \
    --dry-run=client -o yaml | kubectl apply -f -

echo "Secrets created successfully in namespace: ${NAMESPACE}"

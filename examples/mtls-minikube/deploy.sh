#!/bin/bash

# Script to deploy Apicurio Registry with mTLS to Minikube
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="apicurio-mtls"

echo "========================================="
echo "Deploying Apicurio Registry with mTLS"
echo "========================================="
echo ""

# Step 1: Check if minikube is running
echo "1. Checking if minikube is running..."
if ! minikube status > /dev/null 2>&1; then
    echo "ERROR: Minikube is not running. Please start minikube first:"
    echo "  minikube start"
    exit 1
fi
echo "   Minikube is running"
echo ""

# Step 2: Generate certificates if they don't exist
if [ ! -f "${SCRIPT_DIR}/certs/server-keystore.p12" ]; then
    echo "2. Generating certificates..."
    "${SCRIPT_DIR}/certs/generate-certs.sh"
else
    echo "2. Certificates already exist, skipping generation..."
fi
echo ""

# Step 3: Create namespace
echo "3. Creating namespace..."
kubectl apply -f "${SCRIPT_DIR}/k8s/namespace.yaml"
echo ""

# Step 4: Create secrets
echo "4. Creating Kubernetes secrets..."
"${SCRIPT_DIR}/k8s/create-secrets.sh"
echo ""

# Step 5: Check if Apicurio Registry Operator is installed
echo "5. Checking for Apicurio Registry Operator..."
if ! kubectl get crd apicurioregistries3.registry.apicur.io > /dev/null 2>&1; then
    echo "   WARNING: Apicurio Registry Operator CRD not found."
    echo "   Please install the operator first. You can use:"
    echo "   kubectl apply -f https://github.com/Apicurio/apicurio-registry-operator/releases/latest/download/install.yaml"
    echo ""
    read -p "   Do you want to continue anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo "   Operator CRD found"
fi
echo ""

# Step 6: Deploy Apicurio Registry
echo "6. Deploying Apicurio Registry with mTLS configuration..."
kubectl apply -f "${SCRIPT_DIR}/k8s/apicurio-registry-mtls.yaml"
echo ""

# Step 7: Wait for deployment
echo "7. Waiting for deployment to be ready..."
echo "   This may take a few minutes..."
kubectl wait --for=condition=ready pod \
    -l app.kubernetes.io/name=apicurio-registry-mtls \
    -n ${NAMESPACE} \
    --timeout=300s 2>/dev/null || true
echo ""

# Step 8: Display access information
echo "========================================="
echo "Deployment completed!"
echo "========================================="
echo ""
echo "To access the registry, you need to set up port forwarding:"
echo "  kubectl port-forward -n ${NAMESPACE} svc/apicurio-registry-mtls-app-service 8443:8443"
echo ""
echo "Then you can access the registry at: https://localhost:8443"
echo ""
echo "Client certificates are available in: ${SCRIPT_DIR}/certs/"
echo "  - Client Keystore: client-keystore.p12"
echo "  - Client Truststore: client-truststore.p12"
echo "  - Password: apicurio"
echo ""
echo "To test the deployment, run:"
echo "  cd ${SCRIPT_DIR}/client && mvn clean compile exec:java"
echo ""

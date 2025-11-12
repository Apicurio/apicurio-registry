#!/bin/bash

# Script to undeploy Apicurio Registry with mTLS from Minikube
set -e

NAMESPACE="apicurio-mtls"

echo "========================================="
echo "Undeploying Apicurio Registry with mTLS"
echo "========================================="
echo ""

# Check if namespace exists
if ! kubectl get namespace ${NAMESPACE} > /dev/null 2>&1; then
    echo "Namespace ${NAMESPACE} does not exist. Nothing to undeploy."
    exit 0
fi

# Delete the namespace (this will delete all resources in it)
echo "Deleting namespace: ${NAMESPACE}"
kubectl delete namespace ${NAMESPACE}

echo ""
echo "========================================="
echo "Undeployment completed!"
echo "========================================="
echo ""
echo "Note: Generated certificates are still present in the certs/ directory."
echo "To remove them, run:"
echo "  rm -f certs/*.jks certs/*.p12 certs/*.pem certs/*.crt certs/*.key"
echo ""

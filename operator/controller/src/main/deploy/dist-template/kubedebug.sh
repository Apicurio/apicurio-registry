#!/bin/bash

set -e

# Default options
ZIP_OUTPUT=false

show_help() {
    cat << EOF
Usage: $0 [OPTIONS] <namespace> <cr_name>

This script can be used to export information that can help debug Apicurio Registry Operator issues.

Arguments:
    namespace   The Kubernetes namespace where the Apicurio Registry CR is deployed.
    cr_name     The name of the Apicurio Registry custom resource.

Options:
    -z, --zip   Create a zip archive of the kubedebug directory after collecting files
    -h, --help  Show this help message and exit

Examples:
    $0 default simple
    $0 --zip my-namespace my-registry
    $0 -z default simple

The script will create a 'kubedebug' directory containing YAML files and logs
for the Apicurio Registry resources and operator.
EOF
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_help
            exit 0
            ;;
        -z|--zip)
            ZIP_OUTPUT=true
            shift
            ;;
        -*)
            echo "Error: Unknown option $1" >&2
            show_help
            exit 1
            ;;
        *)
            break
            ;;
    esac
done

# Check required arguments
if [[ $# -ne 2 ]]; then
    echo "Error: Missing required arguments" >&2
    echo ""
    show_help
    exit 1
fi

namespace="$1"
cr_name="$2"

echo "Starting kubedebug for namespace: $namespace, CR name: $cr_name"
mkdir -p kubedebug

echo "Collecting Apicurio Registry CR..."
kubectl -n "$namespace" get apicurioregistry3 "$cr_name" -o yaml > "./kubedebug/apicurioregistry3-$cr_name.yaml"

echo "Collecting operand resources..."
for r in pod deployment service ingress route networkpolicy poddisruptionbudget; do
    echo "  Collecting $r resources..."
    for n in $(kubectl -n "$namespace" get "$r" -l "app=$cr_name" --no-headers -o custom-columns=":metadata.name" || true); do
        if [[ -n "$n" ]]; then
            kubectl -n "$namespace" get "$r" "$n" -o yaml > "./kubedebug/$r-$n.yaml"
            if [[ "$r" == "pod" ]]; then
                kubectl -n "$namespace" logs "$n" > "./kubedebug/$r-$n-logs.txt" || echo "Failed to get logs for pod $n" > "./kubedebug/$r-$n-logs.txt"
            fi
        fi
    done
done

echo "Collecting operator information..."
op_namespace=$(kubectl get pods --all-namespaces -l "app=apicurio-registry-operator" --no-headers -o custom-columns=":metadata.namespace" | head -n1)
op_name=$(kubectl get pods --all-namespaces -l "app=apicurio-registry-operator" --no-headers -o custom-columns=":metadata.name" | head -n1)

if [[ -n "$op_namespace" && -n "$op_name" ]]; then
    kubectl -n "$op_namespace" get pod "$op_name" -o yaml > "./kubedebug/pod-$op_name.yaml"
    kubectl -n "$op_namespace" logs "$op_name" > "./kubedebug/pod-$op_name-logs.txt" || echo "Failed to get operator logs" > "./kubedebug/pod-$op_name-logs.txt"
    # Operator ConfigMap
    kubectl -n "$op_namespace" get configmap -l "app=apicurio-registry-operator" -o yaml > "./kubedebug/configmap-operator.yaml" || echo "No operator configmaps found"
else
    echo "Warning: Apicurio Registry Operator not found"
fi

if [ "$ZIP_OUTPUT" = true ]; then
    echo "Creating zip archive of kubedebug directory..."
    timestamp=$(date +"%Y%m%d-%H%M%S")
    zip_filename="kubedebug-$namespace-$cr_name-$timestamp.zip"
    zip -r "$zip_filename" kubedebug
    echo "Zip archive created: $zip_filename"
    rm -rf kubedebug
    echo "Kubedebug completed successfully. Files archived as $zip_filename"
else
    echo "Kubedebug completed successfully. Files saved in ./kubedebug/"
fi

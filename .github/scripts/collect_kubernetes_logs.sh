#!/bin/bash
set -e
echo "Collecting tests logs"
mkdir -p artifacts
cp -r apicurio-registry-k8s-tests-e2e/tests-logs artifacts 
#!/bin/bash
set -e -a

E2E_APICURIO_PROJECT_DIR=$(pwd)

git clone https://github.com/Apicurio/apicurio-registry-k8s-tests-e2e.git

pushd apicurio-registry-k8s-tests-e2e

./scripts/install_kind.sh

KIND_CLUSTER_CONFIG=kind-config-big-cluster.yaml
make run-apicurio-base-ci
make run-apicurio-tests-with-clustered-tests

popd

set +e +a 
#!/bin/bash
set -e -a

E2E_APICURIO_PROJECT_DIR=$(pwd)
# BUNDLE_URL=$(pwd)/apicurio-registry-k8s-tests-e2e/apicurio-registry-operator/docs/resources/install.yaml

git clone https://github.com/Apicurio/apicurio-registry-k8s-tests-e2e.git

pushd apicurio-registry-k8s-tests-e2e

./scripts/install_kind.sh

# make run-apicurio-ci

E2E_EXTRA_MAVEN_ARGS=-Dit.test=RulesResourceIT#testRulesDeletedWithArtifact
E2E_APICURIO_TESTS_PROFILE=acceptance
KIND_CLUSTER_CONFIG=kind-config-big-cluster.yaml
make run-apicurio-base-ci
make run-clustered-tests

popd

set +e +a 
#!/bin/bash
set -e -a

E2E_APICURIO_PROJECT_DIR=$(pwd)

git clone https://github.com/Apicurio/apicurio-registry-k8s-tests-e2e.git

pushd apicurio-registry-k8s-tests-e2e

./scripts/setup-deps.sh

make pull-operator-repo

if [ "$E2E_APICURIO_TESTS_PROFILE" == "clustered" ]
then
    E2E_APICURIO_TESTS_PROFILE=smoke
    KIND_CLUSTER_CONFIG=kind-config-big-cluster.yaml
    make run-apicurio-base-ci
    make run-clustered-tests
else
    make run-apicurio-ci
fi

popd

set +e +a 

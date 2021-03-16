#!/bin/bash
set -e -a

E2E_APICURIO_PROJECT_DIR=$(pwd)
E2E_APICURIO_TESTS_PROFILE=acceptance

git clone https://github.com/Apicurio/apicurio-registry-k8s-tests-e2e.git

pushd apicurio-registry-k8s-tests-e2e

./scripts/install_kind.sh

make run-apicurio-ci

popd

set +e +a 
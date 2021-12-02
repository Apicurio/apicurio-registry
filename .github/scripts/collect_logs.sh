#!/bin/bash
set -e
echo "Collecting tests logs"
mkdir -p artifacts/logs
cp -r integration-tests/testsuite/target/logs artifacts
mkdir -p artifacts/failsafe-reports
cp -r integration-tests/testsuite/target/failsafe-reports artifacts
mkdir -p artifacts/legacy
cp -r integration-tests/legacy-tests/target/logs artifacts/legacy | true
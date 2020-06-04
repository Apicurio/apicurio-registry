#!/bin/bash
set -e
echo "Collecting tests logs"
mkdir -p artifacts/logs
cp -r tests/target/logs artifacts
mkdir -p artifacts/failsafe-reports
cp -r tests/target/failsafe-reports artifacts 
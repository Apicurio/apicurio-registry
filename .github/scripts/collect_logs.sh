#!/bin/bash
set -e
echo "Collecting tests logs"
mkdir -p artifacts/logs
mkdir -p artifacts/failsafe-reports

DIR="integration-tests/target"
if [ -d "$DIR" ]; then
    echo "Collecting testsuite logs"
    for name in logs failsafe-reports surefire-reports; do
        if [ -d "${DIR}/${name}" ]; then
            cp -r "${DIR}/${name}" artifacts/
        fi
    done
fi

mkdir -p artifacts/legacy
DIR="integration-tests/legacy-tests/target"
if [ -d "$DIR" ]; then
  echo "Collecting testsuite logs"
  if [ -d "${DIR}/logs" ]; then
    cp -r "${DIR}/logs" artifacts/legacy/
  fi
fi

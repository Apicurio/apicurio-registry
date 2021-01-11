#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

  "1.2.x")
       echo "Running Integration Tests for Branch '$BRANCH_NAME'"
       mvn verify -Pall -pl tests -Dmaven.javadoc.skip=true
       ;;

   "1.3.x")
       echo "Running Integration Tests for Branch '$BRANCH_NAME'"
       echo "Run Integration Tests - Streams"
       mvn verify -Pacceptance -Pstreams -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress -PdisableSerdesTest
       echo "Run Integration Tests - JPA"
       mvn verify -Pacceptance -Pjpa -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress -PdisableSerdesTest
       echo "Run Integration Tests - Infinispan"
       mvn verify -Pacceptance -Pinfinispan -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress -PdisableSerdesTest
       ;;

  "master" | "2.0.x")
       echo "Running Integration Tests for Branch '$BRANCH_NAME'"
       echo "Run Integration Tests - Streams"
       mvn verify -Pacceptance -Pstreams -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress
       echo "Run Integration Tests - SQL"
       mvn verify -Pacceptance -Psql -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress
       echo "Run Integration Tests - Infinispan"
       mvn verify -Pacceptance -Pinfinispan -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress
       echo "Run Integration Tests - Kafkasql"
       mvn verify -Pacceptance -Pkafkasql -pl tests -Dmaven.javadoc.skip=true --no-transfer-progress
       ;;

  *)
       # any other branch
       echo "Integration Tests Run Failed. Branch '$BRANCH_NAME' not supported."
       exit 1
       ;;

esac





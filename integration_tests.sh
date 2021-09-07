#!/bin/bash
#########################################################################################################
# This script runs all integration tests
#########################################################################################################

make SKIP_TESTS=true BUILD_FLAGS='-Dmaven.javadoc.skip=true --no-transfer-progress -Dmaven.wagon.httpconnectionManager.maxTotal=30 -Dmaven.wagon.http.retryHandler.count=5' build-all &&
./mvnw install -Pintegration-tests -pl integration-tests/integration-tests-common &&
./mvnw verify -Pintegration-tests -Pui -Pinmemory -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false &&
./mvnw verify -Pintegration-tests -Pci -Psql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pclustered -Psql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pci -Pkafkasql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pclustered -Pkafkasql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pmultitenancy -Psql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress -DtrimStackTrace=false &&
./mvnw verify -Pintegration-tests -Pmigration -Psql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pmigration -Pkafkasql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pauth -Psql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pauth -Pkafkasql -pl integration-tests/testsuite -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pci -Pkafkasql -pl integration-tests/legacy-tests -Dmaven.javadoc.skip=true --no-transfer-progress &&
./mvnw verify -Pintegration-tests -Pci -Psql -pl integration-tests/legacy-tests -Dmaven.javadoc.skip=true --no-transfer-progress

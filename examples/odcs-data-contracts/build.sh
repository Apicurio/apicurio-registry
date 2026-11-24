#!/usr/bin/env bash
# Builds the Avro serde from this checkout and compiles the Kafka producer and consumer of steps 9 and 12.
# Usage: ./build.sh   (JDK 17 or later)
set -euo pipefail
cd "$(dirname "$0")"
(cd ../.. && ./mvnw -q install -pl serdes/kafka/avro-serde -am -DskipTests -Dmaven.javadoc.skip=true -Dlocal)
../../mvnw -q -f pom.xml compile

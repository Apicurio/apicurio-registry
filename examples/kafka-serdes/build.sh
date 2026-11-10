#!/usr/bin/env bash
# Builds the tour (Kafka clients using the serdes), the Apicurio Registry Maven plugin it uses, and
# the Kafka Connect converter plugin directory, from this checkout (snapshots are not published).
# Usage: ./build.sh   (JDK 21 or later; the first run takes a few minutes, later runs are quick)
# demo.sh runs it when the tour is not built yet. Maven reads extra arguments from MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am \
  -pl utils/maven-plugin,examples/kafka-serdes/tour,examples/kafka-serdes/connect-plugin \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

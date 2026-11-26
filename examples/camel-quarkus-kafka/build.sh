#!/usr/bin/env bash
# Builds the Camel Quarkus application, and the Avro serde it uses, from this checkout (snapshots are
# not published). The result is target/quarkus-app/quarkus-run.jar, which demo.sh runs.
# Usage: ./build.sh   (JDK 21 or later; the first run takes a few minutes, later runs are quick)
# demo.sh runs it when the application is not built yet. Maven reads extra arguments from MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am -pl examples/camel-quarkus-kafka \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true -Dlocal

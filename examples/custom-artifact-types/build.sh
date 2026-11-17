#!/usr/bin/env bash
# Builds the MARKDOWN providers (target/custom-artifact-types.jar) against this checkout's
# schema-util-common (snapshots are not published). JDK 21 or later; Maven reads MAVEN_ARGS.
# demo.sh runs it when the jar is missing.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am -pl examples/custom-artifact-types \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

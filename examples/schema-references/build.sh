#!/usr/bin/env bash
# Builds, from this checkout, the Apicurio Registry Maven plugin that registers the schemas and the
# serdes client (snapshots are not published). Usage: ./build.sh   (JDK 21 or later; the first run
# takes a few minutes). demo.sh runs it when needed. Maven reads extra arguments from MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am \
  -pl utils/maven-plugin,examples/schema-references/registration,examples/schema-references/serdes \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

#!/usr/bin/env bash
# Builds, from this checkout, the clients that call the registry over TLS: the Java SDK and serdes
# checks, and the Maven plugin (snapshots are not published). Usage: ./build.sh   (JDK 21 or later;
# the first run takes a few minutes). demo.sh runs it when needed. Maven reads extra arguments from
# MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am \
  -pl utils/maven-plugin,examples/tls/clients \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true

#!/usr/bin/env bash
# Builds, from this checkout, the clients that authenticate to the registry: the Java SDK and serdes
# client, the Maven plugin and the CLI (snapshots are not published). Usage: ./build.sh   (JDK 21 or
# later; the first run takes a few minutes). demo.sh runs it when needed. Maven reads extra
# arguments from MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am -DcliSkipNative \
  -pl utils/maven-plugin,cli,examples/authentication/clients \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

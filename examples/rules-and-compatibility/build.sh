#!/usr/bin/env bash
# Builds the validation client (and the validation library it uses) from this checkout.
# Usage: ./build.sh   (JDK 21 or later; the first run takes a few minutes, later runs are quick)
# demo.sh runs it when the client is not built yet. Maven arguments can be passed in MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -pl examples/rules-and-compatibility/validation-client -am \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

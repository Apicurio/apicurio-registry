#!/usr/bin/env bash
# Builds the example's Java parts from this checkout (the serdes snapshots are not published): the
# greeting producer and consumer, the order service, the CDC consumer and the Debezium tracing SMT.
# Usage: ./build.sh   (JDK 21 or later; the first run takes a few minutes, later runs are quick)
# demo.sh runs it when the applications are not built yet. Maven reads extra arguments from MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")/../.."
./mvnw -q install -Pexamples -am \
  -pl examples/otel-tracing/producer,examples/otel-tracing/consumer,examples/otel-tracing/order-service,examples/otel-tracing/cdc-consumer,examples/otel-tracing/debezium-converter \
  -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true -Dassembly.skipAssembly=true

# Debezium Server with the OpenTelemetry Java agent and the example's tracing SMT.
# Build the SMT first (./build.sh); Compose builds this image from the example directory.
FROM quay.io/debezium/server:3.4

USER root

# The agent instruments Debezium's HTTP calls to the Registry and its Kafka producer
ARG OTEL_AGENT_VERSION=2.33.1
RUN curl -sSfL -o /debezium/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar \
    && chmod 644 /debezium/opentelemetry-javaagent.jar

# OtelActivateTracingSpan (debezium-converter module), shaded with its dependencies
COPY debezium-converter/target/apicurio-registry-examples-debezium-otel-converter-*.jar /debezium/lib/otel-tracing-smt.jar
RUN chmod 644 /debezium/lib/otel-tracing-smt.jar

USER jboss

ENV JAVA_OPTS="-javaagent:/debezium/opentelemetry-javaagent.jar"

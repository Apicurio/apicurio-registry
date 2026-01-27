FROM quay.io/debezium/server:3.0

USER root

# Download OpenTelemetry Java Agent
ARG OTEL_AGENT_VERSION=2.10.0
RUN curl -L -o /debezium/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar

# Make OTEL agent readable
RUN chmod 644 /debezium/opentelemetry-javaagent.jar

# Copy the custom OTEL Avro converter JAR (built from debezium-converter module)
# This JAR includes all dependencies (fat JAR) for the custom converter
COPY debezium-converter/target/apicurio-registry-examples-debezium-otel-converter-*.jar /debezium/lib/otel-avro-converter.jar
RUN chmod 644 /debezium/lib/otel-avro-converter.jar

USER jboss

# Set JAVA_OPTS to include the OTEL agent
ENV JAVA_OPTS="-javaagent:/debezium/opentelemetry-javaagent.jar"

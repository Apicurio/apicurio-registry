# Multi-stage Dockerfile for building and running the Quarkus consumer application

# Stage 1: Build
FROM registry.access.redhat.com/ubi8/openjdk-17:1.19 AS builder

USER root
WORKDIR /build

# Copy the Avro schema from producer (shared schema)
COPY --chown=185 ../producer/src/main/avro /build/producer-avro

# Copy Maven wrapper and pom files
COPY --chown=185 pom.xml .
COPY --chown=185 src src

# Build the application
RUN mvn package -DskipTests

# Stage 2: Runtime
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.19

ENV LANGUAGE='en_US:en'

# Copy the built artifact
COPY --from=builder --chown=185 /build/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=185 /build/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=185 /build/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=185 /build/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 185

ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]

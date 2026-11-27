# Dockerfile for running the pre-built Quarkus producer application
# NOTE: Run 'mvn clean package -DskipTests' in this directory before building the image

FROM registry.access.redhat.com/ubi10/openjdk-21-runtime:latest

ENV LANGUAGE='en_US:en'

# Copy the pre-built Quarkus application
COPY --chown=1001:0 target/quarkus-app/lib/ /deployments/lib/
COPY --chown=1001:0 target/quarkus-app/*.jar /deployments/
COPY --chown=1001:0 target/quarkus-app/app/ /deployments/app/
COPY --chown=1001:0 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080
USER 1001

# NOTE: -XX:-UseBASE64Intrinsics mitigates CVE-2026-114938 ("Java4Shell"), a memory corruption
# vulnerability in the OpenJDK Base64 decoder on x86-64 CPUs with AVX-512 VBMI support. Remove
# this flag once the base image ships a fixed OpenJDK build.
# See: https://quarkus.io/blog/java4shell/
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -XX:-UseBASE64Intrinsics"

WORKDIR /deployments
CMD java $JAVA_OPTIONS -jar quarkus-run.jar

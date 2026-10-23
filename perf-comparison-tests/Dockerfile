FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:d44297483e7dbb7d572ba292f0a6c18432b8d023ea89d52ff28dada6d6bb138d
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

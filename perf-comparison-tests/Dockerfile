FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:4ddb8f6fcace87e82d369cdf2dce4fcb9172f2c2e5c81be1a97ddc56d6fd6c15
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

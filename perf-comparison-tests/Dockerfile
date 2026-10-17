FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:23634568e0a325e45feb8b6bf744efaf2212db9a373f6b83f5b2811d0d4c1f74
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

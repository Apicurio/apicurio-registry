FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:2056c5139666c5e52f01af5b80f0def423a94c3fe824321d74041c11e20c9b72
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

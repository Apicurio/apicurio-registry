FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:c1e7e1d56e36b5a88a1c2931bad9c9e5226695238f8b3c814648de10aaf3afc9
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

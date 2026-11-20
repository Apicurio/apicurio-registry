FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:23fb65c7e4cbb94bc59d72fcfb8e78f2fdb865e0d1bc2a1e3d45a692a9e7d6ea
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

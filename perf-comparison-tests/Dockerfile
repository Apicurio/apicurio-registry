FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:6e31d14913ef7655fee3c7f7060125c2e240145ff8ae8825d972842891f03ee9
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

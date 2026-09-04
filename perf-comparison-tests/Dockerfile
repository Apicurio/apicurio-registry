FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:53fc57721acb1941bef7fcd400e35353dace05087309ff482bb6814714d3095d
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

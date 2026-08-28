FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:07122e5a2806f4d570fe3dfadeacd2d55b440b05f23e4639d1ae4966941431bb
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:6e31d14913ef7655fee3c7f7060125c2e240145ff8ae8825d972842891f03ee9
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
# NOTE: -XX:-UseBASE64Intrinsics mitigates CVE-2026-114938 ("Java4Shell"), a memory corruption
# vulnerability in the OpenJDK Base64 decoder on x86-64 CPUs with AVX-512 VBMI support. Remove
# this flag once the base image ships a fixed OpenJDK build.
# See: https://quarkus.io/blog/java4shell/
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-jar", "/opt/benchmark/benchmark.jar"]

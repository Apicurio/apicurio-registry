FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:531a21e0c98cd85c5c5663ad7b5316a90160dbca2e4324f740065a1788143dc6
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

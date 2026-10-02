FROM registry.access.redhat.com/ubi10/openjdk-21-runtime@sha256:6e2216b796e97edb7145d55300b0251c15834f45c21004824976a3024a1f7a2b
USER root
COPY target/apicurio-registry-perf-comparison-tests-*-jar-with-dependencies.jar /opt/benchmark/benchmark.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/benchmark
USER 185
ENTRYPOINT ["java", "-jar", "/opt/benchmark/benchmark.jar"]

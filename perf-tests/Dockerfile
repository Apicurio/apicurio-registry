# Runner image for the perf-main workflow's in-cluster test Job (see k8s/perf-job.yaml).
# Bundles the perf-tests fat jar (PerfTestRunner: runs the Gatling REST simulation and the Kafka
# serde load generator concurrently) with a JDK, since PerfTestRunner launches Gatling as a
# subprocess reusing this same jar's classpath (see PerfTestRunner.runGatling()).
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

USER root
COPY target/apicurio-registry-perf-tests-*-jar-with-dependencies.jar /opt/perf-tests/perf-tests.jar
RUN mkdir -p /results && chmod -R g+rwX /results /opt/perf-tests
USER 185

ENTRYPOINT ["java", "-jar", "/opt/perf-tests/perf-tests.jar"]

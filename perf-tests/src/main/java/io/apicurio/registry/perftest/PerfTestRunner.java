package io.apicurio.registry.perftest;

import io.apicurio.registry.perftest.kafka.KafkaLoadGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for the perf-main workflow's in-cluster test {@code Job} (see
 * {@code k8s/perf-job.yaml}). Runs the Gatling REST simulation and the Kafka serde-based load
 * generator concurrently against the same registry instance, then exits non-zero if either
 * component reports a failure (Gatling: an assertion failure such as error-rate threshold breach;
 * Kafka: excessive produce failures) - the workflow surfaces that exit code as a job failure and
 * notifies via Slack.
 *
 * <p>Gatling is launched as a separate JVM subprocess via its documented CLI entry point
 * ({@code io.gatling.app.Gatling}, the same class the official Gatling bundle's
 * {@code gatling.sh}/{@code gatling.bat} scripts invoke), reusing this jar's own classpath (it
 * bundles {@code gatling-charts-highcharts} and the compiled simulation classes). This avoids
 * depending on any undocumented in-process Gatling API.
 *
 * <p>The Kafka load generator only logs a summary (it isn't itself a latency-measurement tool; it
 * exists to generate realistic concurrent traffic through the serde path while Gatling measures
 * the REST API).
 */
public class PerfTestRunner {

    private static final Logger log = LoggerFactory.getLogger(PerfTestRunner.class);
    private static final String JVM_ADD_OPENS = "--add-opens";

    public static void main(String[] args) throws Exception {
        boolean skipKafka = Boolean.parseBoolean(System.getenv("PERF_SKIP_KAFKA"));
        if (skipKafka) {
            // Useful when running just the Gatling REST simulation against an externally-exposed
            // registry (see k8s/common/run-external-load.sh) - the Kafka load generator's own
            // bootstrap-servers address is normally only resolvable from inside the cluster, and
            // isn't needed to validate REST throughput/capacity.
            log.info("PERF_SKIP_KAFKA=true - running only the Gatling REST simulation.");
            System.exit(runGatling());
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> kafkaResult = executor.submit((Callable<Boolean>) KafkaLoadGenerator::run);
            Future<Integer> gatlingResult = executor.submit((Callable<Integer>) PerfTestRunner::runGatling);

            int gatlingExitCode = gatlingResult.get(30, TimeUnit.MINUTES);
            boolean kafkaOk = kafkaResult.get(1, TimeUnit.MINUTES);

            log.info("Gatling exit code: {}, Kafka load generator ok: {}", gatlingExitCode, kafkaOk);

            if (gatlingExitCode != 0 || !kafkaOk) {
                System.exit(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int runGatling() throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String resultsFolder = System.getProperty("gatling.resultsFolder", "/results/gatling");

        // Gatling requires these --add-opens on JDK 17+/21+ (the same flags its own bundled
        // gatling.sh/gatling.bat scripts pass) since it uses reflection into java.base
        // internals for its stats writers and Netty transport.
        ProcessBuilder pb = new ProcessBuilder(javaBin, JVM_ADD_OPENS, "java.base/java.lang=ALL-UNNAMED",
                JVM_ADD_OPENS, "java.base/java.util=ALL-UNNAMED", JVM_ADD_OPENS,
                "java.base/java.util.concurrent=ALL-UNNAMED", JVM_ADD_OPENS, "java.base/java.nio=ALL-UNNAMED",
                JVM_ADD_OPENS, "java.base/sun.nio.ch=ALL-UNNAMED", JVM_ADD_OPENS,
                "java.base/sun.security.ssl=ALL-UNNAMED", JVM_ADD_OPENS,
                "java.base/sun.security.util=ALL-UNNAMED", JVM_ADD_OPENS, "java.base/java.net=ALL-UNNAMED",
                "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-cp", classpath,
                "io.gatling.app.Gatling", "-s",
                "io.apicurio.registry.perftest.simulations.RegistryApiSimulation", "-rf", resultsFolder,
                "-rd", "Apicurio Registry perf-main run");
        pb.inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }
}


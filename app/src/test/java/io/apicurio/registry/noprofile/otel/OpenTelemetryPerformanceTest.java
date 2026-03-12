package io.apicurio.registry.noprofile.otel;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Performance benchmark test for OpenTelemetry instrumentation overhead in Apicurio Registry.
 *
 * This test measures the performance impact of enabling OTEL tracing, metrics, and logs
 * by running a series of common registry operations and measuring latency and throughput.
 *
 * To run this test:
 * ./mvnw test -pl app -Dtest=OpenTelemetryPerformanceTest -DOpenTelemetryPerformanceTest=enabled
 *
 * The test outputs detailed performance metrics for:
 * - Artifact creation (write operations)
 * - Artifact retrieval (read operations)
 * - Search operations (query operations)
 * - System info endpoint (lightweight operations)
 *
 * Compare results between OTelDisabledProfile and OTelEnabledProfile to measure overhead.
 */
@QuarkusTest
@TestProfile(OpenTelemetryPerformanceTest.OTelDisabledProfile.class)
public class OpenTelemetryPerformanceTest extends AbstractResourceTestBase {

    // Test configuration
    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURED_ITERATIONS = 200;
    private static final int ARTIFACT_BATCH_SIZE = 100;

    private static final String SIMPLE_AVRO_SCHEMA = """
            {
                "type": "record",
                "name": "TestRecord",
                "fields": [
                    {"name": "field1", "type": "string"},
                    {"name": "field2", "type": "int"}
                ]
            }
            """;

    private static final String OPENAPI_SCHEMA_TEMPLATE = """
            {
                "openapi": "3.0.2",
                "info": {
                    "title": "API-%s",
                    "version": "1.0.0",
                    "description": "Test API for performance benchmarking"
                },
                "paths": {}
            }
            """;

    /**
     * Test profile with OTEL completely disabled (baseline)
     */
    public static class OTelDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.enabled", "true",
                    "quarkus.otel.traces.enabled", "false",
                    "quarkus.otel.metrics.enabled", "false",
                    "quarkus.otel.logs.enabled", "false"
            );
        }

        @Override
        public String getConfigProfile() {
            return "otel-disabled";
        }
    }

    /**
     * Test profile with all OTEL signals enabled (100% sampling, no export)
     */
    public static class OTelEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.otel.enabled", "true",
                    "quarkus.otel.traces.enabled", "true",
                    "quarkus.otel.metrics.enabled", "true",
                    "quarkus.otel.logs.enabled", "true",
                    "quarkus.otel.traces.sampler", "always_on",
                    // Use NONE exporter - spans are created but not exported
                    // This isolates instrumentation overhead from network/export overhead
                    "quarkus.otel.traces.exporter", "none"
            );
        }

        @Override
        public String getConfigProfile() {
            return "otel-enabled";
        }
    }

    private boolean isTestEnabled() {
        return "enabled".equals(System.getProperty("OpenTelemetryPerformanceTest"));
    }

    @Test
    public void testOTelPerformance() throws Exception {
        if (!isTestEnabled()) {
            System.out.println("OpenTelemetry Performance Test is disabled.");
            System.out.println("To enable, run with: -DOpenTelemetryPerformanceTest=enabled");
            return;
        }

        String profileName = detectProfile();

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("OpenTelemetry Performance Benchmark");
        System.out.println("=".repeat(80));
        System.out.println("Profile: " + profileName);
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println("Artifact batch size: " + ARTIFACT_BATCH_SIZE);
        System.out.println("-".repeat(80));

        // Run benchmarks
        BenchmarkResult systemInfoResult = benchmarkSystemInfo();
        BenchmarkResult createArtifactResult = benchmarkArtifactCreation();
        BenchmarkResult getArtifactResult = benchmarkArtifactRetrieval(createArtifactResult.artifactIds);
        BenchmarkResult searchResult = benchmarkSearch();
        BenchmarkResult listGroupsResult = benchmarkListGroups();

        // Print results
        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("RESULTS - " + profileName);
        System.out.println("=".repeat(80));
        System.out.println();

        printResult("System Info (GET /system/info)", systemInfoResult);
        printResult("Create Artifact", createArtifactResult);
        printResult("Get Artifact", getArtifactResult);
        printResult("Search Artifacts", searchResult);
        printResult("List Groups", listGroupsResult);

        System.out.println("=".repeat(80));
        System.out.println();

        // Print summary in CSV format for easy comparison
        System.out.println("CSV Summary (copy for comparison):");
        System.out.println("profile,operation,iterations,total_ms,avg_ms,p50_ms,p95_ms,p99_ms,throughput_ops_sec");
        printCsvLine(profileName, "system_info", systemInfoResult);
        printCsvLine(profileName, "create_artifact", createArtifactResult);
        printCsvLine(profileName, "get_artifact", getArtifactResult);
        printCsvLine(profileName, "search", searchResult);
        printCsvLine(profileName, "list_groups", listGroupsResult);
        System.out.println();
    }

    private String detectProfile() {
        // Try to detect which profile is active
        try {
            String tracesEnabled = System.getProperty("quarkus.otel.traces.enabled", "unknown");
            if ("true".equals(tracesEnabled)) {
                return "OTEL_ENABLED";
            } else if ("false".equals(tracesEnabled)) {
                return "OTEL_DISABLED";
            }
        } catch (Exception e) {
            // ignore
        }
        return "OTEL_DISABLED (default)";
    }

    private BenchmarkResult benchmarkSystemInfo() {
        System.out.println("Benchmarking: System Info endpoint...");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            given().when().get("/registry/v3/system/info").then().statusCode(200);
        }

        // Measure
        List<Long> latencies = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long opStart = System.nanoTime();
            given().when().get("/registry/v3/system/info").then().statusCode(200);
            long opEnd = System.nanoTime();
            latencies.add((opEnd - opStart) / 1_000_000); // Convert to ms
        }

        long endTime = System.nanoTime();
        long totalMs = (endTime - startTime) / 1_000_000;

        return new BenchmarkResult(MEASURED_ITERATIONS, totalMs, latencies, null);
    }

    private BenchmarkResult benchmarkArtifactCreation() {
        System.out.println("Benchmarking: Artifact Creation...");
        String groupId = "perf-test-create-" + System.currentTimeMillis();
        List<String> artifactIds = new ArrayList<>();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            String artifactId = "warmup-" + i;
            createTestArtifact(groupId, artifactId, ArtifactType.AVRO, SIMPLE_AVRO_SCHEMA);
        }

        // Measure
        List<Long> latencies = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            String artifactId = "measured-" + i;
            long opStart = System.nanoTime();
            createTestArtifact(groupId, artifactId, ArtifactType.OPENAPI,
                    String.format(OPENAPI_SCHEMA_TEMPLATE, artifactId));
            long opEnd = System.nanoTime();
            latencies.add((opEnd - opStart) / 1_000_000);
            artifactIds.add(artifactId);
        }

        long endTime = System.nanoTime();
        long totalMs = (endTime - startTime) / 1_000_000;

        return new BenchmarkResult(MEASURED_ITERATIONS, totalMs, latencies, artifactIds, groupId);
    }

    private BenchmarkResult benchmarkArtifactRetrieval(List<String> artifactIds) {
        System.out.println("Benchmarking: Artifact Retrieval...");

        if (artifactIds == null || artifactIds.isEmpty()) {
            System.out.println("  Skipping - no artifacts available");
            return new BenchmarkResult(0, 0, List.of(), null);
        }

        String groupId = "perf-test-create-" + System.currentTimeMillis();
        // First, create some artifacts to retrieve
        for (int i = 0; i < Math.min(ARTIFACT_BATCH_SIZE, MEASURED_ITERATIONS); i++) {
            createTestArtifact(groupId, "get-test-" + i, ArtifactType.AVRO, SIMPLE_AVRO_SCHEMA);
        }

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            int idx = i % Math.min(ARTIFACT_BATCH_SIZE, MEASURED_ITERATIONS);
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("get-test-" + idx).get();
        }

        // Measure
        List<Long> latencies = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            int idx = i % Math.min(ARTIFACT_BATCH_SIZE, MEASURED_ITERATIONS);
            long opStart = System.nanoTime();
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("get-test-" + idx).get();
            long opEnd = System.nanoTime();
            latencies.add((opEnd - opStart) / 1_000_000);
        }

        long endTime = System.nanoTime();
        long totalMs = (endTime - startTime) / 1_000_000;

        return new BenchmarkResult(MEASURED_ITERATIONS, totalMs, latencies, null);
    }

    private BenchmarkResult benchmarkSearch() {
        System.out.println("Benchmarking: Search...");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            given()
                    .queryParam("name", "perf-test")
                    .when()
                    .get("/registry/v3/search/artifacts")
                    .then()
                    .statusCode(200);
        }

        // Measure
        List<Long> latencies = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long opStart = System.nanoTime();
            given()
                    .queryParam("name", "measured-" + (i % 50))
                    .when()
                    .get("/registry/v3/search/artifacts")
                    .then()
                    .statusCode(200);
            long opEnd = System.nanoTime();
            latencies.add((opEnd - opStart) / 1_000_000);
        }

        long endTime = System.nanoTime();
        long totalMs = (endTime - startTime) / 1_000_000;

        return new BenchmarkResult(MEASURED_ITERATIONS, totalMs, latencies, null);
    }

    private BenchmarkResult benchmarkListGroups() {
        System.out.println("Benchmarking: List Groups...");

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            given().when().get("/registry/v3/groups").then().statusCode(200);
        }

        // Measure
        List<Long> latencies = new ArrayList<>();
        long startTime = System.nanoTime();

        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            long opStart = System.nanoTime();
            given().when().get("/registry/v3/groups").then().statusCode(200);
            long opEnd = System.nanoTime();
            latencies.add((opEnd - opStart) / 1_000_000);
        }

        long endTime = System.nanoTime();
        long totalMs = (endTime - startTime) / 1_000_000;

        return new BenchmarkResult(MEASURED_ITERATIONS, totalMs, latencies, null);
    }

    private void createTestArtifact(String groupId, String artifactId, String artifactType, String content) {
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(artifactType);

        CreateVersion createVersion = new CreateVersion();
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(ContentTypes.APPLICATION_JSON);
        createVersion.setContent(versionContent);
        createArtifact.setFirstVersion(createVersion);

        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    private void printResult(String operation, BenchmarkResult result) {
        if (result.iterations == 0) {
            System.out.println(operation + ": SKIPPED");
            return;
        }

        System.out.println(operation + ":");
        System.out.printf("  Iterations:     %d%n", result.iterations);
        System.out.printf("  Total time:     %d ms%n", result.totalMs);
        System.out.printf("  Avg latency:    %.2f ms%n", result.getAvgLatency());
        System.out.printf("  P50 latency:    %d ms%n", result.getPercentile(50));
        System.out.printf("  P95 latency:    %d ms%n", result.getPercentile(95));
        System.out.printf("  P99 latency:    %d ms%n", result.getPercentile(99));
        System.out.printf("  Throughput:     %.2f ops/sec%n", result.getThroughput());
        System.out.println();
    }

    private void printCsvLine(String profile, String operation, BenchmarkResult result) {
        if (result.iterations == 0) {
            return;
        }
        System.out.printf("%s,%s,%d,%d,%.2f,%d,%d,%d,%.2f%n",
                profile, operation, result.iterations, result.totalMs,
                result.getAvgLatency(), result.getPercentile(50),
                result.getPercentile(95), result.getPercentile(99),
                result.getThroughput());
    }

    /**
     * Holds benchmark results with statistical analysis
     */
    private static class BenchmarkResult {
        final int iterations;
        final long totalMs;
        final List<Long> latencies;
        final List<String> artifactIds;
        final String groupId;
        private long[] sortedLatencies;

        BenchmarkResult(int iterations, long totalMs, List<Long> latencies, List<String> artifactIds) {
            this(iterations, totalMs, latencies, artifactIds, null);
        }

        BenchmarkResult(int iterations, long totalMs, List<Long> latencies, List<String> artifactIds, String groupId) {
            this.iterations = iterations;
            this.totalMs = totalMs;
            this.latencies = latencies;
            this.artifactIds = artifactIds;
            this.groupId = groupId;
            if (latencies != null && !latencies.isEmpty()) {
                this.sortedLatencies = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            }
        }

        double getAvgLatency() {
            if (latencies == null || latencies.isEmpty()) return 0;
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        long getPercentile(int percentile) {
            if (sortedLatencies == null || sortedLatencies.length == 0) return 0;
            int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.length) - 1;
            return sortedLatencies[Math.max(0, Math.min(index, sortedLatencies.length - 1))];
        }

        double getThroughput() {
            if (totalMs == 0) return 0;
            return (iterations * 1000.0) / totalMs;
        }
    }
}

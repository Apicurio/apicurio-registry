package io.apicurio.registry.noprofile.auth;

import io.apicurio.registry.auth.SearchAuthorizationFilter;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactSearchResultsDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.EditableArtifactMetaDataDto;
import io.apicurio.registry.storage.dto.EditableVersionMetaDataDto;
import io.apicurio.registry.storage.dto.OrderBy;
import io.apicurio.registry.storage.dto.OrderDirection;
import io.apicurio.registry.storage.dto.SearchFilter;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * POC benchmark to measure the overhead of application-layer authorization filtering
 * on search/list operations.
 *
 * This test answers the question: if we filter search results through an authorization
 * check at the application layer (instead of SQL-level filtering), how much overhead
 * does it add?
 *
 * Enable with: -DSearchFilteringBenchmarkTest=enabled
 */
@QuarkusTest
public class SearchFilteringBenchmarkTest {

    private static final Logger log = LoggerFactory.getLogger(SearchFilteringBenchmarkTest.class);

    private static final String GROUP_ID = "SearchFilteringBenchmarkTest";

    private static final String OPENAPI_CONTENT_TEMPLATE = "{"
            + "\"openapi\": \"3.0.2\","
            + "\"info\": {"
            + "\"title\": \"TITLE\","
            + "\"version\": \"VERSION\","
            + "\"description\": \"DESCRIPTION\""
            + "}"
            + "}";

    private static final int WARMUP_ITERATIONS = 10;
    private static final int MEASURE_ITERATIONS = 50;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    SearchAuthorizationFilter filter;

    private boolean isTestEnabled() {
        return "enabled".equals(System.getProperty("SearchFilteringBenchmarkTest"));
    }

    @Test
    public void benchmarkSearchFiltering() {
        if (!isTestEnabled()) {
            return;
        }

        log.info("========================================================================");
        log.info("= Search Filtering Benchmark - Seeding artifacts...                    =");
        log.info("========================================================================");

        int[] artifactCounts = { 100, 1000, 5000 };

        for (int numArtifacts : artifactCounts) {
            String prefix = "bench-" + numArtifacts + "-";
            seedArtifacts(prefix, numArtifacts);
            Set<SearchFilter> filters = Collections.singleton(SearchFilter.ofGroupId(GROUP_ID));

            runBenchmarkSuite(prefix, numArtifacts, filters);

            // Cleanup
            for (int i = 1; i <= numArtifacts; i++) {
                try {
                    storage.deleteArtifact(GROUP_ID, prefix + i);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    private void runBenchmarkSuite(String prefix, int numArtifacts, Set<SearchFilter> filters) {
        int pageSize = 20;

        // Baseline: direct storage search (no filtering)
        long baselineMs = measure(() ->
                storage.searchArtifacts(filters, OrderBy.name, OrderDirection.asc, 0, pageSize));

        // 10% deny rate: every 10th artifact denied
        Predicate<io.apicurio.registry.storage.dto.SearchedArtifactDto> deny10 =
                artifact -> !artifact.getArtifactId().endsWith("0");
        long deny10Ms = measure(() ->
                filter.searchArtifactsWithAuthz(filters, OrderBy.name, OrderDirection.asc,
                        0, pageSize, deny10));

        // 50% deny rate: even-numbered artifacts denied
        Predicate<io.apicurio.registry.storage.dto.SearchedArtifactDto> deny50 =
                artifact -> artifact.getArtifactId().hashCode() % 2 == 0;
        long deny50Ms = measure(() ->
                filter.searchArtifactsWithAuthz(filters, OrderBy.name, OrderDirection.asc,
                        0, pageSize, deny50));

        // 90% deny rate: only every 10th allowed
        Predicate<io.apicurio.registry.storage.dto.SearchedArtifactDto> deny90 =
                artifact -> artifact.getArtifactId().endsWith("5");
        long deny90Ms = measure(() ->
                filter.searchArtifactsWithAuthz(filters, OrderBy.name, OrderDirection.asc,
                        0, pageSize, deny90));

        // Deep pagination: page 10 with 50% deny
        long deepPageMs = measure(() ->
                filter.searchArtifactsWithAuthz(filters, OrderBy.name, OrderDirection.asc,
                        200, pageSize, deny50));

        // Verify correctness
        ArtifactSearchResultsDto baselineResults = storage.searchArtifacts(
                filters, OrderBy.name, OrderDirection.asc, 0, pageSize);
        ArtifactSearchResultsDto filteredResults = filter.searchArtifactsWithAuthz(
                filters, OrderBy.name, OrderDirection.asc, 0, pageSize, deny50);

        Assertions.assertEquals(pageSize, baselineResults.getArtifacts().size());
        Assertions.assertTrue(filteredResults.getArtifacts().size() <= pageSize);
        Assertions.assertTrue(filteredResults.getCount() < baselineResults.getCount());

        // Report
        log.info("========================================================================");
        log.info("= Results: {} artifacts, page size {}",
                numArtifacts, pageSize);
        log.info("=----------------------------------------------------------------------=");
        log.info("| Baseline (no filtering):     {} us", baselineMs);
        log.info("| 10% deny rate:               {} us  ({}x)", deny10Ms, ratio(deny10Ms, baselineMs));
        log.info("| 50% deny rate:               {} us  ({}x)", deny50Ms, ratio(deny50Ms, baselineMs));
        log.info("| 90% deny rate:               {} us  ({}x)", deny90Ms, ratio(deny90Ms, baselineMs));
        log.info("| Deep page (offset=200, 50%): {} us  ({}x)", deepPageMs, ratio(deepPageMs, baselineMs));
        log.info("========================================================================");
    }

    private void seedArtifacts(String prefix, int count) {
        long start = System.currentTimeMillis();
        for (int i = 1; i <= count; i++) {
            String artifactId = prefix + i;
            String title = "API " + artifactId;
            String description = "Artifact number " + i + " for benchmark testing.";
            Map<String, String> labels = new HashMap<>();
            labels.put("benchmark", "true");
            labels.put("index", String.valueOf(i));

            ContentHandle content = ContentHandle.create(
                    OPENAPI_CONTENT_TEMPLATE
                            .replace("TITLE", title)
                            .replace("DESCRIPTION", description)
                            .replace("VERSION", String.valueOf(i)));

            ContentWrapperDto versionContent = ContentWrapperDto.builder()
                    .content(content)
                    .contentType(ContentTypes.APPLICATION_JSON)
                    .build();
            EditableArtifactMetaDataDto metaData = new EditableArtifactMetaDataDto(
                    title, description, null, labels);
            EditableVersionMetaDataDto versionMetaData = EditableVersionMetaDataDto.builder()
                    .name(title)
                    .description(description)
                    .build();

            storage.createArtifact(GROUP_ID, artifactId, ArtifactType.OPENAPI, metaData, null,
                    versionContent, versionMetaData, List.of(), false, false, null);

            if (i % 500 == 0) {
                log.info("  Seeded {} / {} artifacts", i, count);
            }
        }
        long elapsed = System.currentTimeMillis() - start;
        log.info("  Seeded {} artifacts in {} ms", count, elapsed);
    }

    /**
     * Measures average execution time in milliseconds over MEASURE_ITERATIONS,
     * after WARMUP_ITERATIONS warmup runs.
     */
    private long measure(Supplier<?> operation) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.get();
        }

        // Measure
        long totalNs = 0;
        for (int i = 0; i < MEASURE_ITERATIONS; i++) {
            long startNs = System.nanoTime();
            operation.get();
            totalNs += System.nanoTime() - startNs;
        }

        return (totalNs / MEASURE_ITERATIONS) / 1_000; // Convert to microseconds
    }

    private String ratio(long value, long baseline) {
        if (baseline == 0) {
            return "N/A";
        }
        return String.format("%.1f", (double) value / baseline);
    }
}

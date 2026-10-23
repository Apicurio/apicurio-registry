package io.apicurio.registry.noprofile.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ArtifactVersionMetaDataDto;
import io.apicurio.registry.storage.dto.ConsumerVersionEntryDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.dto.DeprecationReadinessDto;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.awaitility.Awaitility.await;

/**
 * Behaviour every storage variant must share for usage telemetry: the same recorded events must give the
 * same per-version metrics, summary counts, consumer heatmap, deprecation readiness and retention result.
 * Events are written straight to the storage, so the REST layer, the buffer and the schedulers are not
 * involved. KafkaSQL applies writes asynchronously through its journal, so every assertion waits for the
 * last write of its fixture to become visible first.
 */
public abstract class AbstractUsageStorageTest extends AbstractResourceTestBase {

    private static final long DAY_MS = Duration.ofDays(1).toMillis();
    private static final long ACTIVE_MS = 7 * DAY_MS;
    private static final long STALE_MS = 30 * DAY_MS;
    private static final Duration VISIBLE_WITHIN = Duration.ofSeconds(30);

    protected abstract RegistryStorage storage();

    @Test
    public void metricsHeatmapAndReadinessAgreeOnTheSameEvents() {
        long now = System.currentTimeMillis();
        String group = TestUtils.generateGroupId("usage");
        Fixture f = createFixture(group);

        recordEvent(f.a1, "payments", now - 3 * DAY_MS);
        recordEvent(f.a1, "analytics", now - 2 * DAY_MS);
        recordEvent(f.a1, "analytics", now - DAY_MS);
        recordEvent(f.a2, "payments", now - 60_000);
        awaitFetches(group, "A", 4);

        List<SchemaUsageSummaryDto> metrics = storage().getArtifactUsageMetrics(group, "A");
        Assertions.assertEquals(2, metrics.size());

        SchemaUsageSummaryDto v1 = metrics.get(0);
        Assertions.assertEquals("1", v1.getVersion());
        Assertions.assertEquals(f.a1, v1.getGlobalId());
        Assertions.assertEquals(3, v1.getTotalFetches());
        Assertions.assertEquals(2, v1.getUniqueClients());
        Assertions.assertEquals(now - 3 * DAY_MS, v1.getFirstFetchedOn());
        Assertions.assertEquals(now - DAY_MS, v1.getLastFetchedOn());
        Assertions.assertEquals("analytics,payments", v1.getClientList());

        SchemaUsageSummaryDto v2 = metrics.get(1);
        Assertions.assertEquals("2", v2.getVersion());
        Assertions.assertEquals(f.a2, v2.getGlobalId());
        Assertions.assertEquals(1, v2.getTotalFetches());
        Assertions.assertEquals(1, v2.getUniqueClients());
        Assertions.assertEquals("payments", v2.getClientList());

        List<ConsumerVersionEntryDto> heatmap = storage().getConsumerVersionHeatmap(group, "A");
        Assertions.assertEquals(List.of("analytics|1|1|2", "payments|1|1|1", "payments|2|2|1"),
                heatmap.stream().map(AbstractUsageStorageTest::describe).toList());

        List<DeprecationReadinessDto> readiness = storage().getDeprecationReadiness(group, "A", "1");
        Assertions.assertEquals(
                List.of("analytics|" + (now - DAY_MS) + "|2", "payments|" + (now - 3 * DAY_MS) + "|1"),
                readiness.stream().map(AbstractUsageStorageTest::describe).toList());
    }

    /**
     * B is fetched directly 60 days ago but is referenced by A, which was fetched a day ago, so B counts
     * once and as active. C was last fetched 45 days ago. The summary spans the whole registry, so the
     * test compares it with a snapshot taken before the fixture was written.
     */
    @Test
    public void summaryCountsEveryVersionOnceAndLetsReferencesInheritLiveness() {
        long now = System.currentTimeMillis();
        String group = TestUtils.generateGroupId("usage");
        Fixture f = createFixture(group);
        UsageSummaryCountsDto before = storage().getUsageSummaryCounts(now, ACTIVE_MS, STALE_MS);

        recordEvent(f.b1, "legacy", now - 60 * DAY_MS);
        recordEvent(f.c1, "old", now - 45 * DAY_MS);
        recordEvent(f.a1, "app", now - DAY_MS);
        awaitFetches(group, "A", 1);
        awaitFetches(group, "B", 1);
        awaitFetches(group, "C", 1);

        UsageSummaryCountsDto after = storage().getUsageSummaryCounts(now, ACTIVE_MS, STALE_MS);
        Assertions.assertEquals(2, after.getActive() - before.getActive(), "A and B (through A)");
        Assertions.assertEquals(0, after.getStale() - before.getStale());
        Assertions.assertEquals(1, after.getDead() - before.getDead(), "C");
    }

    @Test
    public void retentionRemovesOnlyEventsOlderThanTheCutoff() {
        long now = System.currentTimeMillis();
        String group = TestUtils.generateGroupId("usage");
        Fixture f = createFixture(group);

        recordEvent(f.a1, "expired", now - 200 * DAY_MS);
        recordEvent(f.a1, "recent", now - DAY_MS);
        awaitFetches(group, "A", 2);
        Assertions.assertEquals("expired,recent", storage().getArtifactUsageMetrics(group, "A").get(0)
                .getClientList());

        storage().deleteOldUsageEvents(now - 180 * DAY_MS);

        awaitFetches(group, "A", 1);
        SchemaUsageSummaryDto left = storage().getArtifactUsageMetrics(group, "A").get(0);
        Assertions.assertEquals("recent", left.getClientList());
        Assertions.assertEquals(now - DAY_MS, left.getLastFetchedOn());
    }

    /**
     * Events fetched through {@code contentId} carry {@code globalId} 0 and match every version that has
     * that content. Version 1 of D is only ever fetched that way. Version 2 is fetched both ways, so the
     * three events fold into one version, with the counts summed over both kinds. The contents are unique
     * to the group so that no version of another test matches these content ids.
     */
    @Test
    public void contentMatchedEventsCountAgainstTheVersionsThatHaveThatContent() {
        long now = System.currentTimeMillis();
        String group = TestUtils.generateGroupId("usage");
        UsageSummaryCountsDto before = storage().getUsageSummaryCounts(now, ACTIVE_MS, STALE_MS);
        ArtifactVersionMetaDataDto d1 = storage().createArtifact(group, "D", ArtifactType.JSON, null, null,
                wrap("{\"type\":\"string\",\"description\":\"" + group + " d1\"}", null), null,
                Collections.emptyList(), false, false, null).getValue();
        ArtifactVersionMetaDataDto d2 = storage().createArtifactVersion(group, "D", null, ArtifactType.JSON,
                wrap("{\"type\":\"string\",\"description\":\"" + group + " d2\"}", null), null,
                Collections.emptyList(), false, false, null);

        recordByContent(d1.getContentId(), "batch", now - 5 * DAY_MS);
        recordByContent(d1.getContentId(), "batch", now - 4 * DAY_MS);
        recordEvent(d2.getGlobalId(), "payments", now - 3 * DAY_MS);
        recordByContent(d2.getContentId(), "payments", now - 2 * DAY_MS);
        recordByContent(d2.getContentId(), "batch", now - DAY_MS);
        awaitFetches(group, "D", 5);

        List<SchemaUsageSummaryDto> metrics = storage().getArtifactUsageMetrics(group, "D");
        Assertions.assertEquals(2, metrics.size());

        SchemaUsageSummaryDto v1 = metrics.get(0);
        Assertions.assertEquals("1", v1.getVersion());
        Assertions.assertEquals(d1.getGlobalId(), v1.getGlobalId(), "the version's id, not the event's 0");
        Assertions.assertEquals(2, v1.getTotalFetches());
        Assertions.assertEquals(1, v1.getUniqueClients());
        Assertions.assertEquals(now - 5 * DAY_MS, v1.getFirstFetchedOn());
        Assertions.assertEquals(now - 4 * DAY_MS, v1.getLastFetchedOn());
        Assertions.assertEquals("batch", v1.getClientList());

        SchemaUsageSummaryDto v2 = metrics.get(1);
        Assertions.assertEquals("2", v2.getVersion());
        Assertions.assertEquals(d2.getGlobalId(), v2.getGlobalId());
        Assertions.assertEquals(3, v2.getTotalFetches(), "one fetch by globalId and two by contentId");
        Assertions.assertEquals(2, v2.getUniqueClients());
        Assertions.assertEquals(now - 3 * DAY_MS, v2.getFirstFetchedOn());
        Assertions.assertEquals(now - DAY_MS, v2.getLastFetchedOn());
        Assertions.assertEquals("batch,payments", v2.getClientList());

        Assertions.assertEquals(List.of("batch|1|1|2", "batch|2|2|1", "payments|2|2|2"),
                storage().getConsumerVersionHeatmap(group, "D").stream()
                        .map(AbstractUsageStorageTest::describe).toList());
        Assertions.assertEquals(
                List.of("batch|" + (now - DAY_MS) + "|1", "payments|" + (now - 2 * DAY_MS) + "|2"),
                storage().getDeprecationReadiness(group, "D", "2").stream()
                        .map(AbstractUsageStorageTest::describe).toList());

        UsageSummaryCountsDto after = storage().getUsageSummaryCounts(now, ACTIVE_MS, STALE_MS);
        Assertions.assertEquals(2, after.getActive() - before.getActive(), "D1 and D2, each once");
        Assertions.assertEquals(0, after.getStale() - before.getStale());
        Assertions.assertEquals(0, after.getDead() - before.getDead());
    }

    private void recordEvent(long globalId, String clientId, long timestamp) {
        storage().recordUsageEvent(SchemaUsageEventDto.builder().globalId(globalId).contentId(0)
                .clientId(clientId).operation("SERIALIZE").eventTimestamp(timestamp).build());
    }

    private void recordByContent(long contentId, String clientId, long timestamp) {
        storage().recordUsageEvent(SchemaUsageEventDto.builder().globalId(0).contentId(contentId)
                .clientId(clientId).operation("DESERIALIZE").eventTimestamp(timestamp).build());
    }

    private long fetches(String group, String artifactId) {
        return storage().getArtifactUsageMetrics(group, artifactId).stream()
                .mapToLong(SchemaUsageSummaryDto::getTotalFetches).sum();
    }

    private void awaitFetches(String group, String artifactId, long expected) {
        await().atMost(VISIBLE_WITHIN).pollInterval(Duration.ofMillis(250)).untilAsserted(
                () -> Assertions.assertEquals(expected, fetches(group, artifactId)));
    }

    private static String describe(DeprecationReadinessDto r) {
        return r.getClientId() + "|" + r.getLastFetched() + "|" + r.getFetchCount();
    }

    private static String describe(ConsumerVersionEntryDto e) {
        return e.getClientId() + "|" + e.getVersionOrder() + "|" + e.getVersion() + "|" + e.getFetchCount();
    }

    private record Fixture(long a1, long a2, long b1, long c1) {
    }

    /** A has two versions and its first one references B; C stands alone. Contents are all distinct. */
    private Fixture createFixture(String group) {
        long b1 = create(group, "B", null, "{\"type\":\"string\"}");
        long a1 = create(group, "A", List.of(ArtifactReferenceDto.builder().groupId(group).artifactId("B")
                .version("1").name("b").build()), "{\"type\":\"object\"}");
        long a2 = createVersion(group, "A", "{\"type\":\"array\"}");
        long c1 = create(group, "C", null, "{\"type\":\"integer\"}");
        return new Fixture(a1, a2, b1, c1);
    }

    private long create(String group, String artifactId, List<ArtifactReferenceDto> references,
            String content) {
        return storage().createArtifact(group, artifactId, ArtifactType.JSON, null, null,
                wrap(content, references), null, Collections.emptyList(), false, false, null).getValue()
                .getGlobalId();
    }

    private long createVersion(String group, String artifactId, String content) {
        return storage().createArtifactVersion(group, artifactId, null, ArtifactType.JSON,
                wrap(content, null), null, Collections.emptyList(), false, false, null).getGlobalId();
    }

    private static ContentWrapperDto wrap(String content, List<ArtifactReferenceDto> references) {
        return ContentWrapperDto.builder().contentType(ContentTypes.APPLICATION_JSON)
                .content(ContentHandle.create(content))
                .references(references == null ? Collections.emptyList() : references).build();
    }
}

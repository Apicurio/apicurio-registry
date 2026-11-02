package io.apicurio.registry.storage;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.UsageSummaryCountsDto;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

/**
 * Runs the aggregation job by hand, with the scheduler off so that no scheduled run can change the table
 * between an assertion and the next, and checks what it does to the {@code schema_usage} rows.
 */
@QuarkusTest
@TestProfile(UsageAggregationJobTest.Profile.class)
@Tag(ApicurioTestTags.SLOW)
public class UsageAggregationJobTest extends AbstractResourceTestBase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.features.experimental.enabled", "true",
                    "apicurio.usage.telemetry.enabled", "true",
                    "apicurio.usage.retention-days", "30",
                    "quarkus.scheduler.enabled", "false");
        }
    }

    private static final long DAY_MS = Duration.ofDays(1).toMillis();
    private static final String CLIENT_PREFIX = "job-test-";

    @Inject
    UsageAggregationJob job;

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    HandleFactory handles;

    @Test
    public void runPublishesCountsAndDeletesOnlyEventsOlderThanRetention() throws Exception {
        String group = TestUtils.generateGroupId("usage-job");
        long activeId = createArtifact(group, "active", ArtifactType.JSON, "{\"type\":\"string\"}",
                ContentTypes.APPLICATION_JSON).getVersion().getGlobalId();
        long oldId = createArtifact(group, "old", ArtifactType.JSON, "{\"type\":\"integer\"}",
                ContentTypes.APPLICATION_JSON).getVersion().getGlobalId();
        long now = System.currentTimeMillis();
        UsageSummaryCountsDto before = storage.getUsageSummaryCounts(now, 7 * DAY_MS, 30 * DAY_MS);

        storage.recordUsageEvent(event(activeId, "recent-1", now - DAY_MS));
        storage.recordUsageEvent(event(activeId, "recent-2", now - 2 * DAY_MS));
        storage.recordUsageEvent(event(oldId, "expired-1", now - 100 * DAY_MS));
        storage.recordUsageEvent(event(oldId, "expired-2", now - 120 * DAY_MS));
        storage.recordUsageEvent(event(oldId, "expired-3", now - 31 * DAY_MS));

        Assertions.assertEquals(5, usageRows(), "rows before the job runs");

        job.run();

        Assertions.assertEquals(2, usageRows(), "only the two events inside the 30 day retention remain");
        UsageSummaryCountsDto counts = job.getCachedCounts();
        Assertions.assertEquals(1, counts.getActive() - before.getActive());
        Assertions.assertEquals(0, counts.getStale() - before.getStale());
        Assertions.assertEquals(1, counts.getDead() - before.getDead(),
                "counted from the events present before the cleanup");

        job.run();

        UsageSummaryCountsDto afterCleanup = job.getCachedCounts();
        Assertions.assertEquals(2, usageRows(), "a second run has nothing left to delete");
        Assertions.assertEquals(1, afterCleanup.getActive() - before.getActive());
        Assertions.assertEquals(0, afterCleanup.getDead() - before.getDead(),
                "a version whose events were all removed by retention is no longer counted");
    }

    private int usageRows() {
        return handles.<Integer, RuntimeException> withHandle(handle -> handle
                .createQuery("SELECT COUNT(*) FROM schema_usage WHERE clientId LIKE '" + CLIENT_PREFIX
                        + "%'")
                .mapTo(Integer.class).one());
    }

    private static SchemaUsageEventDto event(long globalId, String client, long timestamp) {
        return SchemaUsageEventDto.builder().globalId(globalId).contentId(0)
                .clientId(CLIENT_PREFIX + client).operation("SERIALIZE").eventTimestamp(timestamp).build();
    }
}

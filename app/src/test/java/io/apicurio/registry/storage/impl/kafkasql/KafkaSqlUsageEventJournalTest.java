package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.dto.SchemaUsageSummaryDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

/**
 * End-to-end coverage for the usage event journal path on the KafkaSQL variant.
 * <p>
 * {@link KafkaSqlMessageIndexTest} asserts that the usage event message classes are present in
 * {@link io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlMessageIndex}, but presence in the index
 * is not the same thing as the mutation actually surviving the journal. These tests exercise the path that
 * was broken: submit through {@link KafkaSqlRegistryStorage}, let the record go out to Kafka and come back
 * through the consumer thread, and then read the applied state back out of the local SQL projection.
 * <p>
 * That round trip matters here because both usage event operations are fire-and-forget — neither waits on
 * {@code KafkaSqlCoordinator} — and {@code KafkaSqlValueDeserializer} swallows a failed lookup into a null
 * value that the consumer discards as a tombstone. Nothing surfaces to the caller when it breaks, so an
 * assertion on the read-back side is the only thing that can catch it.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlUsageEventJournalTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = "KafkaSqlUsageEventJournalTest";

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"}}}";

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testRecordUsageEventSurvivesJournalRoundTrip() throws Exception {
        String artifactId = "record-usage-" + UUID.randomUUID();
        String clientId = "client-" + UUID.randomUUID();
        long globalId = createArtifactAndGetGlobalId(artifactId);
        long eventTimestamp = System.currentTimeMillis();

        // contentId is left at 0 so the read-back query matches on globalId only, keeping the
        // assertions below unambiguous.
        kafkaSqlRegistryStorage.recordUsageEvent(SchemaUsageEventDto.builder().globalId(globalId)
                .contentId(0).clientId(clientId).operation("READ").eventTimestamp(eventTimestamp).build());

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            List<SchemaUsageSummaryDto> metrics = kafkaSqlRegistryStorage.getArtifactUsageMetrics(GROUP_ID,
                    artifactId);

            Assertions.assertEquals(1, metrics.size(),
                    "Expected exactly one usage summary row after the journal round trip, but got "
                            + metrics.size() + ". A RecordUsageEvent1Message that cannot be deserialized "
                            + "is discarded by the consumer, so the event never reaches storage.");

            SchemaUsageSummaryDto summary = metrics.get(0);
            Assertions.assertEquals(globalId, summary.getGlobalId());
            Assertions.assertEquals(1, summary.getTotalFetches());
            Assertions.assertEquals(1, summary.getUniqueClients());
            Assertions.assertEquals(eventTimestamp, summary.getFirstFetchedOn());
            Assertions.assertEquals(eventTimestamp, summary.getLastFetchedOn());
            Assertions.assertTrue(summary.getClientList().contains(clientId),
                    "Expected the recorded client id '" + clientId + "' in the aggregated client list, but "
                            + "got '" + summary.getClientList() + "'.");
        });
    }

    @Test
    public void testDeleteOldUsageEventsSurvivesJournalRoundTrip() throws Exception {
        String artifactId = "delete-old-usage-" + UUID.randomUUID();
        String clientId = "client-" + UUID.randomUUID();
        long globalId = createArtifactAndGetGlobalId(artifactId);
        long oldTimestamp = System.currentTimeMillis() - Duration.ofDays(30).toMillis();

        kafkaSqlRegistryStorage.recordUsageEvent(SchemaUsageEventDto.builder().globalId(globalId)
                .contentId(0).clientId(clientId).operation("READ").eventTimestamp(oldTimestamp).build());

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL)
                .until(() -> !kafkaSqlRegistryStorage.getArtifactUsageMetrics(GROUP_ID, artifactId).isEmpty());

        // Prune everything older than a day. The recorded event is 30 days old, so it must be removed.
        kafkaSqlRegistryStorage
                .deleteOldUsageEvents(System.currentTimeMillis() - Duration.ofDays(1).toMillis());

        await().atMost(TIMEOUT).pollInterval(POLL_INTERVAL).untilAsserted(() -> {
            List<SchemaUsageSummaryDto> metrics = kafkaSqlRegistryStorage.getArtifactUsageMetrics(GROUP_ID,
                    artifactId);

            Assertions.assertTrue(metrics.isEmpty(),
                    "Expected the usage event older than the cutoff to be pruned after the journal round "
                            + "trip, but " + metrics.size() + " row(s) remain. A DeleteOldUsageEvents1Message "
                            + "that cannot be deserialized is discarded, so retention never runs.");
        });
    }

    private long createArtifactAndGetGlobalId(String artifactId) throws Exception {
        CreateArtifactResponse response = createArtifact(GROUP_ID, artifactId, ArtifactType.JSON, SCHEMA,
                ContentTypes.APPLICATION_JSON);
        return response.getVersion().getGlobalId();
    }
}
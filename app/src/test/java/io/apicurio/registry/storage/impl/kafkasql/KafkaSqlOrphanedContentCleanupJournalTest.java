package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteAllOrphanedContent0Message;
import io.apicurio.registry.storage.impl.kafkasql.messages.DeleteArtifactVersion3Message;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regression test for https://github.com/Apicurio/apicurio-registry/issues/10700.
 * <p>
 * Content ID allocation deduplicates by content hash against the local SQL state. If orphaned content
 * cleanup is applied locally without being recorded in the KafkaSQL journal, a node replaying the
 * journal never sees the deletion and reuses the original contentId when byte-identical content is
 * re-registered, while the node that ran the cleanup allocates a new one. The cleanup must therefore
 * be journaled between the orphaning operation and the re-registration, so that every replica applies
 * it at the same position.
 */
@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlOrphanedContentCleanupJournalTest extends AbstractResourceTestBase {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";
    private static final String GROUP = "default";

    @Inject
    KafkaSqlRegistryStorage kafkaSqlRegistryStorage;

    @Test
    public void testOrphanCleanupIsJournaledBetweenOrphaningAndReRegistration() throws Exception {
        String artifactId = "orphan-journal-" + UUID.randomUUID();
        String firstContent = "{\"type\":\"object\",\"title\":\"" + UUID.randomUUID() + "\"}";
        String orphanedContent = "{\"type\":\"object\",\"title\":\"" + UUID.randomUUID() + "\"}";

        createArtifact(GROUP, artifactId, ArtifactType.JSON, firstContent, ContentTypes.APPLICATION_JSON);
        long orphanedGlobalId = createArtifactVersion(GROUP, artifactId, orphanedContent,
                ContentTypes.APPLICATION_JSON);
        var orphanedVersion = kafkaSqlRegistryStorage.getArtifactVersionMetaData(orphanedGlobalId);
        long originalContentId = orphanedVersion.getContentId();

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        try {
            consumer.subscribe(List.of(JOURNAL_TOPIC));

            // Orphan the content, clean it up, then re-register byte-identical content.
            kafkaSqlRegistryStorage.deleteArtifactVersion(GROUP, artifactId, orphanedVersion.getVersion());
            kafkaSqlRegistryStorage.deleteAllOrphanedContent();
            long reRegisteredGlobalId = createArtifactVersion(GROUP, artifactId, orphanedContent,
                    ContentTypes.APPLICATION_JSON);

            // The local node removed the orphan, so it allocated a fresh contentId.
            long reRegisteredContentId = kafkaSqlRegistryStorage.getArtifactVersionMetaData(reRegisteredGlobalId)
                    .getContentId();
            Assertions.assertNotEquals(originalContentId, reRegisteredContentId,
                    "Orphaned content should have been removed locally before re-registration");

            List<String> journalTypes = readJournalTypesUntilReRegistration(consumer, artifactId);

            int deleteIdx = journalTypes.indexOf(DeleteArtifactVersion3Message.class.getSimpleName());
            int cleanupIdx = journalTypes.indexOf(DeleteAllOrphanedContent0Message.class.getSimpleName());
            int reRegisterIdx = journalTypes.size() - 1;

            Assertions.assertTrue(deleteIdx >= 0,
                    "Expected the version deletion in the journal, got: " + journalTypes);
            Assertions.assertTrue(cleanupIdx > deleteIdx && cleanupIdx < reRegisterIdx,
                    "Expected a " + DeleteAllOrphanedContent0Message.class.getSimpleName()
                            + " journal message between the version deletion and the re-registration, "
                            + "got: " + journalTypes + ". Without it, a replica replaying the journal "
                            + "reuses contentId " + originalContentId + " while this replica uses "
                            + reRegisteredContentId + ".");
        } finally {
            consumer.close();
        }
    }

    /**
     * Collects the message types of the relevant journal records produced after subscription, stopping
     * at the record that re-registers the version for the given artifact. Records unrelated to the
     * artifact are ignored, except for orphaned content cleanup (which carries no artifact id).
     */
    private List<String> readJournalTypesUntilReRegistration(KafkaConsumer<String, String> consumer,
            String artifactId) {
        String cleanupType = DeleteAllOrphanedContent0Message.class.getSimpleName();
        List<String> types = new ArrayList<>();
        boolean deleteSeen = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                Header header = record.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
                if (header == null) {
                    continue;
                }
                String type = new String(header.value(), StandardCharsets.UTF_8);
                boolean forArtifact = record.value() != null && record.value().contains(artifactId);
                if (forArtifact && type.equals(DeleteArtifactVersion3Message.class.getSimpleName())) {
                    deleteSeen = true;
                    types.add(type);
                } else if (deleteSeen && type.equals(cleanupType)) {
                    types.add(type);
                } else if (deleteSeen && forArtifact && type.startsWith("CreateArtifactVersion")) {
                    types.add(type);
                    return types;
                }
            }
        }
        Assertions.fail("Did not observe the re-registration in the journal, got: " + types);
        return types;
    }
}

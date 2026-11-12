package io.apicurio.registry.noprofile.storage;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlSubmitter;
import io.apicurio.registry.storage.impl.kafkasql.messages.UpdateArtifactMetaData3Message;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class FeatureLabelPreservationKafkaSqlTest extends FeatureLabelPreservationTest {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";
    private static final ObjectMapper MAPPER = new JsonMapper();

    /**
     * The journal holds the merged label map, so a replay writes the same labels. A replay of a
     * map without the feature label would delete it again.
     */
    @Test
    void theJournalRecordsTheMergedLabels() throws Exception {
        String artifactId = newArtifact();
        putArtifactLabels(artifactId, Map.of(CONTRACT_STATUS, "STABLE"));

        List<String> journaled = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(JOURNAL_TOPIC));
            putArtifactLabels(artifactId, Map.of("team", "b"));

            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && !hasUpdate(journaled, artifactId, "team")) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    Header type = rec.headers().lastHeader(KafkaSqlSubmitter.MESSAGE_TYPE_HEADER);
                    if (type != null && UpdateArtifactMetaData3Message.class.getSimpleName()
                            .equals(new String(type.value(), StandardCharsets.UTF_8))) {
                        journaled.add(rec.value());
                    }
                }
            }
        }

        JsonNode update = null;
        for (String value : journaled) {
            JsonNode node = MAPPER.readTree(value);
            if (artifactId.equals(node.path("artifactId").asString())
                    && node.path("metaData").path("labels").has("team")) {
                update = node;
            }
        }
        assertNotNull(update, "no journaled metadata update for " + artifactId);
        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "b"),
                MAPPER.convertValue(update.path("metaData").path("labels"), Map.class));
        assertEquals(Map.of(CONTRACT_STATUS, "STABLE", "team", "b"), artifactLabels(artifactId));
    }

    private static boolean hasUpdate(List<String> journaled, String artifactId, String key) {
        return journaled.stream().anyMatch(v -> v.contains(artifactId) && v.contains("\"" + key + "\""));
    }
}

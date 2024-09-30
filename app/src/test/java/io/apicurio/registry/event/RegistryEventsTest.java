package io.apicurio.registry.event;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rnorth.ducttape.unreliables.Unreliables;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(EventsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class RegistryEventsTest extends AbstractResourceTestBase {

    private KafkaConsumer<String, String> consumer;

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @BeforeAll
    public void init() {
        consumer = getConsumer(System.getProperty("bootstrap.servers"));
        consumer.subscribe(List.of("outbox.event.registry-events"));
    }

    @Test
    void createArtifactEventTest() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateArtifactName";
        final String description = "testCreateArtifactDescription";

        // Execution
        CreateArtifactResponse created = createArtifact(groupId, artifactId, ArtifactType.JSON,
                ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON, (createArtifact -> {
                    createArtifact.setName(name);
                    createArtifact.setDescription(description);
                    createArtifact.getFirstVersion().setVersion(version);
                }));

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getArtifact().getGroupId());
        assertEquals(artifactId, created.getArtifact().getArtifactId());
        assertEquals(version, created.getVersion().getVersion());
        assertEquals(name, created.getArtifact().getName());
        assertEquals(description, created.getArtifact().getDescription());
        assertEquals(ARTIFACT_CONTENT,
                new String(
                        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                                .byVersionExpression("branch=latest").content().get().readAllBytes(),
                        StandardCharsets.UTF_8));

        // Consume the event from the broker
        List<ConsumerRecord<String, String>> changeEvents = drain(consumer, 1);
        Assertions.assertTrue(changeEvents.get(0).value().contains(created.getArtifact().getArtifactId()));
    }

    private KafkaConsumer<String, String> getConsumer(String bootstrapServers) {
        return new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
    }

    private List<ConsumerRecord<String, String>> drain(KafkaConsumer<String, String> consumer,
            int expectedRecordCount) {

        List<ConsumerRecord<String, String>> allRecords = new ArrayList<>();

        Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(50)).iterator().forEachRemaining(allRecords::add);

            return allRecords.size() == expectedRecordCount;
        });

        return allRecords;
    }
}

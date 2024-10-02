package io.apicurio.registry.event.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
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

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_CREATED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_DELETED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_METADATA_UPDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(EventsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class RegistryEventsTest extends AbstractResourceTestBase {

    protected KafkaConsumer<String, String> consumer;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @BeforeAll
    public void init() {
        consumer = getConsumer(System.getProperty("bootstrap.servers"));
        consumer.subscribe(List.of("outbox.event.registry-events"));
    }

    @Test
    void createArtifactEvent() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateArtifactName";
        final String description = "testCreateArtifactDescription";

        ensureArtifactCreatedEvent(groupId, artifactId, version, name, description);
    }

    @Test
    public void updateArtifactMetadataEvent() throws Exception {
        // Preparation
        final String groupId = "updateArtifactMetadataEvent";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "updateArtifactMetadataEventName";
        final String description = "updateArtifactMetadataEventDescription";

        CreateArtifactResponse createdArtifact = ensureArtifactCreatedEvent(groupId, artifactId, version,
                name, description);

        EditableArtifactMetaData emd = new EditableArtifactMetaData();
        emd.setName("updateArtifactMetadataEventNameEdited");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

        // Consume the update events from the broker
        List<ConsumerRecord<String, String>> updateEvents = drain(consumer, 1);

        JsonNode updateEvent = readEventPayload(updateEvents.get(0));

        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_METADATA_UPDATED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
        Assertions.assertEquals("updateArtifactMetadataEventNameEdited", updateEvent.get("name").asText());
    }

    @Test
    public void deleteArtifactEvent() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactEvent";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "deleteArtifactEventName";
        final String description = "deleteArtifactEventDescription";

        CreateArtifactResponse createdArtifact = ensureArtifactCreatedEvent(groupId, artifactId, version,
                name, description);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();

        // Consume the delete event from the broker
        List<ConsumerRecord<String, String>> deleteEvents = drain(consumer, 1);

        JsonNode updateEvent = readEventPayload(deleteEvents.get(0));

        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_DELETED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
    }

    public CreateArtifactResponse ensureArtifactCreatedEvent(String groupId, String artifactId,
            String version, String name, String description) throws Exception {
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

        // Consume the create event from the broker
        List<ConsumerRecord<String, String>> createEvents = drain(consumer, 1);

        JsonNode createEvent = readEventPayload(createEvents.get(0));

        Assertions.assertEquals(groupId, createEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_CREATED.name(), createEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, createEvent.get("artifactId").asText());
        Assertions.assertEquals(name, createEvent.get("name").asText());

        return created;
    }

    protected KafkaConsumer<String, String> getConsumer(String bootstrapServers) {
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

    private JsonNode readEventPayload(ConsumerRecord<String, String> event) throws JsonProcessingException {
        String eventPayload = objectMapper.readTree(event.value()).asText();

        if (eventPayload.isBlank()) {
            eventPayload = event.value();
        }

        return objectMapper.readValue(eventPayload, JsonNode.class);
    }
}

package io.apicurio.registry.event.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.storage.StorageEventType;
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
import org.junit.jupiter.api.Order;
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

import static io.apicurio.registry.storage.StorageEventType.*;
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
    @Order(1)
    public void createGroup() throws Exception {
        // Preparation
        final String groupId = "createGroup";
        final String description = "createGroupDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        // Consume the create event from the broker
        List<JsonNode> events = drain(consumer, groupId, GROUP_CREATED);

        checkGroupEvent(groupId, events);
    }

    @Test
    @Order(2)
    public void updateGroupMetadata() throws Exception {
        // Preparation
        final String groupId = "updateGroupMetadata";
        final String description = "updateGroupMetadataDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        EditableGroupMetaData emd = new EditableGroupMetaData();
        emd.setDescription("updateArtifactMetadataEventDescriptionEdited");
        clientV3.groups().byGroupId(groupId).put(emd);

        // Consume the create event from the broker
        List<JsonNode> events = drain(consumer, groupId, GROUP_METADATA_UPDATED);

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(GROUP_METADATA_UPDATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(GROUP_METADATA_UPDATED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals("updateArtifactMetadataEventDescriptionEdited",
                updateEvent.get("description").asText());
    }

    @Test
    @Order(3)
    public void deleteGroupEvent() throws Exception {
        // Preparation
        final String groupId = "deleteGroupEvent";
        final String description = "deleteGroupEventDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        clientV3.groups().byGroupId(groupId).delete();

        // Consume the delete event from the broker
        List<JsonNode> deleteEvents = drain(consumer, groupId, GROUP_DELETED);

        JsonNode deleteEvent = null;

        for (JsonNode event : deleteEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(GROUP_DELETED.name())) {
                deleteEvent = event;
            }
        }

        Assertions.assertEquals(groupId, deleteEvent.get("groupId").asText());
        Assertions.assertEquals(GROUP_DELETED.name(), deleteEvent.get("eventType").asText());
    }

    @Test
    @Order(4)
    void createArtifactEvent() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateArtifactName";
        final String description = "testCreateArtifactDescription";

        ensureArtifactCreated(groupId, artifactId, version, name, description);
        List<JsonNode> events = drain(consumer, groupId, ARTIFACT_CREATED);
        checkArtifactEvent(groupId, artifactId, name, events);
    }

    @Test
    @Order(5)
    public void updateArtifactMetadataEvent() throws Exception {
        // Preparation
        final String groupId = "updateArtifactMetadataEvent";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "updateArtifactMetadataEventName";
        final String description = "updateArtifactMetadataEventDescription";

        ensureArtifactCreated(groupId, artifactId, version, name, description);

        EditableArtifactMetaData emd = new EditableArtifactMetaData();
        emd.setName("updateArtifactMetadataEventNameEdited");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(emd);

        // Consume the update events from the broker
        List<JsonNode> updateEvents = drain(consumer, groupId, ARTIFACT_METADATA_UPDATED);

        JsonNode updateEvent = null;

        for (JsonNode event : updateEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_METADATA_UPDATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_METADATA_UPDATED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
        Assertions.assertEquals("updateArtifactMetadataEventNameEdited", updateEvent.get("name").asText());
    }

    @Test
    @Order(5)
    public void deleteArtifactEvent() throws Exception {
        // Preparation
        final String groupId = "deleteArtifactEvent";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "deleteArtifactEventName";
        final String description = "deleteArtifactEventDescription";

        ensureArtifactCreated(groupId, artifactId, version, name, description);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();

        // Consume the delete event from the broker
        List<JsonNode> deleteEvents = drain(consumer, groupId, ARTIFACT_DELETED);

        JsonNode updateEvent = null;

        for (JsonNode event : deleteEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_DELETED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_DELETED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
    }

    private void checkGroupEvent(String groupId, List<JsonNode> events) {
        JsonNode createEvent = null;
        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)) {
                createEvent = event;
            }
        }

        Assertions.assertEquals(groupId, createEvent.get("groupId").asText());
        Assertions.assertEquals(GROUP_CREATED.name(), createEvent.get("eventType").asText());
    }

    private void checkArtifactEvent(String groupId, String artifactId, String name, List<JsonNode> events) {
        JsonNode artifactCreatedEvent = null;

        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_CREATED.name())) {
                artifactCreatedEvent = event;
            }
        }

        Assertions.assertEquals(groupId, artifactCreatedEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_CREATED.name(), artifactCreatedEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, artifactCreatedEvent.get("artifactId").asText());
        Assertions.assertEquals(name, artifactCreatedEvent.get("name").asText());
    }

    public CreateArtifactResponse ensureArtifactCreated(String groupId, String artifactId, String version,
            String name, String description) throws Exception {
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

        return created;
    }

    public void ensureGroupCreated(String groupId, String description, Labels labels) throws Exception {
        GroupMetaData created = createGroup(groupId, description, labels, (createGroup -> {
            createGroup.setDescription(description);
            createGroup.setGroupId(groupId);
            createGroup.setLabels(labels);
        }));

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getGroupId());
        assertEquals(description, created.getDescription());
    }

    protected KafkaConsumer<String, String> getConsumer(String bootstrapServers) {
        return new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
    }

    private List<JsonNode> drain(KafkaConsumer<String, String> consumer, String groupId,
            StorageEventType eventType) {

        List<JsonNode> events = new ArrayList<>();

        Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(50)).iterator().forEachRemaining(record -> {
                events.add(readEventPayload(record));
            });

            return events.stream().anyMatch(event -> event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(eventType.name()));

        });
        return events;
    }

    private JsonNode readEventPayload(ConsumerRecord<String, String> event) {
        String eventPayload = null;
        try {
            eventPayload = objectMapper.readTree(event.value()).asText();

            if (eventPayload.isBlank()) {
                eventPayload = event.value();
            }

            return objectMapper.readValue(eventPayload, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

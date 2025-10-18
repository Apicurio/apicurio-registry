package io.apicurio.registry.event.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.StorageEventType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_RULE_CONFIGURED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_CREATED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_DELETED;
import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_VERSION_METADATA_UPDATED;
import static io.apicurio.registry.storage.StorageEventType.GLOBAL_RULE_CONFIGURED;
import static io.apicurio.registry.storage.StorageEventType.GROUP_CREATED;
import static io.apicurio.registry.storage.StorageEventType.GROUP_DELETED;
import static io.apicurio.registry.storage.StorageEventType.GROUP_METADATA_UPDATED;
import static io.apicurio.registry.storage.StorageEventType.GROUP_RULE_CONFIGURED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestProfile(EventsTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class RegistryEventsTest extends AbstractResourceTestBase {

    private static final Logger log = LoggerFactory.getLogger(RegistryEventsTest.class);

    protected KafkaConsumer<String, String> consumer;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";

    @BeforeAll
    public void init() {
        consumer = getConsumer(System.getProperty("bootstrap.servers"));
        consumer.subscribe(List.of("outbox.event.registry-events"));
    }

    @Test
    public void createGroup() throws Exception {
        // Preparation
        final String groupId = "createGroup";
        final String description = "createGroupDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, GROUP_CREATED, Map.of("groupId", groupId));

        Assertions.assertEquals(1, events.size());
        checkGroupEvent(groupId, events);
    }

    @Test
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
        List<JsonNode> events = lookupEvent(consumer, GROUP_METADATA_UPDATED, Map.of("groupId", groupId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(GROUP_METADATA_UPDATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(GROUP_METADATA_UPDATED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals("updateArtifactMetadataEventDescriptionEdited",
                updateEvent.get("description").asText());
    }

    @Test
    public void deleteGroupEvent() throws Exception {
        // Preparation
        final String groupId = "deleteGroupEvent";
        final String description = "deleteGroupEventDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        clientV3.groups().byGroupId(groupId).delete();

        // Consume the delete event from the broker
        List<JsonNode> deleteEvents = lookupEvent(consumer, GROUP_DELETED, Map.of("groupId", groupId));

        JsonNode deleteEvent = null;

        for (JsonNode event : deleteEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(GROUP_DELETED.name())) {
                deleteEvent = event;
            }
        }

        Assertions.assertEquals(1, deleteEvents.size());
        Assertions.assertEquals(groupId, deleteEvent.get("groupId").asText());
        Assertions.assertEquals(GROUP_DELETED.name(), deleteEvent.get("eventType").asText());
    }

    @Test
    void createArtifactEvent() throws Exception {
        // Preparation
        final String groupId = "testCreateArtifact";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "testCreateArtifactName";
        final String description = "testCreateArtifactDescription";

        ensureArtifactCreated(groupId, artifactId, version, name, description);
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_CREATED,
                Map.of("groupId", groupId, "artifactId", artifactId));
        Assertions.assertEquals(1, events.size());
        checkArtifactEvent(groupId, artifactId, name, events.get(0));
    }

    @Test
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
        List<JsonNode> updateEvents = lookupEvent(consumer, ARTIFACT_METADATA_UPDATED,
                Map.of("groupId", groupId, "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : updateEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_METADATA_UPDATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, updateEvents.size());
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

        ensureArtifactCreated(groupId, artifactId, version, name, description);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();

        // Consume the delete event from the broker
        List<JsonNode> deleteEvents = lookupEvent(consumer, ARTIFACT_DELETED,
                Map.of("groupId", groupId, "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : deleteEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_DELETED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, deleteEvents.size());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_DELETED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
    }

    @Test
    public void createArtifactVersion() throws Exception {
        // Preparation
        final String groupId = "createArtifactVersion";

        final String artifactId = generateArtifactId();

        String name = "createArtifactVersionName";
        String description = "createArtifactVersionDescription";

        ensureArtifactCreated(groupId, artifactId, name, description);
        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_VERSION_CREATED,
                Map.of("groupId", groupId, "artifactId", artifactId));
        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_VERSION_CREATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_VERSION_CREATED.name(), updateEvent.get("eventType").asText());
    }

    @Test
    public void updateArtifactVersionMetadata() throws Exception {
        // Preparation
        final String groupId = "updateArtifactVersionMetadata";
        final String artifactId = generateArtifactId();

        String name = "updateArtifactVersionMetadataName";
        String description = "updateArtifactVersionMetadataDescription";

        ensureArtifactCreated(groupId, artifactId, name, description);

        EditableVersionMetaData emd = new EditableVersionMetaData();
        emd.setDescription("updateArtifactVersionMetadataEventDescriptionEdited");
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1").put(emd);

        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_VERSION_METADATA_UPDATED,
                Map.of("groupId", groupId, "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_VERSION_METADATA_UPDATED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_VERSION_METADATA_UPDATED.name(),
                updateEvent.get("eventType").asText());
        Assertions.assertEquals("updateArtifactVersionMetadataEventDescriptionEdited",
                updateEvent.get("description").asText());
    }

    @Test
    public void deleteArtifactVersion() throws Exception {
        // Preparation
        final String groupId = "createArtifactVersion";
        final String artifactId = generateArtifactId();
        String name = "deleteArtifactVersionName";
        String description = "deleteArtifactVersionDescription";

        ensureArtifactCreated(groupId, artifactId, name, description);

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression("1").delete();

        // Consume the delete event from the broker
        List<JsonNode> deleteEvents = lookupEvent(consumer, ARTIFACT_VERSION_DELETED,
                Map.of("groupId", groupId, "artifactId", artifactId));

        JsonNode deleteEvent = null;

        for (JsonNode event : deleteEvents) {
            if (event.get("groupId").asText().equals(groupId)
                    && event.get("eventType").asText().equals(ARTIFACT_VERSION_DELETED.name())) {
                deleteEvent = event;
            }
        }

        Assertions.assertEquals(1, deleteEvents.size());
        Assertions.assertEquals(groupId, deleteEvent.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_VERSION_DELETED.name(), deleteEvent.get("eventType").asText());
    }

    @Test
    public void globalRuleCreated() throws Exception {
        createGlobalRule(RuleType.VALIDITY, "SYNTAX_ONLY");

        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, GLOBAL_RULE_CONFIGURED,
                Map.of("ruleType", RuleType.VALIDITY.value()));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GLOBAL_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GLOBAL_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
    }

    @Test
    public void globalRuleUpdated() throws Exception {
        createGlobalRule(RuleType.VALIDITY, "SYNTAX_ONLY");

        // Consume the create event from the broker
        updateGlobalRule(RuleType.VALIDITY, ValidityLevel.FULL.name());

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, GLOBAL_RULE_CONFIGURED,
                Map.of("rule", ValidityLevel.FULL.name()));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GLOBAL_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GLOBAL_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.FULL.name(), updateEvent.get("rule").asText());
    }

    @Test
    public void globalRuleDeleted() throws Exception {
        createGlobalRule(RuleType.VALIDITY, "SYNTAX_ONLY");

        clientV3.admin().rules().byRuleType(RuleType.VALIDITY.name()).delete();

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, GLOBAL_RULE_CONFIGURED,
                Map.of("rule", ValidityLevel.NONE.name()));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GLOBAL_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GLOBAL_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.NONE.name(), updateEvent.get("rule").asText());
    }

    @Test
    public void groupRuleCreated() throws Exception {
        // Preparation
        final String groupId = "groupRuleConfigured";
        final String description = "groupRuleConfiguredDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        createGroupRule(groupId, RuleType.VALIDITY, "SYNTAX_ONLY");

        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, GROUP_RULE_CONFIGURED, Map.of("groupId", groupId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GROUP_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GROUP_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
    }

    @Test
    public void groupRuleUpdated() throws Exception {
        // Preparation
        final String groupId = "groupRuleUpdated";
        final String description = "groupRuleUpdatedDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        createGroupRule(groupId, RuleType.VALIDITY, "SYNTAX_ONLY");

        updateGroupRule(groupId, RuleType.VALIDITY, ValidityLevel.FULL.name());

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, GROUP_RULE_CONFIGURED,
                Map.of("groupId", groupId, "rule", ValidityLevel.FULL.name()));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GROUP_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GROUP_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.FULL.name(), updateEvent.get("rule").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
    }

    @Test
    public void groupRuleDeleted() throws Exception {
        // Preparation
        final String groupId = "groupRuleDeleted";
        final String description = "groupRuleDeletedDescription";

        Labels labels = new Labels();

        ensureGroupCreated(groupId, description, labels);

        createGroupRule(groupId, RuleType.VALIDITY, "SYNTAX_ONLY");

        clientV3.groups().byGroupId(groupId).rules().byRuleType(RuleType.VALIDITY.name()).delete();

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, GROUP_RULE_CONFIGURED,
                Map.of("groupId", groupId, "rule", ValidityLevel.NONE.name()));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(GROUP_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(GROUP_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.NONE.name(), updateEvent.get("rule").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
    }

    @Test
    public void artifactRuleConfigured() throws Exception {
        // Preparation
        final String groupId = "artifactRuleConfigured";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "artifactRuleConfiguredName";
        final String description = "artifactRuleConfiguredDescription";

        ensureArtifactCreated(groupId, artifactId, version, name, description);
        createArtifactRule(groupId, artifactId, RuleType.VALIDITY, "SYNTAX_ONLY");

        // Consume the create event from the broker
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_RULE_CONFIGURED,
                Map.of("groupId", groupId, "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(ARTIFACT_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(ARTIFACT_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
    }

    @Test
    public void artifactRuleUpdated() throws Exception {
        // Preparation
        final String groupId = "artifactRuleUpdated";
        final String description = "artifactRuleUpdatedDescription";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "artifactRuleUpdatedName";

        ensureArtifactCreated(groupId, artifactId, version, name, description);
        createArtifactRule(groupId, artifactId, RuleType.VALIDITY, "SYNTAX_ONLY");

        updateArtifactRule(groupId, artifactId, RuleType.VALIDITY, ValidityLevel.FULL.name());

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_RULE_CONFIGURED,
                Map.of("groupId", groupId, "rule", ValidityLevel.FULL.name(), "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(ARTIFACT_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(ARTIFACT_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.FULL.name(), updateEvent.get("rule").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
        Assertions.assertEquals(artifactId, updateEvent.get("artifactId").asText());
    }

    @Test
    public void artifactRuleDeleted() throws Exception {
        // Preparation
        final String groupId = "artifactRuleUpdated";
        final String description = "artifactRuleUpdatedDescription";
        final String artifactId = generateArtifactId();

        final String version = "1";
        final String name = "artifactRuleUpdatedName";

        ensureArtifactCreated(groupId, artifactId, version, name, description);
        createArtifactRule(groupId, artifactId, RuleType.VALIDITY, "SYNTAX_ONLY");

        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).rules()
                .byRuleType(RuleType.VALIDITY.name()).delete();

        // Lookup for the update event
        List<JsonNode> events = lookupEvent(consumer, ARTIFACT_RULE_CONFIGURED,
                Map.of("groupId", groupId, "rule", ValidityLevel.NONE.name(), "artifactId", artifactId));

        JsonNode updateEvent = null;

        for (JsonNode event : events) {
            if (event.get("eventType").asText().equals(ARTIFACT_RULE_CONFIGURED.name())) {
                updateEvent = event;
            }
        }

        Assertions.assertEquals(1, events.size());
        Assertions.assertEquals(ARTIFACT_RULE_CONFIGURED.name(), updateEvent.get("eventType").asText());
        Assertions.assertEquals(ValidityLevel.NONE.name(), updateEvent.get("rule").asText());
        Assertions.assertEquals(groupId, updateEvent.get("groupId").asText());
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

    private void checkArtifactEvent(String groupId, String artifactId, String name, JsonNode event) {
        Assertions.assertEquals(groupId, event.get("groupId").asText());
        Assertions.assertEquals(ARTIFACT_CREATED.name(), event.get("eventType").asText());
        Assertions.assertEquals(artifactId, event.get("artifactId").asText());
        Assertions.assertEquals(name, event.get("name").asText());
    }

    public CreateArtifactResponse ensureArtifactCreated(String groupId, String artifactId, String name,
                                                        String description) throws Exception {
        CreateArtifactResponse created = createArtifact(groupId, artifactId, ArtifactType.JSON,
                ARTIFACT_CONTENT, ContentTypes.APPLICATION_JSON, (createArtifact -> {
                    createArtifact.setName(name);
                    createArtifact.setDescription(description);
                }));

        // Assertions
        assertNotNull(created);
        assertEquals(groupId, created.getArtifact().getGroupId());
        assertEquals(artifactId, created.getArtifact().getArtifactId());
        assertEquals(name, created.getArtifact().getName());
        assertEquals(description, created.getArtifact().getDescription());

        return created;
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

    private List<JsonNode> lookupEvent(KafkaConsumer<String, String> consumer, String fieldLookupName,
                                       String fieldValue, StorageEventType eventType) {

        List<JsonNode> events = new ArrayList<>();

        Unreliables.retryUntilTrue(10, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(50)).iterator().forEachRemaining(record -> {
                events.add(readEventPayload(record));
            });

            return events.stream().anyMatch(event -> event.get(fieldLookupName).asText().equals(fieldValue)
                    && event.get("eventType").asText().equals(eventType.name()));

        });
        return events;
    }

    private List<JsonNode> lookupEvent(KafkaConsumer<String, String> consumer, StorageEventType eventType,
                                       Map<String, String> lookups) {

        log.info("Event type: {}", eventType.name());
        log.info("Lookups: {}", lookups);
        
        List<JsonNode> consumedEvents = new ArrayList<>();
        List<JsonNode> lookedUpEvents = new ArrayList<>();

        Unreliables.retryUntilTrue(20, TimeUnit.SECONDS, () -> {
            consumer.poll(Duration.ofMillis(100)).iterator().forEachRemaining(record -> {
                consumedEvents.add(readEventPayload(record));
            });

            log.info("Consumed events: {}", consumedEvents);
            boolean eventFound = false;
            for (JsonNode event : consumedEvents) {
                if (event.get("eventType").asText().equals(eventType.name()) && lookups.keySet().stream()
                        .allMatch(fieldName -> checkField(event, fieldName, lookups.get(fieldName)))) {
                    lookedUpEvents.add(event);
                    eventFound = true;
                } else {
                    eventFound = false;
                }
            }
            return eventFound;
        });
        return lookedUpEvents;
    }

    private boolean checkField(JsonNode event, String fieldName, String expectedFieldValue) {
        return event.get(fieldName).asText().equals(expectedFieldValue);
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

package io.apicurio.registry.storage.impl.kafkasql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.storage.dto.ContractRuleSetDto;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.apicurio.registry.storage.impl.kafkasql.KafkaSqlRegistryStorage.GLOBAL_CONTRACT_RULESET_COORDINATE;
import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class KafkaSqlContractEventsTest extends AbstractResourceTestBase {

    private static final String EVENTS_TOPIC = "registry-events";
    private static final String GROUP = "default";
    private static final Duration DUPLICATE_DETECTION_WINDOW = Duration.ofSeconds(1);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    KafkaSqlRegistryStorage storage;

    @Test
    public void testContractEventsArePublished() throws Exception {
        String artifactId = "contract-events-" + UUID.randomUUID();

        KafkaConsumer<String, String> consumer = createConsumerAtEnd();
        try {
            createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                    ContentTypes.APPLICATION_JSON);

            given().when().contentType("application/json").pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId)
                    .body("{\"status\": \"STABLE\", \"ownerTeam\": \"events-test\"}")
                    .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                    .statusCode(200);

            given().when().contentType("application/json").pathParam("groupId", GROUP)
                    .pathParam("artifactId", artifactId).body("{\"status\": \"DEPRECATED\"}")
                    .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status").then()
                    .statusCode(200);

            List<JsonNode> events = consumeContractEvents(consumer, artifactId, 2);

            JsonNode metadataEvent = findEvent(events, "CONTRACT_METADATA_UPDATED", null);
            Assertions.assertEquals(GROUP, metadataEvent.get("groupId").asText());
            Assertions.assertEquals(artifactId, metadataEvent.get("artifactId").asText());

            JsonNode statusEvent = findEvent(events, "CONTRACT_STATUS_CHANGED", null);
            Assertions.assertEquals(GROUP, statusEvent.get("groupId").asText());
            Assertions.assertEquals(artifactId, statusEvent.get("artifactId").asText());
            Assertions.assertEquals("STABLE", statusEvent.get("fromStatus").asText());
            Assertions.assertEquals("DEPRECATED", statusEvent.get("toStatus").asText());
        } finally {
            consumer.close();
        }
    }

    @Test
    public void testAllContractRulesetEventsArePublished() throws Exception {
        String artifactId = "contract-ruleset-events-" + UUID.randomUUID();
        ContractRuleSetDto ruleset = ContractRuleSetDto.builder()
                .domainRules(List.of()).migrationRules(List.of()).build();

        KafkaConsumer<String, String> consumer = createConsumerAtEnd();
        try {
            createArtifact(GROUP, artifactId, ArtifactType.JSON, "{\"type\":\"object\"}",
                    ContentTypes.APPLICATION_JSON);

            storage.setArtifactContractRuleset(GROUP, artifactId, ruleset);
            storage.deleteArtifactContractRuleset(GROUP, artifactId);
            storage.setVersionContractRuleset(GROUP, artifactId, "1", ruleset);
            storage.deleteVersionContractRuleset(GROUP, artifactId, "1");
            storage.setGlobalContractRuleset(ruleset);
            storage.deleteGlobalContractRuleset();

            List<JsonNode> events = consumeRulesetEvents(consumer, artifactId);

            assertRulesetEvent(events, GROUP, artifactId, null, "SET");
            assertRulesetEvent(events, GROUP, artifactId, null, "DELETE");
            assertRulesetEvent(events, GROUP, artifactId, "1", "SET");
            assertRulesetEvent(events, GROUP, artifactId, "1", "DELETE");
            assertRulesetEvent(events, GLOBAL_CONTRACT_RULESET_COORDINATE,
                    GLOBAL_CONTRACT_RULESET_COORDINATE, null, "SET");
            assertRulesetEvent(events, GLOBAL_CONTRACT_RULESET_COORDINATE,
                    GLOBAL_CONTRACT_RULESET_COORDINATE, null, "DELETE");
        } finally {
            consumer.close();
        }
    }

    private KafkaConsumer<String, String> createConsumerAtEnd() {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"),
                new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(EVENTS_TOPIC));

        long deadline = System.currentTimeMillis() + 10000;
        while (consumer.assignment().isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
        Assertions.assertFalse(consumer.assignment().isEmpty(),
                "Consumer was not assigned a partition for " + EVENTS_TOPIC);
        consumer.endOffsets(consumer.assignment()).forEach(consumer::seek);
        return consumer;
    }

    private List<JsonNode> consumeContractEvents(KafkaConsumer<String, String> consumer,
            String artifactId, int expectedCount) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20000;
        boolean expectedCountReached = false;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                JsonNode event = readEvent(consumerRecord);
                if (artifactId.equals(textValue(event, "artifactId"))
                        && Set.of("CONTRACT_METADATA_UPDATED", "CONTRACT_STATUS_CHANGED")
                                .contains(textValue(event, "eventType"))) {
                    events.add(event);
                }
            }
            if (!expectedCountReached && events.size() >= expectedCount) {
                expectedCountReached = true;
                deadline = System.currentTimeMillis() + DUPLICATE_DETECTION_WINDOW.toMillis();
            }
        }
        Assertions.assertEquals(expectedCount, events.size(),
                "Expected contract events for artifact " + artifactId + ", but found: " + events);
        return events;
    }

    private List<JsonNode> consumeRulesetEvents(KafkaConsumer<String, String> consumer,
            String artifactId) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        Set<String> expected = Set.of(
                eventKey(GROUP, artifactId, null, "SET"),
                eventKey(GROUP, artifactId, null, "DELETE"),
                eventKey(GROUP, artifactId, "1", "SET"),
                eventKey(GROUP, artifactId, "1", "DELETE"),
                eventKey(GLOBAL_CONTRACT_RULESET_COORDINATE,
                        GLOBAL_CONTRACT_RULESET_COORDINATE, null, "SET"),
                eventKey(GLOBAL_CONTRACT_RULESET_COORDINATE,
                        GLOBAL_CONTRACT_RULESET_COORDINATE, null, "DELETE"));
        Set<String> found = new HashSet<>();

        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline && !found.containsAll(expected)) {
            for (ConsumerRecord<String, String> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                JsonNode event = readEvent(consumerRecord);
                if ("CONTRACT_RULESET_CONFIGURED".equals(textValue(event, "eventType"))) {
                    String key = eventKey(textValue(event, "groupId"), textValue(event, "artifactId"),
                            textValue(event, "version"), textValue(event, "action"));
                    if (expected.contains(key)) {
                        events.add(event);
                        found.add(key);
                    }
                }
            }
        }
        Assertions.assertEquals(expected, found,
                "Expected all contract ruleset events, but found: " + events);
        return events;
    }

    private JsonNode readEvent(ConsumerRecord<String, String> record) throws Exception {
        return OBJECT_MAPPER.readTree(record.value());
    }

    private JsonNode findEvent(List<JsonNode> events, String eventType, String action) {
        List<JsonNode> matches = events.stream()
                .filter(event -> eventType.equals(textValue(event, "eventType")))
                .filter(event -> action == null || action.equals(textValue(event, "action")))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                "Expected one " + eventType + " event, but found: " + matches);
        return matches.get(0);
    }

    private void assertRulesetEvent(List<JsonNode> events, String groupId, String artifactId,
            String version, String action) {
        String expectedKey = eventKey(groupId, artifactId, version, action);
        List<JsonNode> matches = events.stream()
                .filter(event -> expectedKey.equals(eventKey(
                        textValue(event, "groupId"), textValue(event, "artifactId"),
                        textValue(event, "version"), textValue(event, "action"))))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                "Expected one contract ruleset event for " + expectedKey + ", but found: " + matches);
    }

    private static String eventKey(String groupId, String artifactId, String version, String action) {
        return groupId + "/" + artifactId + "/" + version + "/" + action;
    }

    private static String textValue(JsonNode event, String field) {
        return event.has(field) ? event.get(field).asText() : null;
    }
}

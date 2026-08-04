package io.apicurio.registry.storage.impl.kafkasql;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class KafkaSqlContractEventsTest extends AbstractResourceTestBase {

    private static final String EVENTS_TOPIC = "registry-events";
    private static final String GROUP = "default";

    @Test
    public void testContractEventsArePublished() throws Exception {
        String artifactId = "contract-events-" + UUID.randomUUID();

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        try {
            consumer.subscribe(List.of(EVENTS_TOPIC));

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

            List<String> events = consumeEvents(consumer, artifactId);

            Assertions.assertTrue(
                    events.stream().anyMatch(e -> e.contains("CONTRACT_METADATA_UPDATED")),
                    "Expected a CONTRACT_METADATA_UPDATED event for artifact " + artifactId
                            + " on the " + EVENTS_TOPIC + " topic. Events found: " + events);
            Assertions.assertTrue(events.stream().anyMatch(e -> e.contains("CONTRACT_STATUS_CHANGED")),
                    "Expected a CONTRACT_STATUS_CHANGED event for artifact " + artifactId + " on the "
                            + EVENTS_TOPIC + " topic. Events found: " + events);
        } finally {
            consumer.close();
        }
    }

    /**
     * Collects the events published for the given artifact, waiting until both contract events have
     * been seen or the timeout expires.
     */
    private List<String> consumeEvents(KafkaConsumer<String, String> consumer, String artifactId) {
        List<String> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> consumerRecord : consumer.poll(Duration.ofMillis(500))) {
                if (consumerRecord.value() != null && consumerRecord.value().contains(artifactId)) {
                    events.add(consumerRecord.value());
                }
            }
            if (events.stream().anyMatch(e -> e.contains("CONTRACT_METADATA_UPDATED"))
                    && events.stream().anyMatch(e -> e.contains("CONTRACT_STATUS_CHANGED"))) {
                break;
            }
        }
        return events;
    }
}

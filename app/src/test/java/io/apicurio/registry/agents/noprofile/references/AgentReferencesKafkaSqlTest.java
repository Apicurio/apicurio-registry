package io.apicurio.registry.agents.noprofile.references;

import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.storage.dto.ContentWrapperDto;
import io.apicurio.registry.storage.impl.kafkasql.KafkaSqlMessage;
import io.apicurio.registry.storage.impl.kafkasql.messages.CreateArtifact11Message;
import io.apicurio.registry.storage.impl.kafkasql.serde.KafkaSqlValueDeserializer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@QuarkusTest
@TestProfile(KafkasqlTestProfile.class)
public class AgentReferencesKafkaSqlTest extends AgentReferencesTest {

    private static final String JOURNAL_TOPIC = "kafkasql-journal";

    /**
     * A KafkaSQL node rebuilds its state by deserializing every journal message and dispatching it to
     * storage. Read the journal back with the same deserializer, check the referenced tool is journaled
     * before the agent, and replay the agent's message into a fresh storage to check it is recreated with
     * the same references.
     */
    @Test
    void referenceGraphIsReplayedFromTheJournal() {
        useIntegrityRule(IntegrityLevel.REFS_EXIST);
        create("tool", ArtifactType.MCP_TOOL, TOOL.formatted("get_weather"));
        create("agent", ArtifactType.AGENT_CARD, AGENT.formatted("Agent"), ref("tool", "get_weather"));

        Map<String, CreateArtifact11Message> created = new HashMap<>();
        Map<String, Long> offsets = new HashMap<>();
        KafkaSqlValueDeserializer deserializer = new KafkaSqlValueDeserializer();
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty("bootstrap.servers.external"),
                        ConsumerConfig.GROUP_ID_CONFIG, "tc-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new ByteArrayDeserializer())) {
            consumer.subscribe(List.of(JOURNAL_TOPIC));
            long deadline = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < deadline && created.size() < 2) {
                for (ConsumerRecord<String, byte[]> journalRecord : consumer.poll(Duration.ofMillis(500))) {
                    KafkaSqlMessage message = deserializer.deserialize(JOURNAL_TOPIC, journalRecord.headers(),
                            journalRecord.value());
                    if (message instanceof CreateArtifact11Message create && groupId.equals(create.getGroupId())) {
                        created.put(create.getArtifactId(), create);
                        offsets.put(create.getArtifactId(), journalRecord.offset());
                    }
                }
            }
        }

        assertEquals(Set.of("tool", "agent"), created.keySet());
        assertTrue(offsets.get("tool") < offsets.get("agent"), "the referenced tool must be replayed first");

        RegistryStorage replayTarget = mock(RegistryStorage.class);
        created.get("agent").dispatchTo(replayTarget);
        ArgumentCaptor<ContentWrapperDto> content = ArgumentCaptor.forClass(ContentWrapperDto.class);
        verify(replayTarget).createArtifact(eq(groupId), eq("agent"), eq(ArtifactType.AGENT_CARD), any(), any(),
                content.capture(), any(), anyList(), anyBoolean(), anyBoolean(), anyString());
        assertEquals(Set.of(groupId + "/tool@1:get_weather"), content.getValue().getReferences().stream()
                .map(AgentReferencesKafkaSqlTest::format).collect(Collectors.toSet()));
    }

    private static String format(ArtifactReferenceDto ref) {
        return ref.getGroupId() + "/" + ref.getArtifactId() + "@" + ref.getVersion() + ":" + ref.getName();
    }
}

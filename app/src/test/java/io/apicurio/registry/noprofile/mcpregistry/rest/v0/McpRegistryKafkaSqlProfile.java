package io.apicurio.registry.noprofile.mcpregistry.rest.v0;

import io.apicurio.registry.utils.tests.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.List;
import java.util.Map;

/**
 * kafkasql storage with the MCP Registry API enabled - used to confirm the full lifecycle (publish, read,
 * list, status, delete) works against a real Kafka-backed registry, not just SQL.
 */
public class McpRegistryKafkaSqlProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.storage.kind", "kafkasql",
                "apicurio.features.experimental.enabled", "true",
                "apicurio.mcp-registry.enabled", "true"
        );
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return List.of(new TestResourceEntry(KafkaTestContainerManager.class));
    }
}

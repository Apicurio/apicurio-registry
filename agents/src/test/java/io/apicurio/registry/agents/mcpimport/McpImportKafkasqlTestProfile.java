package io.apicurio.registry.agents.mcpimport;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaSQL storage with MCP import enabled, as in {@link McpImportTestProfile}.
 */
public class McpImportKafkasqlTestProfile extends KafkasqlTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(super.getConfigOverrides());
        props.putAll(McpImportTestProfile.importOverrides());
        return props;
    }
}

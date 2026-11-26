package io.apicurio.registry.agents.mcpimport;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * As {@link McpImportTestProfile}, with a total timeout short enough that a running import reports
 * progress every 2 seconds and is stale after 5 without it, so a test can watch both happen.
 */
public class McpImportProgressTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(McpImportTestProfile.importOverrides());
        props.put("apicurio.mcp-import.timeout.seconds", "1");
        props.put("apicurio.mcp-import.total-timeout.seconds", "4");
        return props;
    }
}

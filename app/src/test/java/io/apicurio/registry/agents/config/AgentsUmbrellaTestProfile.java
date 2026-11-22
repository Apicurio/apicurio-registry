package io.apicurio.registry.agents.config;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Only the umbrella switch, as a deployment that wants the agent registry would set it, with one
 * feature turned off explicitly, the shared outbound and instance defaults set, and a deprecated
 * name that the instance name overrides.
 */
public class AgentsUmbrellaTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.agents.enabled", "true");
        // An explicit value wins over the umbrella
        props.put("apicurio.mcp-tools.enabled", "false");
        props.put("apicurio.instance.name", "Acme Agents");
        // Deprecated: the instance name takes precedence
        props.put("apicurio.a2a.agent.name", "Legacy Name");
        props.put("apicurio.ai-catalog.host-name", "Legacy Host");
        // No per-feature override: both the MCP import and federation follow it
        props.put("apicurio.outbound.insecure-http.enabled", "true");
        props.put("apicurio.outbound.loopback.enabled", "true");
        // An explicit value wins over the shared outbound default
        props.put("apicurio.federation.peer.loopback.enabled", "false");
        return props;
    }
}

package io.apicurio.registry.noprofile.agents;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Switches on every agent registry feature flag, including the UI's Agents tab. Shared by the tests
 * that run with the agents source set deployed and by those that run without it (-DskipAgents), so
 * the only difference between the two is whether the code is present.
 */
public class AgentsFeatureEnabledProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "apicurio.features.experimental.enabled", "true",
                "apicurio.ui.features.agents.enabled", "true",
                "apicurio.a2a.enabled", "true",
                "apicurio.mcp-tools.enabled", "true",
                "apicurio.ai-catalog.enabled", "true",
                "apicurio.ard.enabled", "true"
        );
    }
}

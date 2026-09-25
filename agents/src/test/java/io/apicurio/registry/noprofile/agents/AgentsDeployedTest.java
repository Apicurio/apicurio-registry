package io.apicurio.registry.noprofile.agents;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Counterpart of core's {@code AgentsExcludedTest}: with the agents module deployed
 * and every agent flag on, the agent artifact types are registered, the discovery endpoints answer and
 * the UI is told to show the Agents tab.
 */
@QuarkusTest
@TestProfile(AgentsFeatureEnabledProfile.class)
public class AgentsDeployedTest extends AbstractResourceTestBase {

    @Test
    public void testAgentArtifactTypesAreRegistered() {
        List<String> types = clientV3.admin().config().artifactTypes().get().stream()
                .map(ArtifactTypeInfo::getName).toList();
        Assertions.assertTrue(types.containsAll(List.of(ArtifactType.AGENT_CARD, ArtifactType.MCP_TOOL,
                ArtifactType.MODEL_SCHEMA, ArtifactType.PROMPT_TEMPLATE)), "Registered types: " + types);
    }

    @Test
    public void testUiAgentsFeatureFollowsFlag() {
        Assertions.assertEquals(Boolean.TRUE, clientV3.system().uiConfig().get().getFeatures().getAgents());
    }

    @Test
    public void testWellKnownAgentCardIsServed() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        RestAssured.given().baseUri("http://localhost:" + port)
                .when().get("/.well-known/agent.json")
                .then().statusCode(200);
    }
}

package io.apicurio.registry.noprofile.agents;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactTypeInfo;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Runs only in builds without the agent registry feature (-DskipAgents; see the 'no-agents' profile in
 * app/pom.xml). Every agent flag is switched on, so any agent behaviour observed here would mean core
 * still carries agent code.
 */
@QuarkusTest
@TestProfile(AgentsFeatureEnabledProfile.class)
public class AgentsExcludedTest extends AbstractResourceTestBase {

    private static final List<String> CORE_ARTIFACT_TYPES = List.of(
            ArtifactType.PROTOBUF, ArtifactType.OPENAPI, ArtifactType.ASYNCAPI, ArtifactType.JSON,
            ArtifactType.AVRO, ArtifactType.GRAPHQL, ArtifactType.KCONNECT, ArtifactType.WSDL,
            ArtifactType.XSD, ArtifactType.XML, ArtifactType.ICEBERG_TABLE, ArtifactType.ICEBERG_VIEW,
            ArtifactType.OPENRPC, ArtifactType.ODCS_CONTRACT, ArtifactType.THRIFT);

    @Test
    public void testOnlyCoreArtifactTypesAreRegistered() {
        List<String> types = clientV3.admin().config().artifactTypes().get().stream()
                .map(ArtifactTypeInfo::getName).toList();
        Assertions.assertEquals(CORE_ARTIFACT_TYPES, types);
    }

    @Test
    public void testAgentArtifactTypeIsRejected() {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(TestUtils.generateArtifactId(),
                ArtifactType.AGENT_CARD, "{\"name\": \"agent\"}", ContentTypes.APPLICATION_JSON);
        RuleViolationProblemDetails error = Assertions.assertThrows(RuleViolationProblemDetails.class,
                () -> clientV3.groups().byGroupId(TestUtils.generateGroupId()).artifacts().post(createArtifact));
        Assertions.assertEquals(400, error.getStatus());
        Assertions.assertEquals("InvalidArtifactTypeException", error.getName());
        Assertions.assertEquals("Invalid or unknown artifact type: AGENT_CARD", error.getTitle());
    }

    @Test
    public void testUiAgentsFeatureIsOffDespiteFlag() {
        Assertions.assertEquals(Boolean.FALSE, clientV3.system().uiConfig().get().getFeatures().getAgents());
    }

    @Test
    public void testRootWellKnownEndpointsAreNotFound() {
        for (String path : List.of("/.well-known/agent.json", "/.well-known/agents", "/.well-known/mcp-tools",
                "/.well-known/ai-catalog.json", "/.well-known/ard.json")) {
            givenAtRoot().when().get(path).then().statusCode(404);
        }
    }

    @Test
    public void testV3WellKnownEndpointsAreNotFound() {
        for (String path : List.of("/registry/v3/well-known/agent.json", "/registry/v3/well-known/agents",
                "/registry/v3/well-known/mcp-tools", "/registry/v3/well-known/ai-catalog.json")) {
            given().when().get(path).then().statusCode(404);
        }
    }

    @Test
    public void testRenderEndpointIsNotFound() {
        given().when()
                .contentType("application/json")
                .body("{\"variables\": {\"name\": \"Alice\"}}")
                .post("/registry/v3/groups/default/artifacts/any-prompt/versions/1/render")
                .then().statusCode(404);
    }

    private static RequestSpecification givenAtRoot() {
        int port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);
        return RestAssured.given().baseUri("http://localhost:" + port);
    }
}

package io.apicurio.registry.noprofile.rest.a2a;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for auto-generating a companion AGENT_CARD artifact from an OPENAPI artifact's
 * {@code x-agent-card} vendor extension (#7135).
 */
@QuarkusTest
@TestProfile(OpenApiAgentCardEnabledProfile.class)
public class OpenApiAgentCardIntegrationTest extends AbstractResourceTestBase {

    private static final String OPENAPI = "OPENAPI";
    private static final String AGENT_CARD = "AGENT_CARD";

    private static final String SKILLS_BLOCK = """
            "capabilities": {},
            "skills": [
              { "id": "get-weather", "name": "Get Weather",
                "description": "Retrieve the current weather for a city", "tags": ["weather"] }
            ],
            "defaultInputModes": ["text"],
            "defaultOutputModes": ["text"]
            """;

    private String openApiWithCard(String version) {
        return """
                {
                  "openapi": "3.0.0",
                  "info": {
                    "title": "Weather API",
                    "description": "A weather service",
                    "version": "%s",
                    "x-agent-card": { %s }
                  },
                  "servers": [ { "url": "https://weather.example.com" } ],
                  "paths": {}
                }
                """.formatted(version, SKILLS_BLOCK);
    }

    private static final String OPENAPI_NO_CARD = """
            {
              "openapi": "3.0.0",
              "info": { "title": "Weather API", "version": "1.0.0" },
              "paths": {}
            }
            """;

    private static final String OPENAPI_MALFORMED_CARD = """
            {
              "openapi": "3.0.0",
              "info": {
                "title": "Weather API",
                "version": "1.0.0",
                "x-agent-card": { "capabilities": {} }
              },
              "servers": [ { "url": "https://weather.example.com" } ],
              "paths": {}
            }
            """;

    private String getVersionContent(String groupId, String artifactId, String version) throws Exception {
        try (InputStream stream = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).content().get()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void extensionPresent_companionCreated() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "weather-api";
        String companionArtifactId = artifactId + "-agent-card";

        createArtifact(groupId, artifactId, OPENAPI, openApiWithCard("1.0.0"), ContentTypes.APPLICATION_JSON);

        ArtifactMetaData companion = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(companionArtifactId).get();
        assertEquals(AGENT_CARD, companion.getArtifactType());
        assertEquals("true",
                companion.getLabels().getAdditionalData().get("apicurio.a2a.openapi-agent-card.generated"));
        assertEquals(groupId, companion.getLabels().getAdditionalData()
                .get("apicurio.a2a.openapi-agent-card.source-group-id"));
        assertEquals(artifactId, companion.getLabels().getAdditionalData()
                .get("apicurio.a2a.openapi-agent-card.source-artifact-id"));

        JsonNode cardContent = MAPPER.readTree(getVersionContent(groupId, companionArtifactId, "1"));
        assertEquals("Weather API", cardContent.get("name").asText());
        assertEquals("1.0.0", cardContent.get("version").asText());
        assertEquals("https://weather.example.com",
                cardContent.get("supportedInterfaces").get(0).get("url").asText());

        // The source OpenAPI artifact is marked with a pointer back to the generated companion.
        ArtifactMetaData source = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .get();
        assertEquals(companionArtifactId, source.getLabels().getAdditionalData()
                .get("apicurio.a2a.openapi-agent-card.artifact-id"));
    }

    @Test
    public void noExtension_noCompanionCreated() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "weather-api-no-card";
        String companionArtifactId = artifactId + "-agent-card";

        createArtifact(groupId, artifactId, OPENAPI, OPENAPI_NO_CARD, ContentTypes.APPLICATION_JSON);

        ProblemDetails problem = assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(companionArtifactId)
                        .get());
        assertEquals(404, problem.getStatus());
    }

    @Test
    public void malformedExtension_openApiWriteRejected() {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "weather-api-malformed-card";

        RuleViolationProblemDetails problem = assertThrows(RuleViolationProblemDetails.class,
                () -> createArtifact(groupId, artifactId, OPENAPI, OPENAPI_MALFORMED_CARD,
                        ContentTypes.APPLICATION_JSON));
        assertEquals(400, problem.getStatus());
        assertEquals("RuleViolationException", problem.getName());

        // Nothing was persisted - not the OpenAPI artifact, not a companion.
        assertThrows(ProblemDetails.class,
                () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get());
    }

    @Test
    public void updatedOpenApi_companionSynced() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "weather-api-sync";
        String companionArtifactId = artifactId + "-agent-card";

        createArtifact(groupId, artifactId, OPENAPI, openApiWithCard("1.0.0"), ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, openApiWithCard("2.0.0"), ContentTypes.APPLICATION_JSON);

        JsonNode v1 = MAPPER.readTree(getVersionContent(groupId, companionArtifactId, "1"));
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, companionArtifactId, "2"));
        assertEquals("1.0.0", v1.get("version").asText());
        assertEquals("2.0.0", v2.get("version").asText());
    }

    @Test
    public void unchangedOpenApiUpdate_noNewCompanionVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "weather-api-nochange";
        String companionArtifactId = artifactId + "-agent-card";

        createArtifact(groupId, artifactId, OPENAPI, openApiWithCard("1.0.0"), ContentTypes.APPLICATION_JSON);
        // A second OpenAPI version whose x-agent-card assembles to identical Agent Card content
        // (same version, same servers, same skills) should not create a second companion version.
        createArtifactVersion(groupId, artifactId, openApiWithCard("1.0.0"), ContentTypes.APPLICATION_JSON);

        var versions = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(companionArtifactId)
                .versions().get();
        assertTrue(versions.getCount() == 1, "Expected exactly one companion version, found "
                + versions.getCount());
    }
}

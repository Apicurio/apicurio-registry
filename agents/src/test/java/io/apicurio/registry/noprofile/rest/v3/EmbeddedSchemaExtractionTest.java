/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.apicurio.registry.util.JsonObjectMapper.MAPPER;

/**
 * Tests that embedded schemas in PROMPT_TEMPLATE and MODEL_SCHEMA artifacts are extracted and
 * auto-registered for every artifact version, not just the first one.
 */
@QuarkusTest
public class EmbeddedSchemaExtractionTest extends AbstractResourceTestBase {

    private static final String PROMPT_TEMPLATE = "PROMPT_TEMPLATE";
    private static final String MODEL_SCHEMA = "MODEL_SCHEMA";

    private static final String PROMPT_V1 = """
            {
              "templateId": "test",
              "name": "Test",
              "template": "Hello {{user}}",
              "outputSchema": {
                "type": "object",
                "properties": {
                  "greeting": { "type": "string" }
                }
              }
            }
            """;

    private static final String PROMPT_V2 = """
            {
              "templateId": "test",
              "name": "Test",
              "template": "Hello {{user}}",
              "outputSchema": {
                "type": "object",
                "properties": {
                  "greeting": { "type": "string" },
                  "sessionId": { "type": "string" }
                }
              }
            }
            """;

    private String getVersionContent(String groupId, String artifactId, String version) throws Exception {
        try (InputStream stream = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version).content().get()) {
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

    @Test
    public void testPromptTemplateOutputSchemaExtractedOnNewVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-with-schema";
        String schemaArtifactId = artifactId + "-output-schema";

        createArtifact(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V1, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, PROMPT_V2, ContentTypes.APPLICATION_JSON);

        // Both versions must reference the extracted schema rather than embedding it inline.
        JsonNode v1 = MAPPER.readTree(getVersionContent(groupId, artifactId, "1"));
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));

        Assertions.assertEquals(groupId + ":" + schemaArtifactId + ":1",
                v1.path("outputSchema").path("$ref").asText());
        Assertions.assertEquals(groupId + ":" + schemaArtifactId + ":2",
                v2.path("outputSchema").path("$ref").asText());

        // Each version must record an outbound reference to the schema version it uses.
        List<ArtifactReference> v1Refs = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("1").references().get();
        List<ArtifactReference> v2Refs = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("2").references().get();

        Assertions.assertEquals(1, v1Refs.size());
        Assertions.assertEquals("1", v1Refs.get(0).getVersion());
        Assertions.assertEquals(1, v2Refs.size());
        Assertions.assertEquals("2", v2Refs.get(0).getVersion());

        // The schema artifact must have a second version holding the updated schema.
        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(schemaArtifactId).versions().get();
        Assertions.assertEquals(2, versions.getCount());

        JsonNode schemaV1 = MAPPER.readTree(getVersionContent(groupId, schemaArtifactId, "1"));
        JsonNode schemaV2 = MAPPER.readTree(getVersionContent(groupId, schemaArtifactId, "2"));

        Assertions.assertTrue(schemaV1.path("properties").has("greeting"));
        Assertions.assertFalse(schemaV1.path("properties").has("sessionId"));
        Assertions.assertTrue(schemaV2.path("properties").has("greeting"));
        Assertions.assertTrue(schemaV2.path("properties").has("sessionId"));
    }

    @Test
    public void testPromptTemplateOutputSchemaReusedWhenUnchanged() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-stable-schema";
        String schemaArtifactId = artifactId + "-output-schema";

        createArtifact(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V1, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, PROMPT_V1, ContentTypes.APPLICATION_JSON);

        // An unchanged schema must be reused instead of producing a redundant version.
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));
        Assertions.assertEquals(groupId + ":" + schemaArtifactId + ":1",
                v2.path("outputSchema").path("$ref").asText());

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(schemaArtifactId).versions().get();
        Assertions.assertEquals(1, versions.getCount());
    }

    @Test
    public void testModelSchemaEmbeddedSchemasExtractedOnNewVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "model-with-schemas";
        String outputSchemaArtifactId = artifactId + "-output-schema";

        String modelV1 = """
                {
                  "name": "Test Model",
                  "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                  "output": { "type": "object", "properties": { "text": { "type": "string" } } }
                }
                """;
        String modelV2 = """
                {
                  "name": "Test Model",
                  "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                  "output": { "type": "object", "properties": { "text": { "type": "string" }, "tokens": { "type": "integer" } } }
                }
                """;

        createArtifact(groupId, artifactId, MODEL_SCHEMA, modelV1, ContentTypes.APPLICATION_JSON);
        createArtifactVersion(groupId, artifactId, modelV2, ContentTypes.APPLICATION_JSON);

        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));

        // The unchanged input schema is reused; the changed output schema gets a new version.
        Assertions.assertEquals(groupId + ":" + artifactId + "-input-schema:1",
                v2.path("input").path("$ref").asText());
        Assertions.assertEquals(groupId + ":" + outputSchemaArtifactId + ":2",
                v2.path("output").path("$ref").asText());

        JsonNode outputSchemaV2 = MAPPER.readTree(getVersionContent(groupId, outputSchemaArtifactId, "2"));
        Assertions.assertTrue(outputSchemaV2.path("properties").has("tokens"));
    }

    @Test
    public void testPromptTemplateOutputSchemaExtractedOnIfExistsCreateVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-ifexists-create";
        String schemaArtifactId = artifactId + "-output-schema";

        createArtifact(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V1, ContentTypes.APPLICATION_JSON);
        postWithIfExists(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V2, IfArtifactExists.CREATE_VERSION);

        // The version added through ifExists must be rewritten exactly like one added through
        // createArtifactVersion: $ref instead of the inline schema, plus an outbound reference.
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));
        Assertions.assertEquals(groupId + ":" + schemaArtifactId + ":2",
                v2.path("outputSchema").path("$ref").asText());

        List<ArtifactReference> v2Refs = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression("2").references().get();
        Assertions.assertEquals(1, v2Refs.size());
        Assertions.assertEquals(schemaArtifactId, v2Refs.get(0).getArtifactId());
        Assertions.assertEquals("2", v2Refs.get(0).getVersion());

        JsonNode schemaV2 = MAPPER.readTree(getVersionContent(groupId, schemaArtifactId, "2"));
        Assertions.assertTrue(schemaV2.path("properties").has("sessionId"));
    }

    @Test
    public void testPromptTemplateFindOrCreateVersionMatchesResubmittedContent() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "prompt-ifexists-find";
        String schemaArtifactId = artifactId + "-output-schema";

        createArtifact(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V1, ContentTypes.APPLICATION_JSON);

        // Re-submitting the original content must find version 1, not create a duplicate, even though
        // what is stored is the rewritten content with a $ref.
        CreateArtifactResponse found = postWithIfExists(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V1,
                IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertEquals("1", found.getVersion().getVersion());
        Assertions.assertEquals(1, clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().get().getCount());
        Assertions.assertEquals(1, clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(schemaArtifactId).versions().get().getCount());

        // Changed content has no match, so a new, rewritten version is created.
        CreateArtifactResponse created = postWithIfExists(groupId, artifactId, PROMPT_TEMPLATE, PROMPT_V2,
                IfArtifactExists.FIND_OR_CREATE_VERSION);
        Assertions.assertEquals("2", created.getVersion().getVersion());
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));
        Assertions.assertEquals(groupId + ":" + schemaArtifactId + ":2",
                v2.path("outputSchema").path("$ref").asText());
    }

    @Test
    public void testModelSchemaEmbeddedSchemasExtractedOnIfExistsCreateVersion() throws Exception {
        String groupId = TestUtils.generateGroupId();
        String artifactId = "model-ifexists-create";

        String model = """
                {
                  "name": "Test Model",
                  "input": { "type": "object", "properties": { "prompt": { "type": "string" } } },
                  "output": { "type": "object", "properties": { "text": { "type": "string" } } }
                }
                """;

        createArtifact(groupId, artifactId, MODEL_SCHEMA, model, ContentTypes.APPLICATION_JSON);
        postWithIfExists(groupId, artifactId, MODEL_SCHEMA, model.replace("Test Model", "Renamed Model"),
                IfArtifactExists.CREATE_VERSION);

        // Unchanged embedded schemas are reused by the ifExists version, just as on the other paths.
        JsonNode v2 = MAPPER.readTree(getVersionContent(groupId, artifactId, "2"));
        Assertions.assertEquals("Renamed Model", v2.path("name").asText());
        Assertions.assertEquals(groupId + ":" + artifactId + "-input-schema:1",
                v2.path("input").path("$ref").asText());
        Assertions.assertEquals(groupId + ":" + artifactId + "-output-schema:1",
                v2.path("output").path("$ref").asText());
    }

    private CreateArtifactResponse postWithIfExists(String groupId, String artifactId, String artifactType,
            String content, IfArtifactExists ifExists) {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, artifactType, content,
                ContentTypes.APPLICATION_JSON);
        return clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact,
                config -> config.queryParameters.ifExists = ifExists);
    }
}

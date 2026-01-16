/*
 * Copyright 2024 Red Hat
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

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Integration tests for the /search/models endpoint (MODEL_SCHEMA capability search).
 */
@QuarkusTest
public class ModelSearchTest extends AbstractResourceTestBase {

    private static final String MODEL_SCHEMA = "MODEL_SCHEMA";

    /**
     * Creates a MODEL_SCHEMA artifact content for testing.
     */
    private String createModelContent(String modelId, String provider, long contextWindow, String... capabilities) {
        StringBuilder capsJson = new StringBuilder("[");
        for (int i = 0; i < capabilities.length; i++) {
            if (i > 0) capsJson.append(", ");
            capsJson.append("\"").append(capabilities[i]).append("\"");
        }
        capsJson.append("]");

        return String.format("""
            {
              "modelId": "%s",
              "provider": "%s",
              "version": "1.0",
              "metadata": {
                "contextWindow": %d,
                "capabilities": %s
              }
            }
            """, modelId, provider, contextWindow, capsJson.toString());
    }

    @Test
    public void testSearchModelsBasic() throws Exception {
        String group = "model-search-test-" + UUID.randomUUID().toString();

        // Create several MODEL_SCHEMA artifacts
        createArtifact(group, "gpt-4-turbo", MODEL_SCHEMA,
                createModelContent("gpt-4-turbo", "openai", 128000, "chat", "function_calling", "vision"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "claude-3-opus", MODEL_SCHEMA,
                createModelContent("claude-3-opus", "anthropic", 200000, "chat", "vision", "tool_use"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "gemini-pro", MODEL_SCHEMA,
                createModelContent("gemini-pro", "google", 32000, "chat", "multimodal"),
                ContentTypes.APPLICATION_JSON);

        // Search all models in the group
        given().when()
                .queryParam("groupId", group)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(3))
                .body("models", notNullValue());
    }

    @Test
    public void testSearchModelsByProvider() throws Exception {
        String group = "provider-search-" + UUID.randomUUID().toString();

        createArtifact(group, "gpt-4", MODEL_SCHEMA,
                createModelContent("gpt-4", "openai", 8192, "chat"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "gpt-4-turbo", MODEL_SCHEMA,
                createModelContent("gpt-4-turbo", "openai", 128000, "chat", "vision"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "claude-3-sonnet", MODEL_SCHEMA,
                createModelContent("claude-3-sonnet", "anthropic", 200000, "chat"),
                ContentTypes.APPLICATION_JSON);

        // Search for OpenAI models only
        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "openai")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2));

        // Search for Anthropic models
        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "anthropic")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("models[0].provider", equalTo("anthropic"));
    }

    @Test
    public void testSearchModelsByCapabilities() throws Exception {
        String group = "capability-search-" + UUID.randomUUID().toString();

        createArtifact(group, "model-vision", MODEL_SCHEMA,
                createModelContent("model-vision", "test", 50000, "chat", "vision"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "model-tools", MODEL_SCHEMA,
                createModelContent("model-tools", "test", 50000, "chat", "tool_use"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "model-both", MODEL_SCHEMA,
                createModelContent("model-both", "test", 50000, "chat", "vision", "tool_use"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "model-basic", MODEL_SCHEMA,
                createModelContent("model-basic", "test", 50000, "chat"),
                ContentTypes.APPLICATION_JSON);

        // Search for models with vision capability
        given().when()
                .queryParam("groupId", group)
                .queryParam("capability", "vision")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2)); // model-vision and model-both

        // Search for models with tool_use capability
        given().when()
                .queryParam("groupId", group)
                .queryParam("capability", "tool_use")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2)); // model-tools and model-both

        // Search for models with both vision AND tool_use
        given().when()
                .queryParam("groupId", group)
                .queryParam("capability", "vision")
                .queryParam("capability", "tool_use")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1)) // only model-both
                .body("models[0].modelId", equalTo("model-both"));
    }

    @Test
    public void testSearchModelsByContextWindow() throws Exception {
        String group = "context-search-" + UUID.randomUUID().toString();

        createArtifact(group, "small-model", MODEL_SCHEMA,
                createModelContent("small-model", "test", 4096, "chat"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "medium-model", MODEL_SCHEMA,
                createModelContent("medium-model", "test", 32000, "chat"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "large-model", MODEL_SCHEMA,
                createModelContent("large-model", "test", 200000, "chat"),
                ContentTypes.APPLICATION_JSON);

        // Search for models with context window >= 30000
        given().when()
                .queryParam("groupId", group)
                .queryParam("minContextWindow", 30000)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2)); // medium and large

        // Search for models with context window <= 50000
        given().when()
                .queryParam("groupId", group)
                .queryParam("maxContextWindow", 50000)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2)); // small and medium

        // Search for models with context window between 10000 and 100000
        given().when()
                .queryParam("groupId", group)
                .queryParam("minContextWindow", 10000)
                .queryParam("maxContextWindow", 100000)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1)); // only medium
    }

    @Test
    public void testSearchModelsCombinedFilters() throws Exception {
        String group = "combined-search-" + UUID.randomUUID().toString();

        // OpenAI models
        createArtifact(group, "gpt-4", MODEL_SCHEMA,
                createModelContent("gpt-4", "openai", 8192, "chat"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "gpt-4-vision", MODEL_SCHEMA,
                createModelContent("gpt-4-vision", "openai", 128000, "chat", "vision"),
                ContentTypes.APPLICATION_JSON);

        // Anthropic models
        createArtifact(group, "claude-instant", MODEL_SCHEMA,
                createModelContent("claude-instant", "anthropic", 100000, "chat"),
                ContentTypes.APPLICATION_JSON);

        createArtifact(group, "claude-3-opus", MODEL_SCHEMA,
                createModelContent("claude-3-opus", "anthropic", 200000, "chat", "vision", "tool_use"),
                ContentTypes.APPLICATION_JSON);

        // Search for OpenAI models with vision capability
        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "openai")
                .queryParam("capability", "vision")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("models[0].modelId", equalTo("gpt-4-vision"));

        // Search for Anthropic models with context window >= 150000
        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "anthropic")
                .queryParam("minContextWindow", 150000)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("models[0].modelId", equalTo("claude-3-opus"));

        // Search for vision models with context window >= 100000
        given().when()
                .queryParam("groupId", group)
                .queryParam("capability", "vision")
                .queryParam("minContextWindow", 100000)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(2)); // gpt-4-vision and claude-3-opus
    }

    @Test
    public void testSearchModelsPagination() throws Exception {
        String group = "pagination-test-" + UUID.randomUUID().toString();

        // Create 10 models
        for (int i = 0; i < 10; i++) {
            createArtifact(group, "model-" + i, MODEL_SCHEMA,
                    createModelContent("model-" + i, "test", 10000 + i * 1000, "chat"),
                    ContentTypes.APPLICATION_JSON);
        }

        // Test limit
        given().when()
                .queryParam("groupId", group)
                .queryParam("limit", 3)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(10)) // Total count
                .body("models.size()", equalTo(3)); // Page size

        // Test offset
        given().when()
                .queryParam("groupId", group)
                .queryParam("offset", 5)
                .queryParam("limit", 3)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(10))
                .body("models.size()", equalTo(3));
    }

    @Test
    public void testSearchModelsNoResults() throws Exception {
        String group = "no-results-" + UUID.randomUUID().toString();

        createArtifact(group, "basic-model", MODEL_SCHEMA,
                createModelContent("basic-model", "test", 8192, "chat"),
                ContentTypes.APPLICATION_JSON);

        // Search for non-existent provider
        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "nonexistent")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(0))
                .body("models.size()", equalTo(0));

        // Search for capability that doesn't exist
        given().when()
                .queryParam("groupId", group)
                .queryParam("capability", "nonexistent_capability")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(0));
    }

    @Test
    public void testSearchModelsResponseFields() throws Exception {
        String group = "fields-test-" + UUID.randomUUID().toString();

        createArtifact(group, "test-model", MODEL_SCHEMA,
                createModelContent("test-model-id", "test-provider", 50000, "chat", "vision"),
                ContentTypes.APPLICATION_JSON, (ca) -> {
                    ca.setName("Test Model Name");
                    ca.setDescription("A test model description");
                });

        given().when()
                .queryParam("groupId", group)
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("models[0].groupId", equalTo(group))
                .body("models[0].artifactId", equalTo("test-model"))
                .body("models[0].modelId", equalTo("test-model-id"))
                .body("models[0].provider", equalTo("test-provider"))
                .body("models[0].contextWindow", equalTo(50000))
                .body("models[0].capabilities", hasItems("chat", "vision"))
                .body("models[0].createdOn", notNullValue())
                .body("models[0].globalId", notNullValue());
    }

    @Test
    public void testSearchModelsWithYamlContent() throws Exception {
        String group = "yaml-test-" + UUID.randomUUID().toString();

        String yamlContent = """
            modelId: yaml-model
            provider: yaml-provider
            version: "1.0"
            metadata:
              contextWindow: 75000
              capabilities:
                - chat
                - code_generation
            """;

        createArtifact(group, "yaml-model", MODEL_SCHEMA,
                yamlContent, ContentTypes.APPLICATION_YAML);

        given().when()
                .queryParam("groupId", group)
                .queryParam("provider", "yaml-provider")
                .get("/registry/v3/search/models")
                .then()
                .statusCode(200)
                .body("count", equalTo(1))
                .body("models[0].modelId", equalTo("yaml-model"))
                .body("models[0].contextWindow", equalTo(75000))
                .body("models[0].capabilities", hasItem("code_generation"));
    }
}

package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(DataContractsEnabledProfile.class)
public class DataContractsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "DataContractsResourceTest";

    // -- Contract Metadata Tests --

    @Test
    public void testGetContractMetadata_Empty() throws Exception {
        String artifactId = "testGetContractMetadata_Empty-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200)
                .body("status", nullValue())
                .body("ownerTeam", nullValue())
                .body("ownerDomain", nullValue());
    }

    @Test
    public void testUpdateAndGetContractMetadata() throws Exception {
        String artifactId = "testUpdateAndGetContractMetadata-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        // Update contract metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "status": "DRAFT",
                            "ownerTeam": "platform-team",
                            "ownerDomain": "payments",
                            "supportContact": "platform@example.com",
                            "classification": "INTERNAL",
                            "stage": "DEV"
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("ownerTeam", equalTo("platform-team"))
                .body("ownerDomain", equalTo("payments"))
                .body("supportContact", equalTo("platform@example.com"))
                .body("classification", equalTo("INTERNAL"))
                .body("stage", equalTo("DEV"));

        // Verify with GET
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"))
                .body("ownerTeam", equalTo("platform-team"))
                .body("ownerDomain", equalTo("payments"));
    }

    @Test
    public void testUpdateContractMetadata_OverwritesPrevious() throws Exception {
        String artifactId = "testUpdateContractMetadata_Overwrite-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        // Set initial metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "status": "DRAFT",
                            "ownerTeam": "team-alpha"
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200);

        // Overwrite with different metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "status": "STABLE",
                            "ownerTeam": "team-beta",
                            "classification": "CONFIDENTIAL"
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200)
                .body("status", equalTo("STABLE"))
                .body("ownerTeam", equalTo("team-beta"))
                .body("classification", equalTo("CONFIDENTIAL"));
    }

    // -- Contract Ruleset Tests (Artifact-level) --

    @Test
    public void testGetArtifactContractRuleset_Empty() throws Exception {
        String artifactId = "testGetArtifactRuleset_Empty-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", empty())
                .body("migrationRules", empty());
    }

    @Test
    public void testSetAndGetArtifactContractRuleset() throws Exception {
        String artifactId = "testSetAndGetArtifactRuleset-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        // Set ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "domainRules": [
                                {
                                    "name": "validate-email",
                                    "kind": "CONDITION",
                                    "type": "CEL",
                                    "mode": "WRITE",
                                    "expr": "message.email.matches('^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$')",
                                    "tags": ["email", "validation"],
                                    "onFailure": "ERROR"
                                }
                            ],
                            "migrationRules": [
                                {
                                    "name": "add-default-field",
                                    "kind": "TRANSFORM",
                                    "type": "CEL_FIELD",
                                    "mode": "UPGRADE",
                                    "expr": "has(message.newField) ? message.newField : 'default'",
                                    "params": {"targetField": "newField"},
                                    "onSuccess": "NONE",
                                    "onFailure": "DLQ"
                                }
                            ]
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("validate-email"))
                .body("domainRules[0].kind", equalTo("CONDITION"))
                .body("domainRules[0].type", equalTo("CEL"))
                .body("domainRules[0].mode", equalTo("WRITE"))
                .body("migrationRules", hasSize(1))
                .body("migrationRules[0].name", equalTo("add-default-field"));

        // Verify with GET
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("validate-email"))
                .body("domainRules[0].tags", hasSize(2))
                .body("migrationRules", hasSize(1))
                .body("migrationRules[0].params.targetField", equalTo("newField"));
    }

    @Test
    public void testDeleteArtifactContractRuleset() throws Exception {
        String artifactId = "testDeleteArtifactRuleset-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        // Set ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "domainRules": [
                                {
                                    "name": "rule1",
                                    "kind": "CONDITION",
                                    "type": "CEL",
                                    "mode": "WRITE"
                                }
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200);

        // Delete ruleset
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(204);

        // Verify empty
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", empty())
                .body("migrationRules", empty());
    }

    // -- Contract Ruleset Tests (Version-level) --

    @Test
    public void testSetAndGetVersionContractRuleset() throws Exception {
        String artifactId = "testSetAndGetVersionRuleset-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        String version = "1";

        // Set version-level ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .body("""
                        {
                            "domainRules": [
                                {
                                    "name": "version-rule",
                                    "kind": "CONDITION",
                                    "type": "CEL",
                                    "mode": "READ",
                                    "disabled": true
                                }
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("version-rule"))
                .body("domainRules[0].disabled", equalTo(true));

        // Verify with GET
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("version-rule"));
    }

    @Test
    public void testDeleteVersionContractRuleset() throws Exception {
        String artifactId = "testDeleteVersionRuleset-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        String version = "1";

        // Set version-level ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .body("""
                        {
                            "domainRules": [
                                {
                                    "name": "temp-rule",
                                    "kind": "TRANSFORM",
                                    "type": "CEL_FIELD",
                                    "mode": "WRITEREAD"
                                }
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200);

        // Delete
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(204);

        // Verify empty
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", empty())
                .body("migrationRules", empty());
    }

    @Test
    public void testArtifactAndVersionRulesetsAreIndependent() throws Exception {
        String artifactId = "testIndependentRulesets-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        String version = "1";

        // Set artifact-level ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "domainRules": [
                                {"name": "artifact-rule", "kind": "CONDITION", "type": "CEL", "mode": "WRITE"}
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200);

        // Set version-level ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .body("""
                        {
                            "domainRules": [
                                {"name": "version-rule", "kind": "TRANSFORM", "type": "CEL_FIELD", "mode": "READ"}
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200);

        // Verify artifact-level has only artifact-rule
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("artifact-rule"));

        // Verify version-level has only version-rule
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("version-rule"));

        // Delete artifact-level ruleset, version-level should remain
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .delete("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(204);

        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("version-rule"));
    }

    @Test
    public void testRulesetReplace() throws Exception {
        String artifactId = "testRulesetReplace-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        // Set initial ruleset with 2 domain rules
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "domainRules": [
                                {"name": "rule-1", "kind": "CONDITION", "type": "CEL", "mode": "WRITE"},
                                {"name": "rule-2", "kind": "CONDITION", "type": "CEL", "mode": "READ"}
                            ],
                            "migrationRules": []
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", hasSize(2));

        // Replace with 1 migration rule
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("""
                        {
                            "domainRules": [],
                            "migrationRules": [
                                {"name": "migration-1", "kind": "TRANSFORM", "type": "CEL_FIELD", "mode": "UPGRADE"}
                            ]
                        }
                        """)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200);

        // Verify replacement
        given()
                .when()
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then()
                .statusCode(200)
                .body("domainRules", empty())
                .body("migrationRules", hasSize(1))
                .body("migrationRules[0].name", equalTo("migration-1"));
    }
}

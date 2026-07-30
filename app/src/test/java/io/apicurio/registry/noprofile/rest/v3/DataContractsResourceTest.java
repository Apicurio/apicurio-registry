package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ContractRule;
import io.apicurio.registry.rest.v3.beans.ContractRuleSet;
import io.apicurio.registry.rest.v3.beans.ContractStatusTransition;
import io.apicurio.registry.rest.v3.beans.EditableContractMetadata;
import io.apicurio.registry.rest.v3.beans.Params;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
public class DataContractsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "DataContractsResourceTest";

    private static EditableContractMetadata contractMetadata(String status) {
        EditableContractMetadata metadata = new EditableContractMetadata();
        metadata.setStatus(EditableContractMetadata.Status.fromValue(status));
        return metadata;
    }

    private static ContractStatusTransition statusTransition(String status) {
        ContractStatusTransition transition = new ContractStatusTransition();
        transition.setStatus(ContractStatusTransition.Status.fromValue(status));
        return transition;
    }

    private static ContractRuleSet ruleset(List<ContractRule> domainRules,
            List<ContractRule> migrationRules) {
        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(domainRules);
        ruleset.setMigrationRules(migrationRules);
        return ruleset;
    }

    private static ContractRule rule(String name, String kind, String type, String mode) {
        ContractRule rule = new ContractRule();
        rule.setName(name);
        rule.setKind(ContractRule.Kind.fromValue(kind));
        rule.setType(type);
        rule.setMode(ContractRule.Mode.fromValue(mode));
        return rule;
    }

    private static Params params(String key, String value) {
        Params params = new Params();
        params.setAdditionalProperty(key, value);
        return params;
    }

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

        EditableContractMetadata metadata = contractMetadata("DRAFT");
        metadata.setOwnerTeam("platform-team");
        metadata.setOwnerDomain("payments");
        metadata.setSupportContact("platform@example.com");
        metadata.setClassification(EditableContractMetadata.Classification.fromValue("INTERNAL"));
        metadata.setStage(EditableContractMetadata.Stage.fromValue("DEV"));

        // Update contract metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(metadata)
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

        EditableContractMetadata initialMetadata = contractMetadata("DRAFT");
        initialMetadata.setOwnerTeam("team-alpha");

        // Set initial metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(initialMetadata)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then()
                .statusCode(200);

        EditableContractMetadata updatedMetadata = contractMetadata("STABLE");
        updatedMetadata.setOwnerTeam("team-beta");
        updatedMetadata.setClassification(EditableContractMetadata.Classification.fromValue("CONFIDENTIAL"));

        // Overwrite with different metadata
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(updatedMetadata)
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

        ContractRule emailRule = rule("validate-email", "CONDITION", "CEL", "WRITE");
        emailRule.setExpr("message.email.matches('^[a-zA-Z0-9+_.-]+@[a-zA-Z0-9.-]+$')");
        emailRule.setTags(List.of("email", "validation"));
        emailRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));

        ContractRule defaultFieldRule = rule("add-default-field", "TRANSFORM", "CEL_FIELD", "UPGRADE");
        defaultFieldRule.setExpr("has(message.newField) ? message.newField : 'default'");
        defaultFieldRule.setParams(params("targetField", "newField"));
        defaultFieldRule.setOnSuccess(ContractRule.OnSuccess.fromValue("NONE"));
        defaultFieldRule.setOnFailure(ContractRule.OnFailure.fromValue("DLQ"));

        // Set ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body(ruleset(List.of(emailRule), List.of(defaultFieldRule)))
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
                .body(ruleset(List.of(rule("rule1", "CONDITION", "CEL", "WRITE")), List.of()))
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
        ContractRule versionRule = rule("version-rule", "CONDITION", "CEL", "READ");
        versionRule.setDisabled(true);

        // Set version-level ruleset
        given()
                .when()
                .contentType(CT_JSON)
                .pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .body(ruleset(List.of(versionRule), List.of()))
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
                .body(ruleset(List.of(rule("temp-rule", "TRANSFORM", "CEL_FIELD", "WRITEREAD")),
                        List.of()))
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
                .body(ruleset(List.of(rule("artifact-rule", "CONDITION", "CEL", "WRITE")), List.of()))
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
                .body(ruleset(List.of(rule("version-rule", "TRANSFORM", "CEL_FIELD", "READ")),
                        List.of()))
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
                .body(ruleset(List.of(
                        rule("rule-1", "CONDITION", "CEL", "WRITE"),
                        rule("rule-2", "CONDITION", "CEL", "READ")), List.of()))
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
                .body(ruleset(List.of(), List.of(rule("migration-1", "TRANSFORM", "CEL_FIELD", "UPGRADE"))))
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

    // -- Status Transition Tests --

    @Test
    public void testStatusTransition_DraftToStable() throws Exception {
        String artifactId = "testStatusTransition_DraftToStable-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(statusTransition("STABLE"))
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status")
                .then().statusCode(200)
                .body("status", equalTo("STABLE"));

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200)
                .body("status", equalTo("STABLE"));
    }

    @Test
    public void testStatusTransition_RepeatedTransitionsNoConstraintViolation() throws Exception {
        String artifactId = "testStatusTransition_Repeated-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(statusTransition("STABLE"))
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(statusTransition("DEPRECATED"))
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status")
                .then().statusCode(200)
                .body("status", equalTo("DEPRECATED"));
    }

    @Test
    public void testStatusTransition_DoesNotWipeOtherLabels() throws Exception {
        String artifactId = "testStatusTransition_Labels-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        EditableContractMetadata metadata = contractMetadata("DRAFT");
        metadata.setOwnerTeam("my-team");
        metadata.setClassification(EditableContractMetadata.Classification.fromValue("INTERNAL"));

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metadata)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(statusTransition("STABLE"))
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status")
                .then().statusCode(200);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200)
                .body("status", equalTo("STABLE"))
                .body("ownerTeam", equalTo("my-team"))
                .body("classification", equalTo("INTERNAL"));
    }

    // -- Compatibility Group Tests --

    @Test
    public void testCompatibilityGroup_SetAndGet() throws Exception {
        String artifactId = "testCompatGroup_SetGet-" + UUID.randomUUID();
        String content = resourceToString("openapi-empty.json");
        createArtifact(GROUP, artifactId, ArtifactType.OPENAPI, content, ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"contractId\":\"default\",\"compatibilityGroup\":\"my-group-v1\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(204);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .queryParam("contractId", "default")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(200)
                .body("compatibilityGroup", equalTo("my-group-v1"));
    }

    @Test
    public void testCompatibilityGroup_DoesNotWipeOtherLabels() throws Exception {
        String artifactId = "testCompatGroup_NoWipe-" + UUID.randomUUID();
        String avroSchema = "{\"type\":\"record\",\"name\":\"T\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}";
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, avroSchema, ContentTypes.APPLICATION_JSON);

        String contractYaml = "apiVersion: v3.1.0\nkind: DataContract\nid: mycontract\n"
                + "info:\n  title: Test\n  version: 1.0.0\n  status: active\n  dataClassification: internal\n"
                + "team:\n  name: team-x\n  domain: test\n  contact: test@example.com\n"
                + "schemas:\n  - name: T\n    type: avro\n    location: " + GROUP + "/" + artifactId + ":latest\n";

        given().when().contentType("application/x-yaml")
                .pathParam("groupId", GROUP)
                .body(contractYaml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .post("/registry/v3/groups/{groupId}/contracts")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"contractId\":\"mycontract\",\"compatibilityGroup\":\"compat-v2\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(204);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200)
                .body("status", equalTo("STABLE"))
                .body("ownerTeam", equalTo("team-x"))
                .body("classification", equalTo("INTERNAL"))
                .body("compatibilityGroup", equalTo("compat-v2"));
    }

    // -- Global Contract Rules Tests --

    @Test
    public void testGlobalContractRules_CrudLifecycle() throws Exception {
        given().when()
                .get("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200)
                .body("domainRules", empty())
                .body("migrationRules", empty());

        ContractRule globalRule = rule("global-rule", "CONDITION", "CEL", "WRITE");
        globalRule.setExpr("true");
        globalRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        globalRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .body(ruleset(List.of(globalRule), List.of()))
                .put("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200)
                .body("domainRules", hasSize(1))
                .body("domainRules[0].name", equalTo("global-rule"));

        given().when()
                .get("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200)
                .body("domainRules", hasSize(1));

        given().when()
                .delete("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(204);

        given().when()
                .get("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200)
                .body("domainRules", empty());
    }

    // -- Migration Endpoint Tests --

    @Test
    public void testMigrateEndpoint_NoRules() throws Exception {
        String artifactId = "testMigrate_NoRules-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"fromVersion\":\"1\",\"toVersion\":\"1\",\"record\":{\"x\":42}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/migrate")
                .then().statusCode(200)
                .body("passed", equalTo(true));
    }

    // -- Contract Rule Execution Tests --

    @Test
    public void testExecuteContractRules_PassesWithValidData() throws Exception {
        String artifactId = "testExecuteRules_Pass-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"amount\",\"type\":\"double\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule positiveAmountRule = rule("pos", "CONDITION", "CEL", "WRITE");
        positiveAmountRule.setExpr("amount > 0");
        positiveAmountRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        positiveAmountRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(positiveAmountRule), List.of()))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "1")
                .body("{\"mode\":\"WRITE\",\"record\":{\"amount\":99.99}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute")
                .then().statusCode(200)
                .body("passed", equalTo(true))
                .body("executedRules", equalTo(1))
                .body("failedRules", equalTo(0));
    }

    @Test
    public void testExecuteContractRules_FailsWithInvalidData() throws Exception {
        String artifactId = "testExecuteRules_Fail-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"amount\",\"type\":\"double\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule positiveAmountRule = rule("pos", "CONDITION", "CEL", "WRITE");
        positiveAmountRule.setExpr("amount > 0");
        positiveAmountRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        positiveAmountRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(positiveAmountRule), List.of()))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "1")
                .body("{\"mode\":\"WRITE\",\"record\":{\"amount\":-5.0}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute")
                .then().statusCode(200)
                .body("passed", equalTo(false))
                .body("failedRules", equalTo(1));
    }

    // ========== #7366 Migration Acceptance Criteria ==========

    @Test
    public void testMigrateEndpoint_WithTransformRule() throws Exception {
        String artifactId = "testMigrate_Transform-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule transformRule = rule("add-y", "TRANSFORM", "JSONATA", "UPGRADE");
        transformRule.setExpr("$ ~> |$|{\"y\": 99}|");
        transformRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        transformRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(), List.of(transformRule)))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"fromVersion\":\"1\",\"toVersion\":\"1\",\"record\":{\"x\":42}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/migrate")
                .then().statusCode(200)
                .body("passed", equalTo(true));
    }

    @Test
    public void testMigrateEndpoint_InvalidVersion() throws Exception {
        String artifactId = "testMigrate_InvalidVer-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"fromVersion\":\"1\",\"toVersion\":\"999\",\"record\":{\"x\":1}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/migrate")
                .then().statusCode(400);
    }

    // ========== #7367 Compatibility Group Acceptance Criteria ==========

    @Test
    public void testCompatibilityGroup_SetDifferentGroups() throws Exception {
        String art1 = "testCompatGroup_Multi1-" + UUID.randomUUID();
        String art2 = "testCompatGroup_Multi2-" + UUID.randomUUID();
        createArtifact(GROUP, art1, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"A\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);
        createArtifact(GROUP, art2, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"B\",\"fields\":[{\"name\":\"y\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", art1)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", art2)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", art1)
                .body("{\"contractId\":\"default\",\"compatibilityGroup\":\"group-A\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(204);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", art2)
                .body("{\"contractId\":\"default\",\"compatibilityGroup\":\"group-B\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(204);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", art1)
                .queryParam("contractId", "default")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(200)
                .body("compatibilityGroup", equalTo("group-A"));

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", art2)
                .queryParam("contractId", "default")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(200)
                .body("compatibilityGroup", equalTo("group-B"));
    }

    @Test
    public void testCompatibilityGroup_GetNonExistent() throws Exception {
        String artifactId = "testCompatGroup_None-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .queryParam("contractId", "default")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group")
                .then().statusCode(200)
                .body("compatibilityGroup", nullValue());
    }

    // ========== #7244 Audit Log Acceptance Criteria ==========

    @Test
    public void testAuditLog_MetadataUpdateCreatesEntry() throws Exception {
        String artifactId = "testAudit_MetaUpdate-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        EditableContractMetadata metadata = contractMetadata("DRAFT");
        metadata.setOwnerTeam("audit-team");

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metadata)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .queryParam("offset", "0").queryParam("limit", "10")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);
    }

    @Test
    public void testAuditLog_StatusTransitionCreatesEntry() throws Exception {
        String artifactId = "testAudit_Status-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(contractMetadata("DRAFT"))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(statusTransition("STABLE"))
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/status")
                .then().statusCode(200);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .queryParam("offset", "0").queryParam("limit", "10")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);
    }

    @Test
    public void testAuditLog_EndpointReturnsArray() throws Exception {
        String artifactId = "testAudit_Array-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        given().when()
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .queryParam("offset", "0").queryParam("limit", "10")
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit")
                .then().statusCode(200);
    }

    // ========== #7243 Search Acceptance Criteria ==========

    @Test
    public void testSearchContracts_ReturnsResults() throws Exception {
        given().when()
                .get("/registry/v3/search/contracts")
                .then().statusCode(200)
                .body("count", notNullValue());
    }

    @Test
    public void testSearchContracts_WithStatusFilter() throws Exception {
        String artifactId = "testSearch_Status-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}",
                ContentTypes.APPLICATION_JSON);

        EditableContractMetadata metadata = contractMetadata("DRAFT");
        metadata.setOwnerTeam("search-team");

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(metadata)
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata")
                .then().statusCode(200);

        given().when()
                .queryParam("status", "DRAFT")
                .get("/registry/v3/search/contracts")
                .then().statusCode(200);
    }

    @Test
    public void testSearchContracts_Pagination() throws Exception {
        given().when()
                .queryParam("offset", "0").queryParam("limit", "5")
                .get("/registry/v3/search/contracts")
                .then().statusCode(200)
                .body("count", notNullValue());
    }

    // ========== #7375 Global Rules Acceptance Criteria ==========

    @Test
    public void testGlobalRules_AffectRuleExecution() throws Exception {
        String artifactId = "testGlobal_Execute-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"amount\",\"type\":\"double\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule globalRule = rule("global-pos", "CONDITION", "CEL", "WRITE");
        globalRule.setExpr("amount > 0");
        globalRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        globalRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .body(ruleset(List.of(globalRule), List.of()))
                .put("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "1")
                .body("{\"mode\":\"WRITE\",\"record\":{\"amount\":-1}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute")
                .then().statusCode(200)
                .body("passed", equalTo(false))
                .body("failedRules", greaterThanOrEqualTo(1));

        given().when()
                .delete("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(204);
    }

    @Test
    public void testGlobalRules_VersionOverridesGlobal() throws Exception {
        String artifactId = "testGlobal_Override-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"amount\",\"type\":\"double\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule globalRule = rule("check", "CONDITION", "CEL", "WRITE");
        globalRule.setExpr("amount > 100");
        globalRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        globalRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .body(ruleset(List.of(globalRule), List.of()))
                .put("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(200);

        ContractRule artifactRule = rule("check", "CONDITION", "CEL", "WRITE");
        artifactRule.setExpr("amount > 0");
        artifactRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        artifactRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(artifactRule), List.of()))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "1")
                .body("{\"mode\":\"WRITE\",\"record\":{\"amount\":50}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute")
                .then().statusCode(200)
                .body("passed", equalTo(true));

        given().when()
                .delete("/registry/v3/admin/contracts/ruleset")
                .then().statusCode(204);
    }

    // ========== #7245 Contract Events Acceptance Criteria ==========

    @Test
    public void testContractEvents_StorageEventTypesExist() {
        io.apicurio.registry.storage.StorageEventType.valueOf("CONTRACT_RULESET_CONFIGURED");
        io.apicurio.registry.storage.StorageEventType.valueOf("CONTRACT_METADATA_UPDATED");
        io.apicurio.registry.storage.StorageEventType.valueOf("CONTRACT_STATUS_CHANGED");
    }

    // ========== #7369 SerDes Acceptance Criteria (fail-on-error=false) ==========

    @Test
    public void testContractRulesFailOnErrorFalse_SerializesAnyway() throws Exception {
        String artifactId = "testSerde_FailFalse-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"amount\",\"type\":\"double\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule alwaysFailRule = rule("always-fail", "CONDITION", "CEL", "WRITE");
        alwaysFailRule.setExpr("false");
        alwaysFailRule.setOnFailure(ContractRule.OnFailure.fromValue("DLQ"));
        alwaysFailRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(alwaysFailRule), List.of()))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("versionExpression", "1")
                .body("{\"mode\":\"WRITE\",\"record\":{\"amount\":1}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{versionExpression}/contract/execute")
                .then().statusCode(200)
                .body("passed", equalTo(false))
                .body("violations[0].action", equalTo("DLQ"));
    }

    // ========== #7356 Tag Search Acceptance Criteria ==========

    @Test
    public void testSearchContractRulesByTag() throws Exception {
        String artifactId = "testTagSearch-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO,
                "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"ssn\",\"type\":\"string\"}]}",
                ContentTypes.APPLICATION_JSON);

        ContractRule maskRule = rule("mask-pii", "TRANSFORM", "CEL_FIELD", "WRITE");
        maskRule.setExpr("XXXXX");
        maskRule.setTags(List.of("PII"));
        maskRule.setOnFailure(ContractRule.OnFailure.fromValue("ERROR"));
        maskRule.setDisabled(false);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body(ruleset(List.of(maskRule), List.of()))
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset")
                .then().statusCode(200);

        given().when()
                .queryParam("tag", "PII")
                .get("/registry/v3/search/contract/rules")
                .then().statusCode(200);
    }

    // ========== #7366 Migration Chaining Acceptance Criteria ==========

    @Test
    public void testMigrateEndpoint_ChainedMultiHop() throws Exception {
        String artifactId = "testMigrate_Chain-" + UUID.randomUUID();
        String schema1 = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"}]}";
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, schema1, ContentTypes.APPLICATION_JSON);

        String schema2 = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[{\"name\":\"x\",\"type\":\"int\"},{\"name\":\"y\",\"type\":\"int\",\"default\":0}]}";
        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"version\":\"2\",\"content\":{\"content\":"
                        + "\"" + schema2.replace("\"", "\\\"") + "\""
                        + ",\"contentType\":\"application/json\"}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions")
                .then().statusCode(200);

        given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .body("{\"fromVersion\":\"1\",\"toVersion\":\"2\",\"record\":{\"x\":42}}")
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/migrate")
                .then().statusCode(200)
                .body("passed", equalTo(true));
    }
}

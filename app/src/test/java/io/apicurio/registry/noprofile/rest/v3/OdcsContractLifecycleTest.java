package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle of an ODCS contract's projection: what submitting, updating and deleting a contract
 * does to the schema artifacts it governs, and how the projection interacts with contract state set
 * through the API (#10958).
 */
@QuarkusTest
public class OdcsContractLifecycleTest extends AbstractResourceTestBase {

    private static final String GROUP = "OdcsContractLifecycleTest";

    @Inject
    @Current
    RegistryStorage storage;

    private static final String AVRO_SCHEMA = """
            {
              "type": "record",
              "name": "OrderEvent",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string"},
                {"name": "totalAmount", "type": "double"}
              ]
            }
            """;

    private static final String AVRO_SCHEMA_WITH_TAGS = """
            {
              "type": "record",
              "name": "TaggedEvent",
              "fields": [
                {"name": "orderId", "type": "string"},
                {"name": "customerEmail", "type": "string", "tags": ["PII", "EMAIL"]}
              ]
            }
            """;

    /** A contract that governs the given artifacts, with one accuracy rule and PII on customerEmail. */
    private static String contract(String contractId, String version, String status, boolean withRule,
            String... artifactIds) {
        StringBuilder yaml = new StringBuilder()
                .append("apiVersion: v3.1.0\nkind: DataContract\nid: ").append(contractId).append('\n')
                .append("info:\n  title: Lifecycle\n  version: ").append(version).append('\n')
                .append("  status: ").append(status).append('\n')
                .append("team:\n  name: orders-team\n")
                .append("serviceLevel:\n  availability: 0.999\n")
                .append("schemas:\n");
        for (String artifactId : artifactIds) {
            yaml.append("  - name: ").append(artifactId).append('\n')
                    .append("    location: ").append(GROUP).append('/').append(artifactId).append('\n')
                    .append("    fields:\n      customerEmail:\n        pii: true\n        tags: [EMAIL]\n");
        }
        if (withRule) {
            yaml.append("quality:\n  accuracy:\n    - name: positive-amount\n")
                    .append("      expression: totalAmount > 0\n");
        }
        return yaml.toString();
    }

    private static ValidatableResponse submit(String yaml) {
        return given().when().header("Content-Type", "application/x-yaml").pathParam("groupId", GROUP)
                .body(yaml.getBytes()).post("/registry/v3/groups/{groupId}/contracts").then();
    }

    private static ValidatableResponse update(String contractId, String yaml) {
        return given().when().header("Content-Type", "application/x-yaml").pathParam("groupId", GROUP)
                .pathParam("contractId", contractId).body(yaml.getBytes())
                .put("/registry/v3/groups/{groupId}/contracts/{contractId}").then();
    }

    private static ValidatableResponse post(String artifactId, String path, String json) {
        return given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body(json)
                .post("/registry/v3/groups/{groupId}/artifacts/{artifactId}" + path).then();
    }

    private static Map<String, String> artifactLabels(String artifactId) {
        Map<String, String> labels = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}").then().statusCode(200)
                .extract().jsonPath().getMap("labels");
        return labels != null ? labels : Map.of();
    }

    private static Map<String, String> versionLabels(String artifactId, String version) {
        Map<String, String> labels = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .pathParam("version", version)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/{version}").then()
                .statusCode(200).extract().jsonPath().getMap("labels");
        return labels != null ? labels : Map.of();
    }

    private static List<String> domainRuleNames(String artifactId) {
        return given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset").then()
                .statusCode(200).extract().jsonPath().getList("domainRules.name", String.class);
    }

    private String newSchema(String prefix) throws Exception {
        String artifactId = prefix + "-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        return artifactId;
    }

    @Test
    public void testDeleteContractRemovesProjection() throws Exception {
        String artifactId = newSchema("delete");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);
        post(artifactId, "/contract/promote", "{\"contractId\":\"" + contractId + "\",\"targetStage\":\"DEV\"}")
                .statusCode(200);
        assertEquals("orders-team", artifactLabels(artifactId).get("contract." + contractId + ".owner.team"));

        given().when().pathParam("groupId", GROUP).pathParam("contractId", contractId)
                .delete("/registry/v3/groups/{groupId}/contracts/{contractId}").then().statusCode(204);

        assertTrue(artifactLabels(artifactId).keySet().stream().noneMatch(k -> k.startsWith("contract.")),
                "labels left: " + artifactLabels(artifactId));
        assertEquals(List.of(), domainRuleNames(artifactId));
        assertTrue(versionLabels(artifactId, "1").keySet().stream().noneMatch(k -> k.startsWith("field-tag.")),
                "tags left: " + versionLabels(artifactId, "1"));
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit").then()
                .statusCode(200).body("action", hasItem("CONTRACT_REMOVED"));
    }

    @Test
    public void testUpdateKeepsStateSetThroughTheApi() throws Exception {
        String artifactId = newSchema("keep-state");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);
        post(artifactId, "/contract/promote", "{\"contractId\":\"" + contractId + "\",\"targetStage\":\"DEV\"}")
                .statusCode(200);
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("{\"contractId\":\"" + contractId + "\",\"compatibilityGroup\":\"orders-v1\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/compatibility-group").then()
                .statusCode(204);
        post(artifactId, "/contract/status", "{\"status\":\"DEPRECATED\"}").statusCode(200);

        // The YAML still says "active" (STABLE): moving back from DEPRECATED is refused with a warning
        update(contractId, contract(contractId, "1.1.0", "active", true, artifactId)).statusCode(200)
                .body("projection.warnings", hasSize(1))
                .body("projection.warnings[0]", containsString("cannot move back"));

        Map<String, String> labels = artifactLabels(artifactId);
        String prefix = "contract." + contractId + ".";
        assertEquals("DEPRECATED", labels.get(prefix + "status"));
        assertEquals("DEV", labels.get(prefix + "stage"));
        assertEquals("orders-v1", labels.get(prefix + "compatibility.group"));
        assertEquals("1.1.0", labels.get(prefix + "version"));
    }

    @Test
    public void testUpdateAppliesAForwardStatusChange() throws Exception {
        String artifactId = newSchema("forward-status");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "draft", true, artifactId)).statusCode(200);

        update(contractId, contract(contractId, "1.1.0", "active", true, artifactId)).statusCode(200)
                .body("projection.warnings", hasSize(0));

        Map<String, String> labels = artifactLabels(artifactId);
        assertEquals("STABLE", labels.get("contract." + contractId + ".status"));
        assertTrue(labels.containsKey("contract." + contractId + ".lifecycle.stable-date"));
    }

    @Test
    public void testUpdateCleansUpWhatTheNewVersionNoLongerDeclares() throws Exception {
        String kept = newSchema("kept");
        String dropped = newSchema("dropped");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, kept, dropped)).statusCode(200);
        assertEquals(List.of("odcs:" + contractId + ":positive-amount"), domainRuleNames(dropped));

        // Version 1.1.0 drops one schema and the accuracy rule
        update(contractId, contract(contractId, "1.1.0", "active", false, kept)).statusCode(200)
                .body("projection.rulesApplied", equalTo(0));

        assertEquals(List.of(), domainRuleNames(kept));
        assertTrue(artifactLabels(kept).containsKey("contract." + contractId + ".id"));
        assertTrue(artifactLabels(dropped).keySet().stream().noneMatch(k -> k.startsWith("contract.")));
        assertEquals(List.of(), domainRuleNames(dropped));
        assertTrue(versionLabels(dropped, "1").isEmpty(), "tags left: " + versionLabels(dropped, "1"));
    }

    @Test
    public void testFieldTagsKeepTheirCaseAndExportRoundTrips() throws Exception {
        String artifactId = newSchema("case");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);

        Map<String, String> tags = versionLabels(artifactId, "1");
        assertEquals("EXTERNAL", tags.get("field-tag." + contractId + ":customerEmail|PII"));
        assertEquals("EXTERNAL", tags.get("field-tag." + contractId + ":customerEmail|EMAIL"));

        String exported = given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/export").then()
                .statusCode(200).extract().asString();
        assertTrue(exported.contains("customerEmail:"), exported);
        assertTrue(exported.contains("pii: true"), exported);
    }

    @Test
    public void testMergingVersionLabelsKeepsOtherLabelsUnchanged() throws Exception {
        String artifactId = "other-labels-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"labels\":{\"Team\":\"Orders\"}}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1").then().statusCode(204);

        submit(contract("c-" + UUID.randomUUID(), "1.0.0", "active", false, artifactId)).statusCode(200);

        assertEquals("Orders", versionLabels(artifactId, "1").get("Team"));
    }

    @Test
    public void testTooLongTagKeyIsSkippedWithoutDroppingTheOthers() throws Exception {
        String artifactId = newSchema("long-tag");
        String contractId = "c-" + UUID.randomUUID();
        String longTag = "T".repeat(260);
        String yaml = "apiVersion: v3.1.0\nkind: DataContract\nid: " + contractId + "\n"
                + "info:\n  version: 1.0.0\nschemas:\n  - location: " + GROUP + "/" + artifactId + "\n"
                + "    fields:\n      customerEmail:\n        tags: [EMAIL, " + longTag + "]\n";

        submit(yaml).statusCode(200).body("projection.tagsApplied", equalTo(1))
                .body("projection.warnings[0]", containsString("too long"));

        assertEquals("EXTERNAL", versionLabels(artifactId, "1").get("field-tag." + contractId + ":customerEmail|EMAIL"));
    }

    @Test
    public void testUpdateMetadataKeepsTheContractLabelsAndChecksTheLifecycle() throws Exception {
        String artifactId = newSchema("metadata");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);

        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"ownerTeam\":\"new-team\",\"status\":\"STABLE\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                .statusCode(200).body("ownerTeam", equalTo("new-team"));

        Map<String, String> labels = artifactLabels(artifactId);
        assertEquals(contractId, labels.get("contract." + contractId + ".id"));
        assertEquals("0.999", labels.get("contract." + contractId + ".sla.availability"));
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/export").then()
                .statusCode(200);

        // Back to DRAFT is refused, as on the status endpoint
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"status\":\"DRAFT\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                .statusCode(409);
        // Skipping straight to PROD is refused, as on the promote endpoint
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"status\":\"STABLE\",\"stage\":\"PROD\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                .statusCode(400);
        assertEquals("STABLE", artifactLabels(artifactId).get("contract." + contractId + ".status"));
    }

    @Test
    public void testFirstPromotionMustBeDev() throws Exception {
        String artifactId = newSchema("promote");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);

        post(artifactId, "/contract/promote", "{\"contractId\":\"" + contractId + "\",\"targetStage\":\"PROD\"}")
                .statusCode(400).body("detail", containsString("Promote to DEV first"));
        assertNull(artifactLabels(artifactId).get("contract." + contractId + ".stage"));
    }

    @Test
    public void testAuditRecordsContractOperations() throws Exception {
        String artifactId = newSchema("audit");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);
        post(artifactId, "/contract/promote", "{\"contractId\":\"" + contractId + "\",\"targetStage\":\"DEV\"}")
                .statusCode(200);
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"domainRules\":[],\"migrationRules\":[]}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset").then()
                .statusCode(200);

        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit").then()
                .statusCode(200)
                .body("action", containsInAnyOrder("CONTRACT_PROJECTED", "PROMOTED", "RULESET_CONFIGURED"))
                .body("find { it.action == 'PROMOTED' }.details", equalTo(contractId + ": none -> DEV"));
    }

    @Test
    public void testDeletingAnArtifactDeletesItsContractRulesAndAudit() throws Exception {
        String artifactId = newSchema("recreate");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);

        // Through storage: artifact deletion is disabled in the REST API of this test profile
        storage.deleteArtifact(GROUP, artifactId);
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);

        assertEquals(List.of(), domainRuleNames(artifactId));
        given().when().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/audit").then()
                .statusCode(200).body("$", hasSize(0));
    }

    @Test
    public void testInlineTagsAreExtractedWhenAVersionIsCreated() throws Exception {
        String artifactId = "inline-" + UUID.randomUUID();
        createArtifact(GROUP, artifactId, ArtifactType.AVRO, AVRO_SCHEMA_WITH_TAGS, ContentTypes.APPLICATION_JSON);

        Map<String, String> labels = versionLabels(artifactId, "1");
        assertEquals("INLINE", labels.get("field-tag.customerEmail|PII"));
        assertEquals("INLINE", labels.get("field-tag.customerEmail|EMAIL"));
        assertFalse(labels.containsKey("field-tag.orderId|PII"));
        // Field tags describe a version's fields; the artifact does not get them
        assertTrue(artifactLabels(artifactId).isEmpty(), "artifact labels: " + artifactLabels(artifactId));
    }

    @Test
    public void testDeletingTheSentinelGroupOrArtifactKeepsTheGlobalRules() throws Exception {
        String sentinel = "__GLOBAL__";
        given().when().contentType(ContentTypes.APPLICATION_JSON)
                .body("{\"domainRules\":[{\"name\":\"keep-me\",\"kind\":\"CONDITION\",\"type\":\"CEL\","
                        + "\"mode\":\"WRITE\",\"expr\":\"true\",\"onFailure\":\"ERROR\"}],\"migrationRules\":[]}")
                .put("/registry/v3/admin/contracts/ruleset").then().statusCode(200);
        try {
            createArtifact(sentinel, sentinel, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
            storage.deleteArtifact(sentinel, sentinel);
            assertEquals(List.of("keep-me"), globalRuleNames());

            createArtifact(sentinel, sentinel, ArtifactType.AVRO, AVRO_SCHEMA, ContentTypes.APPLICATION_JSON);
            storage.deleteGroup(sentinel);
            assertEquals(List.of("keep-me"), globalRuleNames());
        } finally {
            given().when().delete("/registry/v3/admin/contracts/ruleset").then().statusCode(204);
        }
    }

    private static List<String> globalRuleNames() {
        return given().when().get("/registry/v3/admin/contracts/ruleset").then().statusCode(200)
                .extract().jsonPath().getList("domainRules.name", String.class);
    }

    @Test
    public void testFailedProjectionIsRolledBackWithoutLeakingDetails() throws Exception {
        String artifactId = newSchema("rollback");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "draft", true, artifactId)).statusCode(200);
        List<String> rulesBefore = domainRuleNames(artifactId);

        // Version 1.1.0 moves the status forward, then fails: its rule name does not fit the rules table
        String failing = contract(contractId, "1.1.0", "active", false, artifactId)
                + "quality:\n  accuracy:\n    - name: " + "x".repeat(600) + "\n      expression: totalAmount > 0\n";
        String body = update(contractId, failing).statusCode(400).extract().asString();

        assertTrue(body.contains("could not be projected onto its schemas and was not saved"), body);
        assertFalse(body.toLowerCase(java.util.Locale.ROOT).contains("sql"), body);
        List<String> versions = given().when().pathParam("groupId", GROUP).pathParam("contractId", contractId)
                .get("/registry/v3/groups/{groupId}/artifacts/{contractId}/versions").then().statusCode(200)
                .extract().jsonPath().getList("versions.version", String.class);
        assertEquals(List.of("1.0.0"), versions);
        Map<String, String> labels = artifactLabels(artifactId);
        assertEquals("DRAFT", labels.get("contract." + contractId + ".status"));
        assertFalse(labels.containsKey("contract." + contractId + ".lifecycle.stable-date"), labels.toString());
        assertEquals("1.0.0", labels.get("contract." + contractId + ".version"));
        assertEquals(rulesBefore, domainRuleNames(artifactId));
    }

    @Test
    public void testMetadataUpdateKeepsAnOmittedStatusAndStage() throws Exception {
        String artifactId = newSchema("omitted");
        String contractId = "c-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", true, artifactId)).statusCode(200);
        post(artifactId, "/contract/promote", "{\"contractId\":\"" + contractId + "\",\"targetStage\":\"DEV\"}")
                .statusCode(200);
        post(artifactId, "/contract/status", "{\"status\":\"DEPRECATED\"}").statusCode(200);

        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"ownerTeam\":\"new-team\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                .statusCode(200).body("status", equalTo("DEPRECATED")).body("stage", equalTo("DEV"));

        // Leaving status and stage out did not reset them, so they cannot be set back from scratch
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId).body("{\"status\":\"DRAFT\"}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/metadata").then()
                .statusCode(409);
        assertEquals("DEV", artifactLabels(artifactId).get("contract." + contractId + ".stage"));
    }

    @Test
    public void testAnInvalidLocationIsAWarningAndNothingIsHalfSaved() throws Exception {
        String artifactId = newSchema("valid");
        String contractId = "c-" + UUID.randomUUID();
        String yaml = contract(contractId, "1.0.0", "active", true, artifactId)
                .replace("schemas:\n", "schemas:\n  - location: " + GROUP + "/" + "a".repeat(513) + "\n");

        submit(yaml).statusCode(200).body("projection.warnings", hasItem(containsString("Invalid schema location")));

        assertEquals("orders-team", artifactLabels(artifactId).get("contract." + contractId + ".owner.team"));
    }

    @Test
    public void testRemovalAlsoClearsTagsWrittenInLowerCase() throws Exception {
        String artifactId = newSchema("legacy");
        String contractId = "Mixed-" + UUID.randomUUID();
        submit(contract(contractId, "1.0.0", "active", false, artifactId)).statusCode(200);
        // Before label keys kept their case, the tags of contract "Mixed-..." were stored as "mixed-..."
        String legacyKey = "field-tag." + contractId.toLowerCase(java.util.Locale.ROOT) + ":customeremail|pii";
        given().when().contentType(ContentTypes.APPLICATION_JSON).pathParam("groupId", GROUP)
                .pathParam("artifactId", artifactId)
                .body("{\"labels\":{\"" + legacyKey + "\":\"EXTERNAL\",\"Team\":\"Orders\"}}")
                .put("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1").then().statusCode(204);

        given().when().pathParam("groupId", GROUP).pathParam("contractId", contractId)
                .delete("/registry/v3/groups/{groupId}/contracts/{contractId}").then().statusCode(204);

        assertEquals(Map.of("Team", "Orders"), versionLabels(artifactId, "1"));
    }
}

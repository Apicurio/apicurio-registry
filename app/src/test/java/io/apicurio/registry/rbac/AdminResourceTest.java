package io.apicurio.registry.rbac;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.ArtifactReference;
import io.apicurio.registry.rest.client.models.Comment;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.RoleMapping;
import io.apicurio.registry.rest.client.models.RoleMappingSearchResults;
import io.apicurio.registry.rest.client.models.RoleType;
import io.apicurio.registry.rest.client.models.UpdateConfigurationProperty;
import io.apicurio.registry.rest.client.models.UpdateRole;
import io.apicurio.registry.rest.v3.beans.CreateRule;
import io.apicurio.registry.rest.v3.beans.Rule;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(ApplicationRbacEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AdminResourceTest extends AbstractResourceTestBase {

    @Test
    public void testGlobalRulesEndpoint() {
        given().when().contentType(CT_JSON).get("/registry/v3/admin/rules").then().statusCode(200)
                .body(anything());
    }

    @Test
    public void testCreateGlobalRule() throws Exception {
        // Test Rule type null
        CreateRule nullType = new CreateRule();
        nullType.setRuleType(null);
        nullType.setConfig("TestConfig");
        given().when().contentType(CT_JSON).body(nullType).post("/registry/v3/admin/rules").then()
                .statusCode(400);

        // Test Rule config null
        CreateRule nullConfig = new CreateRule();
        nullConfig.setRuleType(RuleType.VALIDITY);
        nullConfig.setConfig(null);
        given().when().contentType(CT_JSON).body(nullConfig).post("/registry/v3/admin/rules").then()
                .statusCode(400);

        // Test Rule config empty
        CreateRule emptyConfig = new CreateRule();
        emptyConfig.setRuleType(RuleType.VALIDITY);
        emptyConfig.setConfig("");
        given().when().contentType(CT_JSON).body(emptyConfig).post("/registry/v3/admin/rules").then()
                .statusCode(400);

    }

    @Test
    public void testGlobalRules() throws Exception {
        // Add a global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                .statusCode(204).body(anything());

        // Verify the rule was added.
        {
            given().when().get("/registry/v3/admin/rules/VALIDITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        }

        // Try to add the rule again - should get a 409
        {
            given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                    .statusCode(409).body("status", equalTo(409))
                    .body("title", equalTo("A rule named 'VALIDITY' already exists."));
        }

        // Add another global rule
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig("BACKWARD");
        given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                .statusCode(204).body(anything());

        // Get the list of rules (should be 2 of them)
        {
            given().when().get("/registry/v3/admin/rules").then().statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[2]", nullValue());
        }

        // Get a single rule by name
        given().when().get("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(200)
                .contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("BACKWARD"));

        // Update a rule's config
        Rule rule = new Rule();
        rule.setRuleType(RuleType.COMPATIBILITY);
        rule.setConfig("FULL");
        given().when().contentType(CT_JSON).body(rule).put("/registry/v3/admin/rules/COMPATIBILITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        {
            given().when().get("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("FULL"));
        }

        // Delete a rule
        given().when().delete("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        {
            given().when().get("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(404)
                    .contentType(ContentType.JSON).body("status", equalTo(404))
                    .body("title", equalTo("No rule named 'COMPATIBILITY' was found."));
        }

        // Get the list of rules (should be 1 of them)
        {
            given().when().get("/registry/v3/admin/rules").then().log().all().statusCode(200)
                    .contentType(ContentType.JSON).body("[0]", equalTo("VALIDITY")).body("[1]", nullValue());
        }

        // Delete all rules
        given().when().delete("/registry/v3/admin/rules").then().statusCode(204);

        // Get the list of rules (no rules now)
        {
            given().when().get("/registry/v3/admin/rules").then().statusCode(200)
                    .contentType(ContentType.JSON).body("[0]", nullValue());
        }

        // Get the other (deleted) rule by name (should fail with a 404)
        given().when().get("/registry/v3/admin/rules/VALIDITY").then().statusCode(404)
                .contentType(ContentType.JSON).body("status", equalTo(404))
                .body("title", equalTo("No rule named 'VALIDITY' was found."));

    }

    @Test
    public void testIntegrityRule() throws Exception {
        // Add a global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.NO_DUPLICATES.name());
        given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                .statusCode(204).body(anything());

        // Get the rule by name
        {
            given().when().get("/registry/v3/admin/rules/INTEGRITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("INTEGRITY"))
                    .body("config", equalTo("NO_DUPLICATES"));
        }

        // Update the rule config
        String newConfig = IntegrityLevel.NO_DUPLICATES + "," + IntegrityLevel.REFS_EXIST;
        Rule rule = new Rule();
        rule.setRuleType(RuleType.INTEGRITY);
        rule.setConfig(newConfig);
        given().when().contentType(CT_JSON).body(rule).put("/registry/v3/admin/rules/INTEGRITY").then()
                .statusCode(200).contentType(ContentType.JSON).body("ruleType", equalTo("INTEGRITY"))
                .body("config", equalTo(newConfig));

        // Verify new config
        {
            given().when().get("/registry/v3/admin/rules/INTEGRITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("INTEGRITY"))
                    .body("config", equalTo(newConfig));
        }

    }

    @Test
    public void testDeleteAllGlobalRules() throws Exception {
        // Add a global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig("FULL");
        given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                .statusCode(204).body(anything());

        // Get a single rule by name
        {
            given().when().get("/registry/v3/admin/rules/VALIDITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        }

        // Delete all rules
        given().when().delete("/registry/v3/admin/rules").then().statusCode(204);

        // Get the (deleted) rule by name (should fail with a 404)
        {
            given().when().get("/registry/v3/admin/rules/VALIDITY").then().statusCode(404)
                    .contentType(ContentType.JSON).body("status", equalTo(404))
                    .body("title", equalTo("No rule named 'VALIDITY' was found."));
        }
    }

    @Test
    public void testCompatilibityLevelNone() throws Exception {
        // Add a global rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.COMPATIBILITY);
        createRule.setConfig(CompatibilityLevel.NONE.name());
        given().when().contentType(CT_JSON).body(createRule).post("/registry/v3/admin/rules").then()
                .statusCode(204).body(anything());

        // Get a single rule by name
        {
            given().when().get("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("NONE"));
        }
    }

    @Test
    void testExport() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = TestUtils.generateGroupId();

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI,
                    artifactContent.replaceAll("Empty API", title), ContentTypes.APPLICATION_JSON);
        }

        ValidatableResponse response = given().when().get("/registry/v3/admin/export").then().statusCode(200);
        InputStream body = response.extract().asInputStream();
        ZipInputStream zip = new ZipInputStream(body);

        int artifactCount = clientV3.groups().byGroupId(group).artifacts().get().getCount();
        Assertions.assertEquals(5, artifactCount);

        AtomicInteger contentCounter = new AtomicInteger(0);
        AtomicInteger versionCounter = new AtomicInteger(0);

        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            String name = entry.getName();

            if (name.endsWith(".Content.json")) {
                contentCounter.incrementAndGet();
            } else if (name.endsWith(".ArtifactVersion.json")) {
                versionCounter.incrementAndGet();
            }

            // Next entry.
            entry = zip.getNextEntry();
        }

        Assertions.assertTrue(contentCounter.get() >= 5);
        Assertions.assertTrue(versionCounter.get() >= 5);
    }

    @Test
    void testExportForBrowser() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testExportForBrowser";
        // Avoid the reference conflict by appending a UUID to the title
        var suffix = UUID.randomUUID().toString();

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx + " " + suffix;
            String artifactId = "Empty-" + idx;
            String emptyApiContent = artifactContent.replaceAll("Empty API", title);
            List<ArtifactReference> refs = idx > 0
                ? getSingletonRefList(group, "Empty-" + (idx - 1), "1", "ref") : Collections.emptyList();

            CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, ArtifactType.OPENAPI,
                    emptyApiContent, ContentTypes.APPLICATION_JSON);
            createArtifact.getFirstVersion().getContent().setReferences(refs);

            clientV3.groups().byGroupId(group).artifacts().post(createArtifact);
        }

        // Export data (browser flow).
        String downloadHref = given().when().queryParam("forBrowser", "true").get("/registry/v3/admin/export")
                .then().statusCode(200).contentType(ContentType.JSON).body("downloadId", notNullValue())
                .body("href", notNullValue()).extract().body().path("href");
        Assertions.assertTrue(downloadHref.startsWith("/apis/"));
        downloadHref = downloadHref.substring(5);

        // Follow href in response
        ValidatableResponse response = given().when().get(downloadHref).then().statusCode(200);
        InputStream body = response.extract().asInputStream();
        ZipInputStream zip = new ZipInputStream(body);

        AtomicInteger contentCounter = new AtomicInteger(0);
        AtomicInteger versionCounter = new AtomicInteger(0);

        ZipEntry entry = zip.getNextEntry();
        while (entry != null) {
            String name = entry.getName();

            if (name.endsWith(".Content.json")) {
                contentCounter.incrementAndGet();
            } else if (name.endsWith(".ArtifactVersion.json")) {
                versionCounter.incrementAndGet();
            }

            // Next entry.
            entry = zip.getNextEntry();
        }

        Assertions.assertTrue(contentCounter.get() >= 5);
        Assertions.assertTrue(versionCounter.get() >= 5);

        // Try the download href again - should fail with 404 because it was already consumed.
        given().when().get(downloadHref).then().statusCode(404);
    }

    @Test
    @Disabled // TODO: Disabled, so I can submit the import/export migration feature in a separate PR.
    void testImport() throws Exception {
        var result = clientV3.search().artifacts().get(config -> {
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 5;
        });
        int artifactsBefore = result.getCount();

        try (InputStream data = resourceToInputStream("../rest/v3/export.zip")) {
            given().when().contentType("application/zip").body(data).post("/registry/v3/admin/import").then()
                    .statusCode(204).body(anything());
        }

        // Verify global rules were imported
        {
            given().when().get("/registry/v3/admin/rules/COMPATIBILITY").then().statusCode(200)
                    .contentType(ContentType.JSON).body("ruleType", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        }

        // Verify artifacts were imported
        // Verify all artifact versions were imported
        // total num of artifacts 3
        result = clientV3.search().artifacts().get(config -> {
            config.queryParameters.offset = 0;
            config.queryParameters.limit = 5;
        });
        int newArtifacts = result.getCount().intValue() - artifactsBefore;
        assertEquals(3, newArtifacts);

        // Verify comments were imported
        List<Comment> comments = clientV3.groups().byGroupId("ImportTest").artifacts()
                .byArtifactId("Artifact-1").versions().byVersionExpression("1.0.2").comments().get();
        assertNotNull(comments);
        assertEquals(2, comments.size());
        assertEquals("COMMENT-2", comments.get(0).getValue());
        assertEquals("COMMENT-1", comments.get(1).getValue());

        comments = clientV3.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-2").versions()
                .byVersionExpression("1.0.1").comments().get();
        assertNotNull(comments);
        assertEquals(1, comments.size());
        assertEquals("COMMENT-3", comments.get(0).getValue());

        // Verify artifact rules were imported
        var rule = clientV3.groups().byGroupId("ImportTest").artifacts().byArtifactId("Artifact-1").rules()
                .byRuleType(RuleType.VALIDITY.name()).get();
        assertNotNull(rule);
        assertEquals("SYNTAX_ONLY", rule.getConfig());

        // the biggest globalId in the export file is 1005
        assertNotNull(clientV3.ids().globalIds().byGlobalId(1005L).get());

        // this is the artifactId for the artifact with globalId 1005
        var lastArtifactMeta = clientV3.groups().byGroupId("ImportTest").artifacts()
                .byArtifactId("Artifact-3").versions().byVersionExpression("branch=latest").get();
        assertEquals("1.0.2", lastArtifactMeta.getVersion());
        assertEquals(1005L, lastArtifactMeta.getGlobalId());

        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.ProblemDetails.class,
                () -> clientV3.ids().globalIds().byGlobalId(1006L).get());
        // ArtifactNotFoundException
        Assertions.assertEquals("ArtifactNotFoundException", exception.getName());
        Assertions.assertEquals(404, exception.getStatus());
    }

    @Test
    public void testRoleMappings() throws Exception {
        // Start with no role mappings
        given().when().get("/registry/v3/admin/roleMappings").then().statusCode(200)
                .contentType(ContentType.JSON).body("roleMappings[0]", nullValue());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        mapping.setPrincipalName("Foo bar");
        given().when().contentType(CT_JSON).body(mapping).post("/registry/v3/admin/roleMappings").then()
                .statusCode(204).body(anything());

        // Verify the mapping was added.
        given().when().get("/registry/v3/admin/roleMappings/TestUser").then().statusCode(200)
                .contentType(ContentType.JSON).body("principalId", equalTo("TestUser"))
                .body("principalName", equalTo("Foo bar")).body("role", equalTo("DEVELOPER"));
        given().when().get("/registry/v3/admin/roleMappings").then().statusCode(200)
                .contentType(ContentType.JSON).body("roleMappings[0].principalId", equalTo("TestUser"))
                .body("roleMappings[0].principalName", equalTo("Foo bar"))
                .body("roleMappings[0].role", equalTo("DEVELOPER"));

        // Try to add the rule again - should get a 409
        given().when().contentType(CT_JSON).body(mapping).post("/registry/v3/admin/roleMappings").then()
                .statusCode(409).body("status", equalTo(409)).body("title",
                        equalTo("A mapping for principal 'TestUser' and role 'DEVELOPER' already exists."));

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        given().when().contentType(CT_JSON).body(mapping).post("/registry/v3/admin/roleMappings").then()
                .statusCode(204).body(anything());

        // Get the list of mappings (should be 2 of them)
        given().when().get("/registry/v3/admin/roleMappings").then().statusCode(200)
                .contentType(ContentType.JSON)
                .body("roleMappings[0].principalId", anyOf(equalTo("TestUser"), equalTo("TestUser2")))
                .body("roleMappings[1].principalId", anyOf(equalTo("TestUser"), equalTo("TestUser2")))
                .body("roleMappings[2]", nullValue());

        // Get a single mapping by principal
        given().when().get("/registry/v3/admin/roleMappings/TestUser2").then().statusCode(200)
                .contentType(ContentType.JSON).body("principalId", equalTo("TestUser2"))
                .body("role", equalTo("ADMIN"));

        // Update a mapping
        UpdateRole update = new UpdateRole();
        update.setRole(RoleType.READ_ONLY);
        given().when().contentType(CT_JSON).body(update).put("/registry/v3/admin/roleMappings/TestUser")
                .then().statusCode(204);

        // Get a single (updated) mapping
        given().when().get("/registry/v3/admin/roleMappings/TestUser").then().statusCode(200)
                .contentType(ContentType.JSON).body("principalId", equalTo("TestUser"))
                .body("role", equalTo("READ_ONLY"));

        // Try to update a role mapping that doesn't exist
        given().when().contentType(CT_JSON).body(update)
                .put("/registry/v3/admin/roleMappings/UnknownPrincipal").then().statusCode(404)
                .contentType(ContentType.JSON).body("status", equalTo(404)).body("title", equalTo(
                        "No mapping for principal 'UnknownPrincipal' and role 'READ_ONLY' was found."));

        // Update a mapping with null RoleType
        update.setRole(null);
        given().when().contentType(CT_JSON).body(update).put("/registry/v3/admin/roleMappings/TestUser")
                .then().statusCode(400);

        // Delete a role mapping
        given().when().delete("/registry/v3/admin/roleMappings/TestUser2").then().statusCode(204)
                .body(anything());

        // Get the (deleted) mapping by name (should fail with a 404)
        given().when().get("/registry/v3/admin/roleMappings/TestUser2").then().statusCode(404)
                .contentType(ContentType.JSON).body("status", equalTo(404))
                .body("title", equalTo("No role mapping for principal 'TestUser2' was found."));

        // Get the list of mappings (should be 1 of them)
        given().when().get("/registry/v3/admin/roleMappings").then().log().all().statusCode(200)
                .contentType(ContentType.JSON).body("roleMappings[0].principalId", equalTo("TestUser"))
                .body("roleMappings[1]", nullValue());

        // Clean up
        given().when().delete("/registry/v3/admin/roleMappings/TestUser").then().statusCode(204)
                .body(anything());
    }

    @Test
    public void testRoleMappingPaging() throws Exception {
        // Start with no role mappings
        Assertions.assertEquals(0, clientV3.admin().roleMappings().get().getCount().intValue());

        // Add some mappings
        for (int i = 0; i < 20; i++) {
            RoleMapping mapping = new RoleMapping();
            mapping.setPrincipalId("principal-" + i);
            mapping.setRole(RoleType.DEVELOPER);
            mapping.setPrincipalName("Principal " + i);
            clientV3.admin().roleMappings().post(mapping);
        }

        // Make sure we created 20 mappings
        Assertions.assertEquals(20, clientV3.admin().roleMappings().get().getCount().intValue());

        // Get the first 5
        RoleMappingSearchResults results = clientV3.admin().roleMappings()
                .get(config -> config.queryParameters.limit = 5);
        Assertions.assertEquals(5, results.getRoleMappings().size());
        Assertions.assertEquals(20, results.getCount());
        Assertions.assertEquals("principal-0", results.getRoleMappings().get(0).getPrincipalId());
        Assertions.assertEquals("principal-4", results.getRoleMappings().get(4).getPrincipalId());

        // Get the second 5
        results = clientV3.admin().roleMappings().get(config -> {
            config.queryParameters.limit = 5;
            config.queryParameters.offset = 5;
        });
        Assertions.assertEquals(5, results.getRoleMappings().size());
        Assertions.assertEquals(20, results.getCount());
        Assertions.assertEquals("principal-5", results.getRoleMappings().get(0).getPrincipalId());
        Assertions.assertEquals("principal-9", results.getRoleMappings().get(4).getPrincipalId());

        // Cleanup
        for (int i = 0; i < 20; i++) {
            clientV3.admin().roleMappings().byPrincipalId("principal-" + i).delete();
        }
    }

    @Test
    public void testConfigProperties() throws Exception {
        String property1Name = "apicurio.ccompat.legacy-id-mode.enabled";
        String property2Name = "apicurio.ccompat.use-canonical-hash";

        // Start with default mappings
        given().when().get("/registry/v3/admin/config/properties").then().statusCode(200)
                .contentType(ContentType.JSON);

        // Fetch property 1, should be false
        given().when().pathParam("propertyName", property1Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property1Name))
                .body("value", equalTo("false"));

        // Fetch property 2, should be false
        given().when().pathParam("propertyName", property2Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property2Name))
                .body("value", equalTo("false"));

        // Set value for property 1
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue("true");
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", property1Name)
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204)
                .body(anything());

        // Verify the property was set.
        given().when().pathParam("propertyName", property1Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property1Name))
                .body("value", equalTo("true"));

        // Set value for property 2
        update = new UpdateConfigurationProperty();
        update.setValue("true");
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", property2Name)
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204)
                .body(anything());

        // Verify the property was set.
        given().when().pathParam("propertyName", property2Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property2Name))
                .body("value", equalTo("true"));

        // Reset a config property
        given().when().pathParam("propertyName", property2Name)
                .delete("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204)
                .body(anything());

        // Verify the property was reset.
        given().when().pathParam("propertyName", property2Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property2Name))
                .body("value", equalTo("false"));

        // Reset the other property
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", property1Name)
                .delete("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(204)
                .body(anything());

        // Verify the property was reset
        given().when().pathParam("propertyName", property1Name)
                .get("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(200)
                .contentType(ContentType.JSON).body("name", equalTo(property1Name))
                .body("value", equalTo("false"));

        // Try to set a config property that doesn't exist.
        update = new UpdateConfigurationProperty();
        update.setValue("foobar");
        given().when().contentType(CT_JSON).body(update).pathParam("propertyName", "property-does-not-exist")
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(404);

        // Try to set a Long property to "foobar" (should be invalid type)
        update = new UpdateConfigurationProperty();
        update.setValue("foobar");
        given().when().contentType(CT_JSON).body(update)
                .pathParam("propertyName", "apicurio.download.href.ttl.seconds")
                .put("/registry/v3/admin/config/properties/{propertyName}").then().statusCode(400);

    }

    private List<ArtifactReference> getSingletonRefList(String groupId, String artifactId, String version,
            String name) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

}

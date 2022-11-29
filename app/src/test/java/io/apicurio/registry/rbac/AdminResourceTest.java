/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.rbac;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.registry.utils.tests.ApplicationRbacEnabledProfile;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.RoleMapping;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.UpdateConfigurationProperty;
import io.apicurio.registry.rest.v2.beans.UpdateRole;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.types.RoleType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonObject;

/**
 * @author eric.wittmann@gmail.com
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(ApplicationRbacEnabledProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class AdminResourceTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.log.level")
    @Info(category = "log", description = "Log level", availableSince = "2.0.0.Final")
    String defaultLogLevel;

    @Test
    public void testGlobalRulesEndpoint() {
        given()
            .when()
                .contentType(CT_JSON)
                .get("/registry/v2/admin/rules")
            .then()
                .statusCode(200)
                .body(anything());
    }

    @Test
    public void testGlobalRules() throws Exception {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON).body(rule)
                .post("/registry/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added.
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON).body(rule)
                    .post("/registry/v2/admin/rules")
                .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        });

        // Add another global rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/registry/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the list of rules (should be 2 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[1]", anyOf(equalTo("VALIDITY"), equalTo("COMPATIBILITY")))
                    .body("[2]", nullValue());
        });

        // Get a single rule by name
        given()
            .when()
                .get("/registry/v2/admin/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("BACKWARD"));

        // Update a rule's config
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .put("/registry/v2/admin/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("FULL"));
        });

        // Try to update a rule's config for a rule that doesn't exist.
//        rule.setType("RuleDoesNotExist");
//        rule.setConfig("rdne-config");
//        given()
//            .when().contentType(CT_JSON).body(rule).put("/registry/v2/admin/rules/RuleDoesNotExist")
//            .then()
//            .statusCode(404)
//            .contentType(ContentType.JSON)
//            .body("error_code", equalTo(404))
//            .body("message", equalTo("No rule named 'RuleDoesNotExist' was found."));

        // Delete a rule
        given()
            .when()
                .delete("/registry/v2/admin/rules/COMPATIBILITY")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/COMPATIBILITY")
                .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No rule named 'COMPATIBILITY' was found."));
        });

        // Get the list of rules (should be 1 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules")
                .then()
                .log().all()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", equalTo("VALIDITY"))
                    .body("[1]", nullValue());
        });

        // Delete all rules
        given()
            .when()
                .delete("/registry/v2/admin/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", nullValue());
        });

        // Get the other (deleted) rule by name (should fail with a 404)
        given()
            .when()
                .get("/registry/v2/admin/rules/VALIDITY")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No rule named 'VALIDITY' was found."));

    }

    @Test
    public void testDeleteAllGlobalRules() throws Exception {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/registry/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete all rules
        given()
            .when()
                .delete("/registry/v2/admin/rules")
            .then()
                .statusCode(204);

        // Get the (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/VALIDITY")
                .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No rule named 'VALIDITY' was found."));
        });
    }

    @Test
    public void testCompatilibityLevelNone() throws Exception {
        // Add a global rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.NONE.name());
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/registry/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("NONE"));
        });
    }

    @Test
    void testLoggerSetsLevel() throws Exception {
        String testLoggerName = "foo.logger.testLoggerSetsLevel";
        Logger logger = Logger.getLogger(testLoggerName);
        logger.setLevel(Level.parse(defaultLogLevel));

        String defaultLogLevel = Logger.getLogger(testLoggerName).getLevel().getName();
        TestUtils.retry(() -> {
            verifyLogLevel(testLoggerName, LogLevel.fromValue(defaultLogLevel));
        });

        //remove default log level to avoid conflicts with the checkLogLevel daemon process
        List<LogLevel> levels =  EnumSet.allOf(LogLevel.class)
            .stream()
            .filter(l -> !l.value().equals(defaultLogLevel))
            .collect(Collectors.toList());


        for (LogLevel level : levels) {
            LogConfiguration lc = new LogConfiguration();
            lc.setLevel(level);
            given()
                .when()
                    .body(lc)
                    .contentType(ContentType.JSON)
                    .pathParam("logger", testLoggerName)
                    .put("/registry/v2/admin/loggers/{logger}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("level", is(level.value()));
            TestUtils.retry(() -> assertEquals(level.value(), Logger.getLogger(testLoggerName).getLevel().getName()));
        }

        clearLogConfig(testLoggerName);
    }

    @Test
    void testLoggerInvalidLevel() {
        JsonObject lc = new JsonObject().put("level", "FOO");
        given()
            .when()
                .body(lc)
                .contentType(ContentType.JSON)
                .pathParam("logger", "foo.logger.invalid")
                .put("/registry/v2/admin/loggers/{logger}")
            .then()
                .statusCode(400);
    }

    private void verifyLogLevel(String loggerName, LogLevel level) {
        given()
            .when()
                .pathParam("logger", loggerName)
                .get("/registry/v2/admin/loggers/{logger}")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("level", is(level.value()));
    }

    private void clearLogConfig(String loggerName) throws Exception {
        given()
        .when()
            .pathParam("logger", loggerName)
            .delete("/registry/v2/admin/loggers/{logger}")
        .then()
            .statusCode(200);

        TestUtils.retry(() -> {
            Response res = given()
                    .when()
                        .get("/registry/v2/admin/loggers")
                    .thenReturn();
                assertEquals(200, res.statusCode());
                NamedLogConfiguration[] configs = res.as(NamedLogConfiguration[].class);

                assertTrue(Stream.of(configs)
                        .filter(named -> named.getName().equals(loggerName))
                        .findAny()
                        .isEmpty());

        }, "Clear log config", 50);
    }

    @Test
    void testLoggersCRUD() throws Exception {
        String testLoggerName = "foo.logger.testLoggersCRUD";
        Logger logger = Logger.getLogger(testLoggerName);
        logger.setLevel(Level.parse(defaultLogLevel));

        String defaultLogLevel = Logger.getLogger(testLoggerName).getLevel().getName();
        TestUtils.retry(() -> {
            verifyLogLevel(testLoggerName, LogLevel.fromValue(defaultLogLevel));
        });

        Consumer<LogLevel> setLog = (level) -> {
            LogConfiguration lc = new LogConfiguration();
            lc.setLevel(level);
            given()
                .when()
                    .body(lc)
                    .contentType(ContentType.JSON)
                    .pathParam("logger", testLoggerName)
                    .put("/registry/v2/admin/loggers/{logger}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("level", is(level.value()));
        };

        Consumer<LogLevel> verifyLevel = (level) -> {
            Response res = given()
                .when()
                    .get("/registry/v2/admin/loggers")
                .thenReturn();
            NamedLogConfiguration[] configs = res.as(NamedLogConfiguration[].class);

            assertTrue(Stream.of(configs)
                    .filter(named -> named.getName().equals(testLoggerName) && named.getLevel().equals(level))
                    .findAny()
                    .isPresent());

            String actualLevel = Logger.getLogger(testLoggerName).getLevel().getName();
            assertEquals(level.value(), actualLevel,
                    "Log value for logger " + testLoggerName + " was NOT set to '" + level.value() + "' it was '" + actualLevel + "', even though the server reported it was.");
        };


        //remove default log level to avoid conflicts with the checkLogLevel daemon process
        List<LogLevel> levels = EnumSet.allOf(LogLevel.class)
            .stream()
            .filter(l -> !l.value().equals(defaultLogLevel))
            .collect(Collectors.toList());
        //pick two random levels that are not the default level
        Random r = new Random();

        Map<String, LogLevel> testLevels = new HashMap<>();
        TestUtils.retry(() -> {
            testLevels.put("first", levels.get(r.nextInt(levels.size())));
            testLevels.put("second", levels.get(r.nextInt(levels.size())));
            assertNotEquals(testLevels.get("first"), testLevels.get("second"));
        });
        LogLevel firstLevel = testLevels.get("first");
        LogLevel secondLevel = testLevels.get("second");

        System.out.println("Going to test log level change from " + defaultLogLevel + " to " + firstLevel.name() + " and then to " + secondLevel.name());

        setLog.accept(firstLevel);
        TestUtils.retry(() -> {
            verifyLogLevel(testLoggerName, firstLevel);
            verifyLevel.accept(firstLevel);
        });

        setLog.accept(secondLevel);
        TestUtils.retry(() -> {
            verifyLogLevel(testLoggerName, secondLevel);
            verifyLevel.accept(secondLevel);
        });

        clearLogConfig(testLoggerName);

        TestUtils.retry(() -> {
            Response res = given()
                    .when()
                        .pathParam("logger", testLoggerName)
                        .get("/registry/v2/admin/loggers/{logger}")
                    .thenReturn();

                assertEquals(200, res.statusCode());
                NamedLogConfiguration cfg = res.getBody().as(NamedLogConfiguration.class);

                assertEquals(cfg.getLevel().value(), Logger.getLogger(testLoggerName).getLevel().getName());
        });
    }

    @Test
    void testExport() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testExport";

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            this.createArtifact(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title));
            waitForArtifact(group, artifactId);
        }

        ValidatableResponse response = given()
            .when()
                .get("/registry/v2/admin/export")
            .then()
                .statusCode(200);
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
    }

    @Test
    void testExportForBrowser() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "testExportForBrowser";

        // Create 5 artifacts in the UUID group
        for (int idx = 0; idx < 5; idx++) {
            String title = "Empty API " + idx;
            String artifactId = "Empty-" + idx;
            List<ArtifactReference> refs = idx > 0 ? getSingletonRefList(group, "Empty-" + (idx - 1), "1", "ref") : Collections.emptyList();
            this.createArtifactWithReferences(group, artifactId, ArtifactType.OPENAPI, artifactContent.replaceAll("Empty API", title), refs);
            waitForArtifact(group, artifactId);
        }

        // Export data (browser flow).
        String downloadHref = given()
            .when()
                .queryParam("forBrowser", "true")
                .get("/registry/v2/admin/export")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("downloadId", notNullValue())
                .body("href", notNullValue())
                .extract().body().path("href");
        Assertions.assertTrue(downloadHref.startsWith("/apis/"));
        downloadHref = downloadHref.substring(5);

        // Follow href in response
        ValidatableResponse response = given()
            .when()
                .get(downloadHref)
            .then()
                .statusCode(200);
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
        given()
            .when()
                .get(downloadHref)
            .then()
                .statusCode(404);
    }

    @Test
    void testImport() throws Exception {
        var result = clientV2.searchArtifacts(null, null, null, null, null, null, null, 0, 5);
        int artifactsBefore = result.getCount();

        try (InputStream data = resourceToInputStream("../rest/v2/export.zip")) {
            given()
                .when()
                    .contentType("application/zip")
                    .body(data)
                    .post("/registry/v2/admin/import")
                .then()
                    .statusCode(204)
                    .body(anything());
        }

        // Verify global rules were imported
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("BACKWARD"));
        });

        // Verify artifacts were imported
        // Verify all artifact versions were imported
        //total num of artifacts 3
        result = clientV2.searchArtifacts(null, null, null, null, null, null, null, 0, 5);
        int newArtifacts = result.getCount().intValue() - artifactsBefore;
        assertEquals(3, newArtifacts);

        // Verify artifact rules were imported
        var rule = clientV2.getArtifactRuleConfig("ImportTest", "Artifact-1", RuleType.VALIDITY);
        assertNotNull(rule);
        assertEquals("SYNTAX_ONLY", rule.getConfig());

        //the biggest globalId in the export file is 1005
        assertNotNull(clientV2.getContentByGlobalId(1005));

        //this is the artifactId for the artifact with globalId 1005
        var lastArtifactMeta = clientV2.getArtifactMetaData("ImportTest", "Artifact-3");
        assertEquals("1.0.2", lastArtifactMeta.getVersion());
        assertEquals(1005, lastArtifactMeta.getGlobalId());

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> clientV2.getContentByGlobalId(1006));

        var meta = clientV2.createArtifact(null, "newartifact", ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        assertEquals(1006, meta.getGlobalId().intValue());
    }


    @Test
    public void testRoleMappings() throws Exception {
        // Start with no role mappings
        given()
            .when()
                .get("/registry/v2/admin/roleMappings")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("[0]", nullValue());

        // Add
        RoleMapping mapping = new RoleMapping();
        mapping.setPrincipalId("TestUser");
        mapping.setRole(RoleType.DEVELOPER);
        mapping.setPrincipalName("Foo bar");
        given()
            .when()
                .contentType(CT_JSON).body(mapping)
                .post("/registry/v2/admin/roleMappings")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the mapping was added.
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings/TestUser")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("principalId", equalTo("TestUser"))
                    .body("principalName", equalTo("Foo bar"))
                    .body("role", equalTo("DEVELOPER"));
        });
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0].principalId", equalTo("TestUser"))
                    .body("[0].principalName", equalTo("Foo bar"))
                    .body("[0].role", equalTo("DEVELOPER"));
        });

        // Try to add the rule again - should get a 409
        TestUtils.retry(() -> {
            given()
                .when()
                    .contentType(CT_JSON).body(mapping)
                    .post("/registry/v2/admin/roleMappings")
                .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A role mapping for this principal already exists."));
        });

        // Add another mapping
        mapping.setPrincipalId("TestUser2");
        mapping.setRole(RoleType.ADMIN);
        given()
            .when()
                .contentType(CT_JSON)
                .body(mapping)
                .post("/registry/v2/admin/roleMappings")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the list of mappings (should be 2 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0].principalId", anyOf(equalTo("TestUser"), equalTo("TestUser2")))
                    .body("[1].principalId", anyOf(equalTo("TestUser"), equalTo("TestUser2")))
                    .body("[2]", nullValue());
        });

        // Get a single mapping by principal
        given()
            .when()
                .get("/registry/v2/admin/roleMappings/TestUser2")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("principalId", equalTo("TestUser2"))
                .body("role", equalTo("ADMIN"));

        // Update a mapping
        UpdateRole update = new UpdateRole();
        update.setRole(RoleType.READ_ONLY);
        given()
            .when()
                .contentType(CT_JSON)
                .body(update)
                .put("/registry/v2/admin/roleMappings/TestUser")
            .then()
                .statusCode(204);

        // Get a single (updated) mapping
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings/TestUser")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("principalId", equalTo("TestUser"))
                    .body("role", equalTo("READ_ONLY"));
        });

        // Try to update a role mapping that doesn't exist
        given()
            .when()
                .contentType(CT_JSON)
                .body(update)
                .put("/registry/v2/admin/roleMappings/UnknownPrincipal")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(404))
                .body("message", equalTo("Role mapping not found for principal."));

        // Delete a role mapping
        given()
            .when()
                .delete("/registry/v2/admin/roleMappings/TestUser2")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the (deleted) mapping by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings/TestUser2")
                .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("Role mapping not found for principal."));
        });

        // Get the list of mappings (should be 1 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/registry/v2/admin/roleMappings")
                .then()
                .log().all()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0].principalId", equalTo("TestUser"))
                    .body("[1]", nullValue());
        });

        // Clean up
        given()
            .when()
                .delete("/registry/v2/admin/roleMappings/TestUser")
            .then()
                .statusCode(204)
                .body(anything());
    }

    @Test
    public void testConfigProperties() throws Exception {
        String property1Name = "registry.ccompat.legacy-id-mode.enabled";
        String property2Name = "registry.ui.features.readOnly";

        // Start with default mappings
        given()
            .when()
                .get("/registry/v2/admin/config/properties")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property1Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property1Name))
                    .body("value", equalTo("false"));
        });
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property2Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property2Name))
                    .body("value", equalTo("false"));
        });

        // Set value for a property
        UpdateConfigurationProperty update = new UpdateConfigurationProperty();
        update.setValue("true");
        given()
            .when()
                .contentType(CT_JSON).body(update)
                .pathParam("propertyName", property1Name)
                .put("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the property was set.
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property1Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property1Name))
                    .body("value", equalTo("true"));
        });

        // Set another property
        update = new UpdateConfigurationProperty();
        update.setValue("true");
        given()
            .when()
                .contentType(CT_JSON).body(update)
                .pathParam("propertyName", property2Name)
                .put("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the property was set.
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property2Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property2Name))
                    .body("value", equalTo("true"));
        });

        // Reset a config property
        given()
            .when()
                .pathParam("propertyName", property2Name)
                .delete("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the property was reset.
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property2Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property2Name))
                    .body("value", equalTo("false"));
        });

        // Reset the other property
        given()
            .when()
                .contentType(CT_JSON).body(update)
                .pathParam("propertyName", property1Name)
                .delete("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the property was reset
        TestUtils.retry(() -> {
            given()
                .when()
                    .pathParam("propertyName", property1Name)
                    .get("/registry/v2/admin/config/properties/{propertyName}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("name", equalTo(property1Name))
                    .body("value", equalTo("false"));
        });

        // Try to set a config property that doesn't exist.
        update = new UpdateConfigurationProperty();
        update.setValue("foobar");
        given()
            .when()
                .contentType(CT_JSON).body(update)
                .pathParam("propertyName", "property-does-not-exist")
                .put("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(404);

        // Try to set a Long property to "foobar" (should be invalid type)
        update = new UpdateConfigurationProperty();
        update.setValue("foobar");
        given()
            .when()
                .contentType(CT_JSON).body(update)
                .pathParam("propertyName", "registry.download.href.ttl")
                .put("/registry/v2/admin/config/properties/{propertyName}")
            .then()
                .statusCode(400);

    }

    private List<ArtifactReference> getSingletonRefList(String groupId, String artifactId, String version, String name) {
        ArtifactReference artifactReference = new ArtifactReference();
        artifactReference.setGroupId(groupId);
        artifactReference.setArtifactId(artifactId);
        artifactReference.setVersion(version);
        artifactReference.setName(name);
        return Collections.singletonList(artifactReference);
    }

}

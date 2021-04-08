/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.LogLevel;
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
public class AdminResourceTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.log.level")
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

            String actualLevel = Logger.getLogger(testLoggerName).getLevel().getName();
            assertEquals(level.value(), actualLevel,
                    "Log value for logger " + testLoggerName + " was NOT set to '" + level.value() + "' it was '" + actualLevel + "', even though the server reported it was.");
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
        verifyLogLevel(testLoggerName, firstLevel);
        verifyLevel.accept(firstLevel);

        setLog.accept(secondLevel);
        verifyLogLevel(testLoggerName, secondLevel);
        verifyLevel.accept(secondLevel);

        clearLogConfig(testLoggerName);

        Response res = given()
            .when()
                .pathParam("logger", testLoggerName)
                .get("/registry/v2/admin/loggers/{logger}")
            .thenReturn();

        assertEquals(200, res.statusCode());
        NamedLogConfiguration cfg = res.getBody().as(NamedLogConfiguration.class);

        assertEquals(cfg.getLevel().value(), Logger.getLogger(testLoggerName).getLevel().getName());
    }

    @Test
    void testExport() throws Exception {
        String artifactContent = resourceToString("openapi-empty.json");
        String group = "export-group";

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
    void testImport() throws Exception {
        try (InputStream data = resourceToInputStream("export.zip")) {
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

        // Verify artifact rules were imported

        // Verify all artifact versions were imported

    }

}

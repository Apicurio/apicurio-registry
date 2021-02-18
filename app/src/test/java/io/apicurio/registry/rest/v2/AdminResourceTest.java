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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.LogConfiguration;
import io.apicurio.registry.rest.v2.beans.NamedLogConfiguration;
import io.apicurio.registry.rules.compatibility.CompatibilityLevel;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

/**
 * @author eric.wittmann@gmail.com
 * @author Fabian Martinez
 */
@QuarkusTest
public class AdminResourceTest extends AbstractResourceTestBase {

    private static final String TEST_LOGGER_NAME = "org.acme.test";

    @ConfigProperty(name = "quarkus.log.level")
    String defaultLogLevel;

    @BeforeEach
    public void setUp() {
        Logger logger = Logger.getLogger(TEST_LOGGER_NAME);
        logger.setLevel(Level.parse(defaultLogLevel));
    }

    @Test
    public void testGlobalRulesEndpoint() {
        given()
            .when()
                .contentType(CT_JSON)
                .get("/v2/admin/rules")
            .then()
                .statusCode(200)
                .body(anything());
    }

    @Test
    public void testGlobalRules() throws Exception {
        // Add a globalIdStore rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON).body(rule)
                .post("/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Verify the rule was added.
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/VALIDITY")
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
                    .post("/v2/admin/rules")
                .then()
                    .statusCode(409)
                    .body("error_code", equalTo(409))
                    .body("message", equalTo("A rule named 'VALIDITY' already exists."));
        });

        // Add another globalIdStore rule
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get the list of rules (should be 2 of them)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules")
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
                .get("/v2/admin/rules/COMPATIBILITY")
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
                .put("/v2/admin/rules/COMPATIBILITY")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("type", equalTo("COMPATIBILITY"))
                .body("config", equalTo("FULL"));

        // Get a single (updated) rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/COMPATIBILITY")
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
//            .when().contentType(CT_JSON).body(rule).put("/v2/admin/rules/RuleDoesNotExist")
//            .then()
//            .statusCode(404)
//            .contentType(ContentType.JSON)
//            .body("error_code", equalTo(404))
//            .body("message", equalTo("No rule named 'RuleDoesNotExist' was found."));

        // Delete a rule
        given()
            .when()
                .delete("/v2/admin/rules/COMPATIBILITY")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/COMPATIBILITY")
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
                    .get("/v2/admin/rules")
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
                .delete("/v2/admin/rules")
            .then()
                .statusCode(204);

        // Get the list of rules (no rules now)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("[0]", nullValue());
        });

        // Get the other (deleted) rule by name (should fail with a 404)
        given()
            .when()
                .get("/v2/admin/rules/VALIDITY")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error_code", equalTo(404))
                .body("message", equalTo("No rule named 'VALIDITY' was found."));

    }

    @Test
    public void testDeleteAllGlobalRules() throws Exception {
        // Add a globalIdStore rule
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("FULL");
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/VALIDITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("VALIDITY"))
                    .body("config", equalTo("FULL"));
        });

        // Delete all rules
        given()
            .when()
                .delete("/v2/admin/rules")
            .then()
                .statusCode(204);

        // Get the (deleted) rule by name (should fail with a 404)
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/VALIDITY")
                .then()
                    .statusCode(404)
                    .contentType(ContentType.JSON)
                    .body("error_code", equalTo(404))
                    .body("message", equalTo("No rule named 'VALIDITY' was found."));
        });
    }

    @Test
    public void testCompatilibityLevelNone() throws Exception {
        // Add a globalIdStore rule
        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig(CompatibilityLevel.NONE.name());
        given()
            .when()
                .contentType(CT_JSON)
                .body(rule)
                .post("/v2/admin/rules")
            .then()
                .statusCode(204)
                .body(anything());

        // Get a single rule by name
        TestUtils.retry(() -> {
            given()
                .when()
                    .get("/v2/admin/rules/COMPATIBILITY")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("type", equalTo("COMPATIBILITY"))
                    .body("config", equalTo("NONE"));
        });
    }

    @Test
    void testLoggerSetsLevel() throws Exception {
        String defaultLogLevel = Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName();
        verifyLogLevel(LogLevel.fromValue(defaultLogLevel));
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
                    .pathParam("logger", TEST_LOGGER_NAME)
                    .put("/v2/admin/loggers/{logger}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("level", is(level.value()));
            TestUtils.retry(() -> assertEquals(level.value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName()));
        }
    }

    @Test
    void testLoggerInvalidLevel() {
        JsonObject lc = new JsonObject().put("level", "FOO");
        given()
            .when()
                .body(lc)
                .contentType(ContentType.JSON)
                .pathParam("logger", TEST_LOGGER_NAME)
                .put("/v2/admin/loggers/{logger}")
            .then()
                .statusCode(400);
    }

    private void verifyLogLevel(LogLevel level) {
        given()
            .when()
                .pathParam("logger", TEST_LOGGER_NAME)
                .get("/v2/admin/loggers/{logger}")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("level", is(level.value()));
    }

    @Test
    void testLoggersCRUD() throws Exception {
        String defaultLogLevel = Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName();
        verifyLogLevel(LogLevel.fromValue(defaultLogLevel));

        Consumer<LogLevel> setLog = (level) -> {
            LogConfiguration lc = new LogConfiguration();
            lc.setLevel(level);
            given()
                .when()
                    .body(lc)
                    .contentType(ContentType.JSON)
                    .pathParam("logger", TEST_LOGGER_NAME)
                    .put("/v2/admin/loggers/{logger}")
                .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("level", is(level.value()));
            assertEquals(level.value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());
        };

        Consumer<LogLevel> verifyLevel = (level) -> {
            Response res = given()
                .when()
                    .get("/v2/admin/loggers")
                .thenReturn();
            NamedLogConfiguration[] configs = res.as(NamedLogConfiguration[].class);
            assertTrue(configs.length == 1);
            assertTrue(configs[0].getName().equals(TEST_LOGGER_NAME));
            assertTrue(configs[0].getLevel().equals(level));

            assertEquals(level.value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());
        };


        //remove default log level to avoid conflicts with the checkLogLevel daemon process
        List<LogLevel> levels =  EnumSet.allOf(LogLevel.class)
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

        setLog.accept(firstLevel);
        TestUtils.retry(() -> verifyLogLevel(firstLevel));
        TestUtils.retry(() -> verifyLevel.accept(firstLevel));

        setLog.accept(secondLevel);
        TestUtils.retry(() -> verifyLogLevel(secondLevel));
        TestUtils.retry(() -> verifyLevel.accept(secondLevel));

        given()
            .when()
                .pathParam("logger", TEST_LOGGER_NAME)
                .delete("/v2/admin/loggers/{logger}")
            .then()
                .statusCode(200);

        TestUtils.retry(() -> {
            Response res = given()
                    .when()
                        .get("/v2/admin/loggers")
                    .thenReturn();
                assertEquals(200, res.statusCode());
                NamedLogConfiguration[] configs = res.as(NamedLogConfiguration[].class);
                assertTrue(configs.length == 0);
        }, "Clear log config", 50);

        Response res = given()
            .when()
                .pathParam("logger", TEST_LOGGER_NAME)
                .get("/v2/admin/loggers/{logger}")
            .thenReturn();

        assertEquals(200, res.statusCode());
        NamedLogConfiguration cfg = res.getBody().as(NamedLogConfiguration.class);

        assertEquals(cfg.getLevel().value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());

    }

}

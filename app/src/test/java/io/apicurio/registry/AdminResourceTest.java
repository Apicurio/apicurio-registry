/*
 * Copyright 2021 Red Hat
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
package io.apicurio.registry;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.LoggingConfiguration;
import io.apicurio.registry.types.LogLevel;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class AdminResourceTest extends AbstractResourceTestBase {

    private static final String TEST_LOGGER_NAME = "org.acme.test";

    @BeforeEach
    public void setUp() {
        Logger logger = Logger.getLogger(TEST_LOGGER_NAME);
        logger.setLevel(Level.INFO);
    }

    @Test
    void testLoggerSetsLevel() {
        for (LogLevel level : LogLevel.values()) {
            RestAssured.given()
                    .when()
                    .body(new LoggingConfiguration(TEST_LOGGER_NAME, level))
                    .contentType(ContentType.JSON)
                    .put("/admin/logging/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("logLevel", is(level.value()));
            assertEquals(level.value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());
        }
    }

    @Test
    void testInvalidLevel() {
        RestAssured.given()
            .when()
            .body(new JsonObject().put("logger", TEST_LOGGER_NAME).put("logLevel", "FOO"))
            .contentType(ContentType.JSON)
            .put("/admin/logging/")
            .then()
            .statusCode(400);
    }

    @Test
    void testCRUD() throws Exception {
        Consumer<LogLevel> setLog = (level) -> {
            RestAssured.given()
                    .when()
                    .body(new LoggingConfiguration(TEST_LOGGER_NAME, level))
                    .contentType(ContentType.JSON)
                    .put("/admin/logging/")
                    .then()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("logLevel", is(level.value()));
            assertEquals(level.value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());
        };

        Consumer<LogLevel> verifyLevel = (level) -> {
            RestAssured.given()
                .when()
                .get("/admin/logging/" + TEST_LOGGER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("logLevel", is(level.value()));

            Response res = RestAssured.given()
                .when()
                .get("/admin/logging/")
                .thenReturn();
            LoggingConfiguration[] configs = res.as(LoggingConfiguration[].class);
            assertTrue(configs.length == 1);
            assertTrue(configs[0].getLogger().equals(TEST_LOGGER_NAME));
            assertTrue(configs[0].getLogLevel().equals(level));
        };

        final LogLevel firstLevel = LogLevel.DEBUG;
        setLog.accept(firstLevel);
        TestUtils.retry(() -> verifyLevel.accept(firstLevel));

        final LogLevel secondLevel = LogLevel.FINEST;
        setLog.accept(secondLevel);
        TestUtils.retry(() -> verifyLevel.accept(secondLevel));

        RestAssured.given()
            .when()
            .delete("/admin/logging/" + TEST_LOGGER_NAME)
            .then()
            .statusCode(200);

        TestUtils.retry(() -> {
            Response res = RestAssured.given()
                    .when()
                    .get("/admin/logging/")
                    .thenReturn();
                assertEquals(200, res.statusCode());
                LoggingConfiguration[] configs = res.as(LoggingConfiguration[].class);
                assertTrue(configs.length == 0);
        }, "Clear log config", 50);

        Response res = RestAssured.given()
            .when()
            .get("/admin/logging/" + TEST_LOGGER_NAME)
            .thenReturn();

        assertEquals(200, res.statusCode());
        LoggingConfiguration cfg = res.getBody().as(LoggingConfiguration.class);

        assertEquals(cfg.getLogLevel().value(), Logger.getLogger(TEST_LOGGER_NAME).getLevel().getName());

    }

}

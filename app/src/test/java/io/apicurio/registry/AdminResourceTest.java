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
package io.apicurio.registry;

import static org.hamcrest.Matchers.is;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class AdminResourceTest extends AbstractResourceTestBase {

    private static final String TEST_LOGGER_NAME = "org.acme.test";

    @BeforeEach
    public void setUp() {
        Logger logger = Logger.getLogger(TEST_LOGGER_NAME);
        logger.setLevel(Level.FINE);
    }

    @Test
    void testLogger() {
        RestAssured.given()
                .when()
                .get("/admin/logging/" + TEST_LOGGER_NAME)
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .body(is("FINE"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "DEBUG", "TRACE", "INFO", "FINE", "FINEST" })
    void testLoggerSetsLevel(String level) {
        RestAssured.given()
                .when()
                .get("/admin/logging/" + TEST_LOGGER_NAME + "?level=" + level)
                .then()
                .statusCode(200)
                .contentType(ContentType.TEXT)
                .body(is(level));
    }

}

/*
 * Copyright 2019 Red Hat
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
package io.apicurio.tests.smokeTests;

import io.apicurio.registry.ccompat.rest.RestConstants;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.utils.HttpUtils;
import io.restassured.response.Response;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

public class SubjectResourceIT extends BaseIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(RulesResourceIT.class);

    @Test
    void testListSubjectsEndpoint() {
        LOGGER.info("Verifying subjects endpoints");

        given()
            .when()
                .contentType(RestConstants.JSON).get("/confluent/subjects")
            .then()
                .statusCode(200)
                .body(anything())
                .log().all();
    }

    @Test
    void sendData() {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");

//        HttpUtils.createSchema(schema, "/artifacts");

//        Response response = given()
//            .when()
//                .contentType(RestConstants.JSON)
//                .body(schema.toString())
//                .post("/artifacts")
//            .then()
//                .statusCode(200)
//                .extract()
//                .response();

        String schemaId = HttpUtils.createSchema(schema.toString()).jsonPath().get("id");

        LOGGER.info("Schema was created with ID: {}", schemaId);

        Response response = given()
            .when()
                .contentType(RestConstants.JSON)
                .get("/artifacts/" + schemaId)
            .then()
                .statusCode(200)
                .extract()
                .response();


        LOGGER.info("Received info about schema with ID {} is:\n{}", schemaId, response.jsonPath().get());
    }
}

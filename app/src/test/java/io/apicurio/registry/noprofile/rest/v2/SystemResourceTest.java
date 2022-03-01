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

package io.apicurio.registry.noprofile.rest.v2;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author eric.wittmann@gmail.com
 */
@QuarkusTest
public class SystemResourceTest extends AbstractResourceTestBase {

    @Test
    public void testSystemInformation() {
        given()
            .when()
                .contentType(CT_JSON)
                .get("/registry/v2/system/info")
            .then()
                .statusCode(200)
                .body("name", notNullValue())
                .body("description", equalTo("High performance, runtime registry for schemas and API designs."))
                .body("version", notNullValue())
                .body("builtOn", notNullValue())
                ;
    }

}

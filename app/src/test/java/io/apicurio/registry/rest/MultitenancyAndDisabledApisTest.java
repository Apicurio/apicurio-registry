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

package io.apicurio.registry.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.anything;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.rest.ConfluentCompatApiTest;
import io.apicurio.registry.ccompat.rest.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(MultipleRequestFiltersTestProfile.class)
public class MultitenancyAndDisabledApisTest extends AbstractResourceTestBase {

    @Test
    public void testRestApi() throws Exception {
        DisableApisFlagsTest.doTestDisabledApis();

        //this should return http 404, it's disabled
        given()
            .baseUri("http://localhost:8081")
            .when()
                .contentType(ContentTypes.COMPAT_SCHEMA_REGISTRY_STABLE_LATEST)
                .body(ConfluentCompatApiTest.SCHEMA_SIMPLE_WRAPPED)
                .post("/t/abc/apis/ccompat/v6/subjects/{subject}/versions", UUID.randomUUID().toString())
            .then()
                .statusCode(404);

        //this should return http 200, it's not disabled
        given()
            .baseUri("http://localhost:8081")
            .when().contentType(CT_JSON).get("/t/abc/apis/ccompat/v6/subjects")
            .then()
            .statusCode(200)
            .body(anything());
    }

}

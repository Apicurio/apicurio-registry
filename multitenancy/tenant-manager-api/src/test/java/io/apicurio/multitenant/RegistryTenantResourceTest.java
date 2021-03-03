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
package io.apicurio.multitenant;

import io.apicurio.multitenant.api.datamodel.NewRegistryTenantRequest;
import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class RegistryTenantResourceTest {

    private static final String TENANTS_PATH = "/api/v1/tenants";

    @Test
    public void testListTenants() {
        given()
          .when().get(TENANTS_PATH)
          .then()
             .statusCode(200)
             .body(is("[]"));
    }

    @Test
    public void testCRUD() {
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        req.setTenantId(UUID.randomUUID().toString());
        req.setOrganizationId("aaa");
        req.setClientId("aaaaa");

        Response res = given()
            .when()
            .contentType(ContentType.JSON)
            .body(req)
            .post(TENANTS_PATH)
            .thenReturn();

        assertEquals(201, res.statusCode());

        RegistryTenant tenant = res.as(RegistryTenant.class);

        assertNotNull(tenant);
        assertNotNull(tenant.getTenantId());
        assertNotNull(tenant.getCreatedOn());

        testGetTenant(tenant.getTenantId(), req);

        testDelete(tenant.getTenantId());
    }

    @Test
    public void testNotFound() {
        testTenantNotFound("abcede");
    }

    private void testGetTenant(String tenantId, NewRegistryTenantRequest req) {
        given()
            .when().get(TENANTS_PATH + "/" + tenantId)
            .then()
               .statusCode(200)
               .body("tenantId", equalTo(tenantId))
               .body("organizationId", equalTo(req.getOrganizationId()));
    }

    public void testDelete(String tenantId) {
        given()
            .when().delete(TENANTS_PATH + "/" + tenantId)
            .then()
               .statusCode(204);

        testTenantNotFound(tenantId);
    }

    private void testTenantNotFound(String tenantId) {
        given()
        .when().get(TENANTS_PATH + "/" + tenantId)
        .then()
           .statusCode(404);
    }

}
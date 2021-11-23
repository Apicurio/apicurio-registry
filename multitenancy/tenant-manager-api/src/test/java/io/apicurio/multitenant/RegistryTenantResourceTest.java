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
import io.apicurio.multitenant.api.datamodel.RegistryTenantList;
import io.apicurio.multitenant.api.datamodel.ResourceType;
import io.apicurio.multitenant.api.datamodel.TenantResource;
import io.apicurio.multitenant.api.datamodel.TenantStatusValue;
import io.apicurio.multitenant.api.datamodel.UpdateRegistryTenantRequest;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class RegistryTenantResourceTest {

    private static final String TENANTS_PATH = "/api/v1/tenants";

    @BeforeEach
    public void cleanup() {
        var client = new TenantManagerClientImpl("http://localhost:8081/");
        List<RegistryTenant> list = client.listTenants();
        list.forEach(t -> {
            if (t.getStatus() == TenantStatusValue.READY) {
                client.deleteTenant(t.getTenantId());
            }
        });

        Response res = given()
          .when().params("status", "READY").get(TENANTS_PATH)
          .thenReturn();

        assertEquals(200, res.statusCode());

        var search = res.as(RegistryTenantList.class);
        assertEquals(0, search.getItems().size());
        assertEquals(0, search.getCount());
    }

    @Test
    public void testCRUD() {
        NewRegistryTenantRequest req = new NewRegistryTenantRequest();
        req.setTenantId(UUID.randomUUID().toString());
        req.setOrganizationId("aaa");
        req.setName("foo");
        req.setDescription("bar");
        TenantResource tr = new TenantResource();
        tr.setLimit(5L);
        tr.setType(ResourceType.MAX_TOTAL_SCHEMAS_COUNT);
        req.setResources(List.of(tr));

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
        assertNotNull(tenant.getResources());
        assertNotNull(tenant.getName());
        assertNotNull(tenant.getDescription());
        assertFalse(tenant.getResources().isEmpty());

        testGetTenant(tenant.getTenantId(), req);

        testUpdateTenant(tenant.getTenantId());

        testDelete(tenant.getTenantId());
    }

    @Test
    public void testNotFound() {
        testTenantNotFound("abcede");
    }

    private void testGetTenant(String tenantId, NewRegistryTenantRequest req) {
        Response res = given()
            .when().get(TENANTS_PATH + "/" + tenantId)
            .thenReturn();

        assertEquals(200, res.statusCode());

        RegistryTenant tenant = res.as(RegistryTenant.class);

        assertEquals(tenantId, tenant.getTenantId());
        assertEquals(req.getOrganizationId(), tenant.getOrganizationId());
        assertNotNull(req.getResources());
        assertNotNull(tenant.getResources());
        assertEquals(toString(req.getResources()), toString(tenant.getResources()));
        assertEquals(req.getName(), tenant.getName());
        assertEquals(req.getDescription(), tenant.getDescription());
    }


    private void testUpdateTenant(String tenantId) {
        UpdateRegistryTenantRequest req = new UpdateRegistryTenantRequest();
        req.setDescription("new description");
        req.setName("new name");
        TenantResource tr = new TenantResource();
        tr.setLimit(256L);
        tr.setType(ResourceType.MAX_LABEL_SIZE_BYTES);
        req.setResources(List.of(tr));

        given().when()
                .contentType(ContentType.JSON)
                .body(req)
                .put(TENANTS_PATH + "/" + tenantId)
                .then()
                .statusCode(204);

        testGetTenantUpdated(tenantId, req);
    }

    private void testGetTenantUpdated(String tenantId, UpdateRegistryTenantRequest req) {
        Response res = given()
            .when().get(TENANTS_PATH + "/" + tenantId)
            .thenReturn();

        assertEquals(200, res.statusCode());

        RegistryTenant tenant = res.as(RegistryTenant.class);

        assertEquals(tenantId, tenant.getTenantId());
        assertNotNull(req.getResources());
        assertNotNull(tenant.getResources());
        assertEquals(req.getName(), tenant.getName());
        assertEquals(req.getDescription(), tenant.getDescription());
        assertEquals(toString(req.getResources()), toString(tenant.getResources()));
    }

    public void testDelete(String tenantId) {
        given()
            .when().delete(TENANTS_PATH + "/" + tenantId)
            .then()
               .statusCode(204);

        Response res = given()
                .when().get(TENANTS_PATH + "/" + tenantId)
                .thenReturn();
        assertEquals(200, res.statusCode());
        RegistryTenant tenant = res.as(RegistryTenant.class);
        assertEquals(TenantStatusValue.TO_BE_DELETED, tenant.getStatus());
    }

    private void testTenantNotFound(String tenantId) {
        given()
        .when().get(TENANTS_PATH + "/" + tenantId)
        .then()
           .statusCode(404);
    }

    public static String toString(List<TenantResource> resources) {
        StringBuilder builder = new StringBuilder();
        if (resources != null && !resources.isEmpty()) {
            List<TenantResource> sorted = new ArrayList<>(resources);
            sorted.sort((r1, r2) -> r1.getType().name().compareTo(r2.getType().name()));
            sorted.forEach(r -> {
                builder.append(r.getType().name());
                builder.append("@");
                builder.append(r.getLimit());
                builder.append("\n");
            });
        }
        return builder.toString();
    }

}
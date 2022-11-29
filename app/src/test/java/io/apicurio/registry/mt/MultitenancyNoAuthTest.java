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

package io.apicurio.registry.mt;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.apicurio.tenantmanager.api.datamodel.ApicurioTenant;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.AdminClient;
import io.apicurio.registry.rest.client.AdminClientFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
@TestProfile(MultitenancyNoAuthTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class MultitenancyNoAuthTest extends AbstractRegistryTestBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultitenancyNoAuthTest.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    MockTenantMetadataService tenantMetadataService;

    @ConfigProperty(name = "quarkus.http.test-port")
    public int testPort;

    @Test
    public void testTenantErrorExceptions() throws Exception {

        if (!storage.supportsMultiTenancy()) {
            throw new TestAbortedException("Multitenancy not supported - aborting test");
        }

        String tenantId1 = UUID.randomUUID().toString();

        String tenantId2 = UUID.randomUUID().toString();
        tenantMetadataService.addToUnauthorizedList(tenantId2);

        String tenant1BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId1;
        String tenant2BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId2;

        AdminClient clientTenant1 = AdminClientFactory.create(tenant1BaseUrl);
        AdminClient clientTenant2 = AdminClientFactory.create(tenant2BaseUrl);

        // NOTE: io.apicurio.registry.mt.TenantNotFoundException is also mapped to HTTP code 403 to avoid scanning attacks
        Assertions.assertThrows(ForbiddenException.class, () -> {
            clientTenant1.listGlobalRules();
        });

        Assertions.assertThrows(ForbiddenException.class, () -> {
            clientTenant2.listGlobalRules();
        });

    }

    @Test
    public void testMultitenantRegistry() throws Exception {

        if (!storage.supportsMultiTenancy()) {
            throw new TestAbortedException("Multitenancy not supported - aborting test");
        }

        String tenantId1 = UUID.randomUUID().toString();
        var tenant1 = new ApicurioTenant();
        tenant1.setTenantId(tenantId1);
        tenant1.setOrganizationId("aaa");
        tenant1.setStatus(TenantStatusValue.READY);
        tenantMetadataService.createTenant(tenant1);

        String tenantId2 = UUID.randomUUID().toString();
        var tenant2 = new ApicurioTenant();
        tenant2.setTenantId(tenantId2);
        tenant2.setOrganizationId("bbb");
        tenant2.setStatus(TenantStatusValue.READY);
        tenantMetadataService.createTenant(tenant2);

        String tenant1BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId1;
        String tenant2BaseUrl = "http://localhost:" + testPort + "/t/" + tenantId2;

        AdminClient adminClientTenant1 = AdminClientFactory.create(tenant1BaseUrl);
        AdminClient adminClientTenant2 = AdminClientFactory.create(tenant2BaseUrl);

        RegistryClient clientTenant1 = RegistryClientFactory.create(tenant1BaseUrl);
        RegistryClient clientTenant2 = RegistryClientFactory.create(tenant2BaseUrl);

        SchemaRegistryClient cclientTenant1 = createConfluentClient(tenant1BaseUrl);
        SchemaRegistryClient cclientTenant2 = createConfluentClient(tenant2BaseUrl);

        try {
            tenantOperations(adminClientTenant1, clientTenant1, cclientTenant1, tenant1BaseUrl);
            try {
                tenantOperations(adminClientTenant2, clientTenant2, cclientTenant2, tenant2BaseUrl);
            } finally {
                cleanTenantArtifacts(clientTenant2);
            }
        } finally {
            cleanTenantArtifacts(clientTenant1);
        }

    }

    private void tenantOperations(AdminClient adminClient, RegistryClient client, SchemaRegistryClient cclient, String baseUrl) throws Exception {
        //test apicurio api
        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0);

        String artifactId = TestUtils.generateArtifactId();
        ArtifactMetaData meta = client.createArtifact(null, artifactId, ArtifactType.JSON, new ByteArrayInputStream("{}".getBytes()));
        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));

        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));

        assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 1);

        Rule ruleConfig = new Rule();
        ruleConfig.setType(RuleType.VALIDITY);
        ruleConfig.setConfig("NONE");
        client.createArtifactRule(meta.getGroupId(), meta.getId(), ruleConfig);

        adminClient.createGlobalRule(ruleConfig);

        //test confluent api
        String subject = TestUtils.generateArtifactId();
        ParsedSchema schema1 = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id1 = cclient.register(subject, schema1);
        // Reset the client cache so that the next line actually does what we want.
        cclient.reset();
        TestUtils.retry(() -> cclient.getSchemaById(id1));

        //test ibm api

        String schemaDefinition = resourceToString("avro.json")
            .replaceAll("\"", "\\\\\"")
            .replaceAll("\n", "\\\\n");

        String schemaName = "testVerifySchema_userInfo";
        String versionName = "testversion_1.0.0";

        // Verify Avro artifact via ibmcompat API
        given()
            .baseUri(baseUrl)
            .when()
                .queryParam("verify", "true")
                .contentType(AbstractResourceTestBase.CT_JSON)
                .body("{\"name\":\"" + schemaName + "\",\"version\":\"" + versionName + "\",\"definition\":\"" + schemaDefinition + "\"}")
                .post("/apis/ibmcompat/v1/schemas")
            .then()
                .statusCode(200)
                .body(equalTo("\"" + schemaDefinition + "\""));
    }

    private void cleanTenantArtifacts(RegistryClient client) throws Exception {
        List<SearchedArtifact> artifacts = client.listArtifactsInGroup(null).getArtifacts();
        for (SearchedArtifact artifact : artifacts) {
            try {
                client.deleteArtifact(artifact.getGroupId(), artifact.getId());
            } catch (ArtifactNotFoundException e) {
                //because of async storage artifact may be already deleted but listed anyway
                LOGGER.info(e.getMessage());
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
        TestUtils.retry(() -> assertTrue(client.listArtifactsInGroup(null).getCount().intValue() == 0));
    }

    private SchemaRegistryClient createConfluentClient(String baseUrl) {

        final List<SchemaProvider> schemaProviders = Arrays
                .asList(new JsonSchemaProvider(), new AvroSchemaProvider(), new ProtobufSchemaProvider());

        return new CachedSchemaRegistryClient(new RestService(baseUrl + "/apis/ccompat/v6"), 3, schemaProviders, null, null);
    }

}
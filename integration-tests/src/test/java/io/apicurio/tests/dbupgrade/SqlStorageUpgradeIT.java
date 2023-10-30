/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.dbupgrade;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.MultitenancyNoAuthTestProfile;
import io.apicurio.registry.utils.tests.PostgreSqlEmbeddedTestResource;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUser;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.CustomTestsUtils;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer.PREPARE_PROTO_GROUP;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Carles Arnal
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.SQL)
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusIntegrationTest
@QuarkusTestResource(value = PostgreSqlEmbeddedTestResource.class, restrictToAnnotatedClass = true)
@QuarkusTestResource(value = SqlStorageUpgradeIT.SqlStorageUpgradeTestInitializer.class, restrictToAnnotatedClass = true)
public class SqlStorageUpgradeIT extends ApicurioRegistryBaseIT implements TestSeparator, Constants {

    private static final Logger logger = LoggerFactory.getLogger(SqlStorageUpgradeIT.class);

    protected static List<UpgradeTestsDataInitializer.TenantData> data;

    private static final String ARTIFACT_CONTENT = "{\"name\":\"redhat\"}";
    private static final String REFERENCE_CONTENT = "{\"name\":\"ibm\"}";

    private static final ObjectMapper mapper = new ObjectMapper();

    protected static CustomTestsUtils.ArtifactData artifactWithReferences;
    protected static List<ArtifactReference> artifactReferences;
    protected static CustomTestsUtils.ArtifactData protoData;

    public static RegistryClient upgradeTenantClient;

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean up
    }

    @Test
    public void testStorageUpgrade() throws Exception {
        //First verify the data created in the old registry version.
        UpgradeTestsDataInitializer.verifyData(data);
        //Add more data to the new registry instance.
        UpgradeTestsDataInitializer.createMoreArtifacts(data);
        //Verify the new data.
        UpgradeTestsDataInitializer.verifyData(data);
    }

    @Test
    public void testStorageUpgradeReferencesContentHash() throws Exception {
        testStorageUpgradeReferencesContentHashUpgrader("referencesContentHash");
    }

    public void testStorageUpgradeReferencesContentHashUpgrader(String testName) throws Exception {
        //Once the storage is filled with the proper information, if we try to create the same artifact with the same references, no new version will be created and the same ids are used.
        CustomTestsUtils.ArtifactData upgradedArtifact = CustomTestsUtils.createArtifactWithReferences(artifactWithReferences.meta.getGroupId(), artifactWithReferences.meta.getId(), upgradeTenantClient, ArtifactType.AVRO, ARTIFACT_CONTENT, artifactReferences);
        assertEquals(artifactWithReferences.meta.getGlobalId(), upgradedArtifact.meta.getGlobalId());
        assertEquals(artifactWithReferences.meta.getContentId(), upgradedArtifact.meta.getContentId());
    }

    @Test
    public void testStorageUpgradeProtobufUpgraderKafkaSql() throws Exception {
        testStorageUpgradeProtobufUpgrader("protobufCanonicalHashKafkaSql");
    }

    public void testStorageUpgradeProtobufUpgrader(String testName) throws Exception {
        //The check must be retried so the kafka storage has been bootstrapped
        retry(() -> assertEquals(3, upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount()));

        var searchResults = upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP);

        var protobufs = searchResults.getArtifacts().stream()
                .filter(ar -> ar.getType().equals(ArtifactType.PROTOBUF))
                .collect(Collectors.toList());

        System.out.println("Protobuf artifacts are " + protobufs.size());
        assertEquals(1, protobufs.size());
        var protoMetadata = upgradeTenantClient.getArtifactMetaData(protobufs.get(0).getGroupId(), protobufs.get(0).getId());
        var content = upgradeTenantClient.getContentByGlobalId(protoMetadata.getGlobalId());

        //search with canonicalize
        var versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, content);
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        String test1content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v1.proto");

        //search with canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), true, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //search without canonicalize
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(protobufs.get(0).getGroupId(), protobufs.get(0).getId(), false, null, IoUtil.toStream(test1content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //create one more protobuf artifact and verify
        String test2content = ApicurioRegistryBaseIT.resourceToString("artifactTypes/" + "protobuf/tutorial_v2.proto");
        protoData = CustomTestsUtils.createArtifact(upgradeTenantClient, PREPARE_PROTO_GROUP, ArtifactType.PROTOBUF, test2content);
        versionMetadata = upgradeTenantClient.getArtifactVersionMetaDataByContent(PREPARE_PROTO_GROUP, protoData.meta.getId(), true, null, IoUtil.toStream(test2content));
        assertEquals(protoData.meta.getContentId(), versionMetadata.getContentId());

        //assert total num of artifacts
        assertEquals(4, upgradeTenantClient.listArtifactsInGroup(PREPARE_PROTO_GROUP).getCount());
    }

    public static class SqlStorageUpgradeTestInitializer implements QuarkusTestResourceLifecycleManager {
        GenericContainer registryContainer;
        GenericContainer tenantManagerContainer;
        TenantManagerClient tenantManager;

        @Override
        public int order() {
            return 10000;
        }

        @Override
        public Map<String, String> start() {
            if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

                String jdbcUrl = System.getProperty("quarkus.datasource.jdbc.url");
                String userName = System.getProperty("quarkus.datasource.username");
                String password = System.getProperty("quarkus.datasource.password");

                String tenantManagerUrl = startTenantManagerApplication("quay.io/apicurio/apicurio-tenant-manager-api:latest", jdbcUrl, userName, password);
                String registryBaseUrl = startOldRegistryVersion("quay.io/apicurio/apicurio-registry-sql:2.1.0.Final", jdbcUrl, userName, password, tenantManagerUrl);

                try {

                    //Warm up until the tenant manager is ready.
                    TestUtils.retry(() -> {
                        getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId);
                    });

                    UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, "http://localhost:8081");

                    //Wait until all the data is available for the upgrade test.
                    TestUtils.retry(() -> Assertions.assertEquals(10, getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 51, SortOrder.asc, SortBy.tenantId).getCount()));

                    MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
                    TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
                    final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

                    //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
                    UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(tenantUpgradeClient.client);
                    UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(tenantUpgradeClient.client);

                    upgradeTenantClient = tenantUpgradeClient.client;

                    //Once the data is set, stop the old registry before running the tests.
                    if (registryContainer != null && registryContainer.isRunning()) {
                        registryContainer.stop();
                    }

                } catch (Exception e) {
                    logger.warn("Error filling old registry with information: ", e);
                }
            }

            return Collections.emptyMap();
        }

        private String startTenantManagerApplication(String tenantManagerImageName, String jdbcUrl, String username, String password) {
            tenantManagerContainer = new GenericContainer<>(tenantManagerImageName)
                    .withEnv(Map.of("DATASOURCE_URL", jdbcUrl,
                            "REGISTRY_ROUTE_URL", "",
                            "DATASOURCE_USERNAME", username,
                            "DATASOURCE_PASSWORD", password,
                            "QUARKUS_HTTP_PORT", "8585"))
                    .withNetworkMode("host");

            tenantManagerContainer.start();
            tenantManagerContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

            return "http://localhost:8585";
        }

        @Override
        public void stop() {
            //Once the data is set, stop the old registry before running the tests.
            if (registryContainer != null && registryContainer.isRunning()) {
                registryContainer.stop();
            }

            if (tenantManagerContainer != null && tenantManagerContainer.isRunning()) {
                tenantManagerContainer.stop();
            }
        }

        private String startOldRegistryVersion(String imageName, String jdbcUrl, String username, String password, String tenantManagerUrl) {
            registryContainer = new GenericContainer<>(imageName)
                    .withEnv(Map.of(
                            "REGISTRY_ENABLE_MULTITENANCY", "true",
                            "TENANT_MANAGER_AUTH_ENABLED", "false",
                            "TENANT_MANAGER_URL", tenantManagerUrl,
                            "REGISTRY_DATASOURCE_URL", jdbcUrl,
                            "REGISTRY_DATASOURCE_USERNAME", username,
                            "REGISTRY_DATASOURCE_PASSWORD", password,
                            "QUARKUS_HTTP_PORT", "8081"))
                    .dependsOn(tenantManagerContainer)
                    .withNetworkMode("host");

            registryContainer.start();
            //TODO change log message
            registryContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

            return "http://localhost:8081";
        }

        public synchronized TenantManagerClient getTenantManagerClient(String tenantManagerUrl) {
            if (tenantManager == null) {
                tenantManager = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);
            }
            return tenantManager;
        }
    }
}

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

package io.apicurio.tests.multitenancy.serdes;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.exception.RateLimitedClientException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.MultitenancyNoAuthTestProfile;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.SqlStorageUpgradeIT;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.serdes.apicurio.SimpleSerdesTesterBuilder;
import io.apicurio.tests.utils.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.KafkaFacade;
import io.apicurio.tests.utils.RateLimitingProxy;
import io.apicurio.tests.utils.RetryLimitingProxy;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.TestProfile;
import io.vertx.core.http.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(Constants.MULTITENANCY)
@QuarkusIntegrationTest
@TestProfile(MultitenancyNoAuthTestProfile.class)
@QuarkusTestResource(value = RateLimitedRegistrySerdeIT.TenantManagerTestResource.class, restrictToAnnotatedClass = true)
public class RateLimitedRegistrySerdeIT extends ApicurioRegistryBaseIT {

    private static final Logger logger = LoggerFactory.getLogger(SqlStorageUpgradeIT.class);

    private final KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    @Override
    public void cleanArtifacts() throws Exception {
        //Don't clean up
    }

    protected void createArtifact(RegistryClient client, String groupId, String artifactId, String artifactType, InputStream artifact) throws Exception {
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, null, artifactType, IfExists.FAIL, false, artifact);

        retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
    }

    @Test
    void testRateLimitingProxy() throws Exception {
        RateLimitingProxy proxy = new RateLimitingProxy(2, getRegistryHost(), getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport("http://localhost:8585", ApicurioRegistryBaseIT.getRegistryBaseUrl());
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;

        //client connecting directly to registry works
        assertNotNull(clientTenant.listArtifactsInGroup(null));

        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            RegistryClient rateLimitedClient = mt.createUserClient(tenant.user, tenantRateLimitedUrl);

            //client connecting to rate limiting proxy , request 1 should be allowed
            assertNotNull(rateLimitedClient.listArtifactsInGroup(null));
            //client connecting to rate limiting proxy , request 2 should be allowed as well
            assertNotNull(rateLimitedClient.listGlobalRules());

            //client connecting to rate limiting proxy , from now requests should fail
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));
            Assertions.assertThrows(RateLimitedClientException.class, rateLimitedClient::listGlobalRules);

        } finally {
            proxy.stop();
        }
    }

    @Test
    void testRetryLimitingProxy() throws Exception {
        RetryLimitingProxy proxy = new RetryLimitingProxy(2, getRegistryHost(), getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport("http://localhost:8585", ApicurioRegistryBaseIT.getRegistryBaseUrl());
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;

        //client connecting directly to registry works
        assertNotNull(clientTenant.listArtifactsInGroup(null));

        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            TestUtils.waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            RegistryClient rateLimitedClient = mt.createUserClient(tenant.user, tenantRateLimitedUrl);

            //client connecting to retry limiting proxy , request 1
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));
            //client connecting to retry limiting proxy , request 2
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));

            //client connecting to retry limiting proxy , from now requests should be allowed
            assertNotNull(rateLimitedClient.listArtifactsInGroup(null));
            assertNotNull(rateLimitedClient.listArtifactsInGroup(null));
            assertNotNull(rateLimitedClient.listGlobalRules());

        } finally {
            proxy.stop();
        }
    }

    @Test
    void testFindLatestRateLimited() throws Exception {

        RateLimitingProxy proxy = new RateLimitingProxy(3, getRegistryHost(), getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport("http://localhost:8585", ApicurioRegistryBaseIT.getRegistryBaseUrl());
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;
        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            TestUtils.waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            String topicName = TestUtils.generateTopic();
            String artifactId = topicName;
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

            createArtifact(clientTenant, topicName, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

            new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                    .withTopic(topicName)

                    //url of the proxy
                    .withCommonProperty(SerdeConfig.REGISTRY_URL, tenantRateLimitedUrl)


                    .withSerializer(AvroKafkaSerializer.class)
                    .withDeserializer(AvroKafkaDeserializer.class)
                    .withStrategy(SimpleTopicIdStrategy.class)
                    .withDataGenerator(avroSchema::generateRecord)
                    .withDataValidator(avroSchema::validateRecord)
                    .withProducerProperty(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, topicName)

                    // make serdes tester send multiple message batches, that will test that the cache is used when loaded
                    .withMessages(4, 5)

                    .build()
                    .test();

        } finally {
            proxy.stop();
        }
    }

    @Test
    void testAutoRegisterRateLimited() throws Exception {

        RateLimitingProxy proxy = new RateLimitingProxy(3, getRegistryHost(), getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport("http://localhost:8585", ApicurioRegistryBaseIT.getRegistryBaseUrl());
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;
        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            TestUtils.waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            String topicName = TestUtils.generateTopic();
            //because of using TopicIdStrategy
            String artifactId = topicName + "-value";
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

            new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                    .withTopic(topicName)

                    //url of the proxy
                    .withCommonProperty(SerdeConfig.REGISTRY_URL, tenantRateLimitedUrl)

                    .withSerializer(AvroKafkaSerializer.class)
                    .withDeserializer(AvroKafkaDeserializer.class)
                    .withStrategy(TopicIdStrategy.class)
                    .withDataGenerator(avroSchema::generateRecord)
                    .withDataValidator(avroSchema::validateRecord)
                    .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                    .withAfterProduceValidator(() -> {
                        return TestUtils.retry(() -> {
                            ArtifactMetaData meta = clientTenant.getArtifactMetaData(null, artifactId);
                            clientTenant.getContentByGlobalId(meta.getGlobalId());
                            return true;
                        });
                    })

                    // make serdes tester send multiple message batches, that will test that the cache is used when loaded
                    .withMessages(4, 5)

                    .build()
                    .test();


            ArtifactMetaData meta = clientTenant.getArtifactMetaData(null, artifactId);
            byte[] rawSchema = IoUtil.toBytes(clientTenant.getContentByGlobalId(meta.getGlobalId()));

            assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

        } finally {
            proxy.stop();
        }
    }

    @Test
    void testRetryRateLimited() throws Exception {

        RetryLimitingProxy proxy = new RetryLimitingProxy(3, getRegistryHost(), getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport("http://localhost:8585", ApicurioRegistryBaseIT.getRegistryBaseUrl());
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;
        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            TestUtils.waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            String topicName = TestUtils.generateTopic();
            //because of using TopicIdStrategy
            String artifactId = topicName + "-value";
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

            new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                    .withTopic(topicName)

                    //url of the proxy
                    .withCommonProperty(SerdeConfig.REGISTRY_URL, tenantRateLimitedUrl)

                    .withSerializer(AvroKafkaSerializer.class)
                    .withDeserializer(AvroKafkaDeserializer.class)
                    .withStrategy(TopicIdStrategy.class)
                    .withDataGenerator(avroSchema::generateRecord)
                    .withDataValidator(avroSchema::validateRecord)
                    .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                    .withAfterProduceValidator(() -> TestUtils.retry(() -> {
                        ArtifactMetaData meta = clientTenant.getArtifactMetaData(null, artifactId);
                        clientTenant.getContentByGlobalId(meta.getGlobalId());
                        return true;
                    }))

                    // make serdes tester send multiple message batches, that will test that the cache is used when loaded
                    .withMessages(4, 5)

                    .build()
                    .test();


            ArtifactMetaData meta = clientTenant.getArtifactMetaData(null, artifactId);
            byte[] rawSchema = IoUtil.toBytes(clientTenant.getContentByGlobalId(meta.getGlobalId()));

            assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

        } finally {
            proxy.stop();
        }

    }

    public static class TenantManagerTestResource implements QuarkusTestResourceLifecycleManager {
        GenericContainer tenantManagerContainer;
        EmbeddedPostgres database;
        TenantManagerClient tenantManager;

        @Override
        public int order() {
            return 10000;
        }

        @Override
        public Map<String, String> start() {
            try {
                database = EmbeddedPostgres.start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            String datasourceUrl = database.getJdbcUrl("postgres", "postgres");

            String tenantManagerUrl = startTenantManagerApplication("quay.io/apicurio/apicurio-tenant-manager-api:latest", datasourceUrl, "postgres", "postgres");

            try {
                //Warm up until the tenant manager is ready.
                TestUtils.retry(() -> {
                    getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId);
                });

            } catch (Exception ex) {
                logger.warn("Error filling old registry with information: ", ex);
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
            if (tenantManagerContainer != null && tenantManagerContainer.isRunning()) {
                tenantManagerContainer.stop();
            }
        }

        public synchronized TenantManagerClient getTenantManagerClient(String tenantManagerUrl) {
            if (tenantManager == null) {
                tenantManager = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);
            }
            return tenantManager;
        }
    }
}

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

package io.apicurio.tests.multitenancy.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.apicurio.tests.utils.RetryLimitingProxy;
import io.vertx.core.http.HttpServer;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

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
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.SimpleSerdesTesterBuilder;
import io.apicurio.tests.utils.RateLimitingProxy;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.MULTITENANCY)
@DisabledIfEnvironmentVariable(named = Constants.CURRENT_ENV, matches = Constants.CURRENT_ENV_MAS_REGEX)
public class RateLimitedRegistrySerdeIT extends ApicurioRegistryBaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    public void teardown() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    protected void createArtifact(RegistryClient client, String groupId, String artifactId, String artifactType, InputStream artifact) throws Exception {
        ArtifactMetaData meta = client.createArtifact(groupId, artifactId, null, artifactType, IfExists.FAIL, false, artifact);

        TestUtils.retry(() -> client.getContentByGlobalId(meta.getGlobalId()));
        assertNotNull(client.getLatestArtifact(meta.getGroupId(), meta.getId()));
    }

    @Test
    void testRateLimitingProxy() throws Exception {
        RateLimitingProxy proxy = new RateLimitingProxy(2, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport();
        var tenant = mt.createTenant();
        RegistryClient clientTenant = tenant.client;

        //client connecting directly to registry works
        assertNotNull(clientTenant.listArtifactsInGroup(null));

        String tenantRateLimitedUrl = proxy.getServerUrl() + "/t/" + tenant.user.tenantId;

        try {
            final CompletableFuture<HttpServer> server = proxy.start();
            TestUtils.waitFor("proxy is ready", Constants.POLL_INTERVAL, Constants.TIMEOUT_GLOBAL, server::isDone);

            RegistryClient rateLimitedClient = mt.createUserClient(tenant.user, tenantRateLimitedUrl);

            //client connecting to rate limiting proxy , request 1 should be allowed
            assertNotNull(rateLimitedClient.listArtifactsInGroup(null));
            //client connecting to rate limiting proxy , request 2 should be allowed as well
            assertNotNull(rateLimitedClient.listGlobalRules());

            //client connecting to rate limiting proxy , from now requests should fail
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listArtifactsInGroup(null));
            Assertions.assertThrows(RateLimitedClientException.class, () -> rateLimitedClient.listGlobalRules());

        } finally {
            proxy.stop();
        }
    }

    @Test
    void testRetryLimitingProxy() throws Exception {
        RetryLimitingProxy proxy = new RetryLimitingProxy(2, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport();
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

        RateLimitingProxy proxy = new RateLimitingProxy(3, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport();
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

                //add auth properties
                .withCommonProperty(SerdeConfig.AUTH_TOKEN_ENDPOINT, tenant.tokenEndpoint)
                //making use of tenant owner is admin feature
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_ID, tenant.user.principalId)
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_SECRET, tenant.user.principalPassword)


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

        RateLimitingProxy proxy = new RateLimitingProxy(3, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport();
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

                //add auth properties
                .withCommonProperty(SerdeConfig.AUTH_TOKEN_ENDPOINT, tenant.tokenEndpoint)
                //making use of tenant owner is admin feature
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_ID, tenant.user.principalId)
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_SECRET, tenant.user.principalPassword)

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

        RetryLimitingProxy proxy = new RetryLimitingProxy(3, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());

        MultitenancySupport mt = new MultitenancySupport();
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

                //add auth properties
                .withCommonProperty(SerdeConfig.AUTH_TOKEN_ENDPOINT, tenant.tokenEndpoint)
                //making use of tenant owner is admin feature
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_ID, tenant.user.principalId)
                .withCommonProperty(SerdeConfig.AUTH_CLIENT_SECRET, tenant.user.principalPassword)

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
}

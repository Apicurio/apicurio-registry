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

package io.apicurio.tests.serdes.apicurio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.RateLimitedClientException;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.utils.RateLimitingProxy;
import io.apicurio.tests.utils.TooManyRequestsMock;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.SERDES)
public class RateLimitedRegistrySerdeIT extends ApicurioV2BaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();
    private TooManyRequestsMock mock = new TooManyRequestsMock();


    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
        mock.start();
    }

    @AfterAll
    public void teardown() throws Exception {
        kafkaCluster.stopIfPossible();
        mock.stop();
    }

    @Test
    public void testClientRateLimitError() {

        RegistryClient client = RegistryClientFactory.create(mock.getMockUrl());

        Assertions.assertThrows(RateLimitedClientException.class, () -> client.getLatestArtifact("test", "test"));

        Assertions.assertThrows(RateLimitedClientException.class, () -> client.createArtifact(null, "aaa", IoUtil.toStream("{}")));

        Assertions.assertThrows(RateLimitedClientException.class, () -> client.getContentByGlobalId(5));

    }

    @Test
    void testFindLatestRateLimited() throws Exception {

        RateLimitingProxy proxy = new RateLimitingProxy(2, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());
        try {
            proxy.start();

            String topicName = TestUtils.generateTopic();
            String artifactId = topicName;
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

            createArtifact(topicName, artifactId, ArtifactType.AVRO, avroSchema.generateSchemaStream());

            new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                .withTopic(topicName)

                //url of the proxy
                .withCommonProperty(SerdeConfig.REGISTRY_URL, proxy.getServerUrl())

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

        RateLimitingProxy proxy = new RateLimitingProxy(2, TestUtils.getRegistryHost(), TestUtils.getRegistryPort());
        try {
            proxy.start();

            String topicName = TestUtils.generateTopic();
            //because of using TopicIdStrategy
            String artifactId = topicName + "-value";
            kafkaCluster.createTopic(topicName, 1, 1);

            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("myrecordapicurio1", List.of("key1"));

            new SimpleSerdesTesterBuilder<GenericRecord, GenericRecord>()
                .withTopic(topicName)

                //url of the proxy
                .withCommonProperty(SerdeConfig.REGISTRY_URL, proxy.getServerUrl())

                .withSerializer(AvroKafkaSerializer.class)
                .withDeserializer(AvroKafkaDeserializer.class)
                .withStrategy(TopicIdStrategy.class)
                .withDataGenerator(avroSchema::generateRecord)
                .withDataValidator(avroSchema::validateRecord)
                .withProducerProperty(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true")
                .withAfterProduceValidator(() -> {
                    return TestUtils.retry(() -> {
                        ArtifactMetaData meta = registryClient.getArtifactMetaData(null, artifactId);
                        registryClient.getContentByGlobalId(meta.getGlobalId());
                        return true;
                    });
                })

                // make serdes tester send multiple message batches, that will test that the cache is used when loaded
                .withMessages(4, 5)

                .build()
                .test();


            //TODO make serdes tester send a second batch, that will test that the cache is used when loaded

            ArtifactMetaData meta = registryClient.getArtifactMetaData(null, artifactId);
            byte[] rawSchema = IoUtil.toBytes(registryClient.getContentByGlobalId(meta.getGlobalId()));

            assertEquals(new String(avroSchema.generateSchemaBytes()), new String(rawSchema));

        } finally {
            proxy.stop();
        }

    }

    @Test
    void testFirstRequestFailsRateLimited() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory("mygroup", "myrecord", List.of("keyB"));

        new WrongConfiguredSerdesTesterBuilder<GenericRecord>()
            .withTopic(topicName)

            //mock url that will return 429 status always
            .withProducerProperty(SerdeConfig.REGISTRY_URL, mock.getMockUrl())

            .withSerializer(AvroKafkaSerializer.class)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(avroSchema::generateRecord)
            .build()
            .test();
    }

}

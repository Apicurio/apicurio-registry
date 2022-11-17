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

import java.util.Map;

import org.apache.kafka.connect.json.JsonSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.common.serdes.json.InvalidMessage;
import io.apicurio.tests.common.serdes.json.ValidMessage;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.SERDES)
public class JsonSchemaSerdeIT extends ApicurioV2BaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    private Class<JsonSchemaKafkaSerializer> serializer = JsonSchemaKafkaSerializer.class;
    private Class<JsonSchemaKafkaDeserializer> deserializer = JsonSchemaKafkaDeserializer.class;

    @BeforeAll
    void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();

        createArtifact(null, artifactId, ArtifactType.JSON, schema.getSchemaStream());

        new SimpleSerdesTesterBuilder<ValidMessage, ValidMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .withDataValidator(schema::validateMessage)
            .build()
            .test();
    }

    @Test
    void testSimpleTopicIdStrategyFindLatest() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();

        createArtifact(null, artifactId, ArtifactType.JSON, schema.getSchemaStream());

        new SimpleSerdesTesterBuilder<ValidMessage, ValidMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(SimpleTopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .withDataValidator(schema::validateMessage)
            .build()
            .test();
    }

    //there is no mechanism for json serdes to auto register a schema, yet
//    @Test
//    @Tag(Constants.ACCEPTANCE)
//    void testTopicIdStrategyAutoRegister() throws Exception {
//        String topicName = TestUtils.generateTopic();
//        //because of using TopicIdStrategy
//        String artifactId = topicName + "-value";
//        kafkaCluster.createTopic(topicName, 1, 1);
//
//        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();
//
//        new SimpleSerdesTesterBuilder<ValidMessage, ValidMessage>()
//            .withTopic(topicName)
//            .withSerializer(serializer)
//            .withDeserializer(deserializer)
//            .withStrategy(TopicIdStrategy.class)
//            .withDataGenerator(schema::generateMessage)
//            .withDataValidator(schema::validateMessage)
//            .withProducerProperty(SerdeConfigKeys.AUTO_REGISTER_ARTIFACT, "true")
//            .withAfterProduceValidator(() -> {
//                return TestUtils.retry(() -> registryClient.getArtifactMetaData(topicName, artifactId) != null);
//            })
//            .build()
//            .test();
//
//
//        ArtifactMetaData meta = registryClient.getArtifactMetaData(topicName, artifactId);
//        byte[] rawSchema = IoUtil.toBytes(registryClient.getContentByGlobalId(meta.getGlobalId()));
//
//        assertEquals(new String(schema.getSchemaBytes()), new String(rawSchema));
//
//    }

    @Test
    void testConsumeReturnSpecificClass() throws Exception {
        String topicName = TestUtils.generateTopic();
        String artifactId = topicName;
        kafkaCluster.createTopic(topicName, 1, 1);

        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();

        createArtifact(null, artifactId, ArtifactType.JSON, schema.getSchemaStream());

        new SimpleSerdesTesterBuilder<ValidMessage, Map<String, Object>>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(SimpleTopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .withDataValidator(schema::validateAsMap)
            .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, Map.class.getName())
            .build()
            .test();
    }

    @Test
    void testWrongSchema() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = topicName + "-value";

        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();
//        ProtobufUUIDTestMessage schemaB = new ProtobufUUIDTestMessage();

        createArtifact(groupId, artifactId, ArtifactType.JSON, schema.getSchemaStream());

        new WrongConfiguredSerdesTesterBuilder<InvalidMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            //note, we use an incorrect wrong data generator in purpose
            .withDataGenerator(count -> {
                InvalidMessage msg = new InvalidMessage();
                msg.setBar("aa");
                msg.setFoo("ss");
                return msg;
            })
            .build()
            .test();
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();

        //note, we don't create any artifact

        new WrongConfiguredSerdesTesterBuilder<ValidMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .build()
            .test();
    }

    @Test
    void testDefaultFallback() throws Exception {
        String topicName = TestUtils.generateTopic();
        kafkaCluster.createTopic(topicName, 1, 1);


        String groupId = TestUtils.generateSubject();
        String artifactId = TestUtils.generateSubject();
        JsonSchemaMsgFactory schema = new JsonSchemaMsgFactory();

        createArtifact(groupId, artifactId, ArtifactType.JSON, schema.getSchemaStream());

        //this test will produce messages using JsonSerializer, which does nothing with the registry and just serializes as json
        //the produced messages won't have the id of the artifact
        //the consumer will read the messages and because there is no id information in the messages the resolver will fail
        //the default fallback will kick in and use the artifact from the configured properties
        new SimpleSerdesTesterBuilder<JsonNode, ValidMessage>()
            .withTopic(topicName)
            .withSerializer(JsonSerializer.class)
            .withDeserializer(deserializer)
            .withStrategy(SimpleTopicIdStrategy.class)
            .withDataGenerator(schema::generateMessageJsonNode)
            .withDataValidator(schema::validateMessage)
            .withConsumerProperty(SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID, groupId)
            .withConsumerProperty(SerdeConfig.FALLBACK_ARTIFACT_ID, artifactId)
            .withConsumerProperty(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, ValidMessage.class.getName())
            .build()
            .test();
    }

}

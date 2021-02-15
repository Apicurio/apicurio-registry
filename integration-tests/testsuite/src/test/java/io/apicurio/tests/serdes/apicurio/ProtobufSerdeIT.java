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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.google.protobuf.DynamicMessage;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfigKeys;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ApicurioV2BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.KafkaFacade;
import io.apicurio.tests.common.serdes.proto.MsgTypes;
import io.apicurio.tests.common.serdes.proto.TestCmmn;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.SERDES)
public class ProtobufSerdeIT extends ApicurioV2BaseIT {

    private KafkaFacade kafkaCluster = KafkaFacade.getInstance();

    private Class<ProtobufKafkaSerializer> serializer = ProtobufKafkaSerializer.class;
    private Class<ProtobufKafkaDeserializer> deserializer = ProtobufKafkaDeserializer.class;

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

        ProtobufMsgFactory schema = new ProtobufMsgFactory();

        createArtifact(topicName, artifactId, ArtifactType.PROTOBUF_FD, schema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<MsgTypes.Msg, DynamicMessage>()
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

        ProtobufMsgFactory schema = new ProtobufMsgFactory();

        createArtifact(topicName, artifactId, ArtifactType.PROTOBUF_FD, schema.generateSchemaStream());

        new SimpleSerdesTesterBuilder<MsgTypes.Msg, DynamicMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(SimpleTopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .withDataValidator(schema::validateMessage)
            .build()
            .test();
    }

    @Test
    @Tag(Constants.ACCEPTANCE)
    void testTopicIdStrategyAutoRegister() throws Exception {
        String topicName = TestUtils.generateTopic();
        //because of using TopicIdStrategy
        String artifactId = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufMsgFactory schema = new ProtobufMsgFactory();

        new SimpleSerdesTesterBuilder<MsgTypes.Msg, DynamicMessage>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withDeserializer(deserializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .withDataValidator(schema::validateMessage)
            .withProducerProperty(SerdeConfigKeys.AUTO_REGISTER_ARTIFACT, "true")
            .withAfterProduceValidator(() -> {
                return TestUtils.retry(() -> registryClient.getArtifactMetaData(topicName, artifactId) != null);
            })
            .build()
            .test();


        ArtifactMetaData meta = registryClient.getArtifactMetaData(topicName, artifactId);
        byte[] rawSchema = IoUtil.toBytes(registryClient.getContentByGlobalId(meta.getGlobalId()));

        assertEquals(new String(schema.generateSchemaBytes()), new String(rawSchema));

    }

    @Test
    void testWrongSchema() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        String groupId = TestUtils.generateSubject();
        String artifactId = topicName + "-value";

        ProtobufMsgFactory schemaA = new ProtobufMsgFactory();
        ProtobufUUIDTestMessage schemaB = new ProtobufUUIDTestMessage();

        createArtifact(groupId, artifactId, ArtifactType.PROTOBUF_FD, schemaA.generateSchemaStream());

        new WrongConfiguredSerdesTesterBuilder<TestCmmn.UUID>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            //note, we use an incorrect wrong data generator in purpose
            .withDataGenerator(schemaB::generateMessage)
            .build()
            .test();
    }

    @Test
    void testArtifactNotFound() throws Exception {
        String topicName = TestUtils.generateSubject();
        kafkaCluster.createTopic(topicName, 1, 1);

        ProtobufMsgFactory schema = new ProtobufMsgFactory();

        //note, we don't create any artifact

        new WrongConfiguredSerdesTesterBuilder<MsgTypes.Msg>()
            .withTopic(topicName)
            .withSerializer(serializer)
            .withStrategy(TopicIdStrategy.class)
            .withDataGenerator(schema::generateMessage)
            .build()
            .test();
    }

}

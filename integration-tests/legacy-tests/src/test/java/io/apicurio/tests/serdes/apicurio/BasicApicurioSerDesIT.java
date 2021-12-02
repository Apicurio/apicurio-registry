/*
 * Copyright 2020 Red Hat
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

import com.google.protobuf.Descriptors;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.BaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.serdes.KafkaClients;
import io.apicurio.tests.utils.subUtils.ArtifactUtils;
import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.common.Constants.SERDES;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag(SERDES)
public class BasicApicurioSerDesIT extends BaseIT {

    @Test
    @Tag(ACCEPTANCE)
    void testAvroApicurioSerDes() throws InterruptedException, ExecutionException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordapicurio1\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        KafkaClients.produceAvroApicurioMessagesTopicStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName, 10).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroApicurioSerDesFail() throws TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = TestUtils.generateSubject();
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroApicurioSerDesWrongStrategyTopic() throws TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = TestUtils.generateSubject();
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroApicurioMessagesTopicStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroApicurioSerDesWrongStrategyRecord() throws TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordapicurio4\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    @Tag(ACCEPTANCE)
    void testEvolveAvroApicurio() throws InterruptedException, ExecutionException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String recordName = TestUtils.generateSubject();
        String subjectName = topicName + "-" + recordName;
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName, 10).get(5, TimeUnit.SECONDS);

        String schemaKey2 = "key2";
        Schema schema2 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"}]}");
        updateArtifactViaApicurioClient(registryClient, schema2, subjectName);

        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName, 20).get(5, TimeUnit.SECONDS);

        String schemaKey3 = "key3";
        Schema schema3 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey3 + "\",\"type\":\"string\"}]}");
        updateArtifactViaApicurioClient(registryClient, schema3, subjectName);

        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema3, 10, schemaKey, schemaKey2, schemaKey3).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName, 30).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroApicurioForMultipleTopics() throws InterruptedException, ExecutionException, TimeoutException {
        String topicName1 = TestUtils.generateTopic();
        String topicName2 = TestUtils.generateTopic();
        String topicName3 = TestUtils.generateTopic();
        String subjectName = TestUtils.generateSubject();
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName1, 1, 1);
        kafkaCluster.createTopic(topicName2, 1, 1);
        kafkaCluster.createTopic(topicName3, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaApicurioClient(registryClient, schema, subjectName);

        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName1, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName2, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName3, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);

        KafkaClients.consumeAvroApicurioMessages(topicName1, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName2, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName3, 10).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testJsonSchemaApicurioSerDes() throws InterruptedException, ExecutionException, TimeoutException {
        String jsonSchema = "{" +
                            "    \"$id\": \"https://example.com/message.schema.json\"," +
                            "    \"$schema\": \"http://json-schema.org/draft-07/schema#\"," +
                            "    \"required\": [" +
                            "        \"message\"," +
                            "        \"time\"" +
                            "    ]," +
                            "    \"type\": \"object\"," +
                            "    \"properties\": {" +
                            "        \"message\": {" +
                "            \"description\": \"\"," +
                "            \"type\": \"string\"" +
                "        }," +
                "        \"time\": {" +
                "            \"description\": \"\"," +
                "            \"type\": \"number\"" +
                "        }" +
                "    }" +
                "}";
        String artifactId = TestUtils.generateArtifactId();
        String subjectName = TestUtils.generateSubject();
        kafkaCluster.createTopic(artifactId, 1, 1);
        LOGGER.debug("++++++++++++++++++ Created topic: {}", artifactId);

        ArtifactMetaData artifact = ArtifactUtils.createArtifact(registryClient, ArtifactType.JSON, artifactId, IoUtil.toStream(jsonSchema));
        LOGGER.debug("++++++++++++++++++ Artifact created: {}", artifact.getGlobalId());

        TestUtils.waitFor(
            "Artifact not registered",
            Constants.POLL_INTERVAL,
            Constants.TIMEOUT_GLOBAL,
            () -> registryClient.getArtifactMetaDataByGlobalId(artifact.getGlobalId()) != null
        );

        KafkaClients.produceJsonSchemaApicurioMessages(artifactId, subjectName, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeJsonSchemaApicurioMessages(artifactId, 10).get(5, TimeUnit.SECONDS);
    }

    private Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
        Serde.Schema.Builder b = Serde.Schema.newBuilder();
        b.setFile(file.toProto());
        for (Descriptors.FileDescriptor d : file.getDependencies()) {
            b.addImport(toSchemaProto(d));
        }
        return b.build();
    }

    @BeforeAll
    static void setupEnvironment() {
        kafkaCluster.startIfNeeded();
    }

    @AfterAll
    static void teardownEnvironment() throws Exception {
        kafkaCluster.stopIfPossible();
    }
}


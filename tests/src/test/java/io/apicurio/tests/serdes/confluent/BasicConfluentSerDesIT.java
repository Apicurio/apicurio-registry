/*
 * Copyright 2019 Red Hat
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

package io.apicurio.tests.serdes.confluent;

import io.apicurio.tests.BaseIT;
import io.apicurio.tests.serdes.KafkaClients;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.tests.Constants.CLUSTER;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag(CLUSTER)
public class BasicConfluentSerDesIT extends BaseIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicConfluentSerDesIT.class);

    @Test
    void testAvroConfluentSerDes(TestInfo testInfo) throws IOException, RestClientException, InterruptedException, ExecutionException, TimeoutException {
        String topicName = "topic-" + testInfo.getTestMethod().get().getName();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordconfluent1\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 10).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroConfluentSerDesFail(TestInfo testInfo) throws IOException, RestClientException {
        String topicName = "topic-" + testInfo.getTestMethod().get().getName();
        String subjectName = "myrecordconfluent2";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroConfluentSerDesWrongStrategyTopic(TestInfo testInfo) throws IOException, RestClientException {
        String topicName = "topic-" + testInfo.getTestMethod().get().getName();
        String subjectName = "myrecordconfluent3";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesTopicStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroConfluentSerDesWrongStrategyRecord(TestInfo testInfo) throws IOException, RestClientException {
        String topicName = "topic-" + testInfo.getTestMethod().get().getName();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecordconfluent4\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testEvolveAvroConfluent(TestInfo testInfo) throws InterruptedException, ExecutionException, TimeoutException, IOException, RestClientException {
        String topicName = "topic-" + testInfo.getTestMethod().get().getName();
        String recordName = "myrecordconfluent5";
        String subjectName = topicName + "-" + recordName;
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 10).get(5, TimeUnit.SECONDS);

        String schemaKey2 = "key2";
        Schema schema2 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"}]}");
        updateArtifactViaApicurioClient(schema2, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 20).get(5, TimeUnit.SECONDS);

        String schemaKey3 = "key3";
        Schema schema3 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName +  "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey3 + "\",\"type\":\"string\"}]}");
        updateArtifactViaApicurioClient(schema3, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema3, 10, schemaKey, schemaKey2, schemaKey3).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 30).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroConfluentForMultipleTopics(TestInfo testInfo) throws InterruptedException, ExecutionException, TimeoutException, IOException, RestClientException {
        String topicName1 = "topic-" + testInfo.getTestMethod().get().getName() + "-1";
        String topicName2 = "topic-" + testInfo.getTestMethod().get().getName() + "-2";
        String topicName3 = "topic-" + testInfo.getTestMethod().get().getName() + "-3";
        String subjectName = "myrecordconfluent6";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName1, 1, 1);
        kafkaCluster.createTopic(topicName2, 1, 1);
        kafkaCluster.createTopic(topicName3, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}");
        createArtifactViaConfluentClient(schema, subjectName);

        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName1, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName2, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName3, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);

        KafkaClients.consumeAvroApicurioMessages(topicName1, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName2, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName3, 10).get(5, TimeUnit.SECONDS);
    }

    @BeforeAll
    static void setupEnvironment() {
        kafkaCluster.start();
    }

    @AfterAll
    static void teardownEnvironment() {
        kafkaCluster.stop();
    }
}


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

package io.apicurio.tests.serdes.confluent;

import static io.apicurio.tests.common.Constants.SERDES;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.avro.Schema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.ConfluentBaseIT;
import io.apicurio.tests.serdes.KafkaClients;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;

@Tag(SERDES)
public class BasicConfluentSerDesIT extends ConfluentBaseIT {

    @Test
    void testAvroConfluentSerDes() throws IOException, RestClientException, InterruptedException, ExecutionException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecordconfluent1\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 10).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroConfluentSerDesFail() throws IOException, RestClientException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent2";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroConfluentSerDesWrongStrategyTopic() throws IOException, RestClientException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent3";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesTopicStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testAvroConfluentSerDesWrongStrategyRecord() throws IOException, RestClientException, TimeoutException {
        String topicName = TestUtils.generateTopic();
        String subjectName = topicName + "-value";
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecordconfluent4\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        assertThrows(ExecutionException.class, () -> KafkaClients.produceAvroConfluentMessagesRecordStrategy(topicName, subjectName, schema, 10, "wrong-key").get(5, TimeUnit.SECONDS));
    }

    @Test
    void testEvolveAvroConfluent() throws InterruptedException, ExecutionException, TimeoutException, IOException, RestClientException {
        String topicName = TestUtils.generateTopic();
        String recordName = "myrecordconfluent5";
        String subjectName = topicName + "-" + recordName;
        String schemaKey = "key1";
        kafkaCluster.createTopic(topicName, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 10).get(5, TimeUnit.SECONDS);

        String schemaKey2 = "key2";
        String rawSchema2 = "{\"type\":\"record\",\"name\":\"" + recordName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"}]}";
        Schema schema2 = new Schema.Parser().parse(rawSchema2);
//        ParsedSchema pschema2 = new AvroSchema(rawSchema2);
        updateArtifactViaApicurioClient(registryClient, schema2, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 20).get(5, TimeUnit.SECONDS);

        String schemaKey3 = "key3";
        String rawSchema3 = "{\"type\":\"record\",\"name\":\"" + recordName +  "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey2 + "\",\"type\":\"string\"},{\"name\":\"" + schemaKey3 + "\",\"type\":\"string\"}]}";
        Schema schema3 = new Schema.Parser().parse(rawSchema3);
//        ParsedSchema pschema3 = new AvroSchema(rawSchema3);
        updateArtifactViaApicurioClient(registryClient, schema3, subjectName);

        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema3, 10, schemaKey, schemaKey2, schemaKey3).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema2, 10, schemaKey, schemaKey2).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroConfluentMessagesTopicRecordStrategy(topicName, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroConfluentMessages(topicName, 30).get(5, TimeUnit.SECONDS);
    }

    @Test
    void testAvroConfluentForMultipleTopics() throws InterruptedException, ExecutionException, TimeoutException, IOException, RestClientException {
        String topicName1 = TestUtils.generateTopic();
        String topicName2 = TestUtils.generateTopic();
        String topicName3 = TestUtils.generateTopic();
        String subjectName = "myrecordconfluent6";
        String schemaKey = "key1";

        kafkaCluster.createTopic(topicName1, 1, 1);
        kafkaCluster.createTopic(topicName2, 1, 1);
        kafkaCluster.createTopic(topicName3, 1, 1);

        String rawSchema = "{\"type\":\"record\",\"name\":\"" + subjectName + "\",\"fields\":[{\"name\":\"" + schemaKey + "\",\"type\":\"string\"}]}";
        Schema schema = new Schema.Parser().parse(rawSchema);
        ParsedSchema pschema = new AvroSchema(rawSchema);
        createArtifactViaConfluentClient(pschema, subjectName);

        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName1, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName2, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);
        KafkaClients.produceAvroApicurioMessagesRecordStrategy(topicName3, subjectName, schema, 10, schemaKey).get(5, TimeUnit.SECONDS);

        KafkaClients.consumeAvroApicurioMessages(topicName1, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName2, 10).get(5, TimeUnit.SECONDS);
        KafkaClients.consumeAvroApicurioMessages(topicName3, 10).get(5, TimeUnit.SECONDS);
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


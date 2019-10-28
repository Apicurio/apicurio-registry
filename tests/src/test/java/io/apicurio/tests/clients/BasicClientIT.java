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

package io.apicurio.tests.clients;

import io.apicurio.tests.BaseIT;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;

public class BasicClientIT extends BaseIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicClientIT.class);
    private static final String SUBJECT_NAME = "foo-value";

    @Test
    void simpleTestSerDes() throws IOException, RestClientException {
        String topicName = "foo";
        String subjectName = topicName + "-value";
        kafkaCluster.createTopic(topicName, 1, 1);

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        GenericRecord avroRecord = new GenericData.Record(schema);
        avroRecord.put("f1", "value1");

        int idOfSchema = confluentService.register(subjectName, schema);
////
        KafkaAvroSerializer serializer = new KafkaAvroSerializer(confluentService);
        byte[] bytes = serializer.serialize(topicName, avroRecord);

//
//        Schema newSchema = confluentService.getBySubjectAndId(SUBJECT_NAME, idOfSchema);
//        LOGGER.info("Checking that created schema is equal to the get schema");
//        assertThat(schema.toString(), is(newSchema.toString()));
//        assertThat(confluentService.getVersion(SUBJECT_NAME, schema), is(confluentService.getVersion(SUBJECT_NAME, newSchema)));

        final Consumer<Long, GenericRecord> consumer = AvroKafka.createConsumer();
        consumer.subscribe(Collections.singletonList(topicName));

        Producer<Object, Object> producer = AvroKafka.createProducer();

        ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, subjectName, avroRecord);
        producer.send(producedRecord);

        final ConsumerRecords<Long, GenericRecord> records = consumer.poll(Duration.ofSeconds(3));
        if (records.count() == 0) {
            LOGGER.info("None found");
        } else records.forEach(record -> {
            LOGGER.info("{} {} {} {}", record.topic(),
                    record.partition(), record.offset(), record.value());
        });
    }

}


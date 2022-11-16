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

package io.apicurio.registry.ccompat;

import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractResourceTestBase;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.Properties;


@QuarkusTest
public class ConfluentSerdeTest extends AbstractResourceTestBase {

    @ConfigProperty(name = "quarkus.http.test-port")
    int testPort;

    @SuppressWarnings({ "rawtypes", "unchecked", "resource" })
    @Test
    public void testProtobufSchemaWithReferences() {
        Properties properties = new Properties();
        String serverUrl = "http://localhost:%s/apis/ccompat/v6";
        properties.setProperty(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, String.format(serverUrl, testPort));

        properties.setProperty(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");

        KafkaProtobufSerializer kafkaProtobufSerializer = new KafkaProtobufSerializer();
        kafkaProtobufSerializer.configure(properties, false);

        byte[] data = kafkaProtobufSerializer.serialize("test",  TableNotification.newBuilder().build());

        KafkaProtobufDeserializer protobufKafkaDeserializer = new KafkaProtobufDeserializer();
        protobufKafkaDeserializer.configure(properties, false);

        protobufKafkaDeserializer.deserialize("test", data);
    }
}

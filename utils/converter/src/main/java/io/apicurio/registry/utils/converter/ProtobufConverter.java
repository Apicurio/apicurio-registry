/*
 * Copyright 2026 Red Hat
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

package io.apicurio.registry.utils.converter;

import com.google.protobuf.Message;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.converter.protobuf.ProtobufData;
import io.apicurio.registry.utils.protobuf.schema.ProtobufSchema;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Protobuf converter.
 */
public class ProtobufConverter<T extends Message> extends SerdeBasedConverter<ProtobufSchema, T> {

    private ProtobufData protobufData;

    @SuppressWarnings("rawtypes")
    @Override
    protected Class<? extends Serializer> serializerClass() {
        return ProtobufKafkaSerializer.class;
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Class<? extends Deserializer> deserializerClass() {
        return ProtobufKafkaDeserializer.class;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        Map copy = new HashMap<>(configs);
        copy.putIfAbsent(REGISTRY_CONVERTER_SERIALIZER_PARAM, new ProtobufKafkaSerializer<>());
        copy.putIfAbsent(REGISTRY_CONVERTER_DESERIALIZER_PARAM, new ProtobufKafkaDeserializer<>());

        super.configure(copy, isKey);

        protobufData = new ProtobufData();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T applySchema(Schema schema, Object value) {
        return (T) protobufData.fromConnectData(schema, value);
    }

    @Override
    protected SchemaAndValue toSchemaAndValue(T result) {
        return protobufData.toConnectData(result);
    }
}

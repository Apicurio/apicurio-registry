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

package io.apicurio.registry.utils.converter;

import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.NonRecordContainer;
import io.apicurio.registry.utils.converter.avro.AvroData;
import io.apicurio.registry.utils.converter.avro.AvroDataConfig;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Avro converter.
 *
 * @author Ales Justin
 * @author Fabian Martinez
 */
public class AvroConverter<T> extends SerdeBasedConverter<org.apache.avro.Schema, T> {
    private AvroData avroData;

    public AvroConverter() {
        super();
    }

    /**
     * @see io.apicurio.registry.utils.converter.SerdeBasedConverter#serializerClass()
     */
    @SuppressWarnings("rawtypes")
    @Override
    protected Class<? extends Serializer> serializerClass() {
        return AvroKafkaSerializer.class;
    }

    /**
     * @see io.apicurio.registry.utils.converter.SerdeBasedConverter#deserializerClass()
     */
    @SuppressWarnings("rawtypes")
    @Override
    protected Class<? extends Deserializer> deserializerClass() {
        return AvroKafkaDeserializer.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // set defaults
        Map copy = new HashMap<>(configs);
        copy.putIfAbsent(REGISTRY_CONVERTER_SERIALIZER_PARAM, new AvroKafkaSerializer<>());
        copy.putIfAbsent(REGISTRY_CONVERTER_DESERIALIZER_PARAM, new AvroKafkaDeserializer<>());

        super.configure(copy, isKey);

        avroData = new AvroData(new AvroDataConfig(copy));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T applySchema(Schema schema, Object value) {
        //noinspection unchecked
        return (T) avroData.fromConnectData(schema, value);
    }

    @Override
    protected SchemaAndValue toSchemaAndValue(T result) {
        if (result instanceof GenericContainer) {
            GenericContainer container = (GenericContainer) result;
            Object value = container;
            Integer version = null; // TODO
            if (result instanceof NonRecordContainer) {
                @SuppressWarnings("rawtypes")
                NonRecordContainer nrc = (NonRecordContainer) result;
                value = nrc.getValue();
            }
            return avroData.toConnectData(container.getSchema(), value, version);
        }
        return new SchemaAndValue(null, result);
    }

}

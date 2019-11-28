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

package io.apicurio.registry.utils.converter;

import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Avro converter.
 *
 * @author Ales Justin
 */
public class AvroConverter<T> extends AbstractConverter<T> {
    public AvroConverter() {
    }

    public AvroConverter(
        AvroKafkaSerializer<T> serializer,
        AvroKafkaDeserializer<T> deserializer
    ) {
        super(serializer, deserializer);
    }

    @Override
    protected Class<? extends Serializer> serializerClass() {
        return AvroKafkaSerializer.class;
    }

    @Override
    protected Class<? extends Deserializer> deserializerClass() {
        return AvroKafkaDeserializer.class;
    }

    public AvroKafkaSerializer<T> getSerializer() {
        return (AvroKafkaSerializer<T>) serializer;
    }

    public AvroKafkaDeserializer<T> getDeserializer() {
        return (AvroKafkaDeserializer<T>) deserializer;
    }
}

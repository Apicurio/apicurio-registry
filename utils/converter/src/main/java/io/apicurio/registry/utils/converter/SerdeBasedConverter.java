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

import io.apicurio.registry.serde.AbstractKafkaDeserializer;
import io.apicurio.registry.serde.AbstractKafkaSerializer;
import io.apicurio.registry.serde.SchemaResolverConfigurer;
import io.apicurio.registry.serde.utils.Utils;
import io.apicurio.registry.utils.IoUtil;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.storage.Converter;

import java.io.Closeable;
import java.util.Map;
import java.util.Objects;

/**
 * Very simplistic converter that delegates most of the work to the configured serializer and deserializer.
 * Subclasses should override applySchema(Schema, Object) and provideSchema(T) or toSchemaAndValue(T).
 *
 * @author Ales Justin
 * @author Fabian Martinez
 */
@SuppressWarnings("rawtypes")
public class SerdeBasedConverter<S, T> extends SchemaResolverConfigurer<S, T> implements Converter, Closeable {

    public static final String REGISTRY_CONVERTER_SERIALIZER_PARAM = "apicurio.registry.converter.serializer";
    public static final String REGISTRY_CONVERTER_DESERIALIZER_PARAM = "apicurio.registry.converter.deserializer";

    protected Serializer<T> serializer;
    private boolean createdSerializer;

    protected Deserializer<T> deserializer;
    private boolean createdDeserializer;

    public SerdeBasedConverter() {
        super();
    }

    protected Class<? extends Serializer> serializerClass() {
        return Serializer.class;
    }

    protected Class<? extends Deserializer> deserializerClass() {
        return Deserializer.class;
    }

    //set converter's schema resolver, to share the cache between serializer and deserializer
    @SuppressWarnings("unchecked")
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (serializer == null) {
            Object sp = configs.get(REGISTRY_CONVERTER_SERIALIZER_PARAM);
            Utils.instantiate(serializerClass(), sp, this::setSerializer);
            createdSerializer = true;
        }
        if (deserializer == null) {
            Object dsp = configs.get(REGISTRY_CONVERTER_DESERIALIZER_PARAM);
            Utils.instantiate(deserializerClass(), dsp, this::setDeserializer);
            createdDeserializer = true;
        }
        if (AbstractKafkaSerializer.class.isAssignableFrom(serializer.getClass())) {
            AbstractKafkaSerializer<S, T> ser = (AbstractKafkaSerializer<S, T>) serializer;
            super.configure(configs, isKey, ser.schemaParser());
            ser.setSchemaResolver(getSchemaResolver());
            ser.configure(configs, isKey);
        }
        if (AbstractKafkaDeserializer.class.isAssignableFrom(deserializer.getClass())) {
            AbstractKafkaDeserializer<S, T> des = (AbstractKafkaDeserializer<S, T>) deserializer;
            des.setSchemaResolver(getSchemaResolver());
            des.configure(configs, isKey);
        }
    }

    @Override
    public void close() {
        if (createdSerializer) {
            IoUtil.closeIgnore(serializer);
        }
        if (createdDeserializer) {
            IoUtil.closeIgnore(deserializer);
        }
    }

    @SuppressWarnings("unchecked")
    protected T applySchema(Schema schema, Object value) {
        //noinspection unchecked
        return (T) value;
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {
        return serializer.serialize(topic, applySchema(schema, value));
    }

    protected Schema provideSchema(T result) {
        return null;
    }

    protected SchemaAndValue toSchemaAndValue(T result) {
        return new SchemaAndValue(provideSchema(result), result);
    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] bytes) {
        T result = deserializer.deserialize(topic, bytes);
        if (result == null) {
            return SchemaAndValue.NULL;
        }
        return toSchemaAndValue(result);
    }

    public void setSerializer(Serializer<T> serializer) {
        this.serializer = Objects.requireNonNull(serializer);
    }

    public void setDeserializer(Deserializer<T> deserializer) {
        this.deserializer = Objects.requireNonNull(deserializer);
    }

}

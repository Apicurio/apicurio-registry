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

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.storage.Converter;

import java.util.Map;

/**
 * Very simplistic converter -- no Schema handling atm.
 *
 * @author Ales Justin
 */
public class AbstractConverter<T> extends AbstractKafkaSerDe implements Converter, AutoCloseable {
    public static final String REGISTRY_CONVERTER_SERIALIZER_PARAM = "apicurio.registry.converter.serializer";
    public static final String REGISTRY_CONVERTER_DESERIALIZER_PARAM = "apicurio.registry.converter.deserializer";

    protected Serializer<T> serializer;
    private boolean createdSerializer;

    protected Deserializer<T> deserializer;
    private boolean createdDeserializer;

    public AbstractConverter() {
    }

    public AbstractConverter(Serde<T> serde) {
        this(serde.serializer(), serde.deserializer());
    }

    public AbstractConverter(Serializer<T> serializer, Deserializer<T> deserializer) {
        this.serializer = serializer;
        this.deserializer = deserializer;
    }

    protected Class<? extends Serializer> serializerClass() {
        return Serializer.class;
    }

    protected Class<? extends Deserializer> deserializerClass() {
        return Deserializer.class;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        if (serializer == null) {
            Object sp = configs.get(REGISTRY_CONVERTER_SERIALIZER_PARAM);
            instantiate(serializerClass(), sp, this::setSerializer);
            serializer.configure(configs, isKey);
            createdSerializer = true;
        }
        if (deserializer == null) {
            Object dsp = configs.get(REGISTRY_CONVERTER_DESERIALIZER_PARAM);
            instantiate(deserializerClass(), dsp, this::setDeserializer);
            deserializer.configure(configs, isKey);
            createdDeserializer = true;
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
        super.close();
    }

    // TODO -- use schema
    protected T applySchema(Schema schema, Object value) {
        //noinspection unchecked
        return (T) value;
    }

    @Override
    public byte[] fromConnectData(String topic, Schema schema, Object value) {
        return serializer.serialize(topic, applySchema(schema, value));
    }

    // TODO -- get schema
    protected Schema provideSchema(T result) {
        return null;
    }

    @Override
    public SchemaAndValue toConnectData(String topic, byte[] bytes) {
        T result = deserializer.deserialize(topic, bytes);
        return new SchemaAndValue(provideSchema(result), result);
    }

    public void setSerializer(Serializer<T> serializer) {
        this.serializer = serializer;
    }

    public void setDeserializer(Deserializer<T> deserializer) {
        this.deserializer = deserializer;
    }
}

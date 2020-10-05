/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

package io.apicurio.registry.utils.serde;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;

import io.apicurio.registry.client.RegistryRestClient;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaDeserializer<T, U, S extends AbstractKafkaDeserializer<T, U, S>> extends AbstractKafkaSerDe<S> implements Deserializer<U> {
    private SchemaCache<T> cache;

    public AbstractKafkaDeserializer() {
    }

    public AbstractKafkaDeserializer(RegistryRestClient client) {
        super(client);
    }

    private synchronized SchemaCache<T> getCache() {
        if (cache == null) {
            cache = new SchemaCache<T>(getClient()) {
                @Override
                protected T toSchema(InputStream schemaData) {
                    return AbstractKafkaDeserializer.this.toSchema(schemaData);
                }
            };
        }
        return cache;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        super.configure(configs, isKey);
    }

    @Override
    public void reset() {
        getCache().clear();
        super.reset();
    }

    protected abstract T toSchema(InputStream schemaData);

    protected abstract U readData(T schema, ByteBuffer buffer, int start, int length);

    protected abstract U readData(Headers headers, T schema, ByteBuffer buffer, int start, int length);

    @Override
    public U deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        ByteBuffer buffer = getByteBuffer(data);
        long id = getIdHandler().readId(buffer);
        T schema = getCache().getSchema(id);
        int length = buffer.limit() - 1 - getIdHandler().idSize();
        int start = buffer.position() + buffer.arrayOffset();
        return readData(schema, buffer, start, length);
    }

    @Override
    public U deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }
        // check if data contains the magic byte
        if (data[0] == MAGIC_BYTE){
            return deserialize(topic, data);
        } else {
            Long id = headerUtils.getGlobalId(headers);
            if (id == null) {
                String artifactId = headerUtils.getArtifactId(headers);
                Integer version = headerUtils.getVersion(headers);
                id = toGlobalId(artifactId, version);
            }
            T schema = getCache().getSchema(id);
            ByteBuffer buffer = ByteBuffer.wrap(data);
            int length = buffer.limit();
            int start = buffer.position();
            return readData(headers, schema, buffer, start, length);
        }
    }

}

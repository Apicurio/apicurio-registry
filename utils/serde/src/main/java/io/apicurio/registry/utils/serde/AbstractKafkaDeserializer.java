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

package io.apicurio.registry.utils.serde;

import io.apicurio.registry.client.RegistryService;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.ByteBuffer;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaDeserializer<T, U, S extends AbstractKafkaDeserializer<T, U, S>> extends AbstractKafkaSerDe<S> implements Deserializer<U> {
    private SchemaCache<T> cache;

    public AbstractKafkaDeserializer() {
    }

    public AbstractKafkaDeserializer(RegistryService client) {
        super(client);
    }

    private synchronized SchemaCache<T> getCache() {
        if (cache == null) {
            cache = new SchemaCache<T>(getClient()) {
                @Override
                protected T toSchema(Response artifactResponse) {
                    return AbstractKafkaDeserializer.this.toSchema(artifactResponse);
                }
            };
        }
        return cache;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(configs);
    }

    @Override
    public void reset() {
        getCache().clear();
        super.reset();
    }

    protected abstract T toSchema(Response response);

    protected abstract U readData(T schema, ByteBuffer buffer, int start, int length);

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
}

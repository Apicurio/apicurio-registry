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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Serializer;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerializer<T, U, S extends AbstractKafkaSerializer<T, U, S>> extends AbstractKafkaStrategyAwareSerDe<T, S> implements Serializer<U> {

    public AbstractKafkaSerializer() {
        this(null);
    }

    public AbstractKafkaSerializer(RegistryRestClient client) {
        super(client);
    }

    public AbstractKafkaSerializer(
        RegistryRestClient client,
        ArtifactIdStrategy<T> artifactIdStrategy,
        GlobalIdStrategy<T> globalIdStrategy
    ) {
        super(client, artifactIdStrategy, globalIdStrategy);
    }

    protected abstract T toSchema(U data);

    protected abstract ArtifactType artifactType();

    protected abstract void serializeData(T schema, U data, OutputStream out) throws IOException;

    protected abstract void serializeData(Headers headers, T schema, U data, ByteArrayOutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        return serialize(topic, null, data);
    }

    @Override
    public byte[] serialize(String topic, Headers headers, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            T schema = toSchema(data);
            String artifactId = getArtifactIdStrategy().artifactId(topic, isKey(), schema);
            long id = getGlobalIdStrategy().findId(getClient(), artifactId, artifactType(), schema);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (headerUtils != null) {
                headerUtils.addSchemaHeaders(headers, artifactId, id);
                serializeData(headers, schema, data, out);
            } else {
                out.write(MAGIC_BYTE);
                getIdHandler().writeId(id, out);
                serializeData(schema, data, out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}

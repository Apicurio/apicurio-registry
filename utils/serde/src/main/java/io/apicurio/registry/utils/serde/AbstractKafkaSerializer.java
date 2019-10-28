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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.serde.strategy.ArtifactIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.IdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerializer<T, U> extends AbstractKafkaSerDe<T> implements Serializer<U> {
    private ArtifactIdStrategy<T> artifactIdStrategy;
    private IdStrategy<T> idStrategy;

    private boolean key; // do we handle key or value with this ser/de?

    public AbstractKafkaSerializer(RegistryService client) {
        this(client, new TopicIdStrategy<>(), new FindBySchemaIdStrategy<>());
    }

    public AbstractKafkaSerializer(
        RegistryService client,
        ArtifactIdStrategy<T> artifactIdStrategy,
        IdStrategy<T> idStrategy
    ) {
        super(client);
        setArtifactIdStrategy(artifactIdStrategy);
        setIdStrategy(idStrategy);
    }

    public AbstractKafkaSerializer<T, U> setKey(boolean key) {
        this.key = key;
        return this;
    }

    public AbstractKafkaSerializer<T, U> setArtifactIdStrategy(ArtifactIdStrategy<T> artifactIdStrategy) {
        this.artifactIdStrategy = Objects.requireNonNull(artifactIdStrategy);
        return this;
    }

    public AbstractKafkaSerializer<T, U> setIdStrategy(IdStrategy<T> idStrategy) {
        this.idStrategy = Objects.requireNonNull(idStrategy);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        key = isKey;
    }

    protected abstract T toSchema(U data);

    protected abstract ArtifactType artifactType();

    protected abstract void serializeData(T schema, U data, ByteArrayOutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            T schema = toSchema(data);
            String artifactId = artifactIdStrategy.artifactId(topic, key, schema);
            long id = idStrategy.findId(getClient(), artifactId, artifactType(), schema);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            out.write(ByteBuffer.allocate(idSize).putLong(id).array());
            serializeData(schema, data, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
    }
}

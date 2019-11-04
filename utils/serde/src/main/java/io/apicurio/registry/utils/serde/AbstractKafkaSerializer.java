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
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicIdStrategy;
import org.apache.kafka.common.serialization.Serializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerializer<T, U, S extends AbstractKafkaSerializer> extends AbstractKafkaSerDe implements Serializer<U> {
    public static final String REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.artifact-id";
    public static final String REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.global-id";

    private ArtifactIdStrategy<T> artifactIdStrategy;
    private GlobalIdStrategy<T> globalIdStrategy;

    private boolean key; // do we handle key or value with this ser/de?

    public AbstractKafkaSerializer() {
        this(null);
    }

    public AbstractKafkaSerializer(RegistryService client) {
        this(client, new TopicIdStrategy<>(), new FindBySchemaIdStrategy<>());
    }

    public AbstractKafkaSerializer(
        RegistryService client,
        ArtifactIdStrategy<T> artifactIdStrategy,
        GlobalIdStrategy<T> globalIdStrategy
    ) {
        super(client);
        setArtifactIdStrategy(artifactIdStrategy);
        setGlobalIdStrategy(globalIdStrategy);
    }

    protected S self() {
        //noinspection unchecked
        return (S) this;
    }

    public S setKey(boolean key) {
        this.key = key;
        return self();
    }

    public S setArtifactIdStrategy(ArtifactIdStrategy<T> artifactIdStrategy) {
        this.artifactIdStrategy = Objects.requireNonNull(artifactIdStrategy);
        return self();
    }

    public S setGlobalIdStrategy(GlobalIdStrategy<T> globalIdStrategy) {
        this.globalIdStrategy = Objects.requireNonNull(globalIdStrategy);
        return self();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(configs);

        Object ais = configs.get(REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM);
        instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);

        Object gis = configs.get(REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM);
        instantiate(GlobalIdStrategy.class, gis, this::setGlobalIdStrategy);

        key = isKey;
    }

    protected abstract T toSchema(U data);

    protected abstract ArtifactType artifactType();

    protected abstract void serializeData(T schema, U data, OutputStream out) throws IOException;

    @Override
    public byte[] serialize(String topic, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            T schema = toSchema(data);
            String artifactId = artifactIdStrategy.artifactId(topic, key, schema);
            long id = globalIdStrategy.findId(getClient(), artifactId, artifactType(), schema);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(MAGIC_BYTE);
            out.write(ByteBuffer.allocate(idSize).putLong(id).array());
            serializeData(schema, data, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerializer<T, U> extends AbstractKafkaSerDe<T> implements Serializer<U> {
    public static final String REGISTER_ARTIFACT_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.artifact-id";
    public static final String REGISTER_GLOBAL_ID_STRATEGY_CONFIG_PARAM = "apicurio.registry.global-id";

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

    public AbstractKafkaSerializer<T, U> setKey(boolean key) {
        this.key = key;
        return this;
    }

    public AbstractKafkaSerializer<T, U> setArtifactIdStrategy(ArtifactIdStrategy<T> artifactIdStrategy) {
        this.artifactIdStrategy = Objects.requireNonNull(artifactIdStrategy);
        return this;
    }

    public AbstractKafkaSerializer<T, U> setGlobalIdStrategy(GlobalIdStrategy<T> globalIdStrategy) {
        this.globalIdStrategy = Objects.requireNonNull(globalIdStrategy);
        return this;
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        configure(configs);

        Object ais = configs.get(REGISTER_ARTIFACT_ID_STRATEGY_CONFIG_PARAM);
        instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);

        Object gis = configs.get(REGISTER_GLOBAL_ID_STRATEGY_CONFIG_PARAM);
        instantiate(GlobalIdStrategy.class, gis, this::setGlobalIdStrategy);

        key = isKey;
    }

    private <V> void instantiate(Class<V> type, Object value, Consumer<V> setter) {
        if (value != null) {
            if (type.isInstance(value)) {
                setter.accept(type.cast(value));
            } else if (value instanceof Class && type.isAssignableFrom((Class<?>) value)) {
                //noinspection unchecked
                setter.accept(instantiate((Class<V>) value));
            } else if (value instanceof String) {
                Class<V> clazz = loadClass(type, (String) value);
                setter.accept(instantiate(clazz));
            } else {
                throw new IllegalArgumentException(String.format("Cannot handle configuration [%s]: %s", key, value));
            }
        }
    }

    // can we overridden if needed; e.g. to use different classloader

    protected <V> Class<V> loadClass(Class<V> type, String className) {
        try {
            //noinspection unchecked
            return (Class<V>) type.getClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected <V> V instantiate(Class<V> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
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

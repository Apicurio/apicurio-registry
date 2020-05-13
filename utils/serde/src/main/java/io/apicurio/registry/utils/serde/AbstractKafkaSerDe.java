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

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.strategy.Legacy4ByteIdHandler;
import io.apicurio.registry.utils.serde.strategy.DefaultIdHandler;
import io.apicurio.registry.utils.serde.strategy.IdHandler;
import io.apicurio.registry.utils.serde.util.Utils;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerDe<T extends AbstractKafkaSerDe<T>> implements AutoCloseable {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    public static final String REGISTRY_URL_CONFIG_PARAM = "apicurio.registry.url";
    public static final String REGISTRY_CACHED_CONFIG_PARAM = "apicurio.registry.cached";

    public static final String REGISTRY_ID_HANDLER_CONFIG_PARAM = "apicurio.registry.id-handler";
    public static final String REGISTRY_CONFLUENT_ID_HANDLER_CONFIG_PARAM = "apicurio.registry.as-confluent";

    public static final byte MAGIC_BYTE = 0x0;

    private IdHandler idHandler;

    private RegistryService client;

    public AbstractKafkaSerDe() {
    }

    public AbstractKafkaSerDe(RegistryService client) {
        this.client = client;
    }

    public static ByteBuffer getByteBuffer(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.get() != MAGIC_BYTE) {
            throw new SerializationException("Unknown magic byte!");
        }
        return buffer;
    }

    protected T self() {
        //noinspection unchecked
        return (T) this;
    }

    public IdHandler getIdHandler() {
        if (idHandler == null) {
            idHandler = new DefaultIdHandler();
        }
        return idHandler;
    }

    public T setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
        return self();
    }

    public T asLegacyId() {
        return setIdHandler(new Legacy4ByteIdHandler());
    }

    protected void configure(Map<String, ?> configs) {
        if (client == null) {
            String baseUrl = (String) configs.get(REGISTRY_URL_CONFIG_PARAM);
            if (baseUrl == null) {
                throw new IllegalArgumentException("Missing registry base url, set " + REGISTRY_URL_CONFIG_PARAM);
            }
            try {
                Object cached = configs.get(REGISTRY_CACHED_CONFIG_PARAM);
                if (Utils.isTrue(cached)) {
                    client = RegistryClient.cached(baseUrl);
                } else {
                    client = RegistryClient.create(baseUrl);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        if (idHandler == null) {
            Object idh = configs.get(REGISTRY_ID_HANDLER_CONFIG_PARAM);
            instantiate(IdHandler.class, idh, this::setIdHandler);

            if (Utils.isTrue(configs.get(REGISTRY_CONFLUENT_ID_HANDLER_CONFIG_PARAM))) {
                if (idHandler != null && !(idHandler instanceof Legacy4ByteIdHandler)) {
                    log.warn(String.format("Duplicate id-handler configuration: %s vs. %s", idh, "as-confluent"));
                }
                setIdHandler(new Legacy4ByteIdHandler());
            }
        }
    }

    protected <V> void instantiate(Class<V> type, Object value, Consumer<V> setter) {
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
                throw new IllegalArgumentException(String.format("Cannot handle configuration [%s]: %s", type.getName(), value));
            }
        }
    }

    // can be overridden if needed; e.g. to use different classloader

    protected <V> Class<V> loadClass(Class<V> type, String className) {
        try {
            //noinspection unchecked
            return (Class<V>) type.getClassLoader().loadClass(className);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    // can be overridden if needed; e.g. to use different instantiation mechanism

    protected <V> V instantiate(Class<V> clazz) {
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected RegistryService getClient() {
        return client;
    }

    public void reset() {
        if (client != null) {
            client.reset();
        }
    }

    public void close() {
        IoUtil.closeIgnore(client);
    }
}

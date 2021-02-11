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

package io.apicurio.registry.serde;

import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.utils.HeaderUtils;
import io.apicurio.registry.serde.utils.Utils;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 * @author Fabian Martinez
 */
public abstract class AbstractKafkaSerDe<T, U, S extends AbstractKafkaSerDe<T, U, S>> implements SchemaParser<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public static final byte MAGIC_BYTE = 0x0;
    protected boolean key; // do we handle key or value with this ser/de?

    private SchemaResolver<T, U> schemaResolver;
    private IdHandler idHandler;
    protected HeaderUtils headerUtils;


    public AbstractKafkaSerDe() {
        //empty
    }

    public AbstractKafkaSerDe(RegistryClient client) {
        this(client, new DefaultSchemaResolver<>());
    }

    public AbstractKafkaSerDe(SchemaResolver<T, U> schemaResolver) {
        this(null, schemaResolver);
    }

    public AbstractKafkaSerDe(
        RegistryClient client,
        SchemaResolver<T, U> schemaResolver
    ) {
        this();
        setSchemaResolver(schemaResolver);
        getSchemaResolver().setClient(client);
    }

    protected SchemaResolver<T, U> getSchemaResolver() {
        return schemaResolver;
    }

    public S setSchemaResolver(SchemaResolver<T, U> schemaResolver) {
        this.schemaResolver = Objects.requireNonNull(schemaResolver);
        return self();
    }

    protected void configure(Map<String, ?> configs, boolean isKey) {
        key = isKey;

        if (this.schemaResolver == null) {
            Object sr = configs.get(SerdeConfigKeys.SCHEMA_RESOLVER);
            if (null == sr) {
                this.setSchemaResolver(new DefaultSchemaResolver<>());
            } else {
                Utils.instantiate(SchemaResolver.class, sr, this::setSchemaResolver);
            }
        }
        getSchemaResolver().configure(configs, isKey, this);

        if (idHandler == null) {
            Object idh = configs.get(SerdeConfigKeys.ID_HANDLER);
            Utils.instantiate(IdHandler.class, idh, this::setIdHandler);

            if (Utils.isTrue(configs.get(SerdeConfigKeys.ENABLE_CONFLUENT_ID_HANDLER))) {
                if (idHandler != null && !(idHandler instanceof Legacy4ByteIdHandler)) {
                    log.warn(String.format("Duplicate id-handler configuration: %s vs. %s", idh, "as-confluent"));
                }
                setIdHandler(new Legacy4ByteIdHandler());
            }
        }

        if (Utils.isTrue(configs.get(SerdeConfigKeys.USE_HEADERS))) {
            headerUtils = new HeaderUtils((Map<String, Object>) configs, isKey);
        }
    }

    protected S self() {
        //noinspection unchecked
        return (S) this;
    }

    public IdHandler getIdHandler() {
        if (idHandler == null) {
            idHandler = new DefaultIdHandler();
        }
        return idHandler;
    }

    public S setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
        return self();
    }

    public S asLegacyId() {
        return setIdHandler(new Legacy4ByteIdHandler());
    }

    public void reset() {
        schemaResolver.reset();
    }

    protected boolean isKey() {
        return key;
    }

    public static ByteBuffer getByteBuffer(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        if (buffer.get() != MAGIC_BYTE) {
            throw new SerializationException("Unknown magic byte!");
        }
        return buffer;
    }

}

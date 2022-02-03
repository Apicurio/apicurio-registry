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

import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;
import io.apicurio.registry.serde.headers.HeadersHandler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 * @author Fabian Martinez
 */
public abstract class AbstractKafkaSerDe<T, U> extends SchemaResolverConfigurer<T, U> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public static final byte MAGIC_BYTE = 0x0;
    protected boolean key; // do we handle key or value with this ser/de?

    protected IdHandler idHandler;
    protected HeadersHandler headersHandler;

    public AbstractKafkaSerDe() {
        super();
    }

    public AbstractKafkaSerDe(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerDe(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerDe(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public abstract void configure(Map<String, ?> configs, boolean isKey);

    protected void configure(BaseKafkaSerDeConfig config, boolean isKey) {
        super.configure(config.originals(), isKey, schemaParser());
        key = isKey;
        if (idHandler == null) {
            Object idh = config.getIdHandler();
            Utils.instantiate(IdHandler.class, idh, this::setIdHandler);

            if (config.enableConfluentIdHandler()) {
                if (idHandler != null && !(idHandler instanceof Legacy4ByteIdHandler)) {
                    log.warn(String.format("Duplicate id-handler configuration: %s vs. %s", idh, "as-confluent"));
                }
                setIdHandler(new Legacy4ByteIdHandler());
            }
        }
        idHandler.configure(config.originals(), isKey);

        boolean headersEnabled = config.enableHeaders();
        if (headersEnabled) {
            Object headersHandler = config.getHeadersHandler();
            Utils.instantiate(HeadersHandler.class, headersHandler, this::setHeadersHandler);
            this.headersHandler.configure(config.originals(), isKey);
        }
    }

    public abstract SchemaParser<T, U> schemaParser();

    public IdHandler getIdHandler() {
        return idHandler;
    }

    public void setHeadersHandler(HeadersHandler headersHandler) {
        this.headersHandler = headersHandler;
    }

    public void setIdHandler(IdHandler idHandler) {
        this.idHandler = Objects.requireNonNull(idHandler);
    }

    public void asLegacyId() {
        setIdHandler(new Legacy4ByteIdHandler());
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

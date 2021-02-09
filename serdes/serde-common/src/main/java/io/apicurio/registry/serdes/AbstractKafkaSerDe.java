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

package io.apicurio.registry.serdes;

import io.apicurio.registry.serdes.utils.HeaderUtils;
import io.apicurio.registry.serdes.utils.Utils;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerDe<T extends AbstractKafkaSerDe<T>> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    public static final byte MAGIC_BYTE = 0x0;
    protected boolean key; // do we handle key or value with this ser/de?

    private IdHandler idHandler;

//    private RegistryClient client;

    protected HeaderUtils headerUtils;


    public AbstractKafkaSerDe() {
        //empty
    }

//    public AbstractKafkaSerDe(RegistryClient client) {
//        this.client = client;
//    }

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

    protected void configure(Map<String, ?> configs, boolean isKey) {
//        if (client == null) {
//            String baseUrl = (String) configs.get(SerdeConfigKeys.REGISTRY_URL);
//            if (baseUrl == null) {
//                throw new IllegalArgumentException("Missing registry base url, set " + SerdeConfigKeys.REGISTRY_URL);
//            }
//
//            String authServerURL = (String) configs.get(SerdeConfigKeys.AUTH_SERVICE_URL);
//
//            try {
//                if (authServerURL != null) {
////                    client = configureClientWithAuthentication(configs, baseUrl, authServerURL);
//                } else {
//                    client = RegistryClientFactory.create(baseUrl);
////                    client = RegistryClientFactory.create(baseUrl, new HashMap<>(configs));
//                }
//            } catch (Exception e) {
//                throw new IllegalStateException(e);
//            }
//        }
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
        key = isKey;
    }

    public void reset() {
        //empty
    }

//    @Override
//    public void close() {
//        IoUtil.closeIgnore(client);
//    }

    protected boolean isKey() {
        return key;
    }

    public Object setKey(boolean key) {
        this.key = key;
        return self();
    }

}

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

/**
 * Common class for both serializer and deserializer.
 *
 * @author Ales Justin
 */
public abstract class AbstractKafkaSerDe<T> {
    public static final String AUTO_REGISTER_SCHEMA_CONFIG_PARAM = "apicurio.kafka.serde.autoRegisterSchema";

    protected static final byte MAGIC_BYTE = 0x0;
    protected static final int idSize = 8;

    private final RegistryService client;

    public AbstractKafkaSerDe(RegistryService client) {
        this.client = client;
    }

    protected RegistryService getClient() {
        return client;
    }
}

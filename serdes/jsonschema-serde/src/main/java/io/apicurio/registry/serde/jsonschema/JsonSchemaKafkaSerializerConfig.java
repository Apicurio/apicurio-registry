/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde.jsonschema;

import static io.apicurio.registry.serde.SerdeConfig.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;

/**
 * @author Fabian Martinez
 */
public class JsonSchemaKafkaSerializerConfig extends BaseKafkaSerDeConfig {

    private static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(VALIDATION_ENABLED, Type.BOOLEAN, VALIDATION_ENABLED_DEFAULT, Importance.MEDIUM, "Whether to validate the data against the json schema");
        return configDef;
    }

    /**
     * Constructor.
     * @param originals
     */
    public JsonSchemaKafkaSerializerConfig(Map<?, ?> originals) {
        super(configDef(), originals);

    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }

}

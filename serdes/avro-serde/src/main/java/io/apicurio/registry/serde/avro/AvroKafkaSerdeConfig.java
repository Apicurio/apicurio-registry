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

package io.apicurio.registry.serde.avro;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;

/**
 * @author Fabian Martinez
 */
public class AvroKafkaSerdeConfig extends BaseKafkaSerDeConfig {

    /**
     * Used by the Avro serde classes to choose an <code>io.apicurio.registry.serde.avro.AvroEncoding</code>,
     * for example <code>JSON</code> or </code>BINARY</code>.  Serializer and Deserializer configuration must match.
     */
    public static final String AVRO_ENCODING = "apicurio.registry.avro.encoding";
    public static final String AVRO_ENCODING_JSON = "JSON";
    public static final String AVRO_ENCODING_BINARY = "BINARY";

    public static final String AVRO_DATUM_PROVIDER = "apicurio.registry.avro-datum-provider";
    public static final String AVRO_DATUM_PROVIDER_DEFAULT = DefaultAvroDatumProvider.class.getName();

    public static final String USE_SPECIFIC_AVRO_READER = "apicurio.registry.use-specific-avro-reader";
    public static final boolean USE_SPECIFIC_AVRO_READER_DEFAULT = false;

    private static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(AVRO_ENCODING, Type.STRING, AvroEncoding.BINARY.name(), Importance.MEDIUM, "TODO docs")
                .define(AVRO_DATUM_PROVIDER, Type.CLASS, AVRO_DATUM_PROVIDER_DEFAULT, Importance.MEDIUM, "TODO docs")
                .define(USE_SPECIFIC_AVRO_READER, Type.BOOLEAN, USE_SPECIFIC_AVRO_READER_DEFAULT, Importance.MEDIUM, "TODO docs");
        return configDef;
    }

    /**
     * Constructor.
     * @param configDef
     * @param originals
     */
    public AvroKafkaSerdeConfig(Map<?, ?> originals) {
        super(configDef(), originals);
    }

    public AvroEncoding getAvroEncoding() {
        return AvroEncoding.valueOf(this.getString(AVRO_ENCODING));
    }

    public Class<?> getAvroDatumProvider() {
        return this.getClass(AVRO_DATUM_PROVIDER);
    }

    public boolean useSpecificAvroReader() {
        return this.getBoolean(USE_SPECIFIC_AVRO_READER);
    }

}

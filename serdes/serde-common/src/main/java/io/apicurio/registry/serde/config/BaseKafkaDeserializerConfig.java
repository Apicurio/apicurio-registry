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

package io.apicurio.registry.serde.config;

import static io.apicurio.registry.serde.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER;
import static io.apicurio.registry.serde.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER_DEFAULT;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

/**
 * @author Fabian Martinez
 */
public class BaseKafkaDeserializerConfig extends BaseKafkaSerDeConfig {

    public static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(FALLBACK_ARTIFACT_PROVIDER, Type.CLASS, FALLBACK_ARTIFACT_PROVIDER_DEFAULT, Importance.HIGH, "TODO docs");

        return configDef;
      }

    public BaseKafkaDeserializerConfig(Map<?, ?> originals) {
        super(configDef(), originals);
    }

    public Object getFallbackArtifactProvider() {
        return this.get(FALLBACK_ARTIFACT_PROVIDER);
    }

}

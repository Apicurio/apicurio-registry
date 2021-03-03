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

package io.apicurio.registry.serde.headers;

import static io.apicurio.registry.serde.SerdeConfig.*;
import static io.apicurio.registry.serde.SerdeHeaders.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;

/**
 * @author Fabian Martinez
 */
public class DefaultHeadersHandlerConfig extends BaseKafkaSerDeConfig {

    public static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_GLOBAL_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_CONTENT_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_CONTENT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_GROUP_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_GROUP_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_ARTIFACT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_VERSION_OVERRIDE_NAME, Type.STRING, HEADER_KEY_VERSION, Importance.HIGH, "TODO docs")

                .define(HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_GLOBAL_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_CONTENT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_GROUP_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_GROUP_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_ARTIFACT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_VERSION_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_VERSION, Importance.HIGH, "TODO docs");

        return configDef;
      }

    public DefaultHeadersHandlerConfig(Map<?, ?> originals) {
        super(configDef(), originals);
    }

    public String getKeyGlobalIdHeader() {
        return this.getString(HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME);
    }

    public String getKeyContentIdHeader() {
        return this.getString(HEADER_KEY_CONTENT_ID_OVERRIDE_NAME);
    }

    public String getKeyGroupIdHeader() {
        return this.getString(HEADER_KEY_GROUP_ID_OVERRIDE_NAME);
    }

    public String getKeyArtifactIdHeader() {
        return this.getString(HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME);
    }

    public String getKeyVersionHeader() {
        return this.getString(HEADER_KEY_VERSION_OVERRIDE_NAME);
    }

    ////

    public String getValueGlobalIdHeader() {
        return this.getString(HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME);
    }

    public String getValueContentIdHeader() {
        return this.getString(HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME);
    }

    public String getValueGroupIdHeader() {
        return this.getString(HEADER_VALUE_GROUP_ID_OVERRIDE_NAME);
    }

    public String getValueArtifactIdHeader() {
        return this.getString(HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME);
    }

    public String getValueVersionHeader() {
        return this.getString(HEADER_VALUE_VERSION_OVERRIDE_NAME);
    }

}

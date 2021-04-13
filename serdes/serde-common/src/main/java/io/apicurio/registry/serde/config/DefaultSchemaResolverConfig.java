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

import static io.apicurio.registry.serde.SerdeConfig.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

/**
 * @author Fabian Martinez
 */
public class DefaultSchemaResolverConfig extends BaseKafkaSerDeConfig {

    public static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(REGISTRY_URL, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_SERVICE_URL, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_REALM, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_CLIENT_ID, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_CLIENT_SECRET, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_USERNAME, Type.STRING, null, Importance.HIGH, "TODO docs")
                .define(AUTH_PASSWORD, Type.STRING, null, Importance.HIGH, "TODO docs")

                .define(ARTIFACT_RESOLVER_STRATEGY, Type.CLASS, ARTIFACT_RESOLVER_STRATEGY_DEFAULT, Importance.HIGH, "TODO docs")

                .define(AUTO_REGISTER_ARTIFACT, Type.BOOLEAN, AUTO_REGISTER_ARTIFACT_DEFAULT, Importance.HIGH, "TODO docs")
                .define(AUTO_REGISTER_ARTIFACT_IF_EXISTS, Type.STRING, AUTO_REGISTER_ARTIFACT_IF_EXISTS_DEFAULT, Importance.MEDIUM, "TODO docs")

                .define(FIND_LATEST_ARTIFACT, Type.BOOLEAN, FIND_LATEST_ARTIFACT_DEFAULT, Importance.HIGH, "TODO docs")

                .define(CHECK_PERIOD_MS, Type.LONG, null, Importance.MEDIUM, "TODO docs")

                .define(EXPLICIT_ARTIFACT_GROUP_ID, Type.STRING, null, Importance.MEDIUM, "TODO docs")
                .define(EXPLICIT_ARTIFACT_ID, Type.STRING, null, Importance.MEDIUM, "TODO docs");

        return configDef;
      }

    public DefaultSchemaResolverConfig(Map<?, ?> originals) {
        super(configDef(), originals);
    }

    public String getRegistryUrl() {
        return this.getString(REGISTRY_URL);
    }

    public String getAuthServiceUrl() {
        return this.getString(AUTH_SERVICE_URL);
    }

    public String getAuthRealm() {
        return this.getString(AUTH_REALM);
    }

    public String getAuthClientId() {
        return this.getString(AUTH_CLIENT_ID);
    }

    public String getAuthClientSecret() {
        return this.getString(AUTH_CLIENT_ID);
    }

    public String getAuthUsername() {
        return this.getString(AUTH_USERNAME);
    }

    public String getAuthPassword() {
        return this.getString(AUTH_PASSWORD);
    }

    public Object getArtifactResolverStrategy() {
        return this.get(ARTIFACT_RESOLVER_STRATEGY);
    }

    public boolean autoRegisterArtifact() {
        return this.getBoolean(AUTO_REGISTER_ARTIFACT);
    }

    public String autoRegisterArtifactIfExists() {
        return this.getString(AUTO_REGISTER_ARTIFACT_IF_EXISTS);
    }

    public boolean findLatest() {
        return this.getBoolean(FIND_LATEST_ARTIFACT);
    }

    public Object getCheckPeriodMs() {
        return this.get(CHECK_PERIOD_MS);
    }

    public String getExplicitArtifactGroupId() {
        return this.getString(EXPLICIT_ARTIFACT_GROUP_ID);
    }

    public String getExplicitArtifactId() {
        return this.getString(EXPLICIT_ARTIFACT_ID);
    }

    public String getExplicitArtifactVersion() {
        Object version = this.originals().get(EXPLICIT_ARTIFACT_VERSION);
        if (version == null) {
            return null;
        }
        return version.toString();
    }

}

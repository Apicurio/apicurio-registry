package io.apicurio.registry.serde.headers;

import static io.apicurio.registry.serde.SerdeConfig.*;
import static io.apicurio.registry.serde.SerdeHeaders.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;


public class DefaultHeadersHandlerConfig extends BaseKafkaSerDeConfig {

    public static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_GLOBAL_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_CONTENT_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_CONTENT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, Type.STRING, HEADER_KEY_CONTENT_HASH, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_GROUP_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_GROUP_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME, Type.STRING, HEADER_KEY_ARTIFACT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_KEY_VERSION_OVERRIDE_NAME, Type.STRING, HEADER_KEY_VERSION, Importance.HIGH, "TODO docs")

                .define(HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_GLOBAL_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_CONTENT_ID, Importance.HIGH, "TODO docs")
                .define(HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, Type.STRING, HEADER_VALUE_CONTENT_HASH, Importance.HIGH, "TODO docs")
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

    public String getKeyContentHashHeader() {
        return this.getString(HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME);
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

    public String getValueContentHashHeader() {
        return this.getString(HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME);
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

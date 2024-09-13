package io.apicurio.registry.serde.headers;

import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.serde.config.KafkaSerdeConfig.*;
import static io.apicurio.registry.serde.headers.KafkaSerdeHeaders.*;
import static java.util.Map.entry;

public class DefaultHeadersHandlerConfig extends SerdeConfig {

    private final Map<String, ?> configDefs = Map.ofEntries(
            entry(HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME, HEADER_KEY_GLOBAL_ID),
            entry(HEADER_KEY_CONTENT_ID_OVERRIDE_NAME, HEADER_KEY_CONTENT_ID),
            entry(HEADER_KEY_CONTENT_HASH_OVERRIDE_NAME, HEADER_KEY_CONTENT_HASH),
            entry(HEADER_KEY_GROUP_ID_OVERRIDE_NAME, HEADER_KEY_GROUP_ID),
            entry(HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME, HEADER_KEY_ARTIFACT_ID),
            entry(HEADER_KEY_VERSION_OVERRIDE_NAME, HEADER_KEY_VERSION),
            entry(HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME, HEADER_VALUE_GLOBAL_ID),
            entry(HEADER_VALUE_CONTENT_ID_OVERRIDE_NAME, HEADER_VALUE_CONTENT_ID),
            entry(HEADER_VALUE_CONTENT_HASH_OVERRIDE_NAME, HEADER_VALUE_CONTENT_HASH),
            entry(HEADER_VALUE_GROUP_ID_OVERRIDE_NAME, HEADER_VALUE_GROUP_ID),
            entry(HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, HEADER_VALUE_ARTIFACT_ID),
            entry(HEADER_VALUE_VERSION_OVERRIDE_NAME, HEADER_VALUE_VERSION)

    );

    public DefaultHeadersHandlerConfig(Map<String, Object> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
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

package io.apicurio.registry.serde.protobuf;

import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.HashMap;
import java.util.Map;

public class ProtobufSerializerConfig extends SerdeConfig {

    /**
     * Constructor.
     *
     * @param originals
     */
    public ProtobufSerializerConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }

    @Override
    protected Map<String, ?> getDefaults() {
        Map<String, Object> joint = new HashMap<>(super.getDefaults());
        joint.putAll(DEFAULTS);
        return joint;
    }

    private static final Map<String, ?> DEFAULTS = Map.of(VALIDATION_ENABLED, VALIDATION_ENABLED_DEFAULT);

}

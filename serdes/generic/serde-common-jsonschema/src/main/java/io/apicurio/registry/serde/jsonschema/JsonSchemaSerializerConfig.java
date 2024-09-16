package io.apicurio.registry.serde.jsonschema;

import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.HashMap;
import java.util.Map;

public class JsonSchemaSerializerConfig extends SerdeConfig {

    /**
     * Constructor.
     *
     * @param originals
     */
    public JsonSchemaSerializerConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }

    private static final Map<String, ?> DEFAULTS = Map.of(VALIDATION_ENABLED, VALIDATION_ENABLED_DEFAULT);

    @Override
    protected Map<String, ?> getDefaults() {
        Map<String, Object> joint = new HashMap<>(super.getDefaults());
        joint.putAll(DEFAULTS);
        return joint;
    }
}

package io.apicurio.registry.serde.config;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.serde.config.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER;
import static io.apicurio.registry.serde.config.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER_DEFAULT;
import static java.util.Map.entry;

public class SerdeDeserializerConfig extends SerdeConfig {

    public SerdeDeserializerConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    private static final Map<String, Object> DEFAULTS = Map
            .ofEntries(entry(FALLBACK_ARTIFACT_PROVIDER, FALLBACK_ARTIFACT_PROVIDER_DEFAULT));

    public Object getFallbackArtifactProvider() {
        return this.getObject(FALLBACK_ARTIFACT_PROVIDER);
    }

    @Override
    protected Map<String, ?> getDefaults() {
        Map<String, Object> joint = new HashMap<>(super.getDefaults());
        joint.putAll(DEFAULTS);
        return joint;
    }
}

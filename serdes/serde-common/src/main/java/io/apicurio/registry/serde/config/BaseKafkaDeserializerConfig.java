package io.apicurio.registry.serde.config;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import java.util.Map;

import static io.apicurio.registry.serde.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER;
import static io.apicurio.registry.serde.SerdeConfig.FALLBACK_ARTIFACT_PROVIDER_DEFAULT;

public class BaseKafkaDeserializerConfig extends BaseKafkaSerDeConfig {

    public static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef().define(FALLBACK_ARTIFACT_PROVIDER, Type.CLASS,
                FALLBACK_ARTIFACT_PROVIDER_DEFAULT, Importance.HIGH, "TODO docs");

        return configDef;
    }

    public BaseKafkaDeserializerConfig(Map<?, ?> originals) {
        super(configDef(), originals);
    }

    public Object getFallbackArtifactProvider() {
        return this.get(FALLBACK_ARTIFACT_PROVIDER);
    }

}

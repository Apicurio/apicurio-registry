package io.apicurio.registry.serde.protobuf;

import static io.apicurio.registry.serde.SerdeConfig.VALIDATION_ENABLED;
import static io.apicurio.registry.serde.SerdeConfig.VALIDATION_ENABLED_DEFAULT;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;


public class ProtobufKafkaSerializerConfig extends BaseKafkaSerDeConfig {

    private static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(VALIDATION_ENABLED, Type.BOOLEAN, VALIDATION_ENABLED_DEFAULT, Importance.MEDIUM, "Whether to validate the data being sent adheres to the schema being used");
        return configDef;
    }

    /**
     * Constructor.
     * @param originals
     */
    public ProtobufKafkaSerializerConfig(Map<?, ?> originals) {
        super(configDef(), originals);

    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }

}

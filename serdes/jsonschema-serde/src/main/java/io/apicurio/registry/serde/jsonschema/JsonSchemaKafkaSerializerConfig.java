package io.apicurio.registry.serde.jsonschema;

import static io.apicurio.registry.serde.SerdeConfig.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;


public class JsonSchemaKafkaSerializerConfig extends BaseKafkaSerDeConfig {

    private static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(VALIDATION_ENABLED, Type.BOOLEAN, VALIDATION_ENABLED_DEFAULT, Importance.MEDIUM, "Whether to validate the data against the json schema");
        return configDef;
    }

    /**
     * Constructor.
     * @param originals
     */
    public JsonSchemaKafkaSerializerConfig(Map<?, ?> originals) {
        super(configDef(), originals);

    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }

}

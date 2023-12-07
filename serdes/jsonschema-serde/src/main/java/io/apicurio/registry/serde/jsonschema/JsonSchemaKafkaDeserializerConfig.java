package io.apicurio.registry.serde.jsonschema;

import static io.apicurio.registry.serde.SerdeConfig.*;

import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import io.apicurio.registry.serde.config.BaseKafkaSerDeConfig;

public class JsonSchemaKafkaDeserializerConfig extends BaseKafkaSerDeConfig {

    public static final String SPECIFIC_RETURN_CLASS_DOC =
        "The specific class to use for deserializing the data into java objects";

    private static ConfigDef configDef() {
        ConfigDef configDef = new ConfigDef()
                .define(DESERIALIZER_SPECIFIC_KEY_RETURN_CLASS, Type.CLASS, null, Importance.MEDIUM, SPECIFIC_RETURN_CLASS_DOC)
                .define(DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, Type.CLASS, null, Importance.MEDIUM, SPECIFIC_RETURN_CLASS_DOC)
                .define(VALIDATION_ENABLED, Type.BOOLEAN, VALIDATION_ENABLED_DEFAULT, Importance.MEDIUM, "Whether to validate the data against the json schema");
        return configDef;
    }

    private boolean isKey;

    /**
     * Constructor.
     * @param originals
     */
    public JsonSchemaKafkaDeserializerConfig(Map<?, ?> originals, boolean isKey) {
        super(configDef(), originals);
        this.isKey = isKey;

    }

    public Class<?> getSpecificReturnClass() {
        if (isKey) {
            return this.getClass(DESERIALIZER_SPECIFIC_KEY_RETURN_CLASS);
        } else {
            return this.getClass(DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS);
        }
    }

    public boolean validationEnabled() {
        return this.getBoolean(VALIDATION_ENABLED);
    }
}

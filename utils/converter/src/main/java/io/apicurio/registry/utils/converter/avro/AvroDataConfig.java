package io.apicurio.registry.utils.converter.avro;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.HashMap;
import java.util.Map;

public class AvroDataConfig extends AbstractConfig {

    public static final String ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG = "enhanced.avro.schema.support";
    public static final boolean ENHANCED_AVRO_SCHEMA_SUPPORT_DEFAULT = false;
    public static final String ENHANCED_AVRO_SCHEMA_SUPPORT_DOC = "Toggle for enabling/disabling enhanced avro schema support: Enum symbol preservation and "
            + "Package Name awareness";

    public static final String CONNECT_META_DATA_CONFIG = "connect.meta.data";
    public static final boolean CONNECT_META_DATA_DEFAULT = true;
    public static final String CONNECT_META_DATA_DOC = "Toggle for enabling/disabling connect converter to add its meta data to the output schema "
            + "or not";

    public static final String SCHEMAS_CACHE_SIZE_CONFIG = "schemas.cache.config";
    public static final int SCHEMAS_CACHE_SIZE_DEFAULT = 1000;
    public static final String SCHEMAS_CACHE_SIZE_DOC = "Size of the converted schemas cache";

    public static final String AVRO_NAMESPACE_CONFIG = "avro.namespace";
    public static final String AVRO_NAMESPACE_DEFAULT = "io.confluent.connect.avro";
    public static final String AVRO_NAMESPACE_DOC = "Avro namespace used when converting Connect schemas that do not "
            + "specify a name. Also used as the prefix for Connect-specific metadata properties embedded in Avro schemas.";

    public static ConfigDef baseConfigDef() {
        return new ConfigDef()
                .define(ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG, ConfigDef.Type.BOOLEAN,
                        ENHANCED_AVRO_SCHEMA_SUPPORT_DEFAULT, ConfigDef.Importance.MEDIUM,
                        ENHANCED_AVRO_SCHEMA_SUPPORT_DOC)
                .define(CONNECT_META_DATA_CONFIG, ConfigDef.Type.BOOLEAN, CONNECT_META_DATA_DEFAULT,
                        ConfigDef.Importance.LOW, CONNECT_META_DATA_DOC)
                .define(SCHEMAS_CACHE_SIZE_CONFIG, ConfigDef.Type.INT, SCHEMAS_CACHE_SIZE_DEFAULT,
                        ConfigDef.Importance.LOW, SCHEMAS_CACHE_SIZE_DOC)
                .define(AVRO_NAMESPACE_CONFIG, ConfigDef.Type.STRING, AVRO_NAMESPACE_DEFAULT,
                        ConfigDef.Importance.MEDIUM, AVRO_NAMESPACE_DOC);
    }

    public AvroDataConfig(Map<?, ?> props) {
        super(baseConfigDef(), props);
    }

    public boolean isEnhancedAvroSchemaSupport() {
        return this.getBoolean(ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG);
    }

    public boolean isConnectMetaData() {
        return this.getBoolean(CONNECT_META_DATA_CONFIG);
    }

    public int getSchemasCacheSize() {
        return this.getInt(SCHEMAS_CACHE_SIZE_CONFIG);
    }

    public String getAvroNamespace() {
        return this.getString(AVRO_NAMESPACE_CONFIG);
    }

    public static class Builder {

        private Map<String, Object> props = new HashMap<>();

        public Builder with(String key, Object value) {
            props.put(key, value);
            return this;
        }

        public AvroDataConfig build() {
            return new AvroDataConfig(props);
        }
    }
}

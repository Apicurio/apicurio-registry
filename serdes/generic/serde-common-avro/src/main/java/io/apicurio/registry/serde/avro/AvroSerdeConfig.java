package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.config.SerdeConfig;

import java.util.HashMap;
import java.util.Map;

public class AvroSerdeConfig extends SerdeConfig {

    /**
     * Used by the Avro serde classes to choose an <code>io.apicurio.registry.serde.avro.AvroEncoding</code>,
     * for example <code>JSON</code> or <code>BINARY</code>. Serializer and Deserializer configuration must
     * match.
     */
    public static final String AVRO_ENCODING = "apicurio.registry.avro.encoding";
    public static final String AVRO_ENCODING_JSON = "JSON";
    public static final String AVRO_ENCODING_BINARY = "BINARY";

    public static final String AVRO_DATUM_PROVIDER = "apicurio.registry.avro-datum-provider";
    public static final String AVRO_DATUM_PROVIDER_DEFAULT = DefaultAvroDatumProvider.class.getName();

    public static final String USE_SPECIFIC_AVRO_READER = "apicurio.registry.use-specific-avro-reader";
    public static final boolean USE_SPECIFIC_AVRO_READER_DEFAULT = false;

    /**
     * Configures the maximum number of entries in the Avro schema parser cache. The cache stores
     * parsed schema results to avoid redundant parsing on repeated calls. Valid values are positive
     * integers.
     */
    public static final String AVRO_SCHEMA_CACHE_SIZE = "apicurio.registry.avro.schema-cache-size";
    public static final long AVRO_SCHEMA_CACHE_SIZE_DEFAULT = 256;

    public AvroSerdeConfig(Map<String, ?> originals) {
        Map<String, Object> joint = new HashMap<>(getDefaults());
        joint.putAll(originals);
        this.originals = joint;
    }

    public AvroEncoding getAvroEncoding() {
        return AvroEncoding.valueOf(this.getString(AVRO_ENCODING));
    }

    public Class<?> getAvroDatumProvider() {
        return this.getClass(AVRO_DATUM_PROVIDER);
    }

    public boolean useSpecificAvroReader() {
        return this.getBoolean(USE_SPECIFIC_AVRO_READER);
    }

    /**
     * Returns the maximum number of entries in the Avro schema parser cache.
     *
     * @return the schema cache size
     */
    public int getSchemaCacheSize() {
        return (int) getLongNonNegative(AVRO_SCHEMA_CACHE_SIZE);
    }

    @Override
    protected Map<String, ?> getDefaults() {
        Map<String, Object> joint = new HashMap<>(super.getDefaults());
        joint.putAll(DEFAULTS);
        return joint;
    }

    private static final Map<String, ?> DEFAULTS = Map.of(AVRO_ENCODING, AvroEncoding.BINARY.name(),
            AVRO_DATUM_PROVIDER, AVRO_DATUM_PROVIDER_DEFAULT, USE_SPECIFIC_AVRO_READER,
            USE_SPECIFIC_AVRO_READER_DEFAULT, AVRO_SCHEMA_CACHE_SIZE, AVRO_SCHEMA_CACHE_SIZE_DEFAULT);
}

package io.apicurio.registry.utils.converter.avro;

import org.apache.kafka.connect.data.Schema;

import java.util.Map;
import java.util.Objects;

public class AvroDataSchemaCacheKey {
    private final String schemaName;
    private final Map<String, String> parameters;

    public AvroDataSchemaCacheKey(Schema schema) {
        this.schemaName = schema.name();
        this.parameters = schema.parameters();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AvroDataSchemaCacheKey other = (AvroDataSchemaCacheKey) obj;
        return Objects.equals(schemaName, other.schemaName) &&
                Objects.equals(parameters, other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaName, parameters);
    }
}

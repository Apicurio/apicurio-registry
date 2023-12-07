package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericContainer;
import org.apache.kafka.common.errors.SerializationException;

import java.util.Objects;

/**
 * Wrapper for all non-record types that includes the schema for the data.
 */
public class NonRecordContainer<T> implements GenericContainer {

    private final Schema schema;
    private final T value;

    public NonRecordContainer(Schema schema, T value) {
        if (schema == null) {
            throw new SerializationException("Schema may not be null.");
        }
        this.schema = schema;
        this.value = value;
    }

    @Override
    public Schema getSchema() {
        return schema;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        NonRecordContainer<?> that = (NonRecordContainer<?>) o;
        return Objects.equals(schema, that.schema)
               && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }
}

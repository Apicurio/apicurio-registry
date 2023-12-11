package io.apicurio.registry.serde.avro;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

public class DefaultAvroDatumProvider<T> implements AvroDatumProvider<T> {
    private Boolean useSpecificAvroReader;
    private Map<String, Schema> schemas = new ConcurrentHashMap<>();

    public DefaultAvroDatumProvider() {
    }

    public DefaultAvroDatumProvider(boolean useSpecificAvroReader) {
        this.useSpecificAvroReader = useSpecificAvroReader;
    }

    public DefaultAvroDatumProvider<T> setUseSpecificAvroReader(boolean useSpecificAvroReader) {
        this.useSpecificAvroReader = useSpecificAvroReader;
        return this;
    }

    @Override
    public void configure(AvroKafkaSerdeConfig config) {
        if (useSpecificAvroReader == null) {
            useSpecificAvroReader = config.useSpecificAvroReader();
        }
    }

    @SuppressWarnings("unchecked")
    private Schema getReaderSchema(Schema schema) {
        return schemas.computeIfAbsent(schema.getFullName(), k -> {
            Class<SpecificRecord> readerClass = SpecificData.get().getClass(schema);
            if (readerClass != null) {
                try {
                    return readerClass.getConstructor().newInstance().getSchema();
                } catch (Exception e) {
                    throw new IllegalStateException(String.format("Error getting schema [%s]: %s",
                                                                  schema.getFullName(),
                                                                  readerClass.getName()),
                                                    e);
                }
            } else {
                throw new IllegalArgumentException("Could not find class "
                                                   + schema.getFullName()
                                                   + " specified in writer's schema whilst finding reader's "
                                                   + "schema for a SpecificRecord.");
            }
        });
    }

    @Override
    public DatumWriter<T> createDatumWriter(T data, Schema schema) {
        if (data instanceof SpecificRecord) {
            return new SpecificDatumWriter<>(schema);
        } else {
            return new GenericDatumWriter<>(schema);
        }
    }

    @Override
    public DatumReader<T> createDatumReader(Schema schema) {
        // do not use SpecificDatumReader if schema is a primitive
        if (useSpecificAvroReader != null && useSpecificAvroReader) {
            if (AvroSchemaUtils.isPrimitive(schema) == false) {
                return new SpecificDatumReader<>(schema, getReaderSchema(schema));
            }
        }
        return new GenericDatumReader<>(schema);
    }

    @Override
    public Schema toSchema(T data) {
        return AvroSchemaUtils.getSchema(data);
    }
}

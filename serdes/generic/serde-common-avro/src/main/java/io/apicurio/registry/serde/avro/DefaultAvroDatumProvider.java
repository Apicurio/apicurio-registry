package io.apicurio.registry.serde.avro;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

import java.util.concurrent.ConcurrentHashMap;

public class DefaultAvroDatumProvider<T> implements AvroDatumProvider<T> {

    private static final int MAX_CACHE_SIZE = 1000;

    private Boolean useSpecificAvroReader;

    /**
     * Cache for reader schemas keyed by schema full name.
     */
    private final ConcurrentHashMap<String, Schema> readerSchemas = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<WriterCacheKey, DatumWriter<T>> writerCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Schema, DatumReader<T>> readerCache = new ConcurrentHashMap<>();

    private record WriterCacheKey(Schema schema, boolean isSpecific) {}

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
    public void configure(AvroSerdeConfig config) {
        if (useSpecificAvroReader == null) {
            useSpecificAvroReader = config.useSpecificAvroReader();
        }
    }

    @SuppressWarnings("unchecked")
    private Schema getReaderSchema(Schema schema) {
        return readerSchemas.computeIfAbsent(schema.getFullName(), k -> {
            Class<SpecificRecord> readerClass = SpecificData.get().getClass(schema);
            if (readerClass != null) {
                try {
                    return readerClass.getConstructor().newInstance().getSchema();
                } catch (Exception e) {
                    throw new IllegalStateException(String.format("Error getting schema [%s]: %s",
                            schema.getFullName(), readerClass.getName()), e);
                }
            } else {
                throw new IllegalArgumentException("Could not find class " + schema.getFullName()
                        + " specified in writer's schema whilst finding reader's "
                        + "schema for a SpecificRecord.");
            }
        });
    }

    @Override
    public DatumWriter<T> createDatumWriter(T data, Schema schema) {
        boolean isSpecific = data instanceof SpecificRecord;
        WriterCacheKey key = new WriterCacheKey(schema, isSpecific);

        return writerCache.computeIfAbsent(key, k -> {
            if (k.isSpecific()) {
                return new SpecificDatumWriter<>(schema);
            } else {
                return new GenericDatumWriter<>(schema);
            }
        });
    }

    @Override
    public DatumReader<T> createDatumReader(Schema schema) {
        return readerCache.computeIfAbsent(schema, k -> {
            // do not use SpecificDatumReader if schema is a primitive
            if (useSpecificAvroReader != null && useSpecificAvroReader) {
                if (!AvroSchemaUtils.isPrimitive(schema)) {
                    return new SpecificDatumReader<>(schema, getReaderSchema(schema));
                }
            }
            return new GenericDatumReader<>(schema);
        });
    }

    @Override
    public Schema toSchema(T data) {
        return AvroSchemaUtils.getSchema(data);
    }
}

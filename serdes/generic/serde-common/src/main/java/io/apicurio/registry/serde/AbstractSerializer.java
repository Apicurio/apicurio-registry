package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.data.SerdeMetadata;
import io.apicurio.registry.serde.data.SerdeRecord;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.apicurio.registry.serde.BaseSerde.MAGIC_BYTE;

public abstract class AbstractSerializer<T, U> implements AutoCloseable {

    /**
     * Default buffer size for serialized output.
     * Pre-sized to avoid array resizing for typical messages.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    /**
     * Maximum number of entries in the fast-path cache.
     * Prevents unbounded memory growth in long-running applications.
     */
    private static final int MAX_CACHE_SIZE = 1000;

    /**
     * Cache key combining topic and a schema identifier.
     * The schemaKey can be either a Class (for SpecificRecord) or a Schema object (for GenericRecord).
     */
    private record SchemaCacheKey(String topic, Object schemaKey) {}

    /**
     * Fast-path cache: maps (topic, schema key) to resolved schema.
     * This bypasses the full resolution flow (object creation + 4 cache lookups)
     * after the first serialization of a message type per topic.
     * Uses LRU eviction to prevent unbounded growth.
     */
    private final Map<SchemaCacheKey, SchemaLookupResult<T>> fastPathCache = createBoundedCache(MAX_CACHE_SIZE);

    private static <K, V> Map<K, V> createBoundedCache(int maxSize) {
        return Collections.synchronizedMap(new LinkedHashMap<K, V>(maxSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize;
            }
        });
    }

    private final BaseSerde<T, U> baseSerde;

    public AbstractSerializer() {
        this.baseSerde = new BaseSerde<>();
    }

    public AbstractSerializer(RegistryClientFacade clientFacade) {
        this.baseSerde = new BaseSerde<>(clientFacade);
    }

    public AbstractSerializer(SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(schemaResolver);
    }

    public AbstractSerializer(RegistryClientFacade clientFacade, SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, schemaResolver);
    }

    public AbstractSerializer(RegistryClientFacade clientFacade, ArtifactReferenceResolverStrategy<T, U> strategy,
                              SchemaResolver<T, U> schemaResolver) {
        this.baseSerde = new BaseSerde<>(clientFacade, strategy, schemaResolver);
    }

    public abstract SchemaParser<T, U> schemaParser();

    public abstract void serializeData(ParsedSchema<T> schema, U data, OutputStream out) throws IOException;

    /**
     * Gets a cache key for the schema associated with this data.
     *
     * For types where the schema is tied to the class (e.g., Avro SpecificRecord, Protobuf GeneratedMessage),
     * this should return the class object.
     *
     * For types where the schema varies per instance (e.g., Avro GenericRecord),
     * this should return an object that uniquely identifies the schema (e.g., the Schema object itself).
     *
     * For types where caching is not safe, this should return null.
     *
     * Subclasses should override this method to provide type-specific logic.
     * The default implementation returns null (no caching).
     *
     * @param data the data to get a cache key for
     * @return an object suitable as a cache key, or null if caching is not safe
     */
    protected Object getSchemaCacheKey(U data) {
        return null;
    }

    public void configure(SerdeConfig config, boolean isKey) {
        baseSerde.configure(config, isKey, schemaParser());
    }

    public byte[] serializeData(String topic, U data) {
        // just return null
        if (data == null) {
            return null;
        }
        try {
            SchemaLookupResult<T> schema = null;
            SchemaCacheKey cacheKey = null;

            // Fast path: use cache if we have a valid schema cache key
            Object schemaKey = getSchemaCacheKey(data);
            if (schemaKey != null) {
                cacheKey = new SchemaCacheKey(topic, schemaKey);
                schema = fastPathCache.get(cacheKey);
            }

            if (schema == null) {
                // Slow path: full resolution
                SerdeMetadata resolverMetadata = new SerdeMetadata(topic, baseSerde.isKey());
                schema = baseSerde.getSchemaResolver()
                        .resolveSchema(new SerdeRecord<>(resolverMetadata, data));

                // Cache result if we have a valid cache key
                if (cacheKey != null) {
                    fastPathCache.put(cacheKey, schema);
                }
            }

            // Pre-size buffer to avoid array resizing for typical messages
            ByteArrayOutputStream out = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
            out.write(MAGIC_BYTE);
            baseSerde.getIdHandler().writeId(schema.toArtifactReference(), out);
            this.serializeData(schema.getParsedSchema(), data, out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public BaseSerde<T, U> getSerdeConfigurer() {
        return baseSerde;
    }

    @Override
    public void close() {
        this.baseSerde.close();
    }
}

package io.apicurio.registry.examples.serdes;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.data.Record;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A custom schema resolver (tour step 11): it wraps the default one and counts lookups, to show the
 * extension point (apicurio.registry.schema-resolver). A real one might add auditing, or resolve
 * schemas from somewhere else.
 */
public class CountingSchemaResolver<S, T> extends DefaultSchemaResolver<S, T> {

    static final AtomicInteger LOOKUPS = new AtomicInteger();

    @Override
    public SchemaLookupResult<S> resolveSchema(Record<T> data) {
        LOOKUPS.incrementAndGet();
        return super.resolveSchema(data);
    }
}

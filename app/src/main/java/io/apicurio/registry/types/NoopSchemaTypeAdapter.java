package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public class NoopSchemaTypeAdapter implements SchemaTypeAdapter {
    public static SchemaTypeAdapter INSTANCE = new NoopSchemaTypeAdapter();

    @Override
    public SchemaWrapper wrapper(String schema) {
        throw new UnsupportedOperationException("Not yet implemented!");
    }
}

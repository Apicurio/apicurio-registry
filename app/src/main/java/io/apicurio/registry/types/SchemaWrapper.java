package io.apicurio.registry.types;

/**
 * @author Ales Justin
 */
public class SchemaWrapper {
    private Object schemaImpl;
    private String canonicalString;

    public SchemaWrapper(Object schemaImpl, String canonicalString) {
        this.schemaImpl = schemaImpl;
        this.canonicalString = canonicalString;
    }

    public <T> T toExactImpl(Class<T> schemaImplType) {
        return schemaImplType.cast(schemaImpl);
    }

    public Object getSchemaImpl() {
        return schemaImpl;
    }

    public String getCanonicalString() {
        return canonicalString;
    }
}

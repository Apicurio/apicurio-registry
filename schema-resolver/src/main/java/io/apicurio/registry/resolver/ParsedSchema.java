package io.apicurio.registry.resolver;

import java.util.List;

public interface ParsedSchema<T> {

    /**
     * @return the parsedSchema
     */
    public T getParsedSchema();

    /**
     * @return the rawSchema
     */
    public byte[] getRawSchema();

    /**
     * @return the schema references (if any)
     */
    public List<ParsedSchema<T>> getSchemaReferences();

    /**
     * @return true if the schema has references
     */
    public boolean hasReferences();

    /**
     * @return the name to be used when referencing this schema
     */
    public String referenceName();

    /**
     * set the name to be used when referencing this schema
     */
    public ParsedSchemaImpl<T> setReferenceName(String referenceName);

    /**
     * @return the rawSchema with all references fully expanded inline (no name-only references).
     * Defaults to {@link #getRawSchema()} when not explicitly set.
     */
    default byte[] getReferencelessRawSchema() {
        return getRawSchema();
    }
}

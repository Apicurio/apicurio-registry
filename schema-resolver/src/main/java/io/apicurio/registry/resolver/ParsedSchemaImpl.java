package io.apicurio.registry.resolver;

import java.util.List;


public class ParsedSchemaImpl<T> implements ParsedSchema<T> {

    private T parsedSchema;
    private byte[] rawSchema;
    private List<ParsedSchema<T>> schemaReferences;
    private String referenceName;

    public ParsedSchemaImpl() {
        //empty
    }

    /**
     * @return the parsedSchema
     */
    @Override
    public T getParsedSchema() {
        return parsedSchema;
    }

    /**
     * @param parsedSchema the parsedSchema to set
     */
    public ParsedSchemaImpl<T> setParsedSchema(T parsedSchema) {
        this.parsedSchema = parsedSchema;
        return this;
    }

    /**
     * @return the rawSchema
     */
    @Override
    public byte[] getRawSchema() {
        return rawSchema;
    }

    /**
     * @param rawSchema the rawSchema to set
     */
    public ParsedSchemaImpl<T> setRawSchema(byte[] rawSchema) {
        this.rawSchema = rawSchema;
        return this;
    }

    /**
     * @return schema references
     */
    public List<ParsedSchema<T>> getSchemaReferences() {
        return schemaReferences;
    }

    /**
     * @param schemaReferences to set
     */
    public ParsedSchemaImpl<T> setSchemaReferences(List<ParsedSchema<T>> schemaReferences) {
        this.schemaReferences = schemaReferences;
        return this;
    }

    @Override
    public boolean hasReferences() {
        return this.schemaReferences != null && !this.schemaReferences.isEmpty();
    }

    @Override
    public String referenceName() {
        return referenceName;
    }

    @Override
    public ParsedSchemaImpl<T> setReferenceName(String referenceName) {
        this.referenceName = referenceName;
        return this;
    }
}

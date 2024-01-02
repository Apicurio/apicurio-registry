package io.apicurio.registry.serde;

/**
 * This class is deprecated and eventually will be replaced by {@link io.apicurio.registry.resolver.ParsedSchemaImpl}
 */
@Deprecated
public class ParsedSchemaImpl<T> implements ParsedSchema<T> {

    private T parsedSchema;
    private byte[] rawSchema;

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
}

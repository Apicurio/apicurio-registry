package io.apicurio.registry.serde;

/**
 * This interface is deprecated and eventually will be replaced by {@link io.apicurio.registry.resolver.ParsedSchema}
 */
@Deprecated
public interface ParsedSchema<T> {

    /**
     * @return the parsedSchema
     */
    public T getParsedSchema();

    /**
     * @return the rawSchema
     */
    public byte[] getRawSchema();

}
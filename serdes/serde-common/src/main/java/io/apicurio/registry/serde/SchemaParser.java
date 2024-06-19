package io.apicurio.registry.serde;

/**
 * This class is deprecated and eventually will be replaced by
 * {@link io.apicurio.registry.resolver.SchemaParser}
 */
@Deprecated
public interface SchemaParser<S> {

    public String artifactType();

    public S parseSchema(byte[] rawSchema);

}

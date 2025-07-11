package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;

/**
 * Common class for both serializer and deserializer.
 */
public abstract class AbstractKafkaSerDe<T, U> extends BaseSerde<T, U> {

    public AbstractKafkaSerDe() {
        super();
    }

    public AbstractKafkaSerDe(RegistryClientFacade sdk) {
        super(sdk);
    }

    public AbstractKafkaSerDe(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerDe(RegistryClientFacade sdk, SchemaResolver<T, U> schemaResolver) {
        super(sdk, schemaResolver);
    }

    public AbstractKafkaSerDe(RegistryClientFacade sdk, ArtifactReferenceResolverStrategy<T, U> strategy,
                              SchemaResolver<T, U> schemaResolver) {
        super(sdk, strategy, schemaResolver);
    }
}

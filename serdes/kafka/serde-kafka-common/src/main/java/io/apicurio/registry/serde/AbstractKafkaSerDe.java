package io.apicurio.registry.serde;

import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;

/**
 * Common class for both serializer and deserializer.
 */
public abstract class AbstractKafkaSerDe<T, U> extends AbstractSerDe<T, U> {

    public AbstractKafkaSerDe() {
        super();
    }

    public AbstractKafkaSerDe(RegistryClient client) {
        super(client);
    }

    public AbstractKafkaSerDe(SchemaResolver<T, U> schemaResolver) {
        super(schemaResolver);
    }

    public AbstractKafkaSerDe(RegistryClient client, SchemaResolver<T, U> schemaResolver) {
        super(client, schemaResolver);
    }

    public AbstractKafkaSerDe(RegistryClient client, ArtifactReferenceResolverStrategy<T, U> strategy,
            SchemaResolver<T, U> schemaResolver) {
        super(client, strategy, schemaResolver);
    }
}

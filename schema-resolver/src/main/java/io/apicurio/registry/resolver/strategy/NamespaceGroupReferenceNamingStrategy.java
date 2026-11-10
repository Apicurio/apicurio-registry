package io.apicurio.registry.resolver.strategy;

/**
 * Registers a referenced schema in a group named after its namespace (Avro namespace, Protobuf package). The
 * artifact ID is the reference name without the leading namespace, so Avro {@code com.acme.Address} becomes
 * {@code com.acme/Address}; Protobuf file paths are kept as they are. Without a namespace, the default group
 * is used.
 */
public class NamespaceGroupReferenceNamingStrategy implements ReferenceNamingStrategy {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReferenceCoordinates name(ReferenceNamingContext context) {
        String namespace = context.namespace();
        String name = context.referenceName();
        if (namespace == null || namespace.isBlank()) {
            return new ReferenceCoordinates(context.defaultGroupId(), name);
        }
        String prefix = namespace + ".";
        String artifactId = name.startsWith(prefix) && name.length() > prefix.length()
                ? name.substring(prefix.length()) : name;
        return new ReferenceCoordinates(namespace, artifactId);
    }
}

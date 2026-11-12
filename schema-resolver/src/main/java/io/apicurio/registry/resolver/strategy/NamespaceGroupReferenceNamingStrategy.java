package io.apicurio.registry.resolver.strategy;

/**
 * Registers a referenced schema in a group named after its namespace (Avro namespace, Protobuf package).
 * For Avro, the artifact ID is the full name without the leading namespace, so {@code com.acme.Address} is
 * registered as artifact {@code Address} in group {@code com.acme}. For other formats, such as Protobuf, the
 * artifact ID is the reference name unchanged (the import path), so distinct files never share an artifact.
 * Without a namespace, the default group is used.
 */
public class NamespaceGroupReferenceNamingStrategy implements ReferenceNamingStrategy {

    private static final String AVRO = "AVRO";

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
        if (!AVRO.equals(context.artifactType())) {
            return new ReferenceCoordinates(namespace, name);
        }
        String prefix = namespace + ".";
        String artifactId = name.startsWith(prefix) && name.length() > prefix.length()
                ? name.substring(prefix.length()) : name;
        return new ReferenceCoordinates(namespace, artifactId);
    }
}

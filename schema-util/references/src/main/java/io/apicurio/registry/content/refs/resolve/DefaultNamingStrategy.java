package io.apicurio.registry.content.refs.resolve;

import io.apicurio.registry.types.ArtifactType;

/**
 * Default naming strategy: Avro targets are named by namespace/simple name, all other targets use the
 * parent group and their normalized source location as artifact ID.
 */
public final class DefaultNamingStrategy implements ArtifactNamingStrategy {

    /** Shared instance. */
    public static final DefaultNamingStrategy INSTANCE = new DefaultNamingStrategy();

    private DefaultNamingStrategy() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArtifactCoordinates name(NamingContext context) {
        IndexedSource target = context.target();
        String parentGroup = context.parent().groupId();
        if (ArtifactType.AVRO.equals(target.artifactType()) && target.avroFullName() != null) {
            String fullName = target.avroFullName();
            int idx = fullName.lastIndexOf('.');
            if (idx < 0) {
                return new ArtifactCoordinates(parentGroup, fullName);
            }
            return new ArtifactCoordinates(fullName.substring(0, idx), fullName.substring(idx + 1));
        }
        return new ArtifactCoordinates(parentGroup, target.source().location());
    }
}
